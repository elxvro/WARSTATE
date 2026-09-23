package com.elxvro.warstate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;
import java.util.Random;

public class GameView extends View {
    private static final int PLAYER = 0;
    private static final int ENEMY = 1;

    private static final int WORLD = 0;
    private static final int BASE = 1;
    private static final int ARMY = 2;
    private static final int TECH = 3;
    private static final int MORE = 4;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final SharedPreferences prefs;

    private final Region[] regions = new Region[] {
            new Region("BURSA",    0.28f, 0.42f, PLAYER, 18, 15500),
            new Region("İSTANBUL", 0.43f, 0.23f, ENEMY,  25, 24300),
            new Region("İZMİR",    0.17f, 0.67f, ENEMY,  20, 17800),
            new Region("ANKARA",   0.60f, 0.46f, ENEMY,  22, 21500),
            new Region("KONYA",    0.53f, 0.72f, ENEMY,  19, 16500),
            new Region("SAMSUN",   0.77f, 0.29f, ENEMY,  21, 19000)
    };

    private final String[] unitNames = {"Asker", "Tank", "Topçu", "Hava"};
    private final String[] unitGlyphs = {"●", "▰", "➤", "✈"};
    private final int[] available = {3500, 120, 80, 12};
    private final int[] selected = {1800, 45, 25, 5};
    private final int[] unitPower = {2, 55, 80, 320};
    private final int[] recruitAmount = {500, 10, 5, 2};
    private final int[] recruitCost = {700, 1200, 1000, 1800};
    private final int[] techLevel = {0, 0, 0};

    private int currentScreen = WORLD;
    private int selectedSource = 0;
    private int selectedTarget = 1;
    private int gold = 12480;
    private int energy = 86;
    private int conquered = 1;

    private String status = "BURSA seçildi • Hedef: İSTANBUL";
    private long statusUntil = 0L;
    private long battleFxUntil = 0L;
    private int battleFxRegion = -1;
    private long resetArmedUntil = 0L;
    private long lastPassiveTick;

    private float W, H;
    private float mapTop, mapBottom, previewTop, unitsTop, actionTop;

