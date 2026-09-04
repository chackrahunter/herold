package de.doncalvin.herold;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.View;

/**
 * Eine Karte im Startbildschirm - vollstaendig selbst gezeichnet.
 *
 * Zusammengesetzt aus LinearLayout und TextView sah das aus wie ein Formular.
 * Hier wird stattdessen alles auf ein Canvas gemalt: eine Abzeichenscheibe in
 * der Kennfarbe mit einem eigenen Zeichen, ein sanfter Verlauf ueber die
 * Flaeche, ein Schimmer am oberen Rand und rechts der zuletzt gemessene Wert.
 *
 * Der Verlauf laeuft von der Kennfarbe her: jede Karte traegt ihre Farbe als
 * Hauch, ohne dass die Flaeche bunt wird.
 */
public class MessKarte extends View {

    public static final int Z_HERZ = 0, Z_KURVE = 1, Z_TROPFEN = 2,
                            Z_THERMO = 3, Z_KOERPER = 4, Z_LUNGE = 5,
                            Z_UHR = 6, Z_PERSON = 7, Z_TELEFON = 8;

    private final Paint flaeche = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint scheibe = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint zeichen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rechteck = new RectF();
    private final Path  pfad     = new Path();

    private final float dp;
    private String titel = "", unten = "", wert = "";
    private int farbe = Stil.TEXT_SCHWACH;
    private int symbol = Z_HERZ;
    private boolean gedrueckt;
    private Shader verlauf;
    private int gebautFuer = -1;

    public MessKarte(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        zeichen.setStyle(Paint.Style.STROKE);
        zeichen.setStrokeCap(Paint.Cap.ROUND);
        zeichen.setStrokeJoin(Paint.Join.ROUND);
        zeichen.setStrokeWidth(2f * dp);
        setClickable(true);
        setFocusable(true);
    }

    public MessKarte setze(String titel, String unten, int farbe, int symbol) {
        this.titel = titel; this.unten = unten; this.farbe = farbe; this.symbol = symbol;
        verlauf = null;
        invalidate();
        return this;
    }

