package com.elxvro.warstate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.graphics.drawable.*;
import android.view.MotionEvent;
import android.view.View;

import java.util.Locale;
import java.util.Random;

public class GameView extends View {
    private static final int PLAYER = 0;
    private static final int ENEMY = 1;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random random = new Random();
    private final SharedPreferences prefs;

    private final Region[] regions = new Region[] {
            new Region("BURSA", 0.28f, 0.35f, PLAYER, 18, 15500),
            new Region("İSTANBUL", 0.57f, 0.23f, ENEMY, 25, 24300),
            new Region("ANKARA", 0.73f, 0.45f, ENEMY, 22, 21500),
            new Region("İZMİR", 0.20f, 0.58f, ENEMY, 20, 17800)
    };

    private final String[] unitNames = {"Asker", "Tank", "Topçu", "Hava"};
    private final int[] available = {3500, 120, 80, 12};
    private final int[] selected = {1200, 35, 20, 4};
    private final int[] unitPower = {2, 55, 80, 320};

    private int selectedSource = 0;
    private int selectedTarget = 1;
    private int gold = 12480;
    private int energy = 86;
    private int conquered = 1;
    private String status = "BURSA seçildi • Hedef: İSTANBUL";
    private long statusUntil = 0L;

    private float W, H;
    private float mapTop, mapBottom, previewTop, unitsTop, actionTop;
    private final RectF attackButton = new RectF();
    private final RectF[] plusButtons = {new RectF(), new RectF(), new RectF(), new RectF()};
    private final RectF[] minusButtons = {new RectF(), new RectF(), new RectF(), new RectF()};

