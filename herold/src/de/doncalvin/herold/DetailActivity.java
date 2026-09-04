package de.doncalvin.herold;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;

/** Eine vergangene Messung mit allen Einzelwerten, aus dem Verlauf heraus. */
public class DetailActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        long zeit = getIntent() != null ? getIntent().getLongExtra("zeit", 0) : 0;
        String art = getIntent() != null ? getIntent().getStringExtra("art") : null;
        String wert = getIntent() != null ? getIntent().getStringExtra("wert") : null;
        String zusatz = getIntent() != null ? getIntent().getStringExtra("zusatz") : null;

        Messdetail d = zeit > 0 ? Messdetail.lies(this, zeit) : null;
        Verlauf.Eintrag e = new Verlauf.Eintrag();
        e.zeit = zeit; e.art = art == null ? "" : art;

        KurvenListe roller = new KurvenListe(this);
        roller.setBackgroundColor(Stil.GRUND);
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        s.setClipChildren(false);
        int rand = Stil.dp(this, 18);
        s.setPadding(rand, Stil.dp(this, 40), rand, Stil.dp(this, 58));

        s.addView(text(new SimpleDateFormat("EEEE, d. MMMM · HH:mm", Locale.GERMANY).format(new Date(zeit)),
                10f, Stil.TEXT_LEISE, Gravity.CENTER, false));
        TextView kopf = text(e.artName().toUpperCase(Locale.GERMANY), 12.5f, e.farbe(), Gravity.CENTER, true);
        kopf.setLetterSpacing(0.06f);
        s.addView(kopf);
        s.addView(luft(8));

        // Hauptwert: aus dem Detail, sonst aus der Verlaufszeile
        String haupt = wert;
        String hauptName = null;
        if (d != null && !d.werte.isEmpty()) {
            Map.Entry<String, String> erster = d.werte.entrySet().iterator().next();
            haupt = erster.getValue(); hauptName = erster.getKey();
        }
        TextView gross = text(haupt == null ? "—" : haupt, 40f, Stil.TEXT_STARK, Gravity.CENTER, false);
        gross.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
        s.addView(gross);
        if (hauptName != null) s.addView(text(hauptName, 10.5f, Stil.TEXT_SCHWACH, Gravity.CENTER, false));
        s.addView(luft(12));

        if (d == null) {
            s.addView(kasten("Nur Kurzfassung", "Zu dieser Messung wurden noch keine Einzelwerte "
                    + "gespeichert — das gibt es erst für Messungen ab heute."
                    + (zusatz != null && !zusatz.isEmpty() ? "\n\nDamals: " + zusatz : ""), Stil.TEXT_LEISE));
        } else {
            if (d.abstaende != null && d.abstaende.size() > 5) {
                PoincareView p = new PoincareView(this);
                p.setzeFarbe(d.befundUrteil >= 0 ? Stil.befund(d.befundUrteil) : Stil.BEFUND_GLEICHMAESSIG);
                int gr = Stil.dp(this, 116);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(gr, gr);
                lp.gravity = Gravity.CENTER_HORIZONTAL;
                p.setLayoutParams(lp);
                p.zeige(d.abstaende, true);
                s.addView(p);
                s.addView(text("jeder Schlagabstand gegen den nächsten", 8.5f, Stil.TEXT_LEISE, Gravity.CENTER, false));
                s.addView(luft(8));
            }
            boolean erster = true;
            for (Map.Entry<String, String> w : d.werte.entrySet()) {
                if (erster) { erster = false; continue; }   // steht schon gross oben
                s.addView(zeile(w.getKey(), w.getValue()));
            }
            if (d.befundTitel != null) {
                s.addView(luft(10));
                s.addView(kasten(d.befundTitel, d.befundText == null ? "" : d.befundText,
                        d.befundUrteil >= 0 ? Stil.befund(d.befundUrteil) : Stil.TEXT_LEISE));
            }
        }

        s.addView(luft(14));
        TextView loeschen = text("Messung löschen", 10.5f, Stil.TEXT_LEISE, Gravity.CENTER, false);
        loeschen.setPadding(0, Stil.dp(this, 10), 0, Stil.dp(this, 10));
        loeschen.setOnClickListener(v -> {
            new Verlauf(this).loesche(zeit);
            Messdetail.loesche(this, zeit);
            KachelBasis.auffrischen(this);
            finish();
        });
        s.addView(loeschen);
        s.addView(text("Herold ist kein Medizinprodukt und stellt keine Diagnose.",
                9f, Stil.TEXT_LEISE, Gravity.CENTER, false));

        roller.addView(s, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(roller);
        Rad.anBinden(roller);
        Rad.fokusHolen(roller);
        roller.post(roller::auftritt);
    }

    private TextView text(String t, float g, int farbe, int lage, boolean fett) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(g); v.setTextColor(farbe); v.setGravity(lage);
        if (fett) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }
    private View luft(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Stil.dp(this, h)));
        return v;
    }
    private View zeile(String links, String rechts) {
        LinearLayout z = new LinearLayout(this);
        z.setOrientation(LinearLayout.HORIZONTAL);
        int p = Stil.dp(this, 5);
        z.setPadding(Stil.dp(this, 6), p, Stil.dp(this, 6), p);
        z.addView(text(links, 10.5f, Stil.TEXT_SCHWACH, Gravity.LEFT, false),
                new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        z.addView(text(rechts, 10.5f, Stil.TEXT_MITTEL, Gravity.RIGHT, false));
        z.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return z;
    }
    private View kasten(String kopf, String inhalt, int akzent) {
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        int p = Stil.dp(this, 13);
        k.setPadding(p, p, p, p);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(Stil.FLAECHE_01);
        hg.setCornerRadius(Stil.dp(this, Stil.ECKE_KASTEN));
        hg.setStroke(Stil.dp(this, 2), MessKarte.mitAlpha(akzent, 0xB0));
        k.setBackground(hg);
        k.addView(text(kopf, 13.5f, akzent, Gravity.LEFT, true));
        TextView i = text(inhalt, 10.5f, Stil.TEXT_MITTEL, Gravity.LEFT, false);
        i.setPadding(0, Stil.dp(this, 5), 0, 0);
        i.setLineSpacing(Stil.dp(this, 2), 1f);
        k.addView(i);
        k.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return k;
    }
}