    /** Der zuletzt gemessene Wert, rechts auf der Karte. Leer lassen, wenn keiner da ist. */
    public MessKarte setzeWert(String w) { wert = w == null ? "" : w; invalidate(); return this; }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: gedrueckt = true;  invalidate(); break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL: gedrueckt = false; invalidate(); break;
        }
        return super.onTouchEvent(e);
    }

    @Override
    protected void onDraw(Canvas c) {
        float b = getWidth(), h = getHeight();
        float ecke = 26 * dp;

        if (verlauf == null || gebautFuer != (int) h) {
            gebautFuer = (int) h;
            // Ein Hauch der Kennfarbe von links oben, sonst neutrale Flaeche.
            verlauf = new LinearGradient(0, 0, b, h,
                    new int[]{ mitAlpha(farbe, 30), Stil.FLAECHE_01, Stil.FLAECHE_01 },
                    new float[]{ 0f, 0.55f, 1f }, Shader.TileMode.CLAMP);
        }

        rechteck.set(0, 0, b, h);
        flaeche.setShader(verlauf);
        flaeche.setAlpha(gedrueckt ? 255 : 235);
        c.drawRoundRect(rechteck, ecke, ecke, flaeche);
        flaeche.setShader(null);
        flaeche.setAlpha(255);

        // Schimmer entlang der Oberkante - gibt der Flaeche eine Woelbung
        flaeche.setColor(mitAlpha(0xFFFFFFFF, gedrueckt ? 10 : 16));
        rechteck.set(dp, dp, b - dp, h * 0.55f);
        c.drawRoundRect(rechteck, ecke, ecke, flaeche);

        // Abzeichenscheibe mit Schein
        float mx = 30 * dp, my = h / 2f, r = 17 * dp;
        flaeche.setShader(new RadialGradient(mx, my, r * 2.1f,
                mitAlpha(farbe, 46), mitAlpha(farbe, 0), Shader.TileMode.CLAMP));
        c.drawCircle(mx, my, r * 2.1f, flaeche);
        flaeche.setShader(null);

        scheibe.setColor(mitAlpha(farbe, 38));
        c.drawCircle(mx, my, r, scheibe);
        zeichen.setColor(farbe);
        zeichneSymbol(c, mx, my, 9.5f * dp);

        // Texte
        float x = 55 * dp;
        text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        text.setColor(Stil.TEXT_STARK);
        text.setTextSize(14.5f * dp);
        boolean zweizeilig = unten != null && !unten.isEmpty();
        c.drawText(titel, x, zweizeilig ? my - 2 * dp : my + 5 * dp, text);
        if (zweizeilig) {
            text.setTypeface(Typeface.DEFAULT);
            text.setColor(Stil.TEXT_SCHWACH);
            text.setTextSize(10.5f * dp);
            c.drawText(unten, x, my + 14 * dp, text);
        }

        // Zuletzt gemessener Wert, rechtsbuendig
        if (!wert.isEmpty()) {
            text.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            text.setColor(farbe);
            text.setTextSize(13f * dp);
            text.setTextAlign(Paint.Align.RIGHT);
            c.drawText(wert, b - 18 * dp, my + 5 * dp, text);
            text.setTextAlign(Paint.Align.LEFT);
        }
    }

    /**
     * Die Zeichen sind absichtlich mit der Hand gezogen und nicht aus einer
     * Schrift geholt: eine Symbolschrift muesste mitgeliefert werden, und
     * zwei Dutzend Striche kosten weniger als eine Schriftdatei.
     */
    private void zeichneSymbol(Canvas c, float mx, float my, float s) {
        pfad.rewind();
        switch (symbol) {
            case Z_HERZ:
                pfad.moveTo(mx, my + s * 0.78f);
                pfad.cubicTo(mx - s * 1.45f, my - s * 0.15f, mx - s * 0.62f, my - s * 1.15f, mx, my - s * 0.34f);
                pfad.cubicTo(mx + s * 0.62f, my - s * 1.15f, mx + s * 1.45f, my - s * 0.15f, mx, my + s * 0.78f);
                c.drawPath(pfad, zeichen);
                return;
            case Z_KURVE:
                pfad.moveTo(mx - s, my);
                pfad.lineTo(mx - s * 0.45f, my);
                pfad.lineTo(mx - s * 0.2f, my - s * 0.85f);
                pfad.lineTo(mx + s * 0.12f, my + s * 0.85f);
                pfad.lineTo(mx + s * 0.4f, my);
                pfad.lineTo(mx + s, my);
                c.drawPath(pfad, zeichen);
                return;
            case Z_TROPFEN:
                pfad.moveTo(mx, my - s);
                pfad.cubicTo(mx + s * 0.95f, my - s * 0.05f, mx + s * 0.62f, my + s * 0.9f, mx, my + s * 0.9f);
                pfad.cubicTo(mx - s * 0.62f, my + s * 0.9f, mx - s * 0.95f, my - s * 0.05f, mx, my - s);
                c.drawPath(pfad, zeichen);
                return;
            case Z_THERMO:
                pfad.moveTo(mx, my - s * 0.9f);
                pfad.lineTo(mx, my + s * 0.35f);
                c.drawPath(pfad, zeichen);
                c.drawCircle(mx, my + s * 0.62f, s * 0.36f, zeichen);
                return;
            case Z_KOERPER:
                c.drawCircle(mx, my, s * 0.9f, zeichen);
                pfad.moveTo(mx - s * 0.9f, my);
                pfad.cubicTo(mx - s * 0.3f, my - s * 0.55f, mx + s * 0.3f, my + s * 0.55f, mx + s * 0.9f, my);
                c.drawPath(pfad, zeichen);
                return;
            case Z_LUNGE:
                pfad.moveTo(mx, my - s * 0.9f);
                pfad.lineTo(mx, my + s * 0.1f);
                pfad.moveTo(mx, my + s * 0.1f);
                pfad.cubicTo(mx - s * 0.2f, my + s * 0.95f, mx - s, my + s * 0.75f, mx - s * 0.85f, my - s * 0.15f);
                pfad.moveTo(mx, my + s * 0.1f);
                pfad.cubicTo(mx + s * 0.2f, my + s * 0.95f, mx + s, my + s * 0.75f, mx + s * 0.85f, my - s * 0.15f);
                c.drawPath(pfad, zeichen);
                return;
            case Z_UHR:
                c.drawCircle(mx, my, s * 0.88f, zeichen);
                pfad.moveTo(mx, my - s * 0.45f);
                pfad.lineTo(mx, my);
                pfad.lineTo(mx + s * 0.42f, my + s * 0.2f);
                c.drawPath(pfad, zeichen);
                return;
            case Z_PERSON:
                c.drawCircle(mx, my - s * 0.42f, s * 0.4f, zeichen);
                pfad.moveTo(mx - s * 0.75f, my + s * 0.85f);
                pfad.cubicTo(mx - s * 0.7f, my + s * 0.05f, mx + s * 0.7f, my + s * 0.05f, mx + s * 0.75f, my + s * 0.85f);
                c.drawPath(pfad, zeichen);
                return;
            default:
                rechteck.set(mx - s * 0.55f, my - s * 0.95f, mx + s * 0.55f, my + s * 0.95f);
                c.drawRoundRect(rechteck, s * 0.22f, s * 0.22f, zeichen);
                pfad.moveTo(mx - s * 0.2f, my + s * 0.62f);
                pfad.lineTo(mx + s * 0.2f, my + s * 0.62f);
                c.drawPath(pfad, zeichen);
        }
    }

    static int mitAlpha(int farbe, int alpha) {
        return (farbe & 0x00FFFFFF) | (alpha << 24);
    }
}
