package com.elxvro.warstate;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.*;
import android.os.SystemClock;
import android.view.HapticFeedbackConstants;
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
    private static final int ALLIANCE = 4;
    private static final int MORE = 5;

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random();
    private final SharedPreferences prefs;

    private float W, H;
    private float topH, mapTop, mapBottom, previewTop, unitsTop, actionTop, navTop;

    private int screen = WORLD;
    private int gold = 12480;
    private int energy = 86;
    private int commandXp = 64;
    private int commanderLevel = 24;

    private final String[] unitNames = {"Asker", "Tank", "Topçu", "Hava"};
    private final int[] unitPower = {2, 55, 82, 330};
    private final int[] unitCount = {3500, 120, 80, 12};
    private final int[] selected = {2100, 55, 35, 6};
    private final int[] tech = {1, 1, 1, 0};

    private final Region[] regions = {
            new Region("BURSA", 0.33f, 0.47f, PLAYER, 18, 15500),
            new Region("İSTANBUL", 0.50f, 0.27f, ENEMY, 25, 24300),
            new Region("İZMİR", 0.24f, 0.70f, ENEMY, 20, 17800),
            new Region("ANKARA", 0.69f, 0.50f, ENEMY, 22, 21500),
            new Region("KONYA", 0.60f, 0.72f, ENEMY, 19, 16600),
            new Region("SAMSUN", 0.78f, 0.30f, ENEMY, 21, 19100)
    };

    private int selectedSource = 0;
    private int selectedTarget = 1;

    private String toast = "BURSA hazır • İSTANBUL hedef";
    private long toastUntil = 0L;
    private long lastTick = System.currentTimeMillis();

    private boolean battleActive = false;
    private long battleStarted = 0L;
    private int pendingChance = 0;

    private final RectF attackRect = new RectF();
    private final RectF autoRect = new RectF();
    private final RectF defendRect = new RectF();
    private final RectF upgradeRect = new RectF();

    private final RectF[] plusRects = {new RectF(),new RectF(),new RectF(),new RectF()};
    private final RectF[] minusRects = {new RectF(),new RectF(),new RectF(),new RectF()};
    private final RectF[] navRects = {
            new RectF(),new RectF(),new RectF(),new RectF(),new RectF(),new RectF()
    };
    private final RectF[] sideRects = {
            new RectF(),new RectF(),new RectF(),new RectF()
    };
    private final RectF[] topRects = {
            new RectF(),new RectF(),new RectF()
    };
    private final RectF[] baseRects = {
            new RectF(),new RectF(),new RectF(),new RectF(),new RectF(),new RectF()
    };
    private final RectF[] techRects = {
            new RectF(),new RectF(),new RectF(),new RectF()
    };

    public GameView(Context context) {
        super(context);
        setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        prefs = context.getSharedPreferences("warstate_save", Context.MODE_PRIVATE);

        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeCap(Paint.Cap.ROUND);
        stroke.setStrokeJoin(Paint.Join.ROUND);

        load();
        migrate();
        applyOfflineIncome();
        setFocusable(true);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void load() {
        gold = prefs.getInt("gold", 12480);
        energy = prefs.getInt("energy", 86);
        commandXp = prefs.getInt("xp", 64);
        commanderLevel = prefs.getInt("commander_level", 24);
        lastTick = prefs.getLong("last_tick", System.currentTimeMillis());

        for (int i=0;i<unitCount.length;i++) {
            unitCount[i] = prefs.getInt("unit_"+i, unitCount[i]);
        }
        for (int i=0;i<tech.length;i++) {
            tech[i] = prefs.getInt("tech_"+i, tech[i]);
        }
        for (int i=0;i<regions.length;i++) {
            regions[i].owner = prefs.getInt("owner_"+i, regions[i].owner);
            regions[i].power = prefs.getInt("region_power_"+i, regions[i].power);
        }

        selectedSource = prefs.getInt("selected_source", 0);
        selectedTarget = prefs.getInt("selected_target", 1);
        if (selectedSource < 0 || selectedSource >= regions.length) selectedSource = 0;
        if (selectedTarget < 0 || selectedTarget >= regions.length) selectedTarget = 1;
    }

    private void migrate() {
        int version = prefs.getInt("save_version", 1);
        if (version >= 3) {
            clampSelection();
            return;
        }

        int[] minimum = {3000, 100, 65, 10};
        for (int i=0;i<4;i++) unitCount[i] = Math.max(unitCount[i], minimum[i]);
        energy = Math.max(energy, 90);
        gold = Math.max(gold, 12000);
        selected[0] = Math.min(2100, unitCount[0]);
        selected[1] = Math.min(55, unitCount[1]);
        selected[2] = Math.min(35, unitCount[2]);
        selected[3] = Math.min(6, unitCount[3]);
        save();
        prefs.edit().putInt("save_version", 3).apply();
        toast = "v0.3.0 komuta paketi aktif • Ordu takviye edildi";
        toastUntil = System.currentTimeMillis() + 5000;
    }

    private void save() {
        SharedPreferences.Editor e = prefs.edit()
                .putInt("gold", gold)
                .putInt("energy", energy)
                .putInt("xp", commandXp)
                .putInt("commander_level", commanderLevel)
                .putLong("last_tick", lastTick)
                .putInt("selected_source", selectedSource)
                .putInt("selected_target", selectedTarget)
                .putInt("save_version", 3);
        for (int i=0;i<4;i++) {
            e.putInt("unit_"+i, unitCount[i]);
            e.putInt("tech_"+i, tech[i]);
        }
        for (int i=0;i<regions.length;i++) {
            e.putInt("owner_"+i, regions[i].owner);
            e.putInt("region_power_"+i, regions[i].power);
        }
        e.apply();
    }

    private void applyOfflineIncome() {
        long now = System.currentTimeMillis();
        long elapsed = Math.max(0, Math.min(now-lastTick, 8L*60*60*1000));
        int mins = (int)(elapsed / 60000L);
        if (mins > 0) {
            gold += mins * controlledCount() * 30;
            energy = Math.min(maxEnergy(), energy + mins/2);
            lastTick = now;
            save();
        }
    }

    private void tickIncome() {
        long now = System.currentTimeMillis();
        long elapsed = now-lastTick;
        if (elapsed >= 60000L) {
            int mins=(int)(elapsed/60000L);
            gold += mins * controlledCount() * 30;
            energy = Math.min(maxEnergy(), energy + mins/2);
            lastTick += mins*60000L;
            save();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        W=getWidth();
        H=getHeight();
        topH=H*.112f;
        mapTop=topH;
        mapBottom=H*.595f;
        previewTop=H*.595f;
        unitsTop=H*.720f;
        actionTop=H*.868f;
        navTop=H*.945f;

        tickIncome();
        drawBackground(c);
        drawTopBar(c);

        if (screen==WORLD) drawWorld(c);
        else if (screen==BASE) drawBase(c);
        else if (screen==ARMY) drawArmy(c);
        else if (screen==TECH) drawTech(c);
        else if (screen==ALLIANCE) drawAlliance(c);
        else drawMore(c);

        drawBottomNav(c);

        if (battleActive) {
            drawBattleOverlay(c);
            postInvalidateDelayed(32);
        }
    }

    private void drawBackground(Canvas c) {
        p.setShader(new LinearGradient(0,0,0,H,
                Color.rgb(3,12,21), Color.rgb(4,24,39), Shader.TileMode.CLAMP));
        c.drawRect(0,0,W,H,p);
        p.setShader(null);
    }

    private void drawTopBar(Canvas c) {
        p.setShader(new LinearGradient(0,0,W,topH,
                Color.rgb(7,24,38), Color.rgb(4,16,28), Shader.TileMode.CLAMP));
        c.drawRect(0,0,W,topH,p);
        p.setShader(null);

        // Komutan amblemi
        Path badge=new Path();
        float bx=W*.055f, by=H*.039f;
        badge.moveTo(bx-W*.034f,by-H*.027f);
        badge.lineTo(bx+W*.034f,by-H*.027f);
        badge.lineTo(bx+W*.030f,by+H*.018f);
        badge.lineTo(bx,by+H*.032f);
        badge.lineTo(bx-W*.030f,by+H*.018f);
        badge.close();
        p.setColor(Color.rgb(15,55,83));
        p.setShadowLayer(dp(6),0,dp(2),Color.BLACK);
        c.drawPath(badge,p);
        p.clearShadowLayer();
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(Color.rgb(83,171,226));
        c.drawPath(badge,stroke);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.034f);
        p.setColor(Color.WHITE);
        c.drawText("W",bx,by+H*.010f,p);
        p.setTextAlign(Paint.Align.LEFT);

        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.023f);
        p.setColor(Color.rgb(205,222,233));
        c.drawText("Komutan",W*.100f,H*.026f,p);
        p.setTextSize(W*.020f);
        p.setColor(Color.WHITE);
        c.drawText("TR•Alparslan",W*.100f,H*.047f,p);
        p.setTextSize(W*.017f);
        p.setColor(Color.rgb(171,195,211));
        c.drawText("Lv. "+commanderLevel,W*.100f,H*.067f,p);

        // XP bar
        p.setColor(Color.rgb(18,44,61));
        roundRect(c,W*.155f,H*.057f,W*.280f,H*.066f,dp(5),p);
        p.setColor(Color.rgb(47,178,241));
        roundRect(c,W*.155f,H*.057f,W*(.155f+.125f*commandXp/100f),H*.066f,dp(5),p);

        drawTopResource(c,W*.335f,H*.028f,"◆",format(gold),Color.rgb(255,193,62));
        drawTopResource(c,W*.515f,H*.028f,"ϟ",energy+"/"+maxEnergy(),Color.rgb(69,195,255));
        drawTopResource(c,W*.700f,H*.028f,"♟",format(totalPersonnel()),Color.rgb(201,219,230));

        // Üst hızlı işlemler
        String[] icons={"▣","★","✉"};
        String[] labels={"Etkinlik","Görevler","Mesajlar"};
        for(int i=0;i<3;i++){
            float l=W*(.725f+i*.087f);
            float t=H*.053f;
            float r=l+W*.077f;
            float b=H*.104f;
            topRects[i].set(l,t,r,b);
            p.setColor(Color.rgb(10,34,50));
            roundRect(c,l,t,r,b,dp(7),p);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(Color.rgb(44,86,109));
            c.drawRoundRect(topRects[i],dp(7),dp(7),stroke);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.020f);
            p.setColor(i==0?Color.rgb(255,194,72):Color.rgb(205,225,238));
            c.drawText(icons[i],(l+r)/2,t+H*.021f,p);
            p.setTextSize(W*.0105f);
            p.setColor(Color.rgb(180,199,211));
            c.drawText(labels[i],(l+r)/2,b-H*.007f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawTopResource(Canvas c,float x,float y,String icon,String value,int color){
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.024f);
        p.setColor(color);
        c.drawText(icon,x,y,p);
        p.setColor(Color.WHITE);
        c.drawText(value,x+W*.030f,y,p);
    }

    private void drawWorld(Canvas c) {
        drawMap(c);
        drawPreview(c);
        drawUnitCards(c);
        drawActionBar(c);
    }

    private void drawMap(Canvas c) {
        // Temiz sinematik strateji zemini: tamamen uygulama içinde çizilir.
        p.setShader(new LinearGradient(0,mapTop,0,mapBottom,
                Color.rgb(7,49,70),Color.rgb(5,30,42),Shader.TileMode.CLAMP));
        c.drawRect(0,mapTop,W,mapBottom,p);
        p.setShader(null);

        // Deniz ışığı ve sis katmanları.
        p.setColor(Color.argb(34,66,174,214));
        c.drawOval(new RectF(-W*.10f,mapTop-H*.020f,W*1.05f,mapTop+H*.110f),p);
        p.setColor(Color.argb(20,220,235,226));
        for(int i=0;i<5;i++){
            float yy=mapTop+H*(.095f+i*.070f);
            c.drawOval(new RectF(W*(.04f+(i%2)*.06f),yy,W*(.92f-(i%3)*.04f),yy+H*.030f),p);
        }

        // Görev kartı
        p.setColor(Color.argb(225,5,25,39));
        roundRect(c,W*.14f,mapTop+H*.008f,W*.68f,mapTop+H*.066f,dp(8),p);
        p.setColor(Color.rgb(216,177,69));
        c.drawRect(W*.14f,mapTop+H*.008f,W*.148f,mapTop+H*.066f,p);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.020f);
        p.setColor(Color.WHITE);
        c.drawText("Anadolu'nun Gücü",W*.168f,mapTop+H*.032f,p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(W*.015f);
        p.setColor(Color.rgb(175,199,214));
        c.drawText("5 bölge ele geçir  ("+controlledCount()+"/5)",W*.168f,mapTop+H*.052f,p);

        // Arazi adası
        Path land=turkeyShape();
        p.setShader(new LinearGradient(W*.15f,mapTop,W*.90f,mapBottom,
                Color.rgb(58,91,58),Color.rgb(83,72,46),Shader.TileMode.CLAMP));
        p.setShadowLayer(dp(18),0,dp(7),Color.argb(180,0,0,0));
        c.drawPath(land,p);
        p.clearShadowLayer();
        p.setShader(null);

        // Kıyı parlaması
        stroke.setStrokeWidth(dp(5));
        stroke.setColor(Color.argb(55,124,221,205));
        c.drawPath(land,stroke);
        stroke.setStrokeWidth(dp(1.8f));
        stroke.setColor(Color.rgb(99,146,101));
        c.drawPath(land,stroke);

        drawTerrain(c);
        drawRoads(c);

        // deniz etiketi
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.019f);
        p.setColor(Color.argb(100,220,239,247));
        p.setLetterSpacing(.20f);
        c.drawText("KARADENİZ",W*.68f,mapTop+H*.092f,p);
        p.setLetterSpacing(0);

        drawSideRail(c);

        // saldırı yolu
        if(validAttackSelection()){
            Region a=regions[selectedSource], b=regions[selectedTarget];
            drawRoute(c,a,b);
        }

        // dekoratif birlikler
        drawMapUnits(c);

        for(int i=0;i<regions.length;i++) drawRegion(c,i,regions[i]);

        drawMinimap(c);
        drawRegionInfo(c);

        if(System.currentTimeMillis()<toastUntil || toastUntil==0L){
            p.setColor(Color.argb(235,3,17,28));
            roundRect(c,W*.19f,mapBottom-H*.045f,W*.79f,mapBottom-H*.010f,dp(8),p);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.0155f);
            p.setColor(Color.WHITE);
            c.drawText(toast,W*.49f,mapBottom-H*.023f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private Path turkeyShape(){
        float mh=mapBottom-mapTop;
        Path q=new Path();
        q.moveTo(W*.145f,mapTop+mh*.55f);
        q.lineTo(W*.180f,mapTop+mh*.42f);
        q.lineTo(W*.250f,mapTop+mh*.37f);
        q.lineTo(W*.300f,mapTop+mh*.27f);
        q.lineTo(W*.390f,mapTop+mh*.23f);
        q.lineTo(W*.455f,mapTop+mh*.18f);
        q.lineTo(W*.550f,mapTop+mh*.21f);
        q.lineTo(W*.620f,mapTop+mh*.19f);
        q.lineTo(W*.715f,mapTop+mh*.24f);
        q.lineTo(W*.825f,mapTop+mh*.31f);
        q.lineTo(W*.925f,mapTop+mh*.43f);
        q.lineTo(W*.900f,mapTop+mh*.55f);
        q.lineTo(W*.820f,mapTop+mh*.61f);
        q.lineTo(W*.785f,mapTop+mh*.72f);
        q.lineTo(W*.690f,mapTop+mh*.75f);
        q.lineTo(W*.610f,mapTop+mh*.84f);
        q.lineTo(W*.500f,mapTop+mh*.81f);
        q.lineTo(W*.420f,mapTop+mh*.88f);
        q.lineTo(W*.325f,mapTop+mh*.83f);
        q.lineTo(W*.245f,mapTop+mh*.85f);
        q.lineTo(W*.185f,mapTop+mh*.75f);
        q.lineTo(W*.145f,mapTop+mh*.67f);
        q.close();
        return q;
    }

    private void drawTerrain(Canvas c){
        float mh=mapBottom-mapTop;

        p.setColor(Color.argb(90,27,103,55));
        c.drawOval(new RectF(W*.18f,mapTop+mh*.26f,W*.43f,mapTop+mh*.49f),p);
        c.drawOval(new RectF(W*.60f,mapTop+mh*.28f,W*.88f,mapTop+mh*.53f),p);
        c.drawOval(new RectF(W*.40f,mapTop+mh*.61f,W*.68f,mapTop+mh*.83f),p);

        // küçük orman kümeleri
        p.setColor(Color.argb(105,18,77,43));
        for(int i=0;i<34;i++){
            float tx=W*(.20f+(i%9)*.075f);
            float ty=mapTop+mh*(.30f+((i*7)%17)*.030f);
            if(tx<W*.88f && ty<mapTop+mh*.80f){
                Path tree=new Path();
                tree.moveTo(tx,ty-H*.010f);
                tree.lineTo(tx-W*.008f,ty+H*.008f);
                tree.lineTo(tx+W*.008f,ty+H*.008f);
                tree.close();
                c.drawPath(tree,p);
            }
        }

        // tarla dokusu
        stroke.setStrokeWidth(dp(1));
        for(int i=0;i<10;i++){
            stroke.setColor(Color.argb(28,225,199,120));
            float y=mapTop+mh*(.37f+i*.042f);
            c.drawLine(W*.20f,y,W*.88f,y+H*.010f,stroke);
        }

        // dağlar
        for(int i=0;i<12;i++){
            float x=W*(.20f+i*.055f);
            float y=mapTop+mh*(.70f+(i%3)*.04f);
            drawMountain(c,x,y,W*.026f,H*.012f);
        }
        for(int i=0;i<6;i++){
            float x=W*(.60f+i*.050f);
            float y=mapTop+mh*(.39f+(i%2)*.04f);
            drawMountain(c,x,y,W*.021f,H*.010f);
        }

        // şehir yolları
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.argb(80,230,221,174));
        for(int i=0;i<regions.length;i++){
            Region r=regions[i];
            float cx=r.x*W;
            float cy=mapTop+r.y*mh;
            c.drawCircle(cx,cy,W*.030f,stroke);
        }
    }

    private void drawMountain(Canvas c,float x,float y,float w,float h){
        Path m=new Path();
        m.moveTo(x-w,y+h);
        m.lineTo(x,y-h);
        m.lineTo(x+w,y+h);
        m.close();
        p.setColor(Color.argb(135,95,97,86));
        c.drawPath(m,p);
        Path snow=new Path();
        snow.moveTo(x-w*.26f,y-h*.25f);
        snow.lineTo(x,y-h);
        snow.lineTo(x+w*.26f,y-h*.25f);
        snow.close();
        p.setColor(Color.argb(120,220,228,218));
        c.drawPath(snow,p);
    }

    private void drawRoads(Canvas c){
        float mh=mapBottom-mapTop;
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(Color.argb(110,205,185,132));
        stroke.setPathEffect(new DashPathEffect(new float[]{dp(5),dp(4)},0));

        Path road=new Path();
        road.moveTo(W*.23f,mapTop+mh*.70f);
        road.cubicTo(W*.32f,mapTop+mh*.50f,W*.44f,mapTop+mh*.35f,W*.68f,mapTop+mh*.50f);
        road.cubicTo(W*.73f,mapTop+mh*.47f,W*.76f,mapTop+mh*.35f,W*.78f,mapTop+mh*.30f);
        c.drawPath(road,stroke);

        Path road2=new Path();
        road2.moveTo(W*.33f,mapTop+mh*.47f);
        road2.cubicTo(W*.42f,mapTop+mh*.58f,W*.50f,mapTop+mh*.70f,W*.60f,mapTop+mh*.72f);
        c.drawPath(road2,stroke);
        stroke.setPathEffect(null);
    }

    private void drawSideRail(Canvas c){
        String[] icons={"◎","◉","♟","▤"};
        String[] labels={"Harita","Keşif","İttifak","Rapor"};
        float l=W*.012f, r=W*.115f;
        float start=mapTop+H*.100f;
        float h=H*.060f;
        for(int i=0;i<4;i++){
            float t=start+i*(h+H*.006f);
            sideRects[i].set(l,t,r,t+h);
            p.setColor(Color.argb(232,4,22,34));
            roundRect(c,l,t,r,t+h,dp(7),p);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(Color.rgb(42,88,113));
            c.drawRoundRect(sideRects[i],dp(7),dp(7),stroke);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.021f);
            p.setColor(Color.rgb(183,216,235));
            c.drawText(icons[i],(l+r)/2,t+H*.023f,p);
            p.setTextSize(W*.011f);
            p.setColor(Color.rgb(196,211,220));
            c.drawText(labels[i],(l+r)/2,t+H*.045f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawMapUnits(Canvas c){
        float mh=mapBottom-mapTop;
        // tanklar
        p.setColor(Color.rgb(37,45,42));
        drawTank(c,W*.42f,mapTop+mh*.62f,W*.034f);
        drawTank(c,W*.61f,mapTop+mh*.41f,W*.032f);
        drawTank(c,W*.73f,mapTop+mh*.62f,W*.029f);

        // uçaklar
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.020f);
        p.setColor(Color.argb(170,214,230,235));
        c.drawText("✈",W*.39f,mapTop+mh*.29f,p);
        c.drawText("✈",W*.67f,mapTop+mh*.25f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawTank(Canvas c,float x,float y,float s){
        p.setColor(Color.rgb(38,49,45));
        roundRect(c,x-s,y-s*.30f,x+s,y+s*.30f,dp(3),p);
        p.setColor(Color.rgb(65,79,67));
        c.drawOval(new RectF(x-s*.38f,y-s*.52f,x+s*.35f,y+s*.12f),p);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(Color.rgb(35,43,39));
        c.drawLine(x,y-s*.35f,x+s*.78f,y-s*.60f,stroke);
    }

    private void drawRoute(Canvas c,Region a,Region b){
        float mh=mapBottom-mapTop;
        float ax=a.x*W, ay=mapTop+a.y*mh;
        float bx=b.x*W, by=mapTop+b.y*mh;

        stroke.setStrokeWidth(dp(7));
        stroke.setColor(Color.argb(60,255,74,61));
        c.drawLine(ax,ay,bx,by,stroke);

        stroke.setStrokeWidth(dp(2.5f));
        stroke.setColor(Color.rgb(255,86,70));
        stroke.setPathEffect(new DashPathEffect(new float[]{dp(7),dp(5)},0));
        c.drawLine(ax,ay,bx,by,stroke);
        stroke.setPathEffect(null);

        double a1=Math.atan2(by-ay,bx-ax);
        float len=dp(14);
        Path head=new Path();
        head.moveTo(bx,by);
        head.lineTo((float)(bx-len*Math.cos(a1-Math.PI/6)),(float)(by-len*Math.sin(a1-Math.PI/6)));
        head.lineTo((float)(bx-len*Math.cos(a1+Math.PI/6)),(float)(by-len*Math.sin(a1+Math.PI/6)));
        head.close();
        p.setColor(Color.rgb(255,86,70));
        c.drawPath(head,p);
    }

    private void drawRegion(Canvas c,int index,Region r){
        float mh=mapBottom-mapTop;
        float cx=r.x*W, cy=mapTop+r.y*mh;
        int col=r.owner==PLAYER?Color.rgb(44,168,255):Color.rgb(245,71,63);
        boolean sel=index==selectedSource || index==selectedTarget;
        float pulse=(float)((Math.sin(SystemClock.uptimeMillis()/300.0)+1)/2.0);
        float rad=W*(sel?.054f+.006f*pulse:.047f);

        r.hit.set(cx-W*.080f,cy-H*.055f,cx+W*.080f,cy+H*.055f);

        p.setShader(new RadialGradient(cx,cy,rad*2.2f,
                Color.argb(sel?155:90,Color.red(col),Color.green(col),Color.blue(col)),
                Color.TRANSPARENT,Shader.TileMode.CLAMP));
        c.drawCircle(cx,cy,rad*2.2f,p);
        p.setShader(null);

        stroke.setStrokeWidth(dp(sel?3.5f:2f));
        stroke.setColor(col);
        c.drawCircle(cx,cy,rad,stroke);

        // Hacimli şehir/üs görünümü
        float baseW=W*.055f;
        float baseH=H*.026f;
        p.setColor(Color.argb(210,7,18,26));
        c.drawOval(new RectF(cx-baseW,cy-baseH*.20f,cx+baseW,cy+baseH*.72f),p);
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(Color.argb(180,Color.red(col),Color.green(col),Color.blue(col)));
        c.drawOval(new RectF(cx-baseW,cy-baseH*.20f,cx+baseW,cy+baseH*.72f),stroke);

        // merkez kule ve yan binalar
        p.setShader(new LinearGradient(cx,cy-H*.038f,cx,cy+H*.010f,
                Color.rgb(48,58,63),Color.rgb(11,23,30),Shader.TileMode.CLAMP));
        c.drawRect(cx-W*.012f,cy-H*.034f,cx+W*.011f,cy+H*.009f,p);
        c.drawRect(cx-W*.034f,cy-H*.018f,cx-W*.015f,cy+H*.009f,p);
        c.drawRect(cx+W*.016f,cy-H*.022f,cx+W*.035f,cy+H*.009f,p);
        p.setShader(null);

        // çatı ışıkları
        p.setColor(Color.argb(220,Color.red(col),Color.green(col),Color.blue(col)));
        c.drawRect(cx-W*.012f,cy-H*.035f,cx+W*.011f,cy-H*.031f,p);
        c.drawRect(cx-W*.034f,cy-H*.019f,cx-W*.015f,cy-H*.016f,p);
        c.drawRect(cx+W*.016f,cy-H*.023f,cx+W*.035f,cy-H*.020f,p);

        // merkez anten ve bayrak
        stroke.setStrokeWidth(dp(1.5f));
        stroke.setColor(Color.rgb(210,219,224));
        c.drawLine(cx,cy-H*.034f,cx,cy-H*.060f,stroke);
        p.setColor(col);
        c.drawRect(cx,cy-H*.058f,cx+W*.030f,cy-H*.043f,p);

        // etiket
        float pw=W*.135f, ph=H*.033f;
        float py=cy+H*.020f;
        p.setColor(Color.argb(235,5,18,28));
        roundRect(c,cx-pw/2,py,cx+pw/2,py+ph,dp(6),p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.018f);
        p.setColor(Color.WHITE);
        c.drawText(r.name,cx,py+H*.015f,p);
        p.setTextSize(W*.013f);
        p.setColor(col);
        c.drawText("LV."+r.level+"  •  "+shortPower(r.power),cx,py+H*.029f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawMinimap(Canvas c){
        float l=W*.805f, t=mapBottom-H*.126f, r=W*.985f, b=mapBottom-H*.062f;
        p.setColor(Color.argb(235,4,20,31));
        roundRect(c,l,t,r,b,dp(7),p);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.rgb(67,111,134));
        c.drawRoundRect(new RectF(l,t,r,b),dp(7),dp(7),stroke);

        Path mini=new Path();
        float ww=r-l, hh=b-t;
        mini.moveTo(l+ww*.08f,t+hh*.55f);
        mini.lineTo(l+ww*.18f,t+hh*.34f);
        mini.lineTo(l+ww*.37f,t+hh*.24f);
        mini.lineTo(l+ww*.60f,t+hh*.30f);
        mini.lineTo(l+ww*.90f,t+hh*.50f);
        mini.lineTo(l+ww*.76f,t+hh*.72f);
        mini.lineTo(l+ww*.42f,t+hh*.78f);
        mini.lineTo(l+ww*.15f,t+hh*.66f);
        mini.close();
        p.setColor(Color.rgb(45,60,60));
        c.drawPath(mini,p);
        for(Region rr:regions){
            p.setColor(rr.owner==PLAYER?Color.rgb(44,168,255):Color.rgb(245,71,63));
            c.drawCircle(l+ww*(rr.x-.10f)/.90f,t+hh*rr.y,dp(2.5f),p);
        }
    }

    private void drawRegionInfo(Canvas c){
        Region r=selectedSource>=0?regions[selectedSource]:regions[0];
        float l=W*.805f, t=mapBottom-H*.057f, rr=W*.985f, b=mapBottom-H*.007f;
        p.setColor(Color.argb(235,4,20,31));
        roundRect(c,l,t,rr,b,dp(7),p);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.012f);
        p.setColor(Color.rgb(170,199,216));
        c.drawText("Bölge Bilgisi",l+W*.012f,t+H*.014f,p);
        p.setTextSize(W*.016f);
        p.setColor(Color.WHITE);
        c.drawText(r.name,l+W*.012f,t+H*.031f,p);
        p.setTextSize(W*.0105f);
        p.setColor(Color.rgb(68,181,248));
        c.drawText(r.owner==PLAYER?"Müttefik Bölge":"Düşman Bölge",l+W*.012f,t+H*.044f,p);
    }

    private void drawPreview(Canvas c){
        p.setColor(Color.rgb(4,18,29));
        c.drawRect(0,previewTop,W,unitsTop,p);

        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.019f);
        p.setColor(Color.rgb(199,220,233));
        c.drawText("Savaş Önizlemesi",W*.025f,previewTop+H*.021f,p);

        Region a=selectedSource>=0?regions[selectedSource]:null;
        Region b=selectedTarget>=0?regions[selectedTarget]:null;
        int atk=selectedPower();
        int def=b==null?0:b.power;
        int chance=battleChance(atk,def);

        // sol panel
        drawBattleSide(c,W*.025f,previewTop+H*.031f,W*.355f,unitsTop-H*.008f,
                a==null?"Kaynak seç":a.name+" (Sen)",true,atk);
        // sağ panel
        drawBattleSide(c,W*.645f,previewTop+H*.031f,W*.975f,unitsTop-H*.008f,
                b==null?"Hedef seç":b.name+" (Düşman)",false,def);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.040f);
        p.setColor(chance>=65?Color.rgb(67,231,144):chance>=40?Color.rgb(255,188,73):Color.rgb(255,95,79));
        c.drawText("%"+chance,W*.5f,previewTop+H*.073f,p);
        p.setTextSize(W*.013f);
        p.setColor(Color.rgb(164,187,201));
        c.drawText("Kazanma Şansı",W*.5f,previewTop+H*.094f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBattleSide(Canvas c,float l,float t,float r,float b,String title,boolean friendly,int power){
        int col=friendly?Color.rgb(44,168,255):Color.rgb(245,71,63);
        p.setColor(Color.rgb(7,28,42));
        roundRect(c,l,t,r,b,dp(7),p);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.argb(170,Color.red(col),Color.green(col),Color.blue(col)));
        c.drawRoundRect(new RectF(l,t,r,b),dp(7),dp(7),stroke);

        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.018f);
        p.setColor(col);
        c.drawText(title,l+W*.015f,t+H*.020f,p);

        String[] glyph={"●","▰","➤","✈"};
        for(int i=0;i<4;i++){
            float x=l+W*(.040f+i*.067f);
            p.setTextAlign(Paint.Align.CENTER);
            p.setTextSize(W*.018f);
            p.setColor(Color.rgb(208,222,230));
            c.drawText(glyph[i],x,t+H*.048f,p);
            p.setTextSize(W*.012f);
            p.setColor(Color.WHITE);
            int val=friendly?selected[i]:enemyUnitEstimate(i);
            c.drawText(format(val),x,t+H*.068f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.013f);
        p.setColor(Color.rgb(185,205,217));
        c.drawText("Toplam Güç: "+format(power),(l+r)/2,b-H*.008f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private int enemyUnitEstimate(int i){
        Region b=selectedTarget>=0?regions[selectedTarget]:regions[1];
        if(i==0) return Math.max(500,b.power/7);
        if(i==1) return Math.max(20,b.power/180);
        if(i==2) return Math.max(15,b.power/240);
        return Math.max(2,b.power/1300);
    }

    private void drawUnitCards(Canvas c){
        float gap=W*.008f;
        float margin=W*.018f;
        float cardW=(W-margin*2-gap*4)/5f;
        float t=unitsTop+H*.006f;
        float b=actionTop-H*.006f;

        for(int i=0;i<5;i++){
            float l=margin+i*(cardW+gap);
            float r=l+cardW;
            p.setShader(new LinearGradient(l,t,l,b,
                    Color.rgb(14,45,65),Color.rgb(7,26,40),Shader.TileMode.CLAMP));
            roundRect(c,l,t,r,b,dp(8),p);
            p.setShader(null);
            stroke.setStrokeWidth(dp(i==0?2:1));
            stroke.setColor(i==0?Color.rgb(70,193,255):Color.rgb(45,96,125));
            c.drawRoundRect(new RectF(l,t,r,b),dp(8),dp(8),stroke);

            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.0155f);
            p.setColor(Color.WHITE);
            c.drawText(i<4?unitNames[i]:"Özel", (l+r)/2,t+H*.018f,p);

            if(i<4){
                p.setTextSize(W*.027f);
                p.setColor(Color.rgb(113,203,255));
                c.drawText(new String[]{"♟","▰","➤","✈"}[i],(l+r)/2,t+H*.050f,p);
                p.setTextSize(W*.012f);
                p.setColor(Color.rgb(164,188,203));
                c.drawText("Mevcut "+format(unitCount[i]),(l+r)/2,t+H*.070f,p);

                p.setTextSize(W*.019f);
                p.setColor(Color.WHITE);
                c.drawText(format(selected[i]),(l+r)/2,t+H*.092f,p);

                float by=b-H*.021f;
                minusRects[i].set(l+W*.006f,by-H*.017f,l+cardW*.31f,by+H*.008f);
                plusRects[i].set(r-cardW*.31f,by-H*.017f,r-W*.006f,by+H*.008f);
                p.setColor(Color.rgb(22,65,90));
                roundRect(c,minusRects[i].left,minusRects[i].top,minusRects[i].right,minusRects[i].bottom,dp(4),p);
                roundRect(c,plusRects[i].left,plusRects[i].top,plusRects[i].right,plusRects[i].bottom,dp(4),p);
                p.setTextSize(W*.021f);
                p.setColor(Color.WHITE);
                c.drawText("−",minusRects[i].centerX(),by+H*.001f,p);
                c.drawText("+",plusRects[i].centerX(),by+H*.001f,p);
            }else{
                p.setTextSize(W*.030f);
                p.setColor(Color.rgb(109,131,145));
                c.drawText("🔒",(l+r)/2,t+H*.055f,p);
                p.setTextSize(W*.011f);
                p.setColor(Color.rgb(239,100,82));
                c.drawText("Komuta Lv.25",(l+r)/2,t+H*.082f,p);
                c.drawText("gerekli",(l+r)/2,t+H*.099f,p);
            }
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawActionBar(Canvas c){
        p.setColor(Color.rgb(5,20,32));
        c.drawRect(0,actionTop,W,navTop,p);

        autoRect.set(W*.018f,actionTop+H*.010f,W*.160f,navTop-H*.010f);
        defendRect.set(W*.172f,actionTop+H*.010f,W*.314f,navTop-H*.010f);
        upgradeRect.set(W*.326f,actionTop+H*.010f,W*.468f,navTop-H*.010f);
        attackRect.set(W*.485f,actionTop+H*.008f,W*.982f,navTop-H*.008f);

        drawSmallAction(c,autoRect,"◎","SEÇ");
        drawSmallAction(c,defendRect,"⬟","SAVUN");
        drawSmallAction(c,upgradeRect,"▲","YÜKSELT");

        p.setShader(new LinearGradient(attackRect.left,attackRect.top,attackRect.right,attackRect.bottom,
                Color.rgb(195,44,42),Color.rgb(255,78,63),Shader.TileMode.CLAMP));
        roundRect(c,attackRect.left,attackRect.top,attackRect.right,attackRect.bottom,dp(10),p);
        p.setShader(null);
        stroke.setStrokeWidth(dp(2));
        stroke.setColor(Color.rgb(255,145,122));
        c.drawRoundRect(attackRect,dp(10),dp(10),stroke);

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.028f);
        p.setColor(Color.WHITE);
        c.drawText("⚔  SAVAŞI BAŞLAT",attackRect.centerX(),attackRect.centerY()+H*.009f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawSmallAction(Canvas c,RectF r,String icon,String label){
        p.setColor(Color.rgb(17,51,72));
        roundRect(c,r.left,r.top,r.right,r.bottom,dp(8),p);
        stroke.setStrokeWidth(dp(1));
        stroke.setColor(Color.rgb(44,87,111));
        c.drawRoundRect(r,dp(8),dp(8),stroke);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.021f);
        p.setColor(Color.rgb(210,229,239));
        c.drawText(icon,r.centerX(),r.top+H*.023f,p);
        p.setTextSize(W*.012f);
        c.drawText(label,r.centerX(),r.bottom-H*.009f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBottomNav(Canvas c){
        p.setColor(Color.rgb(3,14,24));
        c.drawRect(0,navTop,W,H,p);

        String[] names={"ÜS","DÜNYA","BİRLİKLER","TEKNOLOJİ","İTTİFAK","DAHA FAZLA"};
        String[] icons={"⌂","◎","♟","⚛","⚑","▦"};
        for(int i=0;i<6;i++){
            float l=W*i/6f,r=W*(i+1)/6f;
            navRects[i].set(l,navTop,r,H);
            boolean active=(screen==BASE&&i==0)||(screen==WORLD&&i==1)||(screen==ARMY&&i==2)
                    ||(screen==TECH&&i==3)||(screen==ALLIANCE&&i==4)||(screen==MORE&&i==5);
            if(active){
                p.setColor(Color.argb(70,45,166,230));
                c.drawRect(l,navTop,r,H,p);
                p.setColor(Color.rgb(62,193,255));
                c.drawRect(l,navTop,r,navTop+dp(3),p);
            }
            p.setTextAlign(Paint.Align.CENTER);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.019f);
            p.setColor(active?Color.rgb(74,196,255):Color.rgb(144,170,188));
            c.drawText(icons[i],(l+r)/2,navTop+H*.023f,p);
            p.setTextSize(W*.0105f);
            c.drawText(names[i],(l+r)/2,H*.985f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawBase(Canvas c){
        drawSectionHeader(c,"ÜS","Komuta merkezini ve askerî tesisleri geliştir");
        float y=H*.165f;
        String[] n={"Komuta Merkezi","Kışla","Tank Fabrikası","Topçu Üssü","Hava Üssü","Savunma"};
        String[] d={"Lv."+commanderLevel,"Piyade üretimi","Zırhlı birlik","Ağır ateş desteği","Hava gücü","Bölge tahkimatı"};
        int[] cost={2800,700,1200,1000,1800,900};

        for(int i=0;i<6;i++){
            int row=i/2,col=i%2;
            float l=W*(.055f+col*.46f);
            float t=y+row*H*.175f;
            float r=l+W*.405f;
            float b=t+H*.145f;
            baseRects[i].set(l,t,r,b);
            p.setShader(new LinearGradient(l,t,r,b,
                    Color.rgb(12,43,62),Color.rgb(7,27,41),Shader.TileMode.CLAMP));
            roundRect(c,l,t,r,b,dp(11),p);
            p.setShader(null);
            stroke.setStrokeWidth(dp(1));
            stroke.setColor(Color.rgb(43,94,123));
            c.drawRoundRect(baseRects[i],dp(11),dp(11),stroke);

            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.022f);
            p.setColor(Color.WHITE);
            c.drawText(n[i],l+W*.025f,t+H*.034f,p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(W*.015f);
            p.setColor(Color.rgb(146,181,201));
            c.drawText(d[i],l+W*.025f,t+H*.060f,p);

            p.setTextSize(W*.032f);
            p.setColor(Color.rgb(87,190,244));
            c.drawText(new String[]{"♜","♟","▰","➤","✈","⬟"}[i],l+W*.025f,t+H*.102f,p);

            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.016f);
            p.setColor(gold>=cost[i]?Color.rgb(255,192,62):Color.rgb(233,93,81));
            c.drawText("◆ "+format(cost[i]),r-W*.025f,t+H*.104f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        drawWideHint(c,H*.710f,"Seçili bölge: "+regions[selectedSource].name+
                "   •   Savunma "+format(regions[selectedSource].power));
    }

    private void drawArmy(Canvas c){
        drawSectionHeader(c,"BİRLİKLER","Ordunu yönet ve savaş gücünü kontrol et");
        float y=H*.165f;
        String[] role={"Piyade • Bölge kontrolü","Zırhlı • Hücum","Topçu • Destek","Hava • Hızlı taarruz"};
        for(int i=0;i<4;i++){
            float t=y+i*H*.135f;
            p.setColor(Color.rgb(9,35,52));
            roundRect(c,W*.055f,t,W*.945f,t+H*.108f,dp(10),p);
            p.setTextSize(W*.036f);
            p.setColor(Color.rgb(91,199,255));
            c.drawText(new String[]{"♟","▰","➤","✈"}[i],W*.085f,t+H*.063f,p);

            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.024f);
            p.setColor(Color.WHITE);
            c.drawText(unitNames[i],W*.175f,t+H*.038f,p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(W*.015f);
            p.setColor(Color.rgb(145,178,197));
            c.drawText(role[i],W*.175f,t+H*.067f,p);

            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.030f);
            p.setColor(Color.WHITE);
            c.drawText(format(unitCount[i]),W*.900f,t+H*.047f,p);
            p.setTextSize(W*.014f);
            p.setColor(Color.rgb(98,186,236));
            c.drawText("Güç "+format(unitCount[i]*unitPower[i]),W*.900f,t+H*.076f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }

        p.setColor(Color.rgb(10,39,57));
        roundRect(c,W*.055f,H*.735f,W*.945f,H*.835f,dp(10),p);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.024f);
        p.setColor(Color.WHITE);
        c.drawText("Toplam Ordu Gücü",W*.085f,H*.775f,p);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setTextSize(W*.034f);
        p.setColor(Color.rgb(72,212,150));
        c.drawText(format(totalArmyPower()),W*.900f,H*.782f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawTech(Canvas c){
        drawSectionHeader(c,"TEKNOLOJİ","Kalıcı askerî avantajlar geliştir");
        String[] names={"Piyade Gücü","Zırhlı Birlikler","Hava Gücü","Savunma Sistemleri"};
        String[] desc={"+%5 saldırı","Tank gücü +%6","Hava gücü +%8","Kayıplar ve savunma"};
        float y=H*.170f;
        for(int i=0;i<4;i++){
            float t=y+i*H*.145f;
            techRects[i].set(W*.055f,t,W*.945f,t+H*.116f);
            p.setColor(Color.rgb(9,35,52));
            roundRect(c,W*.055f,t,W*.945f,t+H*.116f,dp(10),p);

            p.setTextSize(W*.030f);
            p.setColor(Color.rgb(87,191,246));
            c.drawText(new String[]{"♟","▰","✈","⬟"}[i],W*.085f,t+H*.064f,p);

            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.023f);
            p.setColor(Color.WHITE);
            c.drawText(names[i],W*.165f,t+H*.038f,p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(W*.015f);
            p.setColor(Color.rgb(145,180,199));
            c.drawText(desc[i]+" • Lv."+tech[i]+"/10",W*.165f,t+H*.068f,p);

            int cost=techCost(i);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.016f);
            p.setColor(gold>=cost?Color.rgb(73,201,255):Color.rgb(232,95,82));
            c.drawText("Yükselt  ◆ "+format(cost),W*.900f,t+H*.060f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawAlliance(Canvas c){
        drawSectionHeader(c,"İTTİFAK","TÜRK BİRLİĞİ • Bölgesel komuta ağı");
        p.setColor(Color.rgb(9,35,52));
        roundRect(c,W*.055f,H*.180f,W*.945f,H*.325f,dp(11),p);
        p.setColor(Color.rgb(171,38,37));
        c.drawCircle(W*.155f,H*.252f,W*.055f,p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTextSize(W*.045f);
        p.setColor(Color.WHITE);
        c.drawText("★",W*.155f,H*.270f,p);
        p.setTextAlign(Paint.Align.LEFT);

        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.028f);
        p.setColor(Color.WHITE);
        c.drawText("TÜRK BİRLİĞİ",W*.245f,H*.225f,p);
        p.setTextSize(W*.016f);
        p.setColor(Color.rgb(154,188,207));
        c.drawText("Üye 48/50  •  Güç 12.4M",W*.245f,H*.258f,p);
        c.drawText("Lider: TR•Alparslan",W*.245f,H*.286f,p);

        String[] rows={"İttifak Sohbeti","Üye Listesi","İttifak Görevleri","İttifak Savaşı"};
        for(int i=0;i<4;i++){
            float t=H*(.365f+i*.095f);
            p.setColor(Color.rgb(8,30,45));
            roundRect(c,W*.055f,t,W*.945f,t+H*.072f,dp(8),p);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.020f);
            p.setColor(Color.rgb(216,230,238));
            c.drawText(rows[i],W*.090f,t+H*.044f,p);
            p.setTextAlign(Paint.Align.RIGHT);
            p.setColor(Color.rgb(85,187,241));
            c.drawText("›",W*.900f,t+H*.044f,p);
            p.setTextAlign(Paint.Align.LEFT);
        }
    }

    private void drawMore(Canvas c){
        drawSectionHeader(c,"RAPORLAR & SİSTEM","Son hareketler ve komuta kayıtları");
        String[] title={"Zafer","Keşif Raporu","Üs Geliştirme","Kaynak","Sistem"};
        String[] body={
                "İstanbul saldırı planı hazır.",
                "Ankara savunması analiz edildi.",
                "Tank fabrikası üretime hazır.",
                "+"+(controlledCount()*30)+" Altın / dakika.",
                "WARSTATE v0.3.0 • Build aktif."
        };
        int[] col={Color.rgb(66,211,132),Color.rgb(82,177,239),Color.rgb(87,187,243),Color.rgb(239,185,64),Color.rgb(159,181,194)};
        float y=H*.175f;
        for(int i=0;i<5;i++){
            float t=y+i*H*.105f;
            p.setColor(Color.rgb(8,31,46));
            roundRect(c,W*.055f,t,W*.945f,t+H*.080f,dp(8),p);
            p.setColor(col[i]);
            c.drawCircle(W*.085f,t+H*.040f,dp(5),p);
            p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
            p.setTextSize(W*.019f);
            p.setColor(col[i]);
            c.drawText(title[i],W*.115f,t+H*.031f,p);
            p.setTypeface(Typeface.DEFAULT);
            p.setTextSize(W*.0145f);
            p.setColor(Color.rgb(180,202,215));
            c.drawText(body[i],W*.115f,t+H*.056f,p);
        }
        drawWideHint(c,H*.740f,"Kayıt otomatik • Çevrimdışı gelir 8 saate kadar hesaplanır");
    }

    private void drawSectionHeader(Canvas c,String title,String sub){
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.034f);
        p.setColor(Color.WHITE);
        c.drawText(title,W*.055f,H*.145f,p);
        p.setTypeface(Typeface.DEFAULT);
        p.setTextSize(W*.016f);
        p.setColor(Color.rgb(128,176,201));
        c.drawText(sub,W*.055f,H*.169f,p);
    }

    private void drawWideHint(Canvas c,float y,String s){
        p.setColor(Color.rgb(9,36,52));
        roundRect(c,W*.055f,y,W*.945f,y+H*.070f,dp(9),p);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.017f);
        p.setColor(Color.rgb(190,216,231));
        c.drawText(s,W*.5f,y+H*.043f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBattleOverlay(Canvas c){
        long now=SystemClock.uptimeMillis();
        float elapsed=(now-battleStarted)/1800f;
        float phase=Math.max(0,Math.min(1,elapsed));

        p.setColor(Color.argb(238,2,10,16));
        c.drawRect(0,topH,W,navTop,p);

        // savaş arka planı
        p.setShader(new LinearGradient(0,topH,W,navTop,
                Color.rgb(28,35,34),Color.rgb(12,19,24),Shader.TileMode.CLAMP));
        c.drawRect(W*.03f,topH+H*.02f,W*.97f,navTop-H*.02f,p);
        p.setShader(null);

        // ufuk ve duman
        p.setColor(Color.argb(95,90,78,65));
        for(int i=0;i<7;i++){
            float cx=W*(.12f+i*.13f);
            float cy=H*(.38f+(i%2)*.035f);
            c.drawCircle(cx,cy,W*(.06f+.02f*(i%3)),p);
        }

        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT,Typeface.BOLD));
        p.setTextSize(W*.025f);
        p.setColor(Color.rgb(201,221,232));
        c.drawText(regions[selectedSource].name+"  →  "+regions[selectedTarget].name,W*.5f,topH+H*.055f,p);

        p.setTextSize(W*.014f);
        p.setColor(Color.rgb(146,181,199));
        c.drawText("SAVAŞ DEVAM EDİYOR",W*.5f,topH+H*.083f,p);

        // tanklar hareket ediyor
        float leftX=W*(.14f+.24f*phase);
        float rightX=W*(.86f-.24f*phase);
        drawBigTank(c,leftX,H*.49f,true);
        drawBigTank(c,rightX,H*.49f,false);

        // uçaklar
        p.setTextSize(W*.060f);
        p.setColor(Color.rgb(184,205,215));
        c.save();
        c.rotate(-12,W*(.20f+.55f*phase),H*.28f);
        c.drawText("✈",W*(.20f+.55f*phase),H*.28f,p);
        c.restore();

        // patlamalar
        if(phase>.33f){
            for(int i=0;i<4;i++){
                float f=(phase-.33f)*1.5f;
                float x=W*(.43f+i*.045f);
                float y=H*(.47f+(i%2)*.03f);
                float rr=W*(.015f+.035f*f);
                p.setShader(new RadialGradient(x,y,rr,
                        Color.rgb(255,236,124),Color.argb(0,255,70,20),Shader.TileMode.CLAMP));
                c.drawCircle(x,y,rr,p);
                p.setShader(null);
            }
        }

        // alt ilerleme
        p.setColor(Color.rgb(19,43,57));
        roundRect(c,W*.18f,H*.760f,W*.82f,H*.778f,dp(8),p);
        p.setColor(Color.rgb(230,69,55));
        roundRect(c,W*.18f,H*.760f,W*(.18f+.64f*phase),H*.778f,dp(8),p);

        p.setTextSize(W*.019f);
        p.setColor(Color.WHITE);
        c.drawText("Tahmini başarı  %"+pendingChance,W*.5f,H*.820f,p);
        p.setTextAlign(Paint.Align.LEFT);
    }

    private void drawBigTank(Canvas c,float x,float y,boolean friendly){
        int col=friendly?Color.rgb(47,139,193):Color.rgb(166,61,54);
        p.setColor(col);
        roundRect(c,x-W*.065f,y-H*.018f,x+W*.065f,y+H*.018f,dp(6),p);
        p.setColor(Color.rgb(53,61,56));
        c.drawOval(new RectF(x-W*.030f,y-H*.040f,x+W*.025f,y+H*.005f),p);
        stroke.setStrokeWidth(dp(4));
        stroke.setColor(Color.rgb(64,70,65));
        float dir=friendly?1:-1;
        c.drawLine(x,y-H*.028f,x+dir*W*.075f,y-H*.050f,stroke);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if(e.getAction()!=MotionEvent.ACTION_UP) return true;
        if(battleActive) return true;

        float x=e.getX(), y=e.getY();

        // alt navigasyon
        for(int i=0;i<6;i++){
            if(navRects[i].contains(x,y)){
                screen = i==0?BASE:i==1?WORLD:i==2?ARMY:i==3?TECH:i==4?ALLIANCE:MORE;
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
                return true;
            }
        }

        // üst kısayollar
        for(int i=0;i<3;i++){
            if(topRects[i].contains(x,y)){
                showToast(i==0?"Etkinlik: Anadolu'nun Gücü aktif":i==1?"Görev: 5 bölge ele geçir":"Yeni mesaj yok");
                if(screen!=WORLD) screen=WORLD;
                invalidate();
                return true;
            }
        }

        if(screen==WORLD) handleWorldTouch(x,y);
        else if(screen==BASE) handleBaseTouch(x,y);
        else if(screen==TECH) handleTechTouch(x,y);
        else if(screen==ALLIANCE) handleAllianceTouch(x,y);
        return true;
    }

    private void handleWorldTouch(float x,float y){
        // yan menü
        if(sideRects[0].contains(x,y)){ showToast("Harita görünümü aktif"); invalidate(); return; }
        if(sideRects[1].contains(x,y)){ showToast("Keşif: Ankara savunması "+format(regions[3].power)); invalidate(); return; }
        if(sideRects[2].contains(x,y)){ screen=ALLIANCE; invalidate(); return; }
        if(sideRects[3].contains(x,y)){ screen=MORE; invalidate(); return; }

        for(int i=0;i<regions.length;i++){
            if(regions[i].hit.contains(x,y)){
                if(regions[i].owner==PLAYER){
                    selectedSource=i;
                    showToast(regions[i].name+" kaynak bölge seçildi");
                }else{
                    selectedTarget=i;
                    showToast(regions[i].name+" hedef seçildi");
                }
                save();
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                invalidate();
                return;
            }
        }

        for(int i=0;i<4;i++){
            if(plusRects[i].contains(x,y)){
                selected[i]=Math.min(unitCount[i],selected[i]+step(i));
                invalidate();
                return;
            }
            if(minusRects[i].contains(x,y)){
                selected[i]=Math.max(0,selected[i]-step(i));
                invalidate();
                return;
            }
        }

        if(autoRect.contains(x,y)){
            autoSelect();
            showToast("Hedefe göre otomatik ordu seçildi");
            invalidate();
            return;
        }
        if(defendRect.contains(x,y)){
            fortify();
            invalidate();
            return;
        }
        if(upgradeRect.contains(x,y)){
            screen=TECH;
            invalidate();
            return;
        }
        if(attackRect.contains(x,y)){
            startBattle();
            invalidate();
        }
    }

    private void handleBaseTouch(float x,float y){
        int[] cost={2800,700,1200,1000,1800,900};
        for(int i=0;i<6;i++){
            if(!baseRects[i].contains(x,y)) continue;
            if(gold<cost[i]){
                showToast("Altın yetersiz");
                screen=WORLD;
                invalidate();
                return;
            }
            gold-=cost[i];
            if(i==0){
                commanderLevel++;
                commandXp=Math.min(100,commandXp+10);
            }else if(i==1) unitCount[0]+=500;
            else if(i==2) unitCount[1]+=10;
            else if(i==3) unitCount[2]+=5;
            else if(i==4) unitCount[3]+=2;
            else regions[selectedSource].power+=1600+tech[3]*150;
            save();
            showToast("Üs geliştirmesi tamamlandı");
            screen=WORLD;
            invalidate();
            return;
        }
    }

    private void handleTechTouch(float x,float y){
        for(int i=0;i<4;i++){
            if(!techRects[i].contains(x,y)) continue;
            if(tech[i]>=10) return;
            int cost=techCost(i);
            if(gold<cost){
                showToast("Teknoloji için altın yetersiz");
                screen=WORLD;
                invalidate();
                return;
            }
            gold-=cost;
            tech[i]++;
            save();
            invalidate();
            return;
        }
    }

    private void handleAllianceTouch(float x,float y){
        if(y>H*.35f && y<H*.80f){
            showToast("İttifak modülü prototipte aktif • çevrimdışı test");
            screen=WORLD;
            invalidate();
        }
    }

    private void autoSelect(){
        if(selectedTarget<0 || selectedTarget>=regions.length) return;
        int goal=(int)(regions[selectedTarget].power*1.23f);
        int current=0;
        int[] order={3,1,2,0};
        for(int i=0;i<4;i++) selected[i]=0;
        for(int idx:order){
            int max=(int)(unitCount[idx]*.78f);
            int power=effectiveUnitPower(idx);
            int need=(int)Math.ceil((goal-current)/(double)Math.max(1,power));
            int use=Math.max(0,Math.min(max,need));
            selected[idx]=use;
            current+=use*power;
            if(current>=goal) break;
        }
        clampSelection();
    }

    private void fortify(){
        if(selectedSource<0 || regions[selectedSource].owner!=PLAYER){
            showToast("Önce müttefik bölge seç");
            return;
        }
        int cost=850;
        if(gold<cost){
            showToast("Savunma için 850 altın gerekli");
            return;
        }
        gold-=cost;
        regions[selectedSource].power+=1400+tech[3]*180;
        save();
        showToast(regions[selectedSource].name+" savunması güçlendirildi");
    }

    private void startBattle(){
        if(!validAttackSelection()){
            showToast("Kaynak ve düşman hedef seç");
            return;
        }
        if(selectedPower()<900){
            showToast("Daha fazla birlik seç");
            return;
        }
        if(energy<attackCost()){
            showToast("Enerji yetersiz • Üs ikmali gerekli");
            return;
        }
        for(int i=0;i<4;i++){
            if(selected[i]>unitCount[i]){
                showToast("Birlik sayısı yetersiz");
                return;
            }
        }

        pendingChance=battleChance(selectedPower(),regions[selectedTarget].power);
        battleActive=true;
        battleStarted=SystemClock.uptimeMillis();
        energy-=attackCost();
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);

        postDelayed(() -> {
            resolveBattle();
            battleActive=false;
            invalidate();
        },1800);
    }

    private void resolveBattle(){
        Region target=regions[selectedTarget];
        int chance=pendingChance;
        boolean win=rng.nextInt(100)<chance;

        if(win){
            int loss=Math.max(10,28-chance/7-tech[3]);
            applyLosses(loss);
            target.owner=PLAYER;
            target.power=Math.max(2800,(int)(selectedPower()*.43f));
            gold+=1800+target.level*100;
            commandXp=Math.min(100,commandXp+8);
            showToast("ZAFER • "+target.name+" ele geçirildi • Kayıp %"+loss);
            selectedSource=selectedTarget;
            selectedTarget=findEnemy();
        }else{
            int loss=Math.max(20,40-chance/9-tech[3]);
            applyLosses(loss);
            target.power=Math.max(1200,(int)(target.power*(.82f+rng.nextDouble()*.07f)));
            showToast("SALDIRI DURDURULDU • Düşman zayıfladı • Kayıp %"+loss);
        }

        clampSelection();
        save();
        performHapticFeedback(HapticFeedbackConstants.CONFIRM);
    }

    private void applyLosses(int pct){
        for(int i=0;i<4;i++){
            int lost=(int)Math.ceil(selected[i]*pct/100.0);
            unitCount[i]=Math.max(0,unitCount[i]-lost);
        }
    }

    private int findEnemy(){
        for(int i=0;i<regions.length;i++) if(regions[i].owner==ENEMY) return i;
        showToast("ANADOLU HÂKİMİYETİ TAMAMLANDI!");
        return -1;
    }

    private int step(int i){
        return i==0?100:i==3?1:5;
    }

    private int effectiveUnitPower(int i){
        float bonus=1f;
        if(i==0) bonus+=tech[0]*.05f;
        if(i==1 || i==2) bonus+=tech[1]*.06f;
        if(i==3) bonus+=tech[2]*.08f;
        return Math.max(1,(int)(unitPower[i]*bonus));
    }

    private int selectedPower(){
        int v=0;
        for(int i=0;i<4;i++) v+=selected[i]*effectiveUnitPower(i);
        return v;
    }

    private int totalArmyPower(){
        int v=0;
        for(int i=0;i<4;i++) v+=unitCount[i]*effectiveUnitPower(i);
        return v;
    }

    private int totalPersonnel(){
        return unitCount[0]+unitCount[1]*4+unitCount[2]*5+unitCount[3]*8;
    }

    private int battleChance(int atk,int def){
        if(atk<=0 || def<=0) return 0;
        double ratio=atk/(double)def;
        return (int)Math.max(8,Math.min(94,50+(ratio-1.0)*47.0));
    }

    private int attackCost(){
        return Math.max(5,10-tech[2]/2);
    }

    private int maxEnergy(){
        return 120+tech[2]*5;
    }

    private int techCost(int i){
        return 1800+tech[i]*1100;
    }

    private boolean validAttackSelection(){
        return selectedSource>=0 && selectedSource<regions.length
                && selectedTarget>=0 && selectedTarget<regions.length
                && regions[selectedSource].owner==PLAYER
                && regions[selectedTarget].owner==ENEMY;
    }

    private int controlledCount(){
        int n=0;
        for(Region r:regions) if(r.owner==PLAYER) n++;
        return n;
    }

    private void clampSelection(){
        for(int i=0;i<4;i++) selected[i]=Math.max(0,Math.min(selected[i],unitCount[i]));
    }

    private void showToast(String s){
        toast=s;
        toastUntil=System.currentTimeMillis()+3800;
    }

    private String format(int n){
        return String.format(Locale.US,"%,d",Math.max(0,n)).replace(',','.');
    }

    private String shortPower(int n){
        if(n>=1000000) return String.format(Locale.US,"%.1fM",n/1000000f);
        if(n>=1000) return String.format(Locale.US,"%.1fK",n/1000f);
        return String.valueOf(n);
    }

    private void roundRect(Canvas c,float l,float t,float r,float b,float rad,Paint paint){
        c.drawRoundRect(new RectF(l,t,r,b),rad,rad,paint);
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
