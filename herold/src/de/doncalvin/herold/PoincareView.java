package de.doncalvin.herold;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.PathInterpolator;

import java.util.List;

/**
 * Traegt jeden Schlagabstand gegen den darauffolgenden auf.
 *
 * Das ist die unmittelbarste Darstellung von Regelmaessigkeit, die es fuer
 * Herzschlaege gibt - man braucht keine Kennzahl zu lesen:
 *
 *   dichter Punkt auf der Diagonalen -> gleichmaessiger Schlag
 *   laengliche Zigarre entlang der Diagonalen -> Atemarrhythmie: der Puls
 *       wandert langsam, aufeinanderfolgende Abstaende bleiben aber aehnlich
 *   runde, breite Wolke -> jeder Abstand unabhaengig vom vorigen
 *   Punkte abseits in Faechern -> einzelne Extraschlaege
 *
 * Die Punkte erscheinen in der Reihenfolge, in der sie gemessen wurden, damit
 * sichtbar wird, dass hier eine Aufzeichnung entsteht und kein fertiges Bild.
 */
public class PoincareView extends View {

    private final Paint punkt = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint diagonale = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rahmen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint schein = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float[] x, y;          // in Millisekunden
    private float von, bis;        // Achsenbereich in Millisekunden
    private float anteilSichtbar = 1f;
    private int farbe = Stil.BEFUND_GLEICHMAESSIG;

    public PoincareView(Context c) { this(c, null); }

    public PoincareView(Context c, AttributeSet a) {
        super(c, a);
        punkt.setStyle(Paint.Style.FILL);
        diagonale.setStyle(Paint.Style.STROKE);
        diagonale.setStrokeWidth(Karte.dp(c, 1));
        diagonale.setColor(0xFF2A313C);
        rahmen.setStyle(Paint.Style.STROKE);
        rahmen.setStrokeWidth(Karte.dp(c, 1));
        rahmen.setColor(0xFF1E242D);
    }

    /** Farbe der Punkte - passend zum Urteil, das daneben steht. */
    public void setzeFarbe(int f) { farbe = f; invalidate(); }

    /**
     * @param abstaende Schlagabstaende in Millisekunden, in Messreihenfolge
     */
    public void zeige(List<Integer> abstaende, boolean animiert) {
        if (abstaende == null || abstaende.size() < 3) {
            x = null; y = null; invalidate(); return;
        }
        int n = abstaende.size() - 1;
        x = new float[n];
        y = new float[n];
        float kleinstes = Float.MAX_VALUE, groesstes = Float.MIN_VALUE;
        for (int i = 0; i < n; i++) {
            x[i] = abstaende.get(i);
            y[i] = abstaende.get(i + 1);
            kleinstes = Math.min(kleinstes, Math.min(x[i], y[i]));
            groesstes = Math.max(groesstes, Math.max(x[i], y[i]));
        }
        // Etwas Luft, und nie enger als 200 ms - sonst blaeht ein sehr
        // gleichmaessiger Schlag sein Rauschen zu einer Wolke auf und sieht
        // schlimmer aus, als er ist. Das waere eine Luege durch Maßstab.
        float mitte = (kleinstes + groesstes) / 2f;
        float spanne = Math.max(200f, (groesstes - kleinstes) * 1.35f);
        von = mitte - spanne / 2f;
        bis = mitte + spanne / 2f;

        if (animiert) {
            anteilSichtbar = 0f;
            ValueAnimator a = ValueAnimator.ofFloat(0f, 1f);
            a.setDuration(Math.min(1400, 260 + n * 18));
            a.setInterpolator(new PathInterpolator(0.22f, 1f, 0.36f, 1f));
            a.addUpdateListener(v -> {
                anteilSichtbar = (float) v.getAnimatedValue();
                invalidate();
            });
            a.start();
        } else {
            anteilSichtbar = 1f;
            invalidate();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int b = getWidth(), h = getHeight();
        int seite = Math.min(b, h);
        float links = (b - seite) / 2f, oben = (h - seite) / 2f;
        float rand = Karte.dp(getContext(), 3);
        float feld = seite - 2 * rand;

        c.drawRoundRect(links + rand, oben + rand, links + rand + feld,
                oben + rand + feld, Karte.dp(getContext(), 10),
                Karte.dp(getContext(), 10), rahmen);

        // Diagonale: hier laegen alle Punkte bei vollkommen gleichem Abstand
        c.drawLine(links + rand, oben + rand + feld,
                   links + rand + feld, oben + rand, diagonale);

        if (x == null) return;

        float r = Karte.dp(getContext(), 2.2f);
        int bis_i = Math.max(1, Math.round(x.length * anteilSichtbar));

        for (int i = 0; i < bis_i; i++) {
            float px = links + rand + (x[i] - von) / (bis - von) * feld;
            float py = oben + rand + feld - (y[i] - von) / (bis - von) * feld;
            if (px < links || px > links + seite || py < oben || py > oben + seite) continue;

            // Die zuletzt gezeichneten Punkte leuchten kurz nach, damit man
            // dem Entstehen folgen kann.
            int abstandZumEnde = bis_i - i;
            if (abstandZumEnde < 6 && anteilSichtbar < 1f) {
                float staerke = 1f - abstandZumEnde / 6f;
                schein.setShader(new RadialGradient(px, py, r * 4f,
                        (farbe & 0x00FFFFFF) | ((int) (90 * staerke) << 24),
                        farbe & 0x00FFFFFF, Shader.TileMode.CLAMP));
                c.drawCircle(px, py, r * 4f, schein);
            }
            punkt.setColor(farbe);
            punkt.setAlpha(200);
            c.drawCircle(px, py, r, punkt);
        }
    }
}
