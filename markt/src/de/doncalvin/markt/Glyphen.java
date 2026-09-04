package de.doncalvin.markt;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;

/** Kleine, selbst gezeichnete Symbole - scharf auf jeder Auflösung, ohne Schriftart. */
public final class Glyphen {
    private Glyphen() {}

    public static void zeichne(String name, Canvas c, float cx, float cy, float r, int farbe) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(farbe); p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(r * 0.16f); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND);
        switch (name) {
            case "suche": {
                float rr = r * 0.62f;
                c.drawCircle(cx - r * 0.15f, cy - r * 0.15f, rr, p);
                c.drawLine(cx + rr * 0.55f, cy + rr * 0.55f, cx + r * 0.75f, cy + r * 0.75f, p);
                break;
            }
            case "blatt": {   // Zifferblatt: Kreis + zwei Zeiger
                c.drawCircle(cx, cy, r * 0.78f, p);
                c.drawLine(cx, cy, cx, cy - r * 0.5f, p);
                c.drawLine(cx, cy, cx + r * 0.42f, cy + r * 0.1f, p);
                break;
            }
            case "apps": {    // 2x2 Raster mit runden Ecken
                float s = r * 0.62f, g = r * 0.16f;
                p.setStyle(Paint.Style.FILL);
                for (int ix = 0; ix < 2; ix++) for (int iy = 0; iy < 2; iy++) {
                    float x = cx - s + ix * (s + g), y = cy - s + iy * (s + g);
                    c.drawRoundRect(new RectF(x, y, x + s, y + s), r * 0.18f, r * 0.18f, p);
                }
                break;
            }
            case "meine": {   // Häkchen im Kreis
                c.drawCircle(cx, cy, r * 0.78f, p);
                Path h = new Path();
                h.moveTo(cx - r * 0.34f, cy + r * 0.02f);
                h.lineTo(cx - r * 0.08f, cy + r * 0.3f);
                h.lineTo(cx + r * 0.4f, cy - r * 0.32f);
                c.drawPath(h, p);
                break;
            }
            case "chevron": {
                Path h = new Path();
                h.moveTo(cx - r * 0.3f, cy - r * 0.55f);
                h.lineTo(cx + r * 0.3f, cy);
                h.lineTo(cx - r * 0.3f, cy + r * 0.55f);
                c.drawPath(h, p);
                break;
            }
            case "stern": {
                p.setStyle(Paint.Style.FILL);
                Path st = new Path();
                for (int i = 0; i < 10; i++) {
                    double a = Math.PI / 2 + i * Math.PI / 5;
                    float rad = (i % 2 == 0) ? r : r * 0.42f;
                    float x = cx + (float) Math.cos(a) * rad, y = cy - (float) Math.sin(a) * rad;
                    if (i == 0) st.moveTo(x, y); else st.lineTo(x, y);
                }
                st.close(); c.drawPath(st, p);
                break;
            }
        }
    }
}