    private final RectF attackButton = new RectF();
    private final RectF autoButton = new RectF();
    private final RectF defendButton = new RectF();
    private final RectF[] plusButtons = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF[] minusButtons = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF[] navButtons = {new RectF(), new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF[] baseButtons = {
            new RectF(), new RectF(), new RectF(), new RectF(), new RectF(), new RectF()
    };
    private final RectF[] techButtons = {new RectF(), new RectF(), new RectF()};
    private final RectF armyAutoButton = new RectF();
    private final RectF resetButton = new RectF();

    public GameView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        prefs = context.getSharedPreferences("warstate_save", Context.MODE_PRIVATE);
        load();
        migrateV2();
        applyOfflineProgress();

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(dp(2));
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void load() {
        gold = prefs.getInt("gold", 12480);
        energy = prefs.getInt("energy", 86);
        conquered = prefs.getInt("conquered", 1);

        for (int i = 0; i < regions.length; i++) {
            regions[i].owner = prefs.getInt("owner_" + i, regions[i].owner);
            regions[i].power = prefs.getInt("power_" + i, regions[i].power);
        }
        for (int i = 0; i < available.length; i++) {
            available[i] = prefs.getInt("unit_" + i, available[i]);
        }
        for (int i = 0; i < techLevel.length; i++) {
            techLevel[i] = prefs.getInt("tech_" + i, 0);
        }
        lastPassiveTick = prefs.getLong("last_passive", System.currentTimeMillis());
    }

    private void migrateV2() {
        if (prefs.getInt("save_version", 1) >= 2) return;

        int[] safety = {2600, 80, 50, 8};
        for (int i = 0; i < available.length; i++) {
            available[i] = Math.max(available[i], safety[i]);
        }
        energy = Math.max(energy, 80);
        gold = Math.max(gold, 10000);

        selected[0] = Math.min(1600, available[0]);
        selected[1] = Math.min(40, available[1]);
        selected[2] = Math.min(25, available[2]);
        selected[3] = Math.min(5, available[3]);

        save();
        prefs.edit().putInt("save_version", 2).apply();
        status = "v0.2 takviyesi yüklendi • Ordu yeniden hazır";
        statusUntil = System.currentTimeMillis() + 4500;
    }

    private void applyOfflineProgress() {
        long now = System.currentTimeMillis();
        long delta = Math.max(0L, Math.min(now - lastPassiveTick, 8L * 60L * 60L * 1000L));
        if (delta < 60000L) return;

        int minutes = (int)(delta / 60000L);
        int controlled = controlledCount();
        int oldEnergy = energy;

        energy = Math.min(120 + techLevel[1] * 10, energy + minutes / 2);
        gold += minutes * controlled * 35;

        lastPassiveTick = now;
        if (energy != oldEnergy || minutes > 0) save();
    }

    private void processRealtimeIncome() {
        long now = System.currentTimeMillis();
        long delta = now - lastPassiveTick;
        if (delta < 60000L) return;

        int minutes = (int)(delta / 60000L);
        gold += minutes * controlledCount() * 35;
        energy = Math.min(120 + techLevel[1] * 10, energy + minutes / 2);
        lastPassiveTick += minutes * 60000L;
        save();
    }

    private void save() {
        SharedPreferences.Editor e = prefs.edit()
                .putInt("gold", gold)
                .putInt("energy", energy)
                .putInt("conquered", conquered)
                .putLong("last_passive", lastPassiveTick)
                .putInt("save_version", 2);

        for (int i = 0; i < regions.length; i++) {
            e.putInt("owner_" + i, regions[i].owner);
            e.putInt("power_" + i, regions[i].power);
        }
        for (int i = 0; i < available.length; i++) {
            e.putInt("unit_" + i, available[i]);
        }
        for (int i = 0; i < techLevel.length; i++) {
            e.putInt("tech_" + i, techLevel[i]);
        }
        e.apply();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        W = getWidth();
        H = getHeight();
        processRealtimeIncome();

        drawBackground(c);
        drawTopBar(c);

        if (currentScreen == WORLD) {
            drawWorld(c);
        } else if (currentScreen == BASE) {
            drawBaseScreen(c);
        } else if (currentScreen == ARMY) {
            drawArmyScreen(c);
        } else if (currentScreen == TECH) {
            drawTechScreen(c);
        } else {
            drawMoreScreen(c);
        }

        drawBottomNav(c);

        if (currentScreen == WORLD && (System.currentTimeMillis() < battleFxUntil)) {
            postInvalidateDelayed(45);
        }
    }

    private void setupWorldLayout() {
        mapTop = H * 0.112f;
        mapBottom = H * 0.575f;
        previewTop = H * 0.580f;
        unitsTop = H * 0.705f;
        actionTop = H * 0.875f;
    }

    private void drawBackground(Canvas c) {
        p.setShader(new LinearGradient(0, 0, 0, H,
                Color.rgb(3, 12, 21), Color.rgb(6, 28, 44), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
    }

    private void drawTopBar(Canvas c) {
        float topH = H * 0.112f;
        p.setShader(new LinearGradient(0, 0, W, topH,
                Color.rgb(5, 21, 34), Color.rgb(8, 32, 49), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, topH, p);
        p.setShader(null);

        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W * 0.054f);
        p.setColor(Color.WHITE);
        c.drawText("WARSTATE", W * 0.038f, H * 0.045f, p);

        p.setTextSize(W * 0.022f);
        p.setColor(Color.rgb(95, 194, 255));
        c.drawText("KOMUTA MERKEZİ • v0.2.0", W * 0.040f, H * 0.068f, p);

        drawResource(c, W * 0.52f, H * 0.037f, "◆", format(gold), Color.rgb(255, 191, 64));
        drawResource(c, W * 0.76f, H * 0.037f, "ϟ",
                energy + "/" + (120 + techLevel[1] * 10), Color.rgb(64, 190, 255));

        float progress = controlledCount() / (float) regions.length;
        p.setColor(Color.rgb(12, 45, 66));
        roundRect(c, W * .035f, H * .078f, W * .965f, H * .104f, dp(10), p);
        p.setColor(Color.rgb(34, 137, 203));
        roundRect(c, W * .035f, H * .078f,
                W * (.035f + .930f * progress), H * .104f, dp(10), p);

        p.setTextSize(W * 0.023f);
        p.setColor(Color.WHITE);
        c.drawText("Hâkimiyet  " + controlledCount() + "/" + regions.length +
                "   •   Gelir +" + (controlledCount() * 35) + "/dk", W * .055f, H * .097f, p);
    }

    private void drawResource(Canvas c, float x, float y, String icon, String text, int color) {
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W * 0.030f);
        p.setColor(color);
        c.drawText(icon, x, y, p);
        p.setColor(Color.WHITE);
        c.drawText(text, x + W * 0.042f, y, p);
    }

    private void drawWorld(Canvas c) {
        setupWorldLayout();
        drawMap(c);
        drawPreview(c);
        drawUnits(c);
        drawWorldActions(c);
    }

    private void drawMap(Canvas c) {
        p.setShader(new LinearGradient(0, mapTop, 0, mapBottom,
                Color.rgb(10, 46, 64), Color.rgb(7, 29, 39), Shader.TileMode.CLAMP));
        c.drawRect(0, mapTop, W, mapBottom, p);
        p.setShader(null);

        drawSea(c);
        Path land = turkeyPath();
        p.setShader(new LinearGradient(0, mapTop, W, mapBottom,
                Color.rgb(45, 82, 57), Color.rgb(48, 61, 43), Shader.TileMode.CLAMP));
        p.setShadowLayer(dp(18), 0, dp(7), Color.argb(160, 0, 0, 0));
        c.drawPath(land, p);
        p.clearShadowLayer();
        p.setShader(null);

        stroke.setStrokeWidth(dp(2));
        stroke.setColor(Color.rgb(83, 122, 83));
        c.drawPath(land, stroke);

        drawTerrain(c);
        drawProvinceLines(c);

        if (selectedSource >= 0 && selectedTarget >= 0
                && regions[selectedSource].owner == PLAYER
                && regions[selectedTarget].owner == ENEMY) {
            drawAttackRoute(c, regions[selectedSource], regions[selectedTarget]);
        }

        for (int i = 0; i < regions.length; i++) {
            drawRegion(c, i, regions[i]);
        }

        if (battleFxRegion >= 0 && System.currentTimeMillis() < battleFxUntil) {
            drawBattleFx(c, regions[battleFxRegion]);
        }

        if (System.currentTimeMillis() < statusUntil || statusUntil == 0L) {
            drawStatusToast(c);
        }
    }

    private void drawSea(Canvas c) {
        p.setColor(Color.rgb(9, 58, 82));
        c.drawRect(0, mapTop, W, mapBottom, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp(1));
        for (int i = 0; i < 8; i++) {
            p.setColor(Color.argb(28 + i * 2, 104, 203, 235));
            float y = mapTop + (mapBottom - mapTop) * (.06f + i * .11f);
            c.drawArc(new RectF(-W * .10f, y - H * .03f, W * 1.10f, y + H * .05f),
                    8, 164, false, p);
        }
        p.setStyle(Paint.Style.FILL);

        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W * .023f);
        p.setColor(Color.argb(105, 210, 235, 246));
        p.setLetterSpacing(.18f);
        c.drawText("KARADENİZ", W * .66f, mapTop + H * .055f, p);
        p.setLetterSpacing(0);
    }

    private Path turkeyPath() {
        float t = mapTop;
        float mh = mapBottom - mapTop;
        Path path = new Path();
        path.moveTo(W*.055f, t + mh*.53f);
        path.lineTo(W*.095f, t + mh*.39f);
        path.lineTo(W*.185f, t + mh*.34f);
        path.lineTo(W*.245f, t + mh*.25f);
        path.lineTo(W*.355f, t + mh*.22f);
        path.lineTo(W*.430f, t + mh*.17f);
        path.lineTo(W*.535f, t + mh*.21f);
        path.lineTo(W*.620f, t + mh*.18f);
        path.lineTo(W*.720f, t + mh*.23f);
        path.lineTo(W*.835f, t + mh*.29f);
        path.lineTo(W*.935f, t + mh*.42f);
        path.lineTo(W*.900f, t + mh*.54f);
        path.lineTo(W*.825f, t + mh*.60f);
        path.lineTo(W*.790f, t + mh*.72f);
        path.lineTo(W*.680f, t + mh*.74f);
        path.lineTo(W*.595f, t + mh*.83f);
        path.lineTo(W*.480f, t + mh*.80f);
        path.lineTo(W*.405f, t + mh*.88f);
        path.lineTo(W*.295f, t + mh*.82f);
        path.lineTo(W*.210f, t + mh*.84f);
        path.lineTo(W*.130f, t + mh*.72f);
        path.lineTo(W*.070f, t + mh*.66f);
        path.close();
        return path;
    }

    private void drawTerrain(Canvas c) {
        float mh = mapBottom - mapTop;

        // Orman bölgeleri
        p.setColor(Color.argb(70, 36, 111, 63));
        c.drawOval(new RectF(W*.10f, mapTop+mh*.25f, W*.39f, mapTop+mh*.52f), p);
        c.drawOval(new RectF(W*.60f, mapTop+mh*.30f, W*.88f, mapTop+mh*.54f), p);
        c.drawOval(new RectF(W*.36f, mapTop+mh*.60f, W*.66f, mapTop+mh*.84f), p);

        // Dağ sıraları
        p.setColor(Color.argb(120, 110, 111, 93));
        for (int i = 0; i < 11; i++) {
            float x = W * (.13f + i * .067f);
            float y = mapTop + mh * (.65f + (i % 3) * .045f);
            drawMountain(c, x, y, W*.035f, H*.018f);
        }
        for (int i = 0; i < 6; i++) {
            float x = W * (.56f + i * .055f);
            float y = mapTop + mh * (.35f + (i % 2) * .045f);
            drawMountain(c, x, y, W*.028f, H*.014f);
        }

        // Ana yollar
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(Color.argb(105, 211, 190, 139));
        stroke.setPathEffect(new DashPathEffect(new float[]{dp(6), dp(6)}, 0));
        Path roads = new Path();
        roads.moveTo(W*.18f, mapTop+mh*.66f);
        roads.cubicTo(W*.30f, mapTop+mh*.45f, W*.44f, mapTop+mh*.32f, W*.60f, mapTop+mh*.46f);
        roads.cubicTo(W*.70f, mapTop+mh*.56f, W*.75f, mapTop+mh*.38f, W*.78f, mapTop+mh*.29f);
        c.drawPath(roads, stroke);
        stroke.setPathEffect(null);
    }

    private void drawMountain(Canvas c, float x, float y, float w, float h) {
        Path m = new Path();
        m.moveTo(x-w, y+h);
        m.lineTo(x, y-h);
        m.lineTo(x+w, y+h);
        m.close();
        c.drawPath(m, p);
        p.setColor(Color.argb(85, 205, 211, 195));
        Path snow = new Path();
        snow.moveTo(x-w*.24f, y-h*.25f);
        snow.lineTo(x, y-h);
        snow.lineTo(x+w*.24f, y-h*.25f);
        snow.close();
        c.drawPath(snow, p);
        p.setColor(Color.argb(120, 110, 111, 93));
    }

    private void drawProvinceLines(Canvas c) {
        float mh = mapBottom - mapTop;
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.argb(75, 190, 210, 180));

        float[] xs = {.22f,.35f,.48f,.62f,.75f};
        for (int i=0;i<xs.length;i++) {
            Path line = new Path();
            line.moveTo(W*xs[i], mapTop+mh*(.28f + (i%2)*.05f));
            line.cubicTo(W*(xs[i]-.04f), mapTop+mh*.46f,
                    W*(xs[i]+.04f), mapTop+mh*.62f,
                    W*(xs[i]-.01f), mapTop+mh*.78f);
            c.drawPath(line, stroke);
        }
    }

