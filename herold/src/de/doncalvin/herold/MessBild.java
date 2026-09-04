package de.doncalvin.herold;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;

/**
 * Das lebende Bild waehrend einer Messung - je Messart ein eigenes.
 *
 * Drei Regeln, die das Ganze fluessig halten:
 *
 *  1. EIN Taktgeber. Ein einziger ValueAnimator laeuft endlos und ruft nur
 *     invalidate(). Sein Wert wird nie benutzt. Jede Phase rechnet onDraw aus
 *     der echten Uhrzeit - dann laeuft nichts auseinander, wenn ein Bild
 *     ausfaellt, und es gibt genau eine Stelle zum Anhalten.
 *  2. onDraw legt nichts an. Paint, Path und RectF sind Felder, Pfade werden
 *     mit rewind() geleert (behaelt den Speicher), Farbverlaeufe nur neu
 *     gebaut, wenn sich die Geometrie wirklich geaendert hat.
 *  3. Kein Urteil. Waehrend der Messung wird nichts eingefaerbt und nichts
 *     bewertet - das Bild zeigt, was der Sensor liefert, mehr nicht.
 */
public class MessBild extends View {

    public static final int PULS = 0, SPO2 = 1, TEMP = 2, BIA = 3;

    private int art = SPO2;
    private int kennfarbe = Stil.SAUERSTOFF;

    private final Paint fuellung = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint strich   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint schrift  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint klein    = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path  pfad     = new Path();
    private final Path  gefaess  = new Path();
    private final RectF feld     = new RectF();

    private ValueAnimator takt;
    private long start;

    // --- Fortschritt ----------------------------------------------------
    private float fortschritt;          // 0..1, vom Dienst gemeldet

    // --- Puls -----------------------------------------------------------
    /** Zeitpunkt des naechsten erwarteten Schlags. Der Takt wird aus dem
     *  Median der Abstaende gefuehrt und bei jedem neuen Buendel nachgezogen -
     *  die Werte kommen gebuendelt und verspaetet, ein Bild, das daran haengt,
     *  wuerde ruckeln. */
    private long naechsterSchlag;
    private float rrMs = 1000f;
    private int puls;
    private long letzterWert;           // wann kam zuletzt etwas an
    private boolean schwach;            // Werte kommen, sind aber unbrauchbar
    private final long[] wellen = new long[4];
    private int wellenZeiger;
    private final float[] balken = new float[24];
    private int balkenZahl;

    // --- Sauerstoff -----------------------------------------------------
    private float pegel = 0.12f, pegelZiel = 0.12f;
    private float unruhe;               // 0 = sauberes Signal, 1 = kein Signal

    // --- zwischengespeicherte Farbverlaeufe -----------------------------
    private Shader kernVerlauf, fuellVerlauf;
    private float kernRadiusGebaut = -1, fuellHoeheGebaut = -1;

    private static final Interpolator ANSPRUNG = Stil.anspringen();
    private static final Interpolator NORMAL   = Stil.standard();
    private static final Interpolator ABKLINGEN = new DecelerateInterpolator(1.4f);

    public MessBild(Context c) {
        super(c);
        strich.setStyle(Paint.Style.STROKE);
        strich.setStrokeCap(Paint.Cap.ROUND);
        schrift.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        schrift.setTextAlign(Paint.Align.CENTER);
        klein.setTextAlign(Paint.Align.CENTER);
        klein.setColor(Stil.TEXT_SCHWACH);
    }

    public void setzeArt(int a, int farbe) {
        art = a; kennfarbe = farbe;
        kernVerlauf = null; fuellVerlauf = null;
        kernRadiusGebaut = -1; fuellHoeheGebaut = -1;
        invalidate();
    }

    public void setzeFortschritt(float f) { fortschritt = Math.max(0f, Math.min(1f, f)); }

