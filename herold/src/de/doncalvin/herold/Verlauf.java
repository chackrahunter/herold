package de.doncalvin.herold;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Gedaechtnis fuer frueher gemessene Werte.
 *
 * Eine Zeile je Messung, Felder durch Tabulator getrennt. Kein JSON und keine
 * Datenbank - ohne AndroidX gaebe es weder Gson noch Room, und fuer ein paar
 * tausend Zeilen waere beides ohnehin zu viel Apparat.
 *
 * Die Datei liegt im app-eigenen Verzeichnis auf der Uhr und wird nirgendwohin
 * uebertragen.
 */
public class Verlauf {

    private static final String DATEI = "verlauf.tsv";
    private static final int HOECHSTENS = 2000;

    public static class Eintrag {
        public long zeit;
        public String art;        // EKG, PULS, SPO2, TEMP, BIA
        public String wert;       // Hauptwert mit Einheit, z.B. "62 /min"
        public String zusatz;     // kurze Ergaenzung, z.B. "Gleichmäßig"

        public String zeitText() {
            return new SimpleDateFormat("d.M. HH:mm", Locale.GERMANY).format(new Date(zeit));
        }

        public String artName() {
            switch (art) {
                case "EKG":  return "EKG";
                case "PULS": return "Puls & Rhythmus";
                case "SPO2": return "Sauerstoff";
                case "TEMP": return "Hauttemperatur";
                case "BIA":  return "Körperanalyse";
                case "ATEM": return "Atemfrequenz";
                default:     return art;
            }
        }

        public int farbe() {
            switch (art) {
                case "EKG":  return Stil.EKG;
                case "PULS": return Stil.PULS;
                case "SPO2": return Stil.SAUERSTOFF;
                case "TEMP": return Stil.TEMPERATUR;
                case "BIA":  return Stil.KOERPER;
                case "ATEM": return 0xFF56C8D8;
                default:     return Stil.TEXT_SCHWACH;
            }
        }
    }

    private final File datei;

    public Verlauf(Context c) {
        datei = new File(c.getExternalFilesDir(null), DATEI);
    }

    /** Haengt eine Messung an. Fehler hier duerfen die Messung nicht stoeren. */
    public void schreibe(String art, String wert, String zusatz) {
        try (PrintWriter p = new PrintWriter(new FileWriter(datei, true))) {
            p.println(System.currentTimeMillis() + "\t" + art + "\t"
                    + sauber(wert) + "\t" + sauber(zusatz));
        } catch (Exception e) {
            Log.w("HeroldVerlauf", "konnte nicht schreiben", e);
        }
    }

    /** Neueste zuerst. */
    public List<Eintrag> lies() {
        List<Eintrag> alle = new ArrayList<>();
        if (!datei.exists()) return alle;
        try (BufferedReader r = new BufferedReader(new FileReader(datei))) {
            String z;
            while ((z = r.readLine()) != null) {
                String[] f = z.split("\t", -1);
                if (f.length < 3) continue;
                try {
                    Eintrag e = new Eintrag();
                    e.zeit = Long.parseLong(f[0]);
                    e.art = f[1];
                    e.wert = f[2];
                    e.zusatz = f.length > 3 ? f[3] : "";
                    alle.add(e);
                } catch (NumberFormatException ignored) {}
            }
        } catch (Exception e) {
            Log.w("HeroldVerlauf", "konnte nicht lesen", e);
        }
        Collections.reverse(alle);
        if (alle.size() > HOECHSTENS) return alle.subList(0, HOECHSTENS);
        return alle;
    }

    /** Nur eine Messart, neueste zuerst - fuer Verlaufskurven. */
    public List<Eintrag> lies(String art) {
        List<Eintrag> gefiltert = new ArrayList<>();
        for (Eintrag e : lies()) if (art.equals(e.art)) gefiltert.add(e);
        return gefiltert;
    }

    /** Entfernt eine Messung anhand ihres Zeitstempels. */
    public void loesche(long zeit) {
        List<Eintrag> alle = lies();
        try (PrintWriter p = new PrintWriter(new FileWriter(datei, false))) {
            for (int i = alle.size() - 1; i >= 0; i--) {   // lies() liefert neueste zuerst
                Eintrag e = alle.get(i);
                if (e.zeit == zeit) continue;
                p.println(e.zeit + "\t" + e.art + "\t" + sauber(e.wert) + "\t" + sauber(e.zusatz));
            }
        } catch (Exception ex) {
            Log.w("HeroldVerlauf", "konnte nicht loeschen", ex);
        }
    }

    /** Schreibt eine Messung mit vorgegebenem Zeitstempel - derselbe wie im Detail. */
    public void schreibe(long zeit, String art, String wert, String zusatz) {
        try (PrintWriter p = new PrintWriter(new FileWriter(datei, true))) {
            p.println(zeit + "\t" + art + "\t" + sauber(wert) + "\t" + sauber(zusatz));
        } catch (Exception e) {
            Log.w("HeroldVerlauf", "konnte nicht schreiben", e);
        }
    }

    private static String sauber(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').trim();
    }
}
