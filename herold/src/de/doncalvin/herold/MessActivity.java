package de.doncalvin.herold;

import android.animation.ValueAnimator;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Map;

/**
 * Fuehrt durch eine Messung und zeigt danach das Ergebnis.
 *
 * Waehrend der Messung fuellt das lebende Bild (MessBild) den ganzen
 * Bildschirm; Titel und Hinweis liegen darueber. Erst danach klappt die
 * Ansicht in eine scrollbare Auswertung um.
 */
public class MessActivity extends Activity {

    private String art = SensorService.SPO2;
    private FrameLayout wurzel;
    private MessBild bild;
    private TextView titel, unten;
    private KurvenListe roller;
    private LinearLayout spalte;
    private boolean ergebnisGezeigt;
    private boolean signalSchwach;   // Sensor liefert, aber nichts Brauchbares

    private final BroadcastReceiver empfang = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String phase = i.getStringExtra("phase");
            if (phase == null || ergebnisGezeigt) return;
            switch (phase) {
                case "VERBINDEN":
                    unten.setText("Sensor wird vorbereitet…");
                    break;
                case "WARTEN":
                    String w = i.getStringExtra("meldung");
                    unten.setText(w != null ? w : "Beide Tasten gleichzeitig berühren");
                    break;
                case "MESSEN":
                    bild.setzeFortschritt(i.getIntExtra("fortschritt", 0) / 100f);
                    unten.setText(signalSchwach ? "Uhr fester anlegen,\nArm ruhig halten" : hinweisText());
                    break;
                case "FERTIG":
                    zeigeErgebnis();
                    break;
                case "FEHLER":
                    titel.setText("Nicht möglich");
                    unten.setText(String.valueOf(i.getStringExtra("meldung")));
                    break;
            }
        }
    };

    private final SensorService.LiveHoerer hoerer = new SensorService.LiveHoerer() {
        @Override public void pulse(int[] rr, int anzahl) {
            if (bild != null) bild.post(() -> bild.neuePulse(rr, anzahl));
        }
        @Override public void sauerstoff(int prozent) {
            if (bild != null) bild.post(() -> bild.neuerSauerstoff(prozent));
        }
        @Override public void signal(boolean brauchbar) {
            if (bild == null) return;
            bild.post(() -> {
                signalSchwach = !brauchbar;
                bild.signalSchwach(!brauchbar);
                if (!ergebnisGezeigt && unten != null)
                    unten.setText(brauchbar ? hinweisText() : "Uhr fester anlegen,\nArm ruhig halten");
            });
        }
    };

    private String hinweisText() {
        if (SensorService.TEMP.equals(art)) return "Arm ruhig halten";
        if (SensorService.PULS.equals(art)) return "Ruhig sitzen, normal atmen";
        if (SensorService.BIA.equals(art)) return "Mittelfinger oben, Ringfinger unten";
        return "Arm auflegen, nicht bewegen";
    }

    private String artName() {
        if (SensorService.TEMP.equals(art)) return "Hauttemperatur";
        if (SensorService.PULS.equals(art)) return "Puls & Rhythmus";
        if (SensorService.BIA.equals(art)) return "Körperanalyse";
        return "Sauerstoff";
    }

    private int artFarbe() {
        if (SensorService.TEMP.equals(art)) return Stil.TEMPERATUR;
        if (SensorService.PULS.equals(art)) return Stil.PULS;
        if (SensorService.BIA.equals(art)) return Stil.KOERPER;
        return Stil.SAUERSTOFF;
    }

    private int artBild() {
        if (SensorService.TEMP.equals(art)) return MessBild.TEMP;
        if (SensorService.PULS.equals(art)) return MessBild.PULS;
        if (SensorService.BIA.equals(art)) return MessBild.BIA;
        return MessBild.SPO2;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (getIntent() != null && getIntent().getStringExtra("art") != null)
            art = getIntent().getStringExtra("art");

        wurzel = new FrameLayout(this);
        wurzel.setBackgroundColor(Stil.GRUND);

        bild = new MessBild(this);
        bild.setzeArt(artBild(), artFarbe());
        wurzel.addView(bild, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));

        titel = new TextView(this);
        titel.setText(artName());
        titel.setTextSize(13f);
        titel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titel.setTextColor(artFarbe());
        titel.setGravity(Gravity.CENTER);
        titel.setLetterSpacing(0.06f);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        tp.topMargin = Stil.dp(this, 34);
        wurzel.addView(titel, tp);

        unten = new TextView(this);
        unten.setText("Sensor wird vorbereitet…");
        unten.setTextSize(10.5f);
        unten.setTextColor(Stil.TEXT_SCHWACH);
        unten.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams up = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        up.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        up.bottomMargin = Stil.dp(this, 30);
        up.leftMargin = up.rightMargin = Stil.dp(this, 26);
        wurzel.addView(unten, up);

        setContentView(wurzel);

        // Bild und Text kommen herein statt einfach dazustehen
        bild.setAlpha(0f);
        bild.setScaleX(0.86f); bild.setScaleY(0.86f);
        bild.animate().alpha(1f).scaleX(1f).scaleY(1f)
                .setDuration(Stil.GROSS).setInterpolator(Stil.ein()).start();

        SensorService.live = hoerer;

        Intent start = new Intent(this, SensorService.class)
                .setAction(SensorService.ACTION_START).putExtra("art", art);
        if (getIntent() != null && getIntent().hasExtra("geschlecht"))
            start.putExtra("geschlecht", getIntent().getIntExtra("geschlecht", 0));
        startForegroundService(start);
    }

    /**
     * Uebergang vom laufenden Bild zur Auswertung: das Bild schrumpft und
     * verblasst, die Auswertung kommt darunter hervor. Ein harter Schnitt
     * wuerde den Zusammenhang zerreissen - man soll sehen, dass aus DIESER
     * Messung DIESES Ergebnis wurde.
     */
    private void zeigeErgebnis() {
        if (ergebnisGezeigt) return;
        SensorService.Messung m = SensorService.letzte;
        if (m == null) return;
        ergebnisGezeigt = true;
        SensorService.live = null;

        bild.setzeFortschritt(1f);
        bild.animate().alpha(0f).scaleX(1.12f).scaleY(1.12f)
                .setDuration(Stil.GROSS).setInterpolator(Stil.aus())
                .withEndAction(() -> {
                    // Aus dem Baum nehmen, nicht nur unsichtbar machen: erst
                    // onDetachedFromWindow haelt den 60-Hz-Taktgeber an. Ein
                    // unsichtbares Bild, das weiter zeichnet, kostet genauso
                    // viel Strom wie ein sichtbares.
                    wurzel.removeView(bild);
                    bild = null;
                    baueErgebnis(m);
                }).start();
        titel.animate().alpha(0f).setDuration(Stil.WECHSEL).start();
        unten.animate().alpha(0f).setDuration(Stil.WECHSEL).start();
    }

    private void baueErgebnis(SensorService.Messung m) {
        roller = new KurvenListe(this);
        roller.setBackgroundColor(Stil.GRUND);

        spalte = new LinearLayout(this);
        spalte.setOrientation(LinearLayout.VERTICAL);
        spalte.setGravity(Gravity.CENTER_HORIZONTAL);
        spalte.setClipChildren(false);
        int rand = Stil.dp(this, 18);
        spalte.setPadding(rand, Stil.dp(this, 40), rand, Stil.dp(this, 58));

        spalte.addView(text(artName(), 12.5f, artFarbe(), Gravity.CENTER, true));
        spalte.addView(luft(8));

        boolean ersterWert = true;
        for (Map.Entry<String, String> e : m.werte.entrySet()) {
            if (ersterWert && m.erfolg) {
                TextView gross = text(e.getValue(), 40f, Stil.TEXT_STARK, Gravity.CENTER, false);
                gross.setTypeface(Typeface.create("sans-serif-light", Typeface.NORMAL));
                spalte.addView(gross);
                spalte.addView(text(e.getKey(), 10.5f, Stil.TEXT_SCHWACH, Gravity.CENTER, false));
                spalte.addView(luft(12));
                ersterWert = false;
            } else {
                spalte.addView(zeile(e.getKey(), e.getValue()));
            }
        }

        if (SensorService.TEMP.equals(art)) {
            spalte.addView(luft(4));
            spalte.addView(abweichungsKarte(m));
        }

        if (m.abstaende != null && m.abstaende.size() > 5) {
            spalte.addView(luft(6));
            spalte.addView(streubild(m));
            spalte.addView(text("jeder Schlagabstand gegen den nächsten",
                    8.5f, Stil.TEXT_LEISE, Gravity.CENTER, false));
        }

        if (m.rhythmus != null && m.rhythmus.schlaege > 0) {
            spalte.addView(luft(10));
            spalte.addView(kasten(m.rhythmus.titel, m.rhythmus.text, Stil.befund(m.rhythmus.urteil)));
        } else if (m.meldung != null && !m.meldung.isEmpty()) {
            spalte.addView(luft(10));
            spalte.addView(kasten(m.erfolg ? "Hinweis" : "Nicht auswertbar",
                    m.meldung, m.erfolg ? Stil.TEXT_LEISE : Stil.BEFUND_AUFFAELLIG));
        }

        spalte.addView(luft(12));
        spalte.addView(text("Herold ist kein Medizinprodukt und stellt keine Diagnose. "
                + "Bei Beschwerden bitte ärztlichen Rat einholen.",
                9f, Stil.TEXT_LEISE, Gravity.LEFT, false));

        roller.addView(spalte, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        wurzel.addView(roller, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        Rad.anBinden(roller);
        Rad.fokusHolen(roller);
        roller.post(roller::auftritt);
    }

    // ---- Bausteine -----------------------------------------------------

    /**
     * Abweichung vom persoenlichen Grundwert statt einer Koerpertemperatur.
     * Solange der Grundwert fehlt oder die Messung nicht vergleichbar ist,
     * steht hier der Grund - nie eine Zahl, die etwas anderes vorgibt.
     */
    private View abweichungsKarte(SensorService.Messung m) {
        float haut = Grundwert.zahl(m.werte.get("Haut"));
        float geh  = Grundwert.zahl(m.werte.get("Umgebung"));
        Grundwert g = Grundwert.bilde(this);
        String grund = Float.isNaN(haut) ? "keine Hautmessung" : Grundwert.pruefe(haut, Float.isNaN(geh) ? haut : geh);

        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setGravity(Gravity.CENTER_HORIZONTAL);
        int p = Stil.dp(this, 12);
        k.setPadding(p, p, p, p);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(Stil.FLAECHE_01);
        hg.setCornerRadius(Stil.dp(this, Stil.ECKE_KASTEN));
        k.setBackground(hg);
        k.addView(text("ABWEICHUNG VOM GRUNDWERT", 9.5f, Stil.TEXT_SCHWACH, Gravity.CENTER, true));

        if (grund != null) {
            k.addView(text(grund, 10.5f, Stil.TEXT_MITTEL, Gravity.CENTER, false));
        } else if (Float.isNaN(g.wert)) {
            k.addView(text("wird noch gebildet (" + g.naechte + "/" + Grundwert.NAECHTE_MIN + " Nächte)",
                    10.5f, Stil.TEXT_MITTEL, Gravity.CENTER, false));
            k.addView(text("aus Messungen zwischen 1 und 6 Uhr", 9f, Stil.TEXT_LEISE, Gravity.CENTER, false));
        } else {
            float d = haut - g.wert;
            AbweichungsView av = new AbweichungsView(this, d);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, Stil.dp(this, 34));
            k.addView(av, lp);
            k.addView(text(String.format("%+.1f °C gegenüber deinen letzten %d Nächten", d, g.naechte),
                    10f, Stil.TEXT_SCHWACH, Gravity.CENTER, false));
        }
        k.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return k;
    }

    /** Skalenbalken -1,0 .. +1,0 mit Marker. Kein Gruen, kein Rot - nur die Kennfarbe. */
    private static class AbweichungsView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float d;
        AbweichungsView(Context c, float d) { super(c); this.d = Math.max(-1f, Math.min(1f, d)); }
        @Override protected void onDraw(Canvas c) {
            float dp = getResources().getDisplayMetrics().density;
            float b = getWidth(), y = getHeight() / 2f, x0 = 10 * dp, x1 = b - 10 * dp;
            p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeWidth(4 * dp); p.setColor(Stil.FLAECHE_02);
            c.drawLine(x0, y, x1, y, p);
            p.setStrokeWidth(1.5f * dp); p.setColor(Stil.RAND_DEUTLICH);
            float mitte = (x0 + x1) / 2f;
            c.drawLine(mitte, y - 6 * dp, mitte, y + 6 * dp, p);
            p.setStyle(Paint.Style.FILL); p.setColor(Stil.TEMPERATUR);
            float x = mitte + d * (x1 - x0) / 2f;
            c.drawCircle(x, y, 5 * dp, p);
        }
    }

    private View streubild(SensorService.Messung m) {
        PoincareView p = new PoincareView(this);
        p.setzeFarbe(m.rhythmus == null ? Stil.BEFUND_GLEICHMAESSIG : Stil.befund(m.rhythmus.urteil));
        int gr = Stil.dp(this, 116);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(gr, gr);
        lp.gravity = Gravity.CENTER_HORIZONTAL;
        p.setLayoutParams(lp);
        p.zeige(m.abstaende, true);
        return p;
    }

    private TextView text(String t, float g, int farbe, int lage, boolean fett) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(g); v.setTextColor(farbe); v.setGravity(lage);
        if (fett) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    private View luft(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Stil.dp(this, h)));
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
        z.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
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
        k.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return k;
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(empfang, new IntentFilter(SensorService.ACTION_ZUSTAND),
                Context.RECEIVER_NOT_EXPORTED);
        if (!ergebnisGezeigt) SensorService.live = hoerer;
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(empfang); } catch (Exception ignored) {}
        // Bildschirm weg -> Bild anhalten. Die Messung selbst laeuft im Dienst weiter.
        if (bild != null) bild.anhalten();
    }

    @Override protected void onStart() {
        super.onStart();
        if (bild != null && !ergebnisGezeigt) bild.weiter();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        SensorService.live = null;
        if (!ergebnisGezeigt) {
            startService(new Intent(this, SensorService.class).setAction(SensorService.ACTION_STOP));
        }
    }
}