    /** Neue Schlagabstaende vom Dienst. */
    public void neuePulse(int[] rr, int anzahl) {
        if (anzahl <= 0) return;
        letzterWert = System.currentTimeMillis();
        // Median der letzten Werte als Taktvorgabe
        float[] k = new float[anzahl];
        System.arraycopy(toFloat(rr, anzahl), 0, k, 0, anzahl);
        java.util.Arrays.sort(k);
        float median = k[anzahl / 2];
        if (median > 300 && median < 2000) {
            rrMs = median;
            puls = Math.round(60000f / median);
            if (naechsterSchlag == 0) naechsterSchlag = System.currentTimeMillis();
        }
        for (int i = 0; i < anzahl; i++) {
            if (balkenZahl < balken.length) {
                balken[balkenZahl++] = rr[i];
            } else {
                System.arraycopy(balken, 1, balken, 0, balken.length - 1);
                balken[balken.length - 1] = rr[i];
            }
        }
    }

    private static float[] toFloat(int[] a, int n) {
        float[] f = new float[n];
        for (int i = 0; i < n; i++) f[i] = a[i];
        return f;
    }

    /** Sensor liefert, aber alles unbrauchbar -> der Kern atmet statt zu schlagen. */
    public void signalSchwach(boolean s) { schwach = s; }