    public GameView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        prefs = context.getSharedPreferences("warstate_save", Context.MODE_PRIVATE);
        load();
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
    }

    private void save() {
        SharedPreferences.Editor e = prefs.edit()
                .putInt("gold", gold)
                .putInt("energy", energy)
                .putInt("conquered", conquered);
        for (int i = 0; i < regions.length; i++) {
            e.putInt("owner_" + i, regions[i].owner);
            e.putInt("power_" + i, regions[i].power);
        }
        for (int i = 0; i < available.length; i++) e.putInt("unit_" + i, available[i]);
        e.apply();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        W = getWidth();
        H = getHeight();
        mapTop = H * 0.115f;
        mapBottom = H * 0.605f;
        previewTop = H * 0.61f;
        unitsTop = H * 0.735f;
        actionTop = H * 0.885f;

        drawBackground(c);
        drawTopBar(c);
        drawMap(c);
        drawPreview(c);
        drawUnits(c);
        drawActions(c);
        drawBottomNav(c);
    }

    private void drawBackground(Canvas c) {
        p.setShader(new LinearGradient(0, 0, 0, H,
                Color.rgb(4, 14, 24), Color.rgb(8, 29, 45), Shader.TileMode.CLAMP));
        c.drawRect(0, 0, W, H, p);
        p.setShader(null);
    }

    private void drawTopBar(Canvas c) {
        p.setColor(Color.rgb(9, 28, 44));
        c.drawRect(0, 0, W, mapTop, p);

        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W * 0.058f);
        p.setColor(Color.WHITE);
        c.drawText("WARSTATE", W * 0.045f, H * 0.050f, p);

        p.setTextSize(W * 0.024f);
        p.setColor(Color.rgb(120, 196, 240));
        c.drawText("KOMUTA MERKEZİ • v0.1.0", W * 0.047f, H * 0.072f, p);

        drawResource(c, W * 0.49f, H * 0.038f, "◆", format(gold), Color.rgb(255, 199, 75));
        drawResource(c, W * 0.73f, H * 0.038f, "ϟ", energy + "/120", Color.rgb(74, 194, 255));

        p.setColor(Color.rgb(15, 42, 62));
        roundRect(c, W * 0.04f, H * 0.082f, W * 0.96f, H * 0.108f, dp(10), p);
        p.setTextSize(W * 0.026f);
        p.setColor(Color.rgb(220, 234, 245));
        c.drawText("Görev: 3 düşman bölgesini ele geçir  (" + conquered + "/4)", W * 0.065f, H * 0.100f, p);
    }

    private void drawResource(Canvas c, float x, float y, String icon, String text, int color) {
        p.setTextSize(W * 0.032f);
        p.setColor(color);
        c.drawText(icon, x, y, p);
        p.setColor(Color.WHITE);
        c.drawText(text, x + W * 0.045f, y, p);
    }

    private void drawMap(Canvas c) {
        RectF area = new RectF(0, mapTop, W, mapBottom);
        p.setShader(new LinearGradient(0, mapTop, W, mapBottom,
                Color.rgb(22, 61, 63), Color.rgb(13, 42, 59), Shader.TileMode.CLAMP));
        c.drawRect(area, p);
        p.setShader(null);

        // Deniz/terrain şeritleri
        p.setColor(Color.argb(85, 30, 124, 170));
        c.drawOval(new RectF(-W * .2f, mapTop - H * .03f, W * 1.1f, mapTop + H * .16f), p);
        p.setColor(Color.argb(65, 84, 112, 70));
        for (int i = 0; i < 8; i++) {
            float y = mapTop + H * (0.11f + i * 0.045f);
            c.drawOval(new RectF(W * (0.03f + (i % 3) * .08f), y,
                    W * (.75f + (i % 2) * .17f), y + H * .07f), p);
        }

        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W * .030f);
        p.setColor(Color.argb(95, 220, 240, 255));
        c.drawText("K A R A D E N İ Z", W * .52f, mapTop + H * .055f, p);

        if (selectedSource >= 0 && selectedTarget >= 0) {
            Region a = regions[selectedSource];
            Region b = regions[selectedTarget];
            float ax = a.x * W, ay = mapTop + a.y * (mapBottom - mapTop);
            float bx = b.x * W, by = mapTop + b.y * (mapBottom - mapTop);
            stroke.setColor(Color.rgb(255, 76, 66));
            stroke.setStrokeWidth(dp(4));
            stroke.setPathEffect(new DashPathEffect(new float[]{dp(10), dp(7)}, 0));
            c.drawLine(ax, ay, bx, by, stroke);
            stroke.setPathEffect(null);
            drawArrowHead(c, ax, ay, bx, by);
        }

        for (int i = 0; i < regions.length; i++) drawRegion(c, i, regions[i]);

        // küçük durum mesajı
        if (System.currentTimeMillis() < statusUntil || statusUntil == 0L) {
            p.setColor(Color.argb(220, 4, 17, 28));
            roundRect(c, W * .07f, mapBottom - H * .055f, W * .93f, mapBottom - H * .015f, dp(10), p);
            p.setTextSize(W * .025f);
            p.setColor(Color.WHITE);
            p.setTextAlign(Paint.Align.CENTER);
            c.drawText(status, W * .50f, mapBottom - H * .030f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawRegion(Canvas c, int index, Region r) {
        float cx = r.x * W;
        float cy = mapTop + r.y * (mapBottom - mapTop);
        float rw = W * .205f;
        float rh = H * .065f;
        r.hit.set(cx - rw * .62f, cy - rh * .65f, cx + rw * .62f, cy + rh * .65f);

        int team = r.owner == PLAYER ? Color.rgb(39, 162, 255) : Color.rgb(242, 70, 65);
        p.setShader(new RadialGradient(cx, cy, rw, Color.argb(180, Color.red(team), Color.green(team), Color.blue(team)),
                Color.argb(35, Color.red(team), Color.green(team), Color.blue(team)), Shader.TileMode.CLAMP));
        c.drawOval(new RectF(cx - rw, cy - rh, cx + rw, cy + rh), p);
        p.setShader(null);

        stroke.setStrokeWidth(index == selectedSource || index == selectedTarget ? dp(4) : dp(2));
        stroke.setColor(team);
        c.drawOval(new RectF(cx - rw * .82f, cy - rh * .72f, cx + rw * .82f, cy + rh * .72f), stroke);

        p.setColor(Color.rgb(10, 26, 38));
        roundRect(c, cx - rw * .54f, cy - rh * .28f, cx + rw * .54f, cy + rh * .34f, dp(8), p);

        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W * .031f);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText(r.name, cx, cy - H * .002f, p);
        p.setTextSize(W * .024f);
        p.setColor(team);
        c.drawText((r.owner == PLAYER ? "MÜTTEFİK" : "DÜŞMAN") + " • LV." + r.level, cx, cy + H * .019f, p);
        p.setTextSize(W * .022f);
        p.setColor(Color.rgb(225, 235, 241));
        c.drawText("Güç " + format(r.power), cx, cy + H * .038f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawArrowHead(Canvas c, float ax, float ay, float bx, float by) {
        double angle = Math.atan2(by - ay, bx - ax);
        float len = dp(14);
        Path path = new Path();
        path.moveTo(bx, by);
        path.lineTo((float)(bx - len * Math.cos(angle - Math.PI / 6)),
                (float)(by - len * Math.sin(angle - Math.PI / 6)));
        path.lineTo((float)(bx - len * Math.cos(angle + Math.PI / 6)),
                (float)(by - len * Math.sin(angle + Math.PI / 6)));
        path.close();
        p.setColor(Color.rgb(255, 76, 66));
        c.drawPath(path, p);
    }

    private void drawPreview(Canvas c) {
        p.setColor(Color.rgb(7, 23, 37));
        c.drawRect(0, previewTop, W, unitsTop - H * .008f, p);

        p.setTextSize(W * .030f);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setColor(Color.rgb(205, 225, 239));
        c.drawText("SAVAŞ ÖNİZLEMESİ", W * .04f, previewTop + H * .025f, p);

        Region src = selectedSource >= 0 ? regions[selectedSource] : null;
        Region trg = selectedTarget >= 0 ? regions[selectedTarget] : null;

        float mid = W * .5f;
        p.setTextSize(W * .031f);
        p.setColor(Color.rgb(64, 177, 255));
        c.drawText(src == null ? "Kaynak seç" : src.name + " (Sen)", W * .05f, previewTop + H * .060f, p);
        p.setColor(Color.rgb(255, 94, 85));
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText(trg == null ? "Hedef seç" : trg.name + " (Düşman)", W * .95f, previewTop + H * .060f, p);
        p.setTextAlign(Paint.Align.LEFT);

        int attack = selectedPower();
        int defense = trg == null ? 0 : trg.power;
        int chance = defense == 0 ? 0 : (int)Math.max(8, Math.min(92, 50 + ((attack - defense) * 42.0 / Math.max(attack, defense))));

        p.setTextSize(W * .052f);
        p.setColor(chance >= 50 ? Color.rgb(65, 224, 147) : Color.rgb(255, 177, 70));
        p.setTextAlign(Paint.Align.CENTER);
        c.drawText("%" + chance, mid, previewTop + H * .083f, p);
        p.setTextSize(W * .021f);
        p.setColor(Color.LTGRAY);
        c.drawText("Tahmini başarı", mid, previewTop + H * .105f, p);

        p.setTextSize(W * .024f);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.LEFT);
        c.drawText("Saldırı: " + format(attack), W * .05f, previewTop + H * .103f, p);
        p.setTextAlign(Paint.Align.RIGHT);
        c.drawText("Savunma: " + format(defense), W * .95f, previewTop + H * .103f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawUnits(Canvas c) {
        float gap = W * .012f;
        float margin = W * .025f;
        float cardW = (W - margin * 2 - gap * 3) / 4f;
        float cardH = actionTop - unitsTop - H * .015f;

        for (int i = 0; i < 4; i++) {
            float l = margin + i * (cardW + gap);
            float r = l + cardW;
            float t = unitsTop;
            float b = unitsTop + cardH;
            p.setColor(Color.rgb(13, 38, 57));
            roundRect(c, l, t, r, b, dp(9), p);
            stroke.setColor(Color.rgb(48, 106, 142));
            stroke.setStrokeWidth(dp(1.5f));
            c.drawRoundRect(new RectF(l,t,r,b), dp(9), dp(9), stroke);

            p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(W * .025f);
            p.setColor(Color.WHITE);
            c.drawText(unitNames[i], (l+r)/2, t + H * .026f, p);

            p.setTextSize(W * .038f);
            p.setColor(Color.rgb(118, 202, 255));
            c.drawText(unitGlyph(i), (l+r)/2, t + H * .062f, p);

            p.setTextSize(W * .021f);
            p.setColor(Color.LTGRAY);
            c.drawText("Mevcut " + available[i], (l+r)/2, t + H * .087f, p);

            p.setTextSize(W * .027f);
            p.setColor(Color.WHITE);
            c.drawText(String.valueOf(selected[i]), (l+r)/2, t + H * .114f, p);

            float by = b - H * .026f;
            minusButtons[i].set(l + W*.01f, by-H*.020f, l + cardW*.34f, by+H*.010f);
            plusButtons[i].set(r - cardW*.34f, by-H*.020f, r-W*.01f, by+H*.010f);
            p.setColor(Color.rgb(24, 65, 91));
            roundRect(c, minusButtons[i].left, minusButtons[i].top, minusButtons[i].right, minusButtons[i].bottom, dp(5), p);
            roundRect(c, plusButtons[i].left, plusButtons[i].top, plusButtons[i].right, plusButtons[i].bottom, dp(5), p);
            p.setTextSize(W * .030f);
            p.setColor(Color.WHITE);
            c.drawText("−", minusButtons[i].centerX(), by + H * .002f, p);
            c.drawText("+", plusButtons[i].centerX(), by + H * .002f, p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private String unitGlyph(int i) {
        switch (i) {
            case 0: return "♟";
            case 1: return "▰";
            case 2: return "➶";
            default: return "✈";
        }
    }

    private void drawActions(Canvas c) {
        float t = actionTop;
        float b = H * .948f;
        p.setColor(Color.rgb(8, 26, 40));
        c.drawRect(0, t, W, b, p);

        float side = W * .20f;
        drawSmallAction(c, W*.025f, t+H*.012f, W*.21f, b-H*.012f, "SEÇ");
        drawSmallAction(c, W*.225f, t+H*.012f, W*.41f, b-H*.012f, "SAVUN");

        attackButton.set(W*.44f, t+H*.010f, W*.975f, b-H*.010f);
        p.setShader(new LinearGradient(attackButton.left, attackButton.top, attackButton.right, attackButton.bottom,
                Color.rgb(211, 48, 43), Color.rgb(255, 82, 64), Shader.TileMode.CLAMP));
        roundRect(c, attackButton.left, attackButton.top, attackButton.right, attackButton.bottom, dp(11), p);
        p.setShader(null);
        stroke.setColor(Color.rgb(255, 143, 120));
        stroke.setStrokeWidth(dp(2));
        c.drawRoundRect(attackButton, dp(11), dp(11), stroke);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(W * .035f);
        p.setTextAlign(Paint.Align.CENTER);
        p.setColor(Color.WHITE);
        c.drawText("⚔  SAVAŞI BAŞLAT", attackButton.centerX(), attackButton.centerY() + H*.009f, p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSmallAction(Canvas c, float l, float t, float r, float b, String text) {
        p.setColor(Color.rgb(20, 52, 73));
        roundRect(c,l,t,r,b,dp(9),p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(W*.025f);
        p.setColor(Color.rgb(220,235,245));
        c.drawText(text,(l+r)/2,(t+b)/2+H*.008f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBottomNav(Canvas c) {
        float top = H*.948f;
        p.setColor(Color.rgb(4, 16, 27));
        c.drawRect(0, top, W, H, p);
        String[] labels = {"ÜS", "DÜNYA", "BİRLİKLER", "TEKNOLOJİ", "DAHA FAZLA"};
        for (int i=0;i<labels.length;i++) {
            float cx = W*(i+.5f)/5f;
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(W*.020f);
            p.setColor(i==1 ? Color.rgb(68,183,255) : Color.rgb(154,178,194));
            c.drawText(labels[i], cx, H*.982f, p);
            if (i==1) c.drawRect(cx-W*.055f, top, cx+W*.055f, top+dp(3), p);
        }
        p.setTextAlign(Paint.Align.LEFT);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction() != MotionEvent.ACTION_UP) return true;
        float x = e.getX(), y = e.getY();

        for (int i=0;i<regions.length;i++) {
            if (regions[i].hit.contains(x,y)) {
                if (regions[i].owner == PLAYER) {
                    selectedSource = i;
                    status = regions[i].name + " kaynak bölge seçildi";
                } else {
                    selectedTarget = i;
                    status = regions[i].name + " hedef seçildi";
                }
                statusUntil = System.currentTimeMillis()+2200;
                invalidate();
                return true;
            }
        }

        for (int i=0;i<4;i++) {
            if (plusButtons[i].contains(x,y)) {
                int step = stepFor(i);
                selected[i] = Math.min(available[i], selected[i] + step);
                invalidate();
                return true;
            }
            if (minusButtons[i].contains(x,y)) {
                selected[i] = Math.max(0, selected[i] - stepFor(i));
                invalidate();
                return true;
            }
        }

        if (attackButton.contains(x,y)) {
            attack();
            invalidate();
            return true;
        }
        return true;
    }

    private int stepFor(int i) {
        return i == 0 ? 100 : (i == 1 ? 5 : (i == 2 ? 5 : 1));
    }

    private int selectedPower() {
        int s = 0;
        for (int i=0;i<4;i++) s += selected[i]*unitPower[i];
        return s;
    }

    private void attack() {
        if (selectedSource < 0 || selectedTarget < 0) {
            showStatus("Önce kaynak ve düşman bölge seç");
            return;
        }
        Region source = regions[selectedSource];
        Region target = regions[selectedTarget];
        if (source.owner != PLAYER || target.owner != ENEMY) {
            showStatus("Geçerli bir düşman hedef seç");
            return;
        }
        if (energy < 10) {
            showStatus("Yeterli enerji yok");
            return;
        }
        int attackPower = selectedPower();
        if (attackPower < 800) {
            showStatus("Daha fazla birlik seç");
            return;
        }
        for (int i=0;i<4;i++) {
            if (selected[i] > available[i]) {
                showStatus("Birlik sayısı yetersiz");
                return;
            }
        }

        energy -= 10;
        double attackRoll = attackPower * (0.88 + random.nextDouble()*0.28);
        double defenseRoll = target.power * (0.88 + random.nextDouble()*0.24);

        if (attackRoll >= defenseRoll) {
            int lossPct = 22 + random.nextInt(19);
            for (int i=0;i<4;i++) {
                int lost = (int)Math.ceil(selected[i]*(lossPct/100.0));
                available[i] = Math.max(0, available[i]-lost);
                selected[i] = Math.min(selected[i], available[i]);
            }
            target.owner = PLAYER;
            target.power = Math.max(1200, attackPower - (int)(defenseRoll*.45));
            gold += 1400 + target.level*80;
            conquered++;
            showStatus("ZAFER • " + target.name + " ele geçirildi!");
            selectedSource = selectedTarget;
            selectedTarget = findEnemy();
        } else {
            int lossPct = 48 + random.nextInt(28);
            for (int i=0;i<4;i++) {
                int lost = (int)Math.ceil(selected[i]*(lossPct/100.0));
                available[i] = Math.max(0, available[i]-lost);
                selected[i] = Math.min(selected[i], available[i]);
            }
            target.power = Math.max(900, (int)(target.power*(0.78 + random.nextDouble()*.10)));
            showStatus("SALDIRI DURDURULDU • Birlik kaybı %" + lossPct);
        }
        save();
    }

    private int findEnemy() {
        for (int i=0;i<regions.length;i++) if (regions[i].owner == ENEMY) return i;
        showStatus("TÜM BÖLGELER ELE GEÇİRİLDİ • ZAFER!");
        return -1;
    }

    private void showStatus(String s) {
        status = s;
        statusUntil = System.currentTimeMillis()+3500;
    }

    private String format(int n) {
        return String.format(Locale.US, "%,d", n).replace(',', '.');
    }

    private void roundRect(Canvas c, float l, float t, float r, float b, float radius, Paint paint) {
        c.drawRoundRect(new RectF(l,t,r,b), radius, radius, paint);
    }

    private static class Region {
        final String name;
        final float x, y;
        int owner;
        final int level;
        int power;
        final RectF hit = new RectF();

        Region(String name, float x, float y, int owner, int level, int power) {
            this.name = name;
            this.x = x;
            this.y = y;
            this.owner = owner;
            this.level = level;
            this.power = power;
        }
    }
}