    private void drawAttackRoute(Canvas c, Region a, Region b) {
        float ax = a.x * W;
        float ay = mapTop + a.y * (mapBottom - mapTop);
        float bx = b.x * W;
        float by = mapTop + b.y * (mapBottom - mapTop);

        stroke.setStrokeWidth(dp(7));
        stroke.setColor(Color.argb(55, 255, 75, 65));
        c.drawLine(ax, ay, bx, by, stroke);

        stroke.setStrokeWidth(dp(2.5f));
        stroke.setColor(Color.rgb(255, 89, 72));
        stroke.setPathEffect(new DashPathEffect(new float[]{dp(8), dp(6)}, 0));
        c.drawLine(ax, ay, bx, by, stroke);
        stroke.setPathEffect(null);
        drawArrowHead(c, ax, ay, bx, by);
    }

    private void drawRegion(Canvas c, int index, Region r) {
        float cx = r.x * W;
        float cy = mapTop + r.y * (mapBottom - mapTop);
        float pulse = (float)((Math.sin(System.currentTimeMillis()/350.0)+1.0)/2.0);
        boolean selectedNode = index == selectedSource || index == selectedTarget;
        int team = r.owner == PLAYER ? Color.rgb(48, 169, 255) : Color.rgb(246, 76, 68);

        float radius = W * (selectedNode ? (.072f + pulse*.006f) : .066f);
        r.hit.set(cx - W*.105f, cy - H*.055f, cx + W*.105f, cy + H*.055f);

        p.setShader(new RadialGradient(cx, cy, radius*1.8f,
                Color.argb(selectedNode ? 150 : 95, Color.red(team), Color.green(team), Color.blue(team)),
                Color.TRANSPARENT, Shader.TileMode.CLAMP));
        c.drawCircle(cx, cy, radius*1.8f, p);
        p.setShader(null);

        stroke.setStrokeWidth(dp(selectedNode ? 4 : 2));
        stroke.setColor(team);
        c.drawCircle(cx, cy, radius, stroke);

        // Şehir üs silueti
        p.setColor(Color.rgb(12, 25, 34));
        c.drawRect(cx-W*.028f, cy-H*.019f, cx-W*.006f, cy+H*.008f, p);
        c.drawRect(cx-W*.002f, cy-H*.027f, cx+W*.018f, cy+H*.008f, p);
        c.drawRect(cx+W*.022f, cy-H*.014f, cx+W*.039f, cy+H*.008f, p);
        p.setColor(team);
        c.drawRect(cx-W*.032f, cy+H*.008f, cx+W*.043f, cy+H*.012f, p);

        // Bilgi etiketi
        float pillW = W*.205f;
        float pillH = H*.047f;
        float py = cy + H*.020f;
        p.setColor(Color.argb(235, 4, 19, 30));
        roundRect(c, cx-pillW/2, py, cx+pillW/2, py+pillH, dp(8), p);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W*.025f);
        p.setColor(Color.WHITE);
        c.drawText(r.name, cx, py+H*.018f, p);

