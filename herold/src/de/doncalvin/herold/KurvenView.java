package de.doncalvin.herold;

import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Zeichnet die EKG-Kurve. Die Grundlinie wird laufend abgezogen, sonst
 *  verschwindet der Herzschlag im Gleichspannungsanteil. */
public class KurvenView extends View {

    private final float[] werte = new float[1500];  // drei Sekunden bei 500 Hz
    private int schreibZeiger = 0, gefuellt = 0;
    private float grundlinie = Float.NaN;           // langsam nachgefuehrter Mittelwert
    /**
     * Tiefpass fuer die Anzeige. Gemessen an echten Daten: Das Rauschen
     * zwischen 60 und 150 Hz hat achtmal mehr Energie als das QRS selbst.
     * Ein gleitender Mittelwert daempft das kaum - deshalb ein richtiges
     * Butterworth zweiter Ordnung bei 35 Hz, zweifach hintereinander.
     * 0,5 bis 40 Hz ist auch das Band, mit dem Klinikmonitore arbeiten.
     */
    private static final class Biquad {
        private final double b0,b1,b2,a1,a2;
        private double x1,x2,y1,y2;
        Biquad(double fc, double fs) {
            double w = 2*Math.PI*fc/fs, c = Math.cos(w), al = Math.sin(w)/Math.sqrt(2);
            double a0 = 1+al;
            b0 = ((1-c)/2)/a0; b1 = (1-c)/a0; b2 = ((1-c)/2)/a0;
            a1 = (-2*c)/a0;    a2 = (1-al)/a0;
        }
        float step(float x) {
            double y = b0*x + b1*x1 + b2*x2 - a1*y1 - a2*y2;
            x2 = x1; x1 = x; y2 = y1; y1 = y;
            return (float) y;
        }
        void reset() { x1 = x2 = y1 = y2 = 0; }
    }
    private final Biquad tp1 = new Biquad(35, 500), tp2 = new Biquad(35, 500);
    private volatile int pulsWert = 0;
    private long letzterPeak = 0;
    private final java.util.ArrayDeque<Long> abstaende = new java.util.ArrayDeque<>();
    private final Paint linie  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint schein = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint raster = new Paint();
    private final Paint rasterFein = new Paint();
    private final Path  pfad   = new Path();
    private final float dichte;

    public KurvenView(Context c) { super(c); dichte = d(c); init(); }
    public KurvenView(Context c, android.util.AttributeSet a) { super(c, a); dichte = d(c); init(); }

    private static float d(Context c) { return c.getResources().getDisplayMetrics().density; }

    private void init() {
        // Gruen ist hier kein Urteil, sondern Instrumentenkonvention - so sieht
        // eine Ableitung auf jedem Monitor aus, und niemand liest sie als
        // Entwarnung.
        linie.setColor(Stil.KURVE);
        linie.setStrokeWidth(2.2f * dichte);
        linie.setStyle(Paint.Style.STROKE);
        linie.setStrokeJoin(Paint.Join.ROUND);
        linie.setStrokeCap(Paint.Cap.ROUND);

        // Der Schein ist dieselbe Kurve, nur breit und durchscheinend darunter.
        // Das kostet einen zweiten Zug ueber denselben Pfad und sieht aus wie
        // Nachleuchten auf einem Bildschirm - deutlich weniger Aufwand als ein
        // echter Weichzeichner, der eine Software-Ebene erzwingen wuerde.
        schein.setColor(MessKarte.mitAlpha(Stil.KURVE, 0x38));
        schein.setStrokeWidth(6.5f * dichte);
        schein.setStyle(Paint.Style.STROKE);
        schein.setStrokeJoin(Paint.Join.ROUND);
        schein.setStrokeCap(Paint.Cap.ROUND);

        raster.setColor(0xFF1B2A22);
        raster.setStrokeWidth(1f);
        rasterFein.setColor(0xFF121C17);
        rasterFein.setStrokeWidth(1f);
    }

    public int puls() { return pulsWert; }

    private float[] standbild = null;

    /** Zeigt nach der Messung einen ruhigen Ausschnitt statt des Live-Streams. */
    public void standbild(float[] daten, int von, int anzahl) {
        float[] b = new float[anzahl];
        System.arraycopy(daten, von, b, 0, anzahl);
        standbild = b;
        postInvalidate();
    }

    public void liveWeiter() { standbild = null; }

    /**
     * Nimmt einen Messwert auf. Vorher wird die Grundlinie abgezogen:
     * ein EKG driftet durch Bewegung um ein Vielfaches der Nutzsignalhoehe,
     * das wuerde den Herzschlag voellig ueberdecken.
     */
    public void hinzu(float mv) {
        if (Float.isNaN(grundlinie)) grundlinie = mv;
        grundlinie += (mv - grundlinie) * 0.004f;    // Hochpass ~0,3 Hz bei 500 Hz
        float rein = mv - grundlinie;

        erkennePeak(rein);                            // Erkennung auf dem Rohsignal

        // Fuer die Darstellung sauber tiefpassfiltern
        rein = tp2.step(tp1.step(rein));

        synchronized (werte) {
            werte[schreibZeiger] = rein;
            schreibZeiger = (schreibZeiger + 1) % werte.length;
            if (gefuellt < werte.length) gefuellt++;
        }
        postInvalidateOnAnimation();
    }

