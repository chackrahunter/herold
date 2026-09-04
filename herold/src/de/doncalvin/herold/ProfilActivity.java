package de.doncalvin.herold;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** Koerperdaten fuer die Koerperanalyse - direkt auf der Uhr einstellbar. */
public class ProfilActivity extends Activity {

    private Profil profil;
    private TextView wGroesse, wGewicht, wGeburt, wGeschlecht, wAlter;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        profil = new Profil(this);

        ScrollView roller = new ScrollView(this);
        roller.setBackgroundColor(0xFF000000);
        roller.setVerticalScrollBarEnabled(false);

        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        int rand = Karte.dp(this, 16);
        s.setPadding(rand, Karte.dp(this, 44), rand, Karte.dp(this, 58));

        TextView t = new TextView(this);
        t.setText("Körperdaten");
        t.setTextSize(16f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Stil.KOERPER);
        t.setGravity(Gravity.CENTER);
        s.addView(t);

        TextView u = new TextView(this);
        u.setText("nur für die Körperanalyse,\nbleibt auf der Uhr");
        u.setTextSize(10f);
        u.setTextColor(Stil.TEXT_LEISE);
        u.setGravity(Gravity.CENTER);
        u.setPadding(0, 0, 0, Karte.dp(this, 12));
        s.addView(u);

        wGroesse = regler(s, "Größe", () -> profil.groesseCm() + " cm",
                d -> profil.setzeGroesse(begrenze(profil.groesseCm() + d, 100, 220)));
        wGewicht = regler(s, "Gewicht", () -> zahl(profil.gewichtKg()) + " kg",
                d -> profil.setzeGewicht(begrenze(profil.gewichtKg() + d * 0.5f, 25f, 200f)));
        wGeburt = regler(s, "Geburtsjahr", () -> String.valueOf(profil.geburtsjahr()),
                d -> profil.setzeGeburtstag(profil.geburtstag(), profil.geburtsmonat(),
                        begrenze(profil.geburtsjahr() + d, 1900, 2020)));
        wGeschlecht = regler(s, "Geschlecht", () -> profil.geschlechtName(),
                d -> profil.setzeGeschlecht(profil.geschlecht() == Profil.WEIBLICH
                        ? Profil.MAENNLICH : Profil.WEIBLICH));

        wAlter = new TextView(this);
        wAlter.setTextSize(11f);
        wAlter.setTextColor(Stil.TEXT_SCHWACH);
        wAlter.setGravity(Gravity.CENTER);
        wAlter.setPadding(0, Karte.dp(this, 10), 0, 0);
        s.addView(wAlter);

        roller.addView(s, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(roller);
        Rad.anBinden(roller);
        Rad.fokusHolen(roller);
        auffrischen();
    }

    private interface Anzeige { String text(); }
    private interface Aendern { void um(int schritte); }

    /** Zeile mit Minus, Wert und Plus. */
    private TextView regler(LinearLayout eltern, String name,
                            final Anzeige anzeige, final Aendern aendern) {
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        int p = Karte.dp(this, 10);
        k.setPadding(p, p, p, p);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(Stil.FLAECHE_01);
        hg.setCornerRadius(Karte.dp(this, 18));
        k.setBackground(hg);

        TextView bez = new TextView(this);
        bez.setText(name);
        bez.setTextSize(10f);
        bez.setTextColor(Stil.TEXT_SCHWACH);
        bez.setGravity(Gravity.CENTER);
        k.addView(bez);

        LinearLayout reihe = new LinearLayout(this);
        reihe.setOrientation(LinearLayout.HORIZONTAL);
        reihe.setGravity(Gravity.CENTER_VERTICAL);

        final TextView wert = new TextView(this);
        wert.setTextSize(15f);
        wert.setTypeface(Typeface.DEFAULT_BOLD);
        wert.setTextColor(0xFFFFFFFF);
        wert.setGravity(Gravity.CENTER);

        reihe.addView(taste("−", () -> { aendern.um(-1); auffrischen(); }));
        reihe.addView(wert, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        reihe.addView(taste("+", () -> { aendern.um(+1); auffrischen(); }));
        k.addView(reihe);

        wert.setTag(anzeige);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Karte.dp(this, 8);
        eltern.addView(k, lp);
        return wert;
    }

    private View taste(String zeichen, final Runnable tun) {
        TextView v = new TextView(this);
        v.setText(zeichen);
        v.setTextSize(20f);
        v.setTextColor(Stil.KOERPER);
        v.setGravity(Gravity.CENTER);
        int gr = Karte.dp(this, 38);
        v.setLayoutParams(new LinearLayout.LayoutParams(gr, gr));
        GradientDrawable hg = new GradientDrawable();
        hg.setShape(GradientDrawable.OVAL);
        hg.setColor(Stil.FLAECHE_02);
        v.setBackground(hg);
        v.setOnClickListener(x -> tun.run());
        return v;
    }

    private void auffrischen() {
        for (TextView v : new TextView[]{wGroesse, wGewicht, wGeburt, wGeschlecht}) {
            if (v != null && v.getTag() instanceof Anzeige)
                v.setText(((Anzeige) v.getTag()).text());
        }
        if (wAlter != null) {
            wAlter.setText(profil.vollstaendig()
                    ? "ergibt " + profil.alter() + " Jahre"
                    : "bitte alle Felder ausfüllen");
        }
    }

    private static int begrenze(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
    private static float begrenze(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    private static String zahl(float f) {
        return f == Math.round(f) ? String.valueOf(Math.round(f)) : String.format("%.1f", f);
    }
}
