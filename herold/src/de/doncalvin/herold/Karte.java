package de.doncalvin.herold;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Eine antippbare Zeile im Startbildschirm: Farbpunkt, Titel, Untertitel. */
public class Karte extends LinearLayout {

    public Karte(Context c, String titel, String unten, int akzent) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int p = dp(c, 12);
        setPadding(dp(c, 14), p, dp(c, 14), p);

        GradientDrawable hg = new GradientDrawable();
        hg.setColor(Stil.FLAECHE_01);
        hg.setCornerRadius(dp(c, 22));
        setBackground(hg);

        View punkt = new View(c);
        GradientDrawable kreis = new GradientDrawable();
        kreis.setShape(GradientDrawable.OVAL);
        kreis.setColor(akzent);
        punkt.setBackground(kreis);
        LayoutParams lp = new LayoutParams(dp(c, 12), dp(c, 12));
        lp.rightMargin = dp(c, 12);
        addView(punkt, lp);

        LinearLayout texte = new LinearLayout(c);
        texte.setOrientation(VERTICAL);

        TextView t = new TextView(c);
        t.setText(titel);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(15f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        texte.addView(t);

        if (unten != null && !unten.isEmpty()) {
            TextView u = new TextView(c);
            u.setText(unten);
            u.setTextColor(Stil.TEXT_SCHWACH);
            u.setTextSize(11f);
            texte.addView(u);
        }
        addView(texte, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        setClickable(true);
        setFocusable(true);
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
