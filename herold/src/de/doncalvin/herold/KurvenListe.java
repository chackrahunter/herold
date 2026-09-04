package de.doncalvin.herold;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/**
 * Eine Liste, die der Rundung des Displays folgt.
 *
 * Auf einem runden Bildschirm ist eine gerade Liste falsch: oben und unten
 * schneidet die Rundung Text ab, und alles wirkt gleich wichtig. Hier wird
 * jede Karte danach behandelt, wie weit sie von der Bildmitte entfernt ist -
 * sie schrumpft, wird blasser und rueckt zur Mitte hin ein. Das lenkt den
 * Blick dorthin, wo das Display am breitesten ist.
 *
 *   Skalierung  = 1 - 0.30 * d^2
 *   Deckkraft   = 1 - 0.78 * d^2      (d = Abstand zur Mitte, 0..1)
 *   Einruecken  = 13 dp * d^2         (folgt der Kreissehne)
 *
 * Quadratisch, nicht linear: nahe der Mitte soll fast nichts passieren, erst
 * zum Rand hin deutlich.
 */
public class KurvenListe extends ScrollView {

    private final float dp;

    public KurvenListe(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        setVerticalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setClipChildren(false);
        setClipToPadding(false);
    }

    @Override
    protected void onScrollChanged(int l, int t, int ol, int ot) {
        super.onScrollChanged(l, t, ol, ot);
        formen();
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        formen();
    }

    /** Wendet Groesse, Deckkraft und Einruecken auf jede Karte an. */
    public void formen() {
        if (getChildCount() == 0) return;
        View inhalt = getChildAt(0);
        if (!(inhalt instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) inhalt;
        float mitte = getScrollY() + getHeight() / 2f;
        float halb = getHeight() / 2f;
        if (halb <= 0) return;

        for (int i = 0; i < g.getChildCount(); i++) {
            View k = g.getChildAt(i);
            if (k.getHeight() == 0) continue;
            float kMitte = k.getTop() + k.getHeight() / 2f + g.getTop();
            float d = Math.min(1f, Math.abs(kMitte - mitte) / halb);
            float dd = d * d;
            float s = 1f - 0.30f * dd;
            k.setScaleX(s);
            k.setScaleY(s);
            k.setAlpha(Math.max(0.10f, 1f - 0.78f * dd));
            k.setTranslationX(13 * dp * dd);
        }
    }

    /** Laesst die Karten nacheinander hereinkommen. */
    public void auftritt() {
        View inhalt = getChildAt(0);
        if (!(inhalt instanceof ViewGroup)) return;
        ViewGroup g = (ViewGroup) inhalt;
        for (int i = 0; i < g.getChildCount(); i++) {
            final View k = g.getChildAt(i);
            k.setAlpha(0f);
            k.setTranslationY(18 * dp);
            k.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setStartDelay(60L + i * Stil.VERSATZ)
                    .setDuration(Stil.KOMMT + 60)
                    .setInterpolator(Stil.ein())
                    .withEndAction(this::formen)
                    .start();
        }
    }

    @Override
    protected void dispatchDraw(Canvas c) {
        super.dispatchDraw(c);
    }
}
