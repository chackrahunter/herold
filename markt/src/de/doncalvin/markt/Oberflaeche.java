package de.doncalvin.markt;

import android.app.Activity;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Gemeinsame Bausteine: Titelzeile, Hinweistext, Liste mit Luenette. */
public final class Oberflaeche {
    private Oberflaeche() {}

    /** Kleine Ueberschrift in Kapitaelchen-Optik, wie in Herold. */
    public static TextView kopf(Activity a, String text, String unter) {
        LinearLayout box = new LinearLayout(a); box.setOrientation(LinearLayout.VERTICAL); box.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView t = new TextView(a);
        t.setText(text.toUpperCase());
        t.setTextColor(Stil.TEXT_SCHWACH); t.setTextSize(11); t.setLetterSpacing(0.18f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setGravity(Gravity.CENTER);
        if (unter != null && !unter.isEmpty()) {
            t.setText(text.toUpperCase() + "\n");
            TextView u = new TextView(a); u.setText(unter); u.setTextColor(Stil.TEXT_LEISE); u.setTextSize(10.5f); u.setGravity(Gravity.CENTER);
            t.append(u.getText());
        }
        t.setPadding(0, 0, 0, Stil.dp(a, 8));
        return t;
    }

    public static TextView hinweis(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text); t.setTextColor(Stil.TEXT_SCHWACH); t.setTextSize(12.5f); t.setGravity(Gravity.CENTER);
        t.setLineSpacing(0, 1.15f);
        int p = Stil.dp(a, 14);
        t.setPadding(p, Stil.dp(a, 18), p, Stil.dp(a, 18));
        return t;
    }

    public static TextView fuss(Activity a, String text) {
        TextView t = new TextView(a);
        t.setText(text); t.setTextColor(Stil.TEXT_LEISE); t.setTextSize(10); t.setGravity(Gravity.CENTER);
        t.setPadding(0, Stil.dp(a, 14), 0, 0);
        return t;
    }

    /** Liste + Spalte anlegen, Luenette anbinden, als Inhalt setzen. Gibt die Spalte zurueck. */
    public static LinearLayout liste(Activity a, KurvenListe[] rollerAus) {
        KurvenListe roller = new KurvenListe(a);
        roller.setBackgroundColor(Stil.GRUND);
        LinearLayout spalte = new LinearLayout(a);
        spalte.setOrientation(LinearLayout.VERTICAL);
        spalte.setClipChildren(false);
        int rand = Stil.dp(a, 14);
        spalte.setPadding(rand, Stil.dp(a, 30), rand, Stil.dp(a, 62));
        roller.addView(spalte, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        a.setContentView(roller);
        Rad.anBinden(roller); Rad.fokusHolen(roller);
        if (rollerAus != null && rollerAus.length > 0) rollerAus[0] = roller;
        return spalte;
    }

    public static LinearLayout.LayoutParams kartenMass(Activity a) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.bottomMargin = Stil.dp(a, 8);
        return p;
    }
}
