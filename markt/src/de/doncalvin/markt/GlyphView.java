package de.doncalvin.markt;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/** Zeigt ein einzelnes Glyph mittig. */
public class GlyphView extends View {
    private final String name; private final int farbe; private final float anteil;
    public GlyphView(Context c, String name, int farbe) { this(c, name, farbe, 0.5f); }
    public GlyphView(Context c, String name, int farbe, float anteil) { super(c); this.name = name; this.farbe = farbe; this.anteil = anteil; }
    @Override protected void onDraw(Canvas cv) {
        float r = Math.min(getWidth(), getHeight()) * anteil;
        Glyphen.zeichne(name, cv, getWidth() / 2f, getHeight() / 2f, r, farbe);
    }
}