    /** Neuer gueltiger Sauerstoffwert. */
    public void neuerSauerstoff(int prozent) {
        letzterWert = System.currentTimeMillis();
        pegelZiel = Math.max(0f, Math.min(1f, (prozent - 88f) / 12f));
    }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start = System.currentTimeMillis();
        weiter();
    }

    @Override protected void onDetachedFromWindow() {
        anhalten();
        super.onDetachedFromWindow();
    }

    /** Taktgeber stoppen - bei ausgeschaltetem Bildschirm zeichnet niemand mit. */
    public void anhalten() {
        if (takt != null) { takt.cancel(); takt = null; }
    }

    /** Taktgeber wieder anwerfen, falls er angehalten war. */
    public void weiter() {
        if (takt != null || !isAttachedToWindow()) return;
        takt = ValueAnimator.ofFloat(0f, 1f);
        takt.setDuration(1000);
        takt.setRepeatCount(ValueAnimator.INFINITE);
        takt.setInterpolator(null);
        takt.addUpdateListener(a -> invalidate());
        takt.start();
    }

    @Override
    protected void onDraw(Canvas c) {
        float b = getWidth(), h = getHeight();
        float mx = b / 2f, my = h / 2f;
        float dp = getResources().getDisplayMetrics().density;
        long jetzt = System.currentTimeMillis();

        // Der Messkranz laeuft ganz aussen, damit die Mitte fuer das
        // eigentliche Bild frei bleibt. Ein Kreis darf bis an den Rand, weil
        // er der Rundung des Displays folgt.
        float kranz = Math.min(mx, my) - 5 * dp;
        strich.setStrokeWidth(3 * dp);
        strich.setShader(null);
        strich.setColor(Stil.FLAECHE_02);
        feld.set(mx - kranz, my - kranz, mx + kranz, my + kranz);
        c.drawArc(feld, 0, 360, false, strich);
        strich.setColor(kennfarbe);
        strich.setAlpha(210);
        c.drawArc(feld, -90, 360 * fortschritt, false, strich);
        strich.setAlpha(255);

        switch (art) {
            case PULS: zeichnePuls(c, mx, my, dp, jetzt); break;
            case SPO2: zeichneSauerstoff(c, mx, my, dp, jetzt); break;
            case TEMP: zeichneWaerme(c, mx, my, dp, jetzt); break;
            default:   zeichneKoerper(c, mx, my, dp, jetzt); break;
        }
    }

    // ------------------------------------------------------------------
    // Puls: ein Kern, der im gemessenen Takt schlaegt
    // ------------------------------------------------------------------
    private void zeichnePuls(Canvas c, float mx, float my, float dp, long jetzt) {
        boolean stille = schwach || (letzterWert > 0 && jetzt - letzterWert > 3000);

        // Metronom weiterstellen und Wellen ausloesen
        if (naechsterSchlag > 0 && jetzt >= naechsterSchlag) {
            wellen[wellenZeiger] = jetzt;
            wellenZeiger = (wellenZeiger + 1) % wellen.length;
            naechsterSchlag += Math.max(300, (long) rrMs);
            if (jetzt - naechsterSchlag > 3000) naechsterSchlag = jetzt;   // aufgeholt
        }

        float skala;
        if (stille || naechsterSchlag == 0) {
            // Kein Signal: ruhiges Atmen statt Herzschlag, kein Blinken, kein Rot
            float p = ((jetzt - start) % 1200) / 1200f;
            skala = 1f + 0.04f * (float) Math.sin(p * 2 * Math.PI);
        } else {
            long seit = jetzt - (naechsterSchlag - (long) rrMs);
            skala = huellkurve(seit, rrMs);
        }

        float kern = 26 * dp * skala;

        // Schein als Farbverlauf statt Weichzeichner - sonst zeichnet die
        // ganze Uhr in Software.
        if (kernVerlauf == null || Math.abs(kern - kernRadiusGebaut) > dp) {
            kernRadiusGebaut = kern;
            kernVerlauf = new RadialGradient(mx, my, kern * 1.9f,
                    new int[]{ (kennfarbe & 0xFFFFFF) | 0x66000000,
                               (kennfarbe & 0xFFFFFF) | 0x22000000,
                                kennfarbe & 0xFFFFFF },
                    new float[]{ 0f, 0.55f, 1f }, Shader.TileMode.CLAMP);
        }
        fuellung.setShader(kernVerlauf);
        fuellung.setAlpha(stille ? 90 : 255);
        c.drawCircle(mx, my, kern * 1.9f, fuellung);
        fuellung.setShader(null);
        fuellung.setColor(kennfarbe);
        fuellung.setAlpha(stille ? 100 : 255);
        c.drawCircle(mx, my, kern, fuellung);
        fuellung.setAlpha(255);

        // zweiter Ring: ein duenner Halo um den Kern, der dem Schlag mit halber
        // Amplitude folgt - gibt dem Kern Tiefe, ohne zu konkurrieren
        strich.setStrokeWidth(1.2f * dp);
        strich.setColor(MessKarte.mitAlpha(kennfarbe, stille ? 40 : 90));
        c.drawCircle(mx, my, 26 * dp * (1f + (skala - 1f) * 0.5f) + 12 * dp, strich);

        // Wellen: je Schlag ein Ring nach aussen
        for (long w : wellen) {
            if (w == 0) continue;
            float t = (jetzt - w) / 620f;
            if (t < 0 || t > 1) continue;
            float e = Stil.ein().getInterpolation(t);
            strich.setColor(kennfarbe);
            strich.setAlpha((int) (150 * (1 - t)));
            strich.setStrokeWidth((3f - 2.4f * t) * dp);
            c.drawCircle(mx, my, (26 + 38 * e) * dp, strich);
        }
        strich.setAlpha(255);

        // Zahl in der Mitte - laufender Wert, kein Ergebnis, also nicht hochzaehlen
        schrift.setColor(Stil.TEXT_STARK);
        schrift.setTextSize(26 * dp);
        c.drawText(puls > 0 ? String.valueOf(puls) : "–", mx, my + 8 * dp, schrift);
        klein.setTextSize(9 * dp);
        klein.setColor(0xB3FFFFFF);                   // weiss, 70 % - auf dem Kern lesbar
        c.drawText("/min", mx, my + 21 * dp, klein);
        klein.setColor(Stil.TEXT_SCHWACH);

        // Taktleiste: die letzten Abstaende als Silhouette, ohne Einfaerbung
        if (balkenZahl > 1) {
            float basis = my + 48 * dp;
            float mitte = balken[balkenZahl / 2];
            float breite = 5 * dp;
            float x0 = mx - (balkenZahl - 1) * breite / 2f;
            strich.setStrokeWidth(2.5f * dp);
            strich.setColor(Stil.TEXT_SCHWACH);
            strich.setAlpha(178);
            for (int i = 0; i < balkenZahl; i++) {
                float hoehe = balken[i] / Math.max(1f, mitte) * 12 * dp;
                hoehe = Math.max(4 * dp, Math.min(24 * dp, hoehe));
                float x = x0 + i * breite;
                c.drawLine(x, basis, x, basis - hoehe, strich);
            }
            strich.setAlpha(255);
        }
    }

    /**
     * Zweischlag statt Sinus. Ein Sinus wirkt wie Atmung, erst der zweite Ton
     * macht daraus einen Herzschlag.
     */
    private static float huellkurve(long seit, float rr) {
        float k = Math.min(1f, rr / 520f);
        float t = seit;
        if (t < 90 * k)  return 1.00f + 0.15f  * ANSPRUNG.getInterpolation(t / (90 * k));
        if (t < 210 * k) return 1.15f - 0.105f * NORMAL.getInterpolation((t - 90 * k) / (120 * k));
        if (t < 290 * k) return 1.045f + 0.04f * NORMAL.getInterpolation((t - 210 * k) / (80 * k));
        if (t < 430 * k) return 1.085f - 0.085f * ABKLINGEN.getInterpolation((t - 290 * k) / (140 * k));
        return 1f;
    }

    // ------------------------------------------------------------------
    // Sauerstoff: ein Gefaess, das sich fuellt
    // ------------------------------------------------------------------
    private void zeichneSauerstoff(Canvas c, float mx, float my, float dp, long jetzt) {
        float r = 46 * dp;
        boolean schwach = letzterWert > 0 && jetzt - letzterWert > 1500;
        unruhe += ((schwach ? 1f : 0f) - unruhe) * 0.08f;   // weiches Nachziehen
        pegel += (pegelZiel - pegel) * 0.06f;

        strich.setStrokeWidth(2 * dp);
        strich.setColor(kennfarbe);
        strich.setAlpha(89);
        c.drawCircle(mx, my, r, strich);
        strich.setAlpha(255);

        gefaess.rewind();
        gefaess.addCircle(mx, my, r - dp, Path.Direction.CW);

        float oberkante = my + r - 2 * pegel * r;
        if (fuellVerlauf == null || Math.abs(oberkante - fuellHoeheGebaut) > dp) {
            fuellHoeheGebaut = oberkante;
            fuellVerlauf = new LinearGradient(mx, oberkante, mx, my + r,
                    (kennfarbe & 0xFFFFFF) | 0xD1000000, 0xFF1E5F9E, Shader.TileMode.CLAMP);
        }

        // Zwei gegenlaeufige Wellen, damit sich das Muster nie wiederholt
        float t = (jetzt - start) / 1000f;
        float amp = (2.5f + 2.5f * unruhe) * dp;
        pfad.rewind();
        pfad.moveTo(mx - r, my + r);
        for (int i = 0; i <= 48; i++) {
            float x = mx - r + (2 * r) * i / 48f;
            float y = oberkante
                    + amp * (float) Math.sin((x - mx) / (62 * dp) * 6.283f + 0.9f * t)
                    + amp * 0.64f * (float) Math.sin((x - mx) / (41 * dp) * 6.283f - 1.35f * t);
            if (i == 0) pfad.lineTo(x, y); else pfad.lineTo(x, y);
        }
        pfad.lineTo(mx + r, my + r);
        pfad.close();

        c.save();
        c.clipPath(gefaess);
        fuellung.setShader(fuellVerlauf);
        // Bei schlechtem Signal entsaettigt die Fuellung - ein "halt still"
        // ohne Text und ohne Vorwurf.
        fuellung.setAlpha((int) (255 - 90 * unruhe));
        c.drawPath(pfad, fuellung);
        fuellung.setShader(null);
        fuellung.setAlpha(255);
        c.restore();

        // Waehrend der Messung steht hier bewusst keine Zahl: ein Zwischenwert
        // waere eine Aussage, die jemand ablesen und behalten koennte. Auch
        // kein Text - der Hinweis kommt von der Anzeige darunter, sonst steht
        // er doppelt da.
    }

    // ------------------------------------------------------------------
    // Hauttemperatur: ein Waermefeld, das aufgeht
    // ------------------------------------------------------------------
    private void zeichneWaerme(Canvas c, float mx, float my, float dp, long jetzt) {
        float r = (18 + 36 * fortschritt) * dp;
        if (kernVerlauf == null || Math.abs(r - kernRadiusGebaut) > dp) {
            kernRadiusGebaut = r;
            int kern  = mische(0xFF5A2A18, 0xFFFF7847, 0xFFFFC08A, fortschritt);
            int mitte = mische2(0xFF2A1008, 0xFFB04A22, fortschritt);
            kernVerlauf = new RadialGradient(mx, my, r,
                    new int[]{ kern, mitte, 0x00000000 },
                    new float[]{ 0f, 0.55f, 1f }, Shader.TileMode.CLAMP);
        }
        fuellung.setShader(kernVerlauf);
        c.drawCircle(mx, my, r, fuellung);
        fuellung.setShader(null);

        // Waermeschlieren, die nach oben ziehen
        strich.setStrokeWidth(1.5f * dp);
        strich.setColor(Stil.TEMPERATUR);
        for (int s = 0; s < 3; s++) {
            float p = (((jetzt - start) + s * 870L) % 2600L) / 2600f;
            float y = my - 4 * dp - 22 * dp * p;
            int alpha = (int) (64 * (p < 0.67f ? 1f : (1f - (p - 0.67f) / 0.33f)));
            if (alpha <= 0) continue;
            strich.setAlpha(alpha);
            pfad.rewind();
            for (int i = 0; i <= 24; i++) {
                float x = mx - 30 * dp + 60 * dp * i / 24f;
                float yy = y + 3 * dp * (float) Math.sin((x - mx) / (44 * dp) * 6.283f + p * 6.283f);
                if (i == 0) pfad.moveTo(x, yy); else pfad.lineTo(x, yy);
            }
            c.drawPath(pfad, strich);
        }
        strich.setAlpha(255);
    }

    // ------------------------------------------------------------------
    // Koerperanalyse: Ringe, die sich von innen nach aussen schliessen
    // ------------------------------------------------------------------
    private void zeichneKoerper(Canvas c, float mx, float my, float dp, long jetzt) {
        strich.setStrokeWidth(4 * dp);
        for (int i = 0; i < 3; i++) {
            float r = (22 + i * 13) * dp;
            float anteil = Math.max(0f, Math.min(1f, fortschritt * 3f - i));
            feld.set(mx - r, my - r, mx + r, my + r);
            strich.setColor(Stil.FLAECHE_02);
            c.drawArc(feld, 0, 360, false, strich);
            if (anteil <= 0) continue;
            strich.setColor(kennfarbe);
            strich.setAlpha(255 - i * 55);
            c.drawArc(feld, -90, 360 * anteil, false, strich);
            strich.setAlpha(255);
        }
        // Der Strom fliesst zwischen den beiden Tasten - ein Funke, der wandert
        float p = ((jetzt - start) % 1500L) / 1500f;
        float w = (float) Math.toRadians(-90 + 360 * p);
        fuellung.setColor(kennfarbe);
        c.drawCircle(mx + (float) Math.cos(w) * 48 * dp,
                     my + (float) Math.sin(w) * 48 * dp, 3 * dp, fuellung);
    }

    // ---- Farbmischung ohne ArgbEvaluator-Objekt ------------------------

    private static int mische2(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int ar = (a >> 16) & 255, ag = (a >> 8) & 255, ab = a & 255;
        int br = (b >> 16) & 255, bg = (b >> 8) & 255, bb = b & 255;
        return 0xFF000000
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                |  (int) (ab + (bb - ab) * t);
    }

    private static int mische(int a, int b, int c, float t) {
        return t < 0.5f ? mische2(a, b, t * 2f) : mische2(b, c, (t - 0.5f) * 2f);
    }
}
