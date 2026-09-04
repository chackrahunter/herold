package de.doncalvin.herold;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Liste der frueheren Messungen, neueste oben. */
public class VerlaufActivity extends Activity {

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        ScrollView roller = new ScrollView(this);
        roller.setBackgroundColor(0xFF000000);
        roller.setVerticalScrollBarEnabled(false);

        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        int rand = Karte.dp(this, 16);
        s.setPadding(rand, Karte.dp(this, 44), rand, Karte.dp(this, 58));

        TextView t = new TextView(this);
        t.setText("Verlauf");
        t.setTextSize(16f);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setTextColor(Stil.VERLAUF);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, Karte.dp(this, 12));
        s.addView(t);

        List<Verlauf.Eintrag> alle = new Verlauf(this).lies();
        if (alle.isEmpty()) {
            TextView leer = new TextView(this);
            leer.setText("Noch keine Messungen.\nJede Messung wird hier abgelegt.");
            leer.setTextSize(11f);
            leer.setTextColor(Stil.TEXT_LEISE);
            leer.setGravity(Gravity.CENTER);
            s.addView(leer);
        } else {
            String letzterTag = "";
            for (Verlauf.Eintrag e : alle) {
                String tag = e.zeitText().split(" ")[0];
                if (!tag.equals(letzterTag)) {
                    letzterTag = tag;
                    TextView kopf = new TextView(this);
                    kopf.setText(tag);
                    kopf.setTextSize(10f);
                    kopf.setTextColor(Stil.TEXT_LEISE);
                    kopf.setPadding(Karte.dp(this, 6), Karte.dp(this, 10),
                            0, Karte.dp(this, 4));
                    s.addView(kopf);
                }
                s.addView(zeile(e));
            }
        }

        roller.addView(s, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(roller);
        Rad.anBinden(roller);
        Rad.fokusHolen(roller);
    }

    private View zeile(Verlauf.Eintrag e) {
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.HORIZONTAL);
        k.setGravity(Gravity.CENTER_VERTICAL);
        int p = Karte.dp(this, 10);
        k.setPadding(p, p, p, p);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(Stil.FLAECHE_01);
        hg.setCornerRadius(Karte.dp(this, 16));
        k.setBackground(hg);

        View punkt = new View(this);
        GradientDrawable kreis = new GradientDrawable();
        kreis.setShape(GradientDrawable.OVAL);
        kreis.setColor(e.farbe());
        punkt.setBackground(kreis);
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(
                Karte.dp(this, 9), Karte.dp(this, 9));
        pp.rightMargin = Karte.dp(this, 10);
        k.addView(punkt, pp);

        LinearLayout texte = new LinearLayout(this);
        texte.setOrientation(LinearLayout.VERTICAL);
        texte.addView(text(e.wert, 14f, 0xFFFFFFFF, true));
        String unten = e.artName();
        if (e.zusatz != null && !e.zusatz.isEmpty()) unten += " · " + e.zusatz;
        texte.addView(text(unten, 9.5f, Stil.TEXT_SCHWACH, false));
        k.addView(texte, new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        k.addView(text(e.zeitText().split(" ")[1], 10f, Stil.TEXT_LEISE, false));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Karte.dp(this, 6);
        k.setLayoutParams(lp);
        k.setClickable(true);
        k.setOnClickListener(v -> startActivity(new Intent(this, DetailActivity.class)
                .putExtra("zeit", e.zeit).putExtra("art", e.art)
                .putExtra("wert", e.wert).putExtra("zusatz", e.zusatz)));
        return k;
    }

    private TextView text(String s, float g, int farbe, boolean fett) {
        TextView v = new TextView(this);
        v.setText(s);
        v.setTextSize(g);
        v.setTextColor(farbe);
        if (fett) v.setTypeface(Typeface.DEFAULT_BOLD);
        return v;
    }
}