        p.setTextSize(W*.0175f);
        p.setColor(team);
        c.drawText((r.owner==PLAYER ? "MÜTTEFİK" : "DÜŞMAN") + "  LV." + r.level,
                cx, py+H*.034f, p);

        p.setTextSize(W*.0165f);
        p.setColor(Color.rgb(218,231,238));
        c.drawText(format(r.power), cx, py+H*.048f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBattleFx(Canvas c, Region r) {
        float cx = r.x*W;
        float cy = mapTop + r.y*(mapBottom-mapTop);
        float remain = (battleFxUntil-System.currentTimeMillis())/1200f;
        float phase = 1f-Math.max(0f, Math.min(1f, remain));

        p.setStyle(Paint.Style.STROKE);
        for (int i=0;i<3;i++) {
            float rad = W*(.035f + phase*.08f + i*.018f);
            p.setStrokeWidth(dp(4-i));
            p.setColor(Color.argb((int)(170*(1-phase)), 255, 167-i*35, 45));
            c.drawCircle(cx,cy,rad,p);
        }
        p.setStyle(Paint.Style.FILL);
    }

    private void drawStatusToast(Canvas c) {
        float l=W*.07f, r=W*.93f;
        float b=mapBottom-H*.012f, t=b-H*.043f;
        p.setColor(Color.argb(235, 4, 19, 30));
        roundRect(c,l,t,r,b,dp(9),p);
        stroke.setColor(Color.argb(150, 73, 177, 236));
        stroke.setStrokeWidth(dp(1));
        c.drawRoundRect(new RectF(l,t,r,b),dp(9),dp(9),stroke);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W*.0215f);
        p.setColor(Color.WHITE);
        c.drawText(status,W*.5f,t+H*.027f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawArrowHead(Canvas c, float ax, float ay, float bx, float by) {
        double angle = Math.atan2(by-ay,bx-ax);
        float len=dp(15);
        Path path=new Path();
        path.moveTo(bx,by);
        path.lineTo((float)(bx-len*Math.cos(angle-Math.PI/6)),
                (float)(by-len*Math.sin(angle-Math.PI/6)));
        path.lineTo((float)(bx-len*Math.cos(angle+Math.PI/6)),
                (float)(by-len*Math.sin(angle+Math.PI/6)));
        path.close();
        p.setColor(Color.rgb(255,89,72));
        c.drawPath(path,p);
    }

    private void drawPreview(Canvas c) {
        p.setColor(Color.rgb(5, 20, 32));
        c.drawRect(0, previewTop, W, unitsTop-H*.006f, p);

        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W*.026f);
        p.setColor(Color.rgb(204,224,236));
        c.drawText("SAVAŞ ÖNİZLEMESİ",W*.035f,previewTop+H*.024f,p);

        Region src = selectedSource>=0 ? regions[selectedSource] : null;
        Region trg = selectedTarget>=0 ? regions[selectedTarget] : null;

        p.setTextSize(W*.027f);
        p.setColor(Color.rgb(64,177,255));
        c.drawText(src==null ? "Kaynak seç" : src.name+" (Sen)",W*.05f,previewTop+H*.058f,p);

        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(Color.rgb(255,94,85));
        c.drawText(trg==null ? "Hedef seç" : trg.name+" (Düşman)",W*.95f,previewTop+H*.058f,p);
        p.setTextAlign(Paint.Align.LEFT);

        int attack=selectedPower();
        int defense=trg==null ? 0 : trg.power;
        int chance=battleChance(attack,defense);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W*.050f);
        p.setColor(chance>=65 ? Color.rgb(60,224,145) :
                (chance>=40 ? Color.rgb(255,183,67) : Color.rgb(255,101,83)));
        c.drawText("%"+chance,W*.5f,previewTop+H*.082f,p);

        p.setTextSize(W*.0185f);
        p.setColor(Color.LTGRAY);
        c.drawText("TAHMİNİ BAŞARI",W*.5f,previewTop+H*.101f,p);

        p.setTextSize(W*.021f);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("Saldırı "+format(attack),W*.05f,previewTop+H*.104f,p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Savunma "+format(defense),W*.95f,previewTop+H*.104f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawUnits(Canvas c) {
        float gap=W*.010f;
        float margin=W*.020f;
        float cardW=(W-margin*2-gap*3)/4f;
        float cardH=actionTop-unitsTop-H*.012f;

        for(int i=0;i<4;i++){
            float l=margin+i*(cardW+gap);
            float r=l+cardW;
            float t=unitsTop;
            float b=t+cardH;

            p.setShader(new LinearGradient(l,t,l,b,
                    Color.rgb(14,45,64),Color.rgb(9,28,42),Shader.TileMode.CLAMP));
            roundRect(c,l,t,r,b,dp(9),p);
            p.setShader(null);

            stroke.setColor(Color.rgb(42,106,143));
            stroke.setStrokeWidth(dp(1.3f));
            c.drawRoundRect(new RectF(l,t,r,b),dp(9),dp(9),stroke);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.0225f);
            p.setColor(Color.WHITE);
            c.drawText(unitNames[i],(l+r)/2,t+H*.024f,p);

            p.setTextSize(W*.033f);
            p.setColor(Color.rgb(105,201,255));
            c.drawText(unitGlyphs[i],(l+r)/2,t+H*.055f,p);

            p.setTextSize(W*.0175f);
            p.setColor(Color.rgb(168,193,207));
            c.drawText("Mevcut "+available[i],(l+r)/2,t+H*.081f,p);

            p.setTextSize(W*.025f);
            p.setColor(Color.WHITE);
            c.drawText(String.valueOf(selected[i]),(l+r)/2,t+H*.107f,p);

            float by=b-H*.022f;
            minusButtons[i].set(l+W*.008f,by-H*.018f,l+cardW*.34f,by+H*.010f);
            plusButtons[i].set(r-cardW*.34f,by-H*.018f,r-W*.008f,by+H*.010f);
            p.setColor(Color.rgb(22,67,92));
            roundRect(c,minusButtons[i].left,minusButtons[i].top,minusButtons[i].right,minusButtons[i].bottom,dp(5),p);
            roundRect(c,plusButtons[i].left,plusButtons[i].top,plusButtons[i].right,plusButtons[i].bottom,dp(5),p);

            p.setTextSize(W*.028f);
            p.setColor(Color.WHITE);
            c.drawText("−",minusButtons[i].centerX(),by+H*.002f,p);
            c.drawText("+",plusButtons[i].centerX(),by+H*.002f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawWorldActions(Canvas c) {
        float t=actionTop;
        float b=H*.946f;
        p.setColor(Color.rgb(6,22,34));
        c.drawRect(0,t,W,b,p);

        autoButton.set(W*.018f,t+H*.009f,W*.205f,b-H*.009f);
        defendButton.set(W*.218f,t+H*.009f,W*.405f,b-H*.009f);
        drawActionButton(c,autoButton,"OTOMATİK",false);
        drawActionButton(c,defendButton,"SAVUN",false);

        attackButton.set(W*.43f,t+H*.007f,W*.982f,b-H*.007f);
        p.setShader(new LinearGradient(attackButton.left,attackButton.top,attackButton.right,attackButton.bottom,
                Color.rgb(190,45,42),Color.rgb(255,79,64),Shader.TileMode.CLAMP));
        roundRect(c,attackButton.left,attackButton.top,attackButton.right,attackButton.bottom,dp(11),p);
        p.setShader(null);
        stroke.setColor(Color.rgb(255,150,128));
        stroke.setStrokeWidth(dp(2));
        c.drawRoundRect(attackButton,dp(11),dp(11),stroke);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.031f);
        p.setColor(Color.WHITE);
        c.drawText("⚔  SAVAŞI BAŞLAT",attackButton.centerX(),attackButton.centerY()+H*.009f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawActionButton(Canvas c, RectF rect, String text, boolean accent) {
        p.setColor(accent ? Color.rgb(28,104,151) : Color.rgb(18,55,77));
        roundRect(c,rect.left,rect.top,rect.right,rect.bottom,dp(9),p);
        stroke.setColor(Color.rgb(42,95,126));
        stroke.setStrokeWidth(dp(1));
        c.drawRoundRect(rect,dp(9),dp(9),stroke);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.020f);
        p.setColor(Color.rgb(225,238,246));
        c.drawText(text,rect.centerX(),rect.centerY()+H*.007f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBaseScreen(Canvas c) {
        float top=H*.125f;
        float bottom=H*.940f;
        drawSectionTitle(c,"ÜS KOMUTASI","Takviye üret, enerji doldur ve bölge savunmasını yükselt");

        Region src = selectedSource>=0 ? regions[selectedSource] : regions[0];
        drawInfoCard(c,W*.05f,top+H*.055f,W*.95f,top+H*.145f,
                "KOMUTA MERKEZİ • "+src.name,
                "Savunma "+format(src.power)+"  •  Pasif gelir +"+(controlledCount()*35)+"/dk",
                Color.rgb(54,169,242));

        String[] labels={
                "Piyade taburu", "Tank bölüğü", "Topçu bataryası", "Hava filosu",
                "Enerji ikmali", "Bölge tahkimatı"
        };
        String[] desc={
                "+500 Asker", "+10 Tank", "+5 Topçu", "+2 Hava",
                "+30 Enerji", "+"+(1500+techLevel[2]*250)+" Savunma"
        };
        int[] costs={700,1200,1000,1800,900,1100};

        float rowTop=top+H*.165f;
        float rowH=H*.086f;
        float gap=H*.012f;
        for(int i=0;i<6;i++){
            float t=rowTop+i*(rowH+gap);
            float b=t+rowH;
            baseButtons[i].set(W*.05f,t,W*.95f,b);

            p.setColor(Color.rgb(10,35,51));
            roundRect(c,W*.05f,t,W*.95f,b,dp(10),p);
            stroke.setColor(Color.rgb(31,82,111));
            stroke.setStrokeWidth(dp(1));
            c.drawRoundRect(baseButtons[i],dp(10),dp(10),stroke);

            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.024f);
            p.setColor(Color.WHITE);
            c.drawText(labels[i],W*.08f,t+H*.031f,p);

            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(W*.019f);
            p.setColor(Color.rgb(137,190,217));
            c.drawText(desc[i],W*.08f,t+H*.058f,p);

            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.021f);
            p.setColor(gold>=costs[i] ? Color.rgb(255,196,72) : Color.rgb(231,91,79));
            c.drawText("◆ "+format(costs[i]),W*.90f,t+H*.046f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        p.setTextSize(W*.018f);
        p.setColor(Color.rgb(128,160,180));
        c.drawText("İpucu: Dünya ekranında istediğin müttefik bölgeyi seçip sonra Üs'e gel.",
                W*.055f,bottom-H*.020f,p);
    }

    private void drawArmyScreen(Canvas c) {
        float top=H*.125f;
        drawSectionTitle(c,"BİRLİKLER","Ordunun mevcut gücü ve savaş kapasitesi");

        int totalArmy=0;
        for(int i=0;i<4;i++) totalArmy+=available[i]*unitPower[i];
        drawInfoCard(c,W*.05f,top+H*.055f,W*.95f,top+H*.145f,
                "TOPLAM ORDU GÜCÜ",format(totalArmy),
                Color.rgb(69,190,255));

        float rowTop=top+H*.175f;
        float rowH=H*.118f;
        for(int i=0;i<4;i++){
            float t=rowTop+i*(rowH+H*.015f);
            p.setColor(Color.rgb(10,35,52));
            roundRect(c,W*.05f,t,W*.95f,t+rowH,dp(11),p);

            p.setTextSize(W*.046f);
            p.setColor(Color.rgb(88,195,255));
            c.drawText(unitGlyphs[i],W*.08f,t+H*.066f,p);

            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.026f);
            p.setColor(Color.WHITE);
            c.drawText(unitNames[i],W*.20f,t+H*.038f,p);

            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(W*.019f);
            p.setColor(Color.rgb(151,182,201));
            c.drawText(unitRole(i),W*.20f,t+H*.066f,p);

            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.035f);
            p.setColor(Color.WHITE);
            c.drawText(format(available[i]),W*.90f,t+H*.052f,p);
            p.setTextSize(W*.017f);
            p.setColor(Color.rgb(120,181,215));
            c.drawText("Güç "+format(available[i]*unitPower[i]),W*.90f,t+H*.080f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        armyAutoButton.set(W*.12f,H*.822f,W*.88f,H*.880f);
        drawWideButton(c,armyAutoButton,"DÜNYA İÇİN OTOMATİK ORDU HAZIRLA",Color.rgb(24,111,164));
    }

    private String unitRole(int i){
        if(i==0) return "Bölge tutma ve temel hücum";
        if(i==1) return "Yüksek zırh ve kırma gücü";
        if(i==2) return "Uzak menzil destek ateşi";
        return "Hızlı taarruz ve üstün ateş gücü";
    }

    private void drawTechScreen(Canvas c) {
        float top=H*.125f;
        drawSectionTitle(c,"TEKNOLOJİ","Kalıcı savaş bonusları geliştir");

        String[] names={"ATEŞ GÜCÜ","LOJİSTİK","ZIRH & TAHKİMAT"};
        String[] effects={
                "Saldırı gücü +%5 / seviye",
                "Enerji kapasitesi +10, saldırı maliyeti -1 / seviye",
                "Kayıplar azalır, tahkimat bonusu yükselir"
        };

        float t0=top+H*.080f;
        float cardH=H*.180f;
        for(int i=0;i<3;i++){
            float t=t0+i*(cardH+H*.025f);
            float b=t+cardH;

            p.setShader(new LinearGradient(W*.05f,t,W*.95f,b,
                    Color.rgb(12,43,62),Color.rgb(7,28,43),Shader.TileMode.CLAMP));
            roundRect(c,W*.05f,t,W*.95f,b,dp(12),p);
            p.setShader(null);

            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.027f);
            p.setColor(Color.WHITE);
            c.drawText(names[i],W*.08f,t+H*.040f,p);

            p.setTextAlign(Paint.Align.RIGHT);
            p.setTextSize(W*.022f);
            p.setColor(Color.rgb(94,201,255));
            c.drawText("LV."+techLevel[i]+"/5",W*.90f,t+H*.040f,p);
            p.setTextAlign(Paint.Align.LEFT);

            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(W*.019f);
            p.setColor(Color.rgb(155,184,201));
            c.drawText(effects[i],W*.08f,t+H*.077f,p);

            int cost=techCost(i);
            techButtons[i].set(W*.58f,t+H*.105f,W*.90f,t+H*.157f);
            p.setColor(techLevel[i]>=5 ? Color.rgb(40,60,70) :
                    (gold>=cost ? Color.rgb(25,115,168) : Color.rgb(90,55,56)));
            roundRect(c,techButtons[i].left,techButtons[i].top,
                    techButtons[i].right,techButtons[i].bottom,dp(8),p);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.019f);
            p.setColor(Color.WHITE);
            c.drawText(techLevel[i]>=5 ? "MAKSİMUM" : "GELİŞTİR  ◆ "+format(cost),
                    techButtons[i].centerX(),techButtons[i].centerY()+H*.006f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawMoreScreen(Canvas c) {
        float top=H*.125f;
        drawSectionTitle(c,"SAVAŞ DOSYASI","Oyun bilgileri ve test araçları");

        drawInfoCard(c,W*.05f,top+H*.075f,W*.95f,top+H*.190f,
                "WARSTATE v0.2.0",
                "Seç • güçlendir • saldır • bölgeyi ele geçir",
                Color.rgb(81,178,235));

        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.024f);
        p.setColor(Color.WHITE);
        c.drawText("NASIL OYNANIR?",W*.06f,top+H*.245f,p);

        String[] tips={
                "1. Dünya ekranında mavi bölgeni ve kırmızı hedefi seç.",
                "2. Birlik miktarını ayarla veya OTOMATİK seçimi kullan.",
                "3. Başarı yüzdesini kontrol edip savaşı başlat.",
                "4. Üs ekranından takviye al, Teknoloji'den kalıcı bonus aç.",
                "5. Fethedilen bölge pasif altın gelirini artırır."
        };
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(W*.020f);
        p.setColor(Color.rgb(171,198,213));
        for(int i=0;i<tips.length;i++){
            c.drawText(tips[i],W*.065f,top+H*(.285f+i*.047f),p);
        }

        resetButton.set(W*.12f,H*.760f,W*.88f,H*.820f);
        boolean armed=System.currentTimeMillis()<resetArmedUntil;
        drawWideButton(c,resetButton,
                armed ? "TEKRAR DOKUN • YENİ OYUN BAŞLASIN" : "YENİ OYUN / KAYDI SIFIRLA",
                armed ? Color.rgb(187,59,51) : Color.rgb(65,72,80));

        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(W*.0175f);
        p.setColor(Color.rgb(111,145,166));
        c.drawText("Sıfırlama iki dokunuşla onaylanır.",W*.5f,H*.850f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSectionTitle(Canvas c,String title,String subtitle){
        float top=H*.125f;
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.038f);
        p.setColor(Color.WHITE);
        c.drawText(title,W*.05f,top+H*.010f,p);

        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(W*.019f);
        p.setColor(Color.rgb(125,174,201));
        c.drawText(subtitle,W*.05f,top+H*.040f,p);
    }

    private void drawInfoCard(Canvas c,float l,float t,float r,float b,String title,String sub,int accent){
        p.setColor(Color.rgb(9,34,50));
        roundRect(c,l,t,r,b,dp(11),p);
        p.setColor(accent);
        c.drawRect(l,t,l+dp(4),b,p);

        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.026f);
        p.setColor(Color.WHITE);
        c.drawText(title,l+W*.035f,t+H*.036f,p);

        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(W*.019f);
        p.setColor(Color.rgb(150,184,203));
        c.drawText(sub,l+W*.035f,t+H*.067f,p);
    }

    private void drawWideButton(Canvas c,RectF rect,String text,int color){
        p.setColor(color);
        roundRect(c,rect.left,rect.top,rect.right,rect.bottom,dp(10),p);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.argb(150,150,210,240));
        c.drawRoundRect(rect,dp(10),dp(10),stroke);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.021f);
        p.setColor(Color.WHITE);
        c.drawText(text,rect.centerX(),rect.centerY()+H*.007f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBottomNav(Canvas c) {
        float top=H*.946f;
        p.setColor(Color.rgb(3,15,25));
        c.drawRect(0,top,W,H,p);

        String[] labels={"ÜS","DÜNYA","BİRLİKLER","TEKNOLOJİ","DAHA FAZLA"};
        for(int i=0;i<5;i++){
            float l=W*i/5f;
            float r=W*(i+1)/5f;
            navButtons[i].set(l,top,r,H);
            boolean active=(i==0&&currentScreen==BASE)
                    ||(i==1&&currentScreen==WORLD)
                    ||(i==2&&currentScreen==ARMY)
                    ||(i==3&&currentScreen==TECH)
                    ||(i==4&&currentScreen==MORE);

            if(active){
                p.setColor(Color.argb(70,51,167,232));
                c.drawRect(l,top,r,H,p);
                p.setColor(Color.rgb(66,190,255));
                c.drawRect(l,top,r,top+dp(3),p);
            }

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.0165f);
            p.setColor(active ? Color.rgb(84,197,255) : Color.rgb(151,176,192));
            c.drawText(labels[i],(l+r)/2,H*.980f,p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction()!=MotionEvent.ACTION_UP) return true;
        float x=e.getX(), y=e.getY();

        for(int i=0;i<navButtons.length;i++){
            if(navButtons[i].contains(x,y)){
                currentScreen = i==0 ? BASE : i==1 ? WORLD : i==2 ? ARMY : i==3 ? TECH : MORE;
                invalidate();
                return true;
            }
        }

        if(currentScreen==WORLD) return handleWorldTouch(x,y);
        if(currentScreen==BASE) return handleBaseTouch(x,y);
        if(currentScreen==ARMY) return handleArmyTouch(x,y);
        if(currentScreen==TECH) return handleTechTouch(x,y);
        return handleMoreTouch(x,y);
    }

    private boolean handleWorldTouch(float x,float y){
        for(int i=0;i<regions.length;i++){
            if(regions[i].hit.contains(x,y)){
                if(regions[i].owner==PLAYER){
                    selectedSource=i;
                    showStatus(regions[i].name+" kaynak bölge seçildi");
                }else{
                    selectedTarget=i;
                    showStatus(regions[i].name+" hedef seçildi");
                }
                invalidate();
                return true;
            }
        }

        for(int i=0;i<4;i++){
            if(plusButtons[i].contains(x,y)){
                selected[i]=Math.min(available[i],selected[i]+stepFor(i));
                invalidate();
                return true;
            }
            if(minusButtons[i].contains(x,y)){
                selected[i]=Math.max(0,selected[i]-stepFor(i));
                invalidate();
                return true;
            }
        }

        if(autoButton.contains(x,y)){
            autoSelectArmy();
            showStatus("Ordu hedefe göre otomatik hazırlandı");
            invalidate();
            return true;
        }
        if(defendButton.contains(x,y)){
            fortifySelected();
            invalidate();
            return true;
        }
        if(attackButton.contains(x,y)){
            attack();
            invalidate();
            return true;
        }
        return true;
    }

    private boolean handleBaseTouch(float x,float y){
        int[] costs={700,1200,1000,1800,900,1100};
        for(int i=0;i<baseButtons.length;i++){
            if(!baseButtons[i].contains(x,y)) continue;
            if(gold<costs[i]){
                showStatus("Altın yetersiz • "+format(costs[i])+" gerekli");
                currentScreen=WORLD;
                invalidate();
                return true;
            }

            gold-=costs[i];
            if(i<4){
                available[i]+=recruitAmount[i];
                showStatus(unitNames[i]+" takviyesi hazır");
            }else if(i==4){
                energy=Math.min(120+techLevel[1]*10,energy+30);
                showStatus("Enerji ikmali tamamlandı");
            }else{
                Region src=selectedSource>=0?regions[selectedSource]:regions[0];
                src.power+=1500+techLevel[2]*250;
                showStatus(src.name+" savunması güçlendirildi");
            }
            save();
            invalidate();
            return true;
        }
        return true;
    }

    private boolean handleArmyTouch(float x,float y){
        if(armyAutoButton.contains(x,y)){
            autoSelectArmy();
            currentScreen=WORLD;
            showStatus("Otomatik ordu seçildi");
            invalidate();
        }
        return true;
    }

    private boolean handleTechTouch(float x,float y){
        for(int i=0;i<techButtons.length;i++){
            if(!techButtons[i].contains(x,y)) continue;
            if(techLevel[i]>=5) return true;
            int cost=techCost(i);
            if(gold<cost){
                showStatus("Teknoloji için altın yetersiz");
                currentScreen=WORLD;
                invalidate();
                return true;
            }
            gold-=cost;
            techLevel[i]++;
            save();
            invalidate();
            return true;
        }
        return true;
    }

    private boolean handleMoreTouch(float x,float y){
        if(resetButton.contains(x,y)){
            long now=System.currentTimeMillis();
            if(now<resetArmedUntil){
                resetGame();
                currentScreen=WORLD;
            }else{
                resetArmedUntil=now+4000;
            }
            invalidate();
        }
        return true;
    }

    private void autoSelectArmy(){
        Region target=selectedTarget>=0?regions[selectedTarget]:null;
        int targetPower=target==null?16000:target.power;
        for(int i=0;i<4;i++) selected[i]=0;

        int goal=(int)(targetPower*1.20f/(1f+techLevel[0]*.05f));

        int[] order={3,1,2,0};
        int current=0;
        for(int idx:order){
            int maxUse=(int)Math.ceil(available[idx]*.78);
            int needed=(int)Math.ceil((goal-current)/(double)unitPower[idx]);
            int use=Math.max(0,Math.min(maxUse,needed));
            selected[idx]=use;
            current+=use*unitPower[idx];
            if(current>=goal) break;
        }

        if(current<800){
            selected[0]=Math.min(available[0],500);
        }
    }

    private void fortifySelected(){
        if(selectedSource<0 || regions[selectedSource].owner!=PLAYER){
            showStatus("Önce müttefik bölge seç");
            return;
        }
        int cost=750;
        if(gold<cost){
            showStatus("Savunma için 750 altın gerekli");
            return;
        }
        gold-=cost;
        regions[selectedSource].power+=1200+techLevel[2]*200;
        save();
        showStatus(regions[selectedSource].name+" savunması +"+(1200+techLevel[2]*200));
    }

    private int stepFor(int i){
        return i==0?100:(i==3?1:5);
    }

    private int rawSelectedPower(){
        int s=0;
        for(int i=0;i<4;i++) s+=selected[i]*unitPower[i];
        return s;
    }

    private int selectedPower(){
        return (int)(rawSelectedPower()*(1f+techLevel[0]*.05f));
    }

    private int battleChance(int attack,int defense){
        if(defense<=0 || attack<=0) return 0;
        double ratio=attack/(double)defense;
        return (int)Math.max(8,Math.min(94,50+(ratio-1.0)*48.0));
    }

    private int attackEnergyCost(){
        return Math.max(5,10-techLevel[1]);
    }

    private void attack(){
        if(selectedSource<0 || selectedTarget<0){
            showStatus("Önce kaynak ve düşman bölge seç");
            return;
        }

        Region source=regions[selectedSource];
        Region target=regions[selectedTarget];

        if(source.owner!=PLAYER || target.owner!=ENEMY){
            showStatus("Geçerli bir düşman hedef seç");
            return;
        }

        int energyCost=attackEnergyCost();
        if(energy<energyCost){
            showStatus("Enerji yetersiz • Üs ekranından ikmal yap");
            return;
        }

        int attackPower=selectedPower();
        if(attackPower<800){
            showStatus("Daha fazla birlik seç");
            return;
        }

        for(int i=0;i<4;i++){
            if(selected[i]>available[i]){
                showStatus("Birlik sayısı yetersiz");
                return;
            }
        }

        energy-=energyCost;
        int chance=battleChance(attackPower,target.power);
        boolean win=random.nextInt(100)<chance;

        battleFxRegion=selectedTarget;
        battleFxUntil=System.currentTimeMillis()+1200;

        if(win){
            int lossPct=Math.max(12,30-chance/6-random.nextInt(6)-techLevel[2]*2);
            applyLosses(lossPct);

            target.owner=PLAYER;
            target.power=Math.max(2200,(int)(attackPower*.42f));
            gold+=1700+target.level*95;
            conquered=Math.max(conquered,controlledCount());

            showStatus("ZAFER • "+target.name+" ele geçirildi • Kayıp %"+lossPct);
            selectedSource=selectedTarget;
            selectedTarget=findEnemy();
        }else{
            int lossPct=Math.max(22,42-chance/8+random.nextInt(9)-techLevel[2]*2);
            applyLosses(lossPct);

            target.power=Math.max(1000,(int)(target.power*(.82f+random.nextDouble()*.08f)));
            showStatus("SALDIRI DURDURULDU • Düşman zayıfladı • Kayıp %"+lossPct);
        }

        autoClampSelection();
        save();
    }

    private void applyLosses(int pct){
        for(int i=0;i<4;i++){
            int lost=(int)Math.ceil(selected[i]*(pct/100.0));
            available[i]=Math.max(0,available[i]-lost);
        }
    }

    private void autoClampSelection(){
        for(int i=0;i<4;i++){
            selected[i]=Math.min(selected[i],available[i]);
        }
    }

    private int findEnemy(){
        for(int i=0;i<regions.length;i++){
            if(regions[i].owner==ENEMY) return i;
        }
        showStatus("TÜM BÖLGELER ELE GEÇİRİLDİ • HÂKİMİYET TAMAMLANDI!");
        return -1;
    }

    private int techCost(int i){
        return 2200+techLevel[i]*1400;
    }

    private int controlledCount(){
        int n=0;
        for(Region r:regions) if(r.owner==PLAYER) n++;
        return n;
    }

    private void resetGame(){
        prefs.edit().clear().apply();

        regions[0].owner=PLAYER; regions[0].power=15500;
        regions[1].owner=ENEMY;  regions[1].power=24300;
        regions[2].owner=ENEMY;  regions[2].power=17800;
        regions[3].owner=ENEMY;  regions[3].power=21500;
        regions[4].owner=ENEMY;  regions[4].power=16500;
        regions[5].owner=ENEMY;  regions[5].power=19000;

        int[] startUnits={3500,120,80,12};
        int[] startSelected={1800,45,25,5};
        for(int i=0;i<4;i++){
            available[i]=startUnits[i];
            selected[i]=startSelected[i];
        }
        for(int i=0;i<3;i++) techLevel[i]=0;

        gold=12480;
        energy=86;
        conquered=1;
        selectedSource=0;
        selectedTarget=1;
        lastPassiveTick=System.currentTimeMillis();
        resetArmedUntil=0;
        status="Yeni harekât başladı • BURSA hazır";
        statusUntil=System.currentTimeMillis()+4000;
        save();
    }

    private void showStatus(String s){
        status=s;
        statusUntil=System.currentTimeMillis()+3500;
    }

    private String format(int n){
        return String.format(Locale.US,"%,d",n).replace(',','.');
    }

    private void roundRect(Canvas c,float l,float t,float r,float b,float radius,Paint paint){
        c.drawRoundRect(new RectF(l,t,r,b),radius,radius,paint);
    }

    private static class Region {
        final String name;
        final float x,y;
        int owner;
        final int level;
        int power;
        final RectF hit=new RectF();

        Region(String name,float x,float y,int owner,int level,int power){
            this.name=name;
            this.x=x;
            this.y=y;
            this.owner=owner;
            this.level=level;
            this.power=power;
        }
    }
}
