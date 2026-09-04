package de.doncalvin.herold;

import android.content.Context;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.List;

/**
 * Persoenlicher Grundwert der Hauttemperatur - so machen es Apple, Oura,
 * Whoop und Samsung: keine Koerpertemperatur als Zahl, sondern die Abweichung
 * vom eigenen Nachtmittel.
 *
 * Warum kein Absolutwert: der "Umgebungs"-Wert des Sensors ist die
 * Gehaeusetemperatur des Chips, nicht die Raumluft; jedes Waermefluss-Modell
 * rechnet damit Unsinn. Und bei Fieberanstieg wird die Handgelenkshaut
 * KAELTER, weil sich die Gefaesse verengen - eine Hautformel zeigt dann
 * faelschlich "normal". Ein "kein Fieber" ist aus Haut nicht ableitbar.
 */
public class Grundwert {

    public static final int NAECHTE_MIN = 3;

    public final float wert;        // Median der Nachtwerte, NaN wenn zu wenig
    public final int naechte;       // wie viele Naechte liegen vor

    private Grundwert(float w, int n) { wert = w; naechte = n; }

    /** Nachtmessungen (01-06 Uhr) der letzten 7 Tage aus dem Verlauf. */
    public static Grundwert bilde(Context c) {
        List<Float> werte = new ArrayList<>();
        long grenze = System.currentTimeMillis() - 7L * 24 * 3600 * 1000;
        Calendar k = Calendar.getInstance();
        for (Verlauf.Eintrag e : new Verlauf(c).lies("TEMP")) {
            if (e.zeit < grenze) continue;
            k.setTimeInMillis(e.zeit);
            int h = k.get(Calendar.HOUR_OF_DAY);
            if (h < 1 || h >= 6) continue;
            float v = zahl(e.wert);
            if (v >= 30f && v <= 40f) werte.add(v);
        }
        if (werte.size() < NAECHTE_MIN) return new Grundwert(Float.NaN, werte.size());
        Collections.sort(werte);
        return new Grundwert(werte.get(werte.size() / 2), werte.size());
    }

    /**
     * Ist die aktuelle Messung ueberhaupt vergleichbar? Sonst wird keine
     * Abweichung gezeigt, sondern der Grund.
     */
    public static String pruefe(float haut, float gehaeuse) {
        if (haut < 30f)                       return "Handgelenk kühl — Wert nicht vergleichbar";
        if (gehaeuse >= 39f)                  return "Uhr ist warm (Laden, Display) — später messen";
        if (Math.abs(gehaeuse - haut) > 2.5f) return "Uhr noch nicht eingeschwungen";
        return null;
    }

    static float zahl(String s) {
        if (s == null) return Float.NaN;
        StringBuilder z = new StringBuilder();
        for (char ch : s.toCharArray()) {
            if (Character.isDigit(ch)) z.append(ch);
            else if ((ch == ',' || ch == '.') && z.length() > 0) z.append('.');
            else if (z.length() > 0) break;
        }
        try { return Float.parseFloat(z.toString()); } catch (Exception e) { return Float.NaN; }
    }
}
