package de.doncalvin.herold;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Uhr - drei Punkte - iPhone. Die Punkte laufen als Welle, solange eine
 * Verbindung steht; ohne Verbindung liegen sie still und leise.
 *
 * Ein Taktgeber, Phasen aus der Uhrzeit, in onDraw wird nichts angelegt.
 */
public class KopplungView extends View {

    private final Paint strich = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint punkt  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint schein = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path pfad = new Path();
    private final RectF r = new RectF();
    private final float dp;
    private ValueAnimator takt;
    private long start;
    private boolean verbunden;
    private Shader scheinVerlauf;
    private int scheinBreite = -1;

    public KopplungView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        strich.setStyle(Paint.Style.STROKE);
        strich.setStrokeWidth(2f * dp);
        strich.setStrokeCap(Paint.Cap.ROUND);
        strich.setStrokeJoin(Paint.Join.ROUND);
        strich.setColor(Stil.TEXT_MITTEL);
        punkt.setStyle(Paint.Style.FILL);
    }

    public void setzeVerbunden(boolean v) {
        if (verbunden == v) return;
        verbunden = v;
        if (v) weiter(); else anhalten();
        invalidate();
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start = System.currentTimeMillis();
        if (verbunden) weiter();
    }

    @Override protected void onDetachedFromWindow() { anhalten(); super.onDetachedFromWindow(); }

    public void weiter() {
        if (takt != null || !isAttachedToWindow()) return;
        takt = ValueAnimator.ofFloat(0f, 1f);
        takt.setDuration(1000); takt.setRepeatCount(ValueAnimator.INFINITE);
        takt.setInterpolator(null);
        takt.addUpdateListener(a -> invalidate());
        takt.start();
    }

    public void anhalten() { if (takt != null) { takt.cancel(); takt = null; } }

    @Override
    protected void onDraw(Canvas c) {
        float b = getWidth(), h = getHeight(), my = h / 2f, mx = b / 2f;
        long jetzt = System.currentTimeMillis();

        // weicher Schein hinter der Mitte
        if (scheinVerlauf == null || scheinBreite != (int) b) {
            scheinBreite = (int) b;
            int f = verbunden ? Stil.PULS : Stil.IPHONE;
            // Radius nie groesser als die halbe Hoehe - sonst schneidet der
            // Ansichtsrand den Schein zu einem sichtbaren Rechteck ab.
            float rs = Math.min(h / 2f, b * 0.42f);
            scheinVerlauf = new RadialGradient(mx, my, rs,
                    MessKarte.mitAlpha(f, 34), MessKarte.mitAlpha(f, 0), Shader.TileMode.CLAMP);
        }
        schein.setShader(scheinVerlauf);
        c.drawCircle(mx, my, Math.min(h / 2f, b * 0.42f), schein);

        // Uhr-Glyphe links: Kreis mit zwei kurzen Baendern
        float ux = mx - 64 * dp, ru = 11 * dp;
        c.drawCircle(ux, my, ru, strich);
        c.drawLine(ux, my - ru - 7 * dp, ux, my - ru - 1.5f * dp, strich);
        c.drawLine(ux, my + ru + 1.5f * dp, ux, my + ru + 7 * dp, strich);

        // iPhone-Glyphe rechts: abgerundetes Rechteck mit Lautsprecherstrich
        float px = mx + 64 * dp;
        r.set(px - 8 * dp, my - 15 * dp, px + 8 * dp, my + 15 * dp);
        c.drawRoundRect(r, 3.5f * dp, 3.5f * dp, strich);
        c.drawLine(px - 3 * dp, my + 11 * dp, px + 3 * dp, my + 11 * dp, strich);

        // drei Punkte als Welle (1,4 s, Versatz 0,2 s), Deckkraft 0,2..1, Skala 0,8..1,1
        int farbe = verbunden ? Stil.PULS : Stil.TEXT_LEISE;
        for (int i = 0; i < 3; i++) {
            float x = mx + (i - 1) * 14 * dp;
            float a = 0.2f, s = 0.8f;
            if (verbunden) {
                float p = (((jetzt - start) - i * 200L) % 1400L + 1400L) % 1400L / 1400f;
                float w = (float) (0.5 - 0.5 * Math.cos(p * 2 * Math.PI));   // 0..1..0
                a = 0.2f + 0.8f * w; s = 0.8f + 0.3f * w;
            }
            punkt.setColor(MessKarte.mitAlpha(farbe, (int) (255 * a)));
            c.drawCircle(x, my, 3.2f * dp * s, punkt);
        }
        // Verbindungslinien, sehr leise
        strich.setColor(MessKarte.mitAlpha(Stil.TEXT_LEISE, 110));
        c.drawLine(ux + ru + 6 * dp, my, mx - 22 * dp, my, strich);
        c.drawLine(mx + 22 * dp, my, px - 8 * dp - 6 * dp, my, strich);
        strich.setColor(Stil.TEXT_MITTEL);
    }
}
