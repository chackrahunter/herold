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
import android.widget.FrameLayout;
import android.widget.TextView;

/** Zeigt das Ergebnis einer EKG-Messung samt Rhythmusbewertung. */
public class ErgebnisActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        KurvenListe roller = new KurvenListe(this);
        roller.setBackgroundColor(Stil.GRUND);

        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setClipChildren(false);
        int rand = Stil.dp(this, 18);
        s.setPadding(rand, Stil.dp(this, 40), rand, Stil.dp(this, 60));

        EkgAuswertung a = EkgService.letztesErgebnis;
        if (getIntent() != null && getIntent().getBooleanExtra("selbsttest", false)) {
            a = beispiel();
        }
        if (a == null) {
            s.addView(text("Keine Messung vorhanden.", 14f, Stil.TEXT_SCHWACH, Gravity.CENTER));
            fertig(roller, s);
            return;
        }

        TextView kopf = text("EKG", 12.5f, Stil.EKG, Gravity.CENTER);
        kopf.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        kopf.setLetterSpacing(0.06f);
        s.addView(kopf);
        s.addView(abstand(6));

        // Puls gross oben - leicht gesetzt, damit die Zahl traegt und nicht schreit
        TextView gross = text(a.puls > 0 ? String.valueOf(a.puls) : "—", 44f, Stil.TEXT_STARK, Gravity.CENTER);
        gross.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        s.addView(gross);
        s.addView(text(a.puls > 0 ? "Schläge pro Minute" : "nicht auswertbar",
                10.5f, Stil.TEXT_SCHWACH, Gravity.CENTER));
        s.addView(abstand(12));

        // Standbild der Kurve
        if (a.kurve != null && a.kurve.length > 500) {
            KurvenView k = new KurvenView(this, null);
            LinearLayout.LayoutParams kp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Karte.dp(this, 62));
            kp.bottomMargin = Karte.dp(this, 10);
            int mitte = Math.max(0, a.kurve.length / 2 - 1250);
            k.standbild(a.kurve, mitte, Math.min(2500, a.kurve.length - mitte));
            s.addView(k, kp);
        }

        if (a.abstaende != null && a.abstaende.size() > 5) {
            PoincareView pv = new PoincareView(this);
            pv.setzeFarbe(a.rhythmus == null ? Stil.BEFUND_GLEICHMAESSIG : Stil.befund(a.rhythmus.urteil));
            int gr = Karte.dp(this, 118);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(gr, gr);
            lp.gravity = Gravity.CENTER_HORIZONTAL;
            pv.setLayoutParams(lp);
            pv.zeige(a.abstaende, true);
            s.addView(pv);
            TextView bu = text("jeder Schlagabstand gegen den nächsten",
                    9f, Stil.TEXT_LEISE, Gravity.CENTER);
            bu.setPadding(0, Karte.dp(this, 2), 0, Karte.dp(this, 6));
            s.addView(bu);
        }

        // Rhythmus
        RhythmusAnalyse.Ergebnis r = a.rhythmus;
        if (r == null) {
            s.addView(block("Rhythmus nicht geprüft",
                    "Das Signal war für eine Rhythmusbewertung zu unruhig.",
                    Stil.TEXT_LEISE));
        } else {
            int farbe = Stil.befund(r.urteil);
            s.addView(block(r.titel, r.text, farbe));

            if (r.schlaege > 0) {
                s.addView(abstand(6));
                if (r.atemzuege > 0f)
                    s.addView(zeile("Atemzüge", Math.round(r.atemzuege) + " /min"));
                s.addView(zeile("Schläge gewertet", String.valueOf(r.schlaege)));
                s.addView(zeile("Schwankung", Math.round(r.streuung * 1000) / 10f + " %"));
                s.addView(zeile("RMSSD", Math.round(r.rmssd) + " ms"));
                s.addView(zeile("pNN50", Math.round(r.pnn50) + " %"));
                s.addView(zeile("Regelmaß", String.format("%.2f", r.kopplung)));
            }
        }

        if (a.patMs > 0) {
            s.addView(abstand(8));
            s.addView(zeile("Pulsankunftszeit", a.patMs + " ms"));
            s.addView(zeile("Spanne", a.patVon + "–" + a.patBis + " ms · " + a.patN + " Schläge"));
            TextView pe = text("Laufzeit von der R-Zacke bis zur Pulswelle am Handgelenk. "
                    + "Sie verändert sich mit dem Blutdruck, ist aber keine Blutdruckmessung — "
                    + "als Verlauf über Tage aussagekräftig, nicht als Einzelwert.",
                    9.5f, Stil.TEXT_LEISE, Gravity.LEFT);
            pe.setPadding(Stil.dp(this, 6), Stil.dp(this, 2), Stil.dp(this, 6), 0);
            s.addView(pe);
        }
        s.addView(abstand(8));
        s.addView(zeile("Signalgüte", String.valueOf(a.guete)));
        s.addView(zeile("Ausschlag", String.format("%.2f mV", a.amplitude)));

        s.addView(abstand(12));
        TextView hinweis = text(
                "Herold ist kein Medizinprodukt. Die Auswertung ersetzt keine "
              + "ärztliche Untersuchung und stellt keine Diagnose. Bei Beschwerden "
              + "oder auffälligen Ergebnissen bitte ärztlichen Rat einholen.",
                9.5f, Stil.TEXT_LEISE, Gravity.LEFT);
        s.addView(hinweis);

        fertig(roller, s);
    }

    /** Nachgebildetes Ergebnis, um die Anzeige ohne echte Messung zu pruefen. */
    private EkgAuswertung beispiel() {
        EkgAuswertung a = new EkgAuswertung();
        a.puls = 57; a.schlaege = 56; a.amplitude = 1.24f; a.guete = "gut";
        a.kurve = new float[2600];
        for (int i = 0; i < a.kurve.length; i++) {
            double t = i / 500.0, ph = (t % 0.97) / 0.97;
            a.kurve[i] = (float) (0.9 * Math.exp(-Math.pow((ph - 0.30) / 0.012, 2))
                    - 0.22 * Math.exp(-Math.pow((ph - 0.26) / 0.016, 2))
                    - 0.20 * Math.exp(-Math.pow((ph - 0.34) / 0.018, 2))
                    + 0.13 * Math.exp(-Math.pow((ph - 0.58) / 0.055, 2)));
        }
        // Echte Schlagabstaende aus einer Messung, damit die Vorschau zeigt,
        // wie es tatsaechlich aussieht - eine reine Sinuswelle faellt im
        // Streubild auf wenige Punkte zusammen und taeuscht.
        int[] echt = {1024,1041,1092,968,1070,1096,954,962,1149,1170,1000,1105,
            1194,970,1024,1143,951,1059,1182,1149,1027,1147,1176,965,959,1042,
            1026,915,1069,1174,993,1076,1120,985,966,1118,1037,893,1036,1099,
            972,1084,1155,1048,919,1086,1146,971,1080,1150,1041,962,1123,1152,977,1080};
        java.util.List<Integer> rr = new java.util.ArrayList<>();
        for (int v : echt) rr.add(v);
        a.abstaende = rr;
        a.rhythmus = RhythmusAnalyse.pruefe(rr);
        return a;
    }

    private void fertig(KurvenListe roller, LinearLayout s) {
        roller.addView(s, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(roller);
        Rad.anBinden(roller);
        Rad.fokusHolen(roller);
        roller.post(roller::auftritt);
    }

    private TextView text(String t, float groesse, int farbe, int lage) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextSize(groesse);
        v.setTextColor(farbe);
        v.setGravity(lage);
        return v;
    }

    /** Farbig abgesetzter Kasten fuer das Urteil. */
    private View block(String titel, String inhalt, int akzent) {
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        int p = Karte.dp(this, 12);
        k.setPadding(p, p, p, p);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(Stil.FLAECHE_01);
        hg.setCornerRadius(Stil.dp(this, Stil.ECKE_KASTEN));
        hg.setStroke(Stil.dp(this, 2), MessKarte.mitAlpha(akzent, 0xB0));
        k.setBackground(hg);

        TextView t = text(titel, 13.5f, akzent, Gravity.LEFT);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        k.addView(t);
        TextView i = text(inhalt, 10.5f, Stil.TEXT_MITTEL, Gravity.LEFT);
        i.setPadding(0, Karte.dp(this, 4), 0, 0);
        i.setLineSpacing(Karte.dp(this, 2), 1f);
        k.addView(i);
        return k;
    }

    /** Beschriftung links, Wert rechts. */
    private View zeile(String links, String rechts) {
        LinearLayout z = new LinearLayout(this);
        z.setOrientation(LinearLayout.HORIZONTAL);
        z.setPadding(Karte.dp(this, 4), Karte.dp(this, 4), Karte.dp(this, 4), Karte.dp(this, 4));
        TextView a = text(links, 10.5f, Stil.TEXT_SCHWACH, Gravity.LEFT);
        z.addView(a, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        z.addView(text(rechts, 10.5f, Stil.TEXT_MITTEL, Gravity.RIGHT));
        return z;
    }

    private View abstand(int hoehe) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Karte.dp(this, hoehe)));
        return v;
    }
}
