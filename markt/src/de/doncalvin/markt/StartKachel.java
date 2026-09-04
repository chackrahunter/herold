package de.doncalvin.markt;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Eine Kategorie-Kachel auf dem Start: getönter Verlauf, Glyph, Titel, Chevron. */
public class StartKachel extends LinearLayout {
    public StartKachel(Context c, String glyph, int farbe, String titel, String unter) {
        super(c);
        setOrientation(HORIZONTAL); setGravity(Gravity.CENTER_VERTICAL);
        int r = Stil.dp(c, Stil.ECKE_KARTE);
        setBackground(hintergrund(c, farbe, r));
        setClickable(true); setFocusable(true);
        int p = Stil.dp(c, 12);
        setPadding(p, Stil.dp(c, 12), Stil.dp(c, 14), Stil.dp(c, 12));
        setMinimumHeight(Stil.dp(c, 66));

        // Glyph in getöntem Kreis
        View kreis = new View(c);
        GradientDrawable g = new GradientDrawable(); g.setShape(GradientDrawable.OVAL);
        g.setColor(mischen(farbe, 0x00000000, 0.72f)); // dunkle, getönte Scheibe
        kreis.setBackground(g);
        FrameGlyph fg = new FrameGlyph(c, glyph, farbe);
        android.widget.FrameLayout box = new android.widget.FrameLayout(c);
        int d = Stil.dp(c, 40);
        box.addView(kreis, new android.widget.FrameLayout.LayoutParams(d, d));
        box.addView(fg, new android.widget.FrameLayout.LayoutParams(d, d));
        LayoutParams bp = new LayoutParams(d, d); bp.rightMargin = Stil.dp(c, 12);
        addView(box, bp);

        LinearLayout spalte = new LinearLayout(c); spalte.setOrientation(VERTICAL);
        TextView t = new TextView(c); t.setText(titel); t.setTextColor(Stil.TEXT_STARK); t.setTextSize(15.5f);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        TextView u = new TextView(c); u.setText(unter); u.setTextColor(Stil.TEXT_SCHWACH); u.setTextSize(11.5f);
        spalte.addView(t); spalte.addView(u);
        addView(spalte, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        GlyphView chev = new GlyphView(c, "chevron", Stil.TEXT_SCHWACH, 0.28f);
        addView(chev, new LayoutParams(Stil.dp(c, 18), Stil.dp(c, 34)));
    }

    private StateListDrawable hintergrund(Context c, int farbe, int r) {
        GradientDrawable ruhe = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ mischen(farbe, Stil.FLAECHE_01, 0.22f), Stil.FLAECHE_01 });
        ruhe.setCornerRadius(r); ruhe.setStroke(Stil.dp(c, 1), mischen(farbe, Stil.RAND_LEISE, 0.35f));
        GradientDrawable druck = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{ mischen(farbe, Stil.FLAECHE_02, 0.34f), Stil.FLAECHE_02 });
        druck.setCornerRadius(r); druck.setStroke(Stil.dp(c, 1), mischen(farbe, Stil.RAND_DEUTLICH, 0.4f));
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{ android.R.attr.state_pressed }, druck);
        s.addState(new int[]{}, ruhe);
        return s;
    }

    /** farbe über grund legen, anteil = Deckkraft der Farbe. */
    static int mischen(int farbe, int grund, float anteil) {
        int ar = (grund >>> 24), ag = grund == 0 ? 255 : 255;
        float a = anteil;
        int r = (int) (((farbe >> 16) & 0xFF) * a + ((grund >> 16) & 0xFF) * (1 - a));
        int gr = (int) (((farbe >> 8) & 0xFF) * a + ((grund >> 8) & 0xFF) * (1 - a));
        int b = (int) ((farbe & 0xFF) * a + (grund & 0xFF) * (1 - a));
        return 0xFF000000 | (r << 16) | (gr << 8) | b;
    }

    static class FrameGlyph extends View {
        private final String name; private final int farbe;
        FrameGlyph(Context c, String name, int farbe) { super(c); this.name = name; this.farbe = farbe; }
        @Override protected void onDraw(android.graphics.Canvas cv) {
            float r = Math.min(getWidth(), getHeight()) * 0.30f;
            Glyphen.zeichne(name, cv, getWidth() / 2f, getHeight() / 2f, r, farbe);
        }
    }
}
