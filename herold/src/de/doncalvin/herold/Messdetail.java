package de.doncalvin.herold;

import android.content.Context;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Alle Einzelwerte einer Messung, damit man sie im Verlauf spaeter wieder
 * ansehen kann - nicht nur die eine Zahl der Verlaufszeile.
 *
 * Eine Datei je Messung, benannt nach dem Zeitstempel, Inhalt als
 * Schluessel=Wert-Zeilen. Schlagabstaende stehen als kommagetrennte Liste
 * unter "abstaende", damit das Streubild spaeter neu gezeichnet werden kann.
 */
public class Messdetail {

    private static final String ORDNER = "messungen";

    public final long zeit;
    public final String art;
    public final Map<String, String> werte = new LinkedHashMap<>();
    public List<Integer> abstaende;
    public String befundTitel, befundText;
    public int befundUrteil = -1;

    public Messdetail(long zeit, String art) { this.zeit = zeit; this.art = art; }

    private static File datei(Context c, long zeit) {
        File d = new File(c.getExternalFilesDir(null), ORDNER);
        if (!d.exists()) d.mkdirs();
        return new File(d, zeit + ".txt");
    }

    public void schreibe(Context c) {
        try (PrintWriter p = new PrintWriter(new FileWriter(datei(c, zeit), false))) {
            p.println("art=" + art);
            for (Map.Entry<String, String> e : werte.entrySet())
                p.println("w:" + sauber(e.getKey()) + "=" + sauber(e.getValue()));
            if (abstaende != null && !abstaende.isEmpty()) {
                StringBuilder b = new StringBuilder();
                for (Integer a : abstaende) { if (b.length() > 0) b.append(','); b.append(a); }
                p.println("abstaende=" + b);
            }
            if (befundTitel != null) p.println("befundTitel=" + sauber(befundTitel));
            if (befundText != null)  p.println("befundText=" + sauber(befundText));
            if (befundUrteil >= 0)   p.println("befundUrteil=" + befundUrteil);
        } catch (Exception e) {
            Log.w("HeroldDetail", "konnte Detail nicht schreiben", e);
        }
    }

    /** null, wenn zu dieser Messung kein Detail vorliegt (aeltere Eintraege). */
    public static Messdetail lies(Context c, long zeit) {
        File f = datei(c, zeit);
        if (!f.exists()) return null;
        Messdetail m = null;
        try (BufferedReader r = new BufferedReader(new FileReader(f))) {
            String z; String art = "";
            List<String[]> zeilen = new ArrayList<>();
            while ((z = r.readLine()) != null) {
                int i = z.indexOf('=');
                if (i < 0) continue;
                String k = z.substring(0, i), v = z.substring(i + 1);
                if (k.equals("art")) art = v; else zeilen.add(new String[]{k, v});
            }
            m = new Messdetail(zeit, art);
            for (String[] kv : zeilen) {
                String k = kv[0], v = kv[1];
                if (k.startsWith("w:")) m.werte.put(k.substring(2), v);
                else if (k.equals("abstaende")) {
                    m.abstaende = new ArrayList<>();
                    for (String s : v.split(",")) {
                        try { m.abstaende.add(Integer.parseInt(s.trim())); } catch (NumberFormatException ignored) {}
                    }
                }
                else if (k.equals("befundTitel")) m.befundTitel = v;
                else if (k.equals("befundText"))  m.befundText = v.replace("\\n", "\n");
                else if (k.equals("befundUrteil")) { try { m.befundUrteil = Integer.parseInt(v); } catch (NumberFormatException ignored) {} }
            }
        } catch (Exception e) {
            Log.w("HeroldDetail", "konnte Detail nicht lesen", e);
        }
        return m;
    }

    public static void loesche(Context c, long zeit) {
        try { datei(c, zeit).delete(); } catch (Exception ignored) {}
    }

    private static String sauber(String s) {
        return s == null ? "" : s.replace("\n", "\\n").replace("\r", "");
    }
}
