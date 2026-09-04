package de.doncalvin.markt;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Eine App-Karte: links das echte App-Symbol in einer runden Kachel, in der
 * Mitte Name und Entwickler, rechts die Bewertung. Ruhe = eine Stufe Grau,
 * Druck = eine Stufe heller. Kein Schatten (auf Schwarz sinnlos), dafür ein
 * feiner Rand für Tiefe.
 */
public class Kachel extends LinearLayout {
    public final ImageView bild;
    public final TextView titel, unter;
    private final SternView sterne;
    // rechts unten deklariert
    private final View punkt;

    public Kachel(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        int r = Stil.dp(c, Stil.ECKE_KARTE);
        setBackground(hintergrund(r));
        setClickable(true); setFocusable(true);
        int p = Stil.dp(c, 11);
        setPadding(p, Stil.dp(c, 9), Stil.dp(c, 14), Stil.dp(c, 9));
        setMinimumHeight(Stil.dp(c, 62));

        int g = Stil.dp(c, 42);
        bild = new ImageView(c);
        bild.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bild.setBackgroundColor(Stil.FLAECHE_03);
        final int ecke = Stil.dp(c, 12);
        bild.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), ecke); }
        });
        bild.setClipToOutline(true);
        LayoutParams bp = new LayoutParams(g, g); bp.rightMargin = Stil.dp(c, 11);
        addView(bild, bp); bild.setVisibility(GONE);

        punkt = new View(c);
        GradientDrawable kreis = new GradientDrawable(); kreis.setShape(GradientDrawable.OVAL); kreis.setColor(Stil.TEXT_SCHWACH);
        punkt.setBackground(kreis);
        LayoutParams pp = new LayoutParams(Stil.dp(c, 11), Stil.dp(c, 11)); pp.rightMargin = Stil.dp(c, 12);
        addView(punkt, pp);

        LinearLayout spalte = new LinearLayout(c); spalte.setOrientation(VERTICAL);
        titel = new TextView(c); titel.setTextColor(Stil.TEXT_STARK); titel.setTextSize(15); titel.setMaxLines(1);
        titel.setEllipsize(android.text.TextUtils.TruncateAt.END);
        titel.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        unter = new TextView(c); unter.setTextColor(Stil.TEXT_SCHWACH); unter.setTextSize(11.5f); unter.setMaxLines(1);
        unter.setEllipsize(android.text.TextUtils.TruncateAt.END);
        spalte.addView(titel); spalte.addView(unter);
        addView(spalte, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        sterne = new SternView(c);
        LayoutParams sp = new LayoutParams(Stil.dp(c, 34), Stil.dp(c, 30)); sp.leftMargin = Stil.dp(c, 4);
        addView(sterne, sp); sterne.setVisibility(GONE);

        rechts = new TextView(c); rechts.setTextSize(10.5f); rechts.setPadding(Stil.dp(c, 6), 0, 0, 0);
        addView(rechts); rechts.setVisibility(GONE);
    }

    public final TextView rechts;

    private StateListDrawable hintergrund(int r) {
        GradientDrawable ruhe = new GradientDrawable(); ruhe.setColor(Stil.FLAECHE_01); ruhe.setCornerRadius(r);
        ruhe.setStroke(Stil.dp(getContext(), 1), Stil.RAND_LEISE);
        GradientDrawable druck = new GradientDrawable(); druck.setColor(Stil.FLAECHE_02); druck.setCornerRadius(r);
        druck.setStroke(Stil.dp(getContext(), 1), Stil.RAND_DEUTLICH);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{ android.R.attr.state_pressed }, druck);
        s.addState(new int[]{}, ruhe);
        return s;
    }

    public Kachel farbe(int farbe) {
        ((GradientDrawable) punkt.getBackground()).setColor(farbe);
        punkt.setVisibility(VISIBLE); bild.setVisibility(GONE); sterne.setVisibility(GONE);
        return this;
    }

    public Kachel app(AppEintrag e) {
        punkt.setVisibility(GONE); bild.setVisibility(VISIBLE);
        bild.setImageDrawable(null); bild.setBackgroundColor(Stil.FLAECHE_03);
        Bilder.lade(e.symbolUrl, bild);
        titel.setText(e.name);
        String u = e.entwickler;
        String gr = e.groesseText();
        if (!gr.isEmpty()) u = u.isEmpty() ? gr : u + " · " + gr;
        unter.setText(u);
        if (e.bewertung > 0) { sterne.setzen(e.bewertung); sterne.setVisibility(VISIBLE); } else sterne.setVisibility(GONE);
        return this;
    }

    public Kachel text(String t, String u) { titel.setText(t); unter.setText(u); return this; }
    public Kachel stand(String s) {
        rechts.setText(s);
        rechts.setTextColor("Update".equals(s) ? Stil.AKZENT : Stil.TEXT_LEISE);
        rechts.setVisibility(s == null || s.isEmpty() ? GONE : VISIBLE);
        sterne.setVisibility(GONE);
        return this;
    }

    /** Ein Stern plus Note - klein, rechts an der Karte. */
    static class SternView extends View {
        private float note; private final Paint t = new Paint(Paint.ANTI_ALIAS_FLAG);
        SternView(Context c) { super(c); t.setColor(Stil.TEXT_SCHWACH); t.setTextSize(Stil.dp(c, 11)); t.setTextAlign(Paint.Align.CENTER); }
        void setzen(float n) { note = n; invalidate(); }
        @Override protected void onDraw(Canvas cv) {
            float cx = getWidth() / 2f, r = Stil.dp(getContext(), 5);
            Glyphen.zeichne("stern", cv, cx, getHeight() * 0.32f, r, Stil.KOERPER);
            cv.drawText(String.format(java.util.Locale.GERMANY, "%.1f", note), cx, getHeight() * 0.9f, t);
        }
    }
}