    public void leeren() {
        synchronized (werte) { schreibZeiger = 0; gefuellt = 0; }
        grundlinie = Float.NaN;
        tp1.reset(); tp2.reset();
        pulsWert = 0; abstaende.clear();
        postInvalidate();
    }

    /**
     * Schlagerkennung nach Pan-Tompkins: ableiten, quadrieren, integrieren.
     * Eine einfache Schwelle auf dem Rohsignal reicht nicht - die T-Welle
     * wuerde mitgezaehlt und der Puls waere doppelt so hoch.
     */
    private final float[] fenster = new float[50];   // 100 ms bei 500 Hz
    private int fZeiger = 0; private float fSumme = 0;
    private float v1, v2, v3, v4;                    // fuer die Ableitung
    private float schwelle = 0f;

    private void erkennePeak(float v) {
        float ab = (-v1 - 2*v2 + 2*v4 + v) / 8f;     // 5-Punkt-Ableitung
        v1 = v2; v2 = v3; v3 = v4; v4 = v;
        float energie = ab * ab;

        fSumme -= fenster[fZeiger];
        fenster[fZeiger] = energie;
        fSumme += energie;
        fZeiger = (fZeiger + 1) % fenster.length;
        float mwi = fSumme / fenster.length;

        // Schwelle folgt dem Signal langsam nach
        schwelle = Math.max(schwelle * 0.999f, mwi * 0.5f);

        long jetzt = System.currentTimeMillis();
        if (mwi > schwelle * 0.8f && jetzt - letzterPeak > 280) {
            if (letzterPeak > 0) {
                long ab2 = jetzt - letzterPeak;
                if (ab2 > 300 && ab2 < 2000) {
                    abstaende.add(ab2);
                    while (abstaende.size() > 10) abstaende.poll();
                    java.util.ArrayList<Long> s2 = new java.util.ArrayList<>(abstaende);
                    java.util.Collections.sort(s2);
                    long median = s2.get(s2.size() / 2);
                    pulsWert = (int) (60000 / median);
                }
            }
            letzterPeak = jetzt;
        }
    }

    @Override protected void onDraw(Canvas c) {
        int b = getWidth(), h = getHeight();

        // Raster wie auf EKG-Papier: feine Teilung, jede fuenfte Linie kraeftiger.
        float fein = 5f * dichte;
        for (float x = 0; x < b; x += fein) {
            c.drawLine(x, 0, x, h, rasterFein);
        }
        for (float y = h / 2f; y < h; y += fein) {
            c.drawLine(0, y, b, y, rasterFein);
            c.drawLine(0, h - y, b, h - y, rasterFein);
        }
        for (float x = 0; x < b; x += fein * 5) c.drawLine(x, 0, x, h, raster);
        for (float y = h / 2f; y < h; y += fein * 5) {
            c.drawLine(0, y, b, y, raster);
            c.drawLine(0, h - y, b, h - y, raster);
        }

        float[] kopie; int n;
        if (standbild != null) { kopie = standbild; n = kopie.length; }
        else synchronized (werte) {
            n = gefuellt;
            if (n < 8) return;
            kopie = new float[n];
            for (int i = 0; i < n; i++)
                kopie[i] = werte[(schreibZeiger - n + i + werte.length) % werte.length];
        }

        // Mehr Messpunkte als Bildpunkte -> pro Spalte den Extremwert nehmen,
        // sonst sieht eine 500-Hz-Kurve auf 400 Pixeln wie Rauschen aus.
        if (n > b * 2) {
            float[] red = new float[b];
            for (int x = 0; x < b; x++) {
                int a1 = (int) ((long) x * n / b), a2 = (int) ((long) (x+1) * n / b);
                float ext = 0;
                for (int i = a1; i < Math.min(a2, n); i++)
                    if (Math.abs(kopie[i]) > Math.abs(ext)) ext = kopie[i];
                red[x] = ext;
            }
            kopie = red; n = b;
        }

        float mittel = 0f;                  // Grundlinie ist bereits abgezogen
        float spanne = 0.05f;
        for (float v : kopie) spanne = Math.max(spanne, Math.abs(v));

        pfad.rewind();
        float letztesX = 0, letztesY = h / 2f;
        for (int i = 0; i < n; i++) {
            float x = b * (float) i / (n - 1);
            float y = h / 2f - (kopie[i] - mittel) / spanne * (h * 0.42f);
            if (i == 0) pfad.moveTo(x, y); else pfad.lineTo(x, y);
            letztesX = x; letztesY = y;
        }
        c.drawPath(pfad, schein);
        c.drawPath(pfad, linie);

        // Im Livebetrieb laeuft ein Punkt an der Spitze mit - dann sieht man,
        // dass die Aufzeichnung laeuft, auch wenn die Kurve gerade flach ist.
        if (standbild == null) {
            linie.setStyle(Paint.Style.FILL);
            c.drawCircle(letztesX, letztesY, 2.6f * dichte, linie);
            linie.setStyle(Paint.Style.STROKE);
        }
    }
}
