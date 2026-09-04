package de.doncalvin.herold;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Wertet eine fertige EKG-Aufzeichnung aus.
 *
 * Bewusst NACH der Messung statt live: Die Schwelle fuer die Schlagerkennung
 * muss aus dem gesamten Fenster stammen. Live gerechnet wird sie von den
 * Einschwing-Artefakten am Anfang zerstoert - genau daran ist der erste
 * Versuch gescheitert.
 */
public class EkgAuswertung {

    public static final float FS = 500f;

    public int    puls;            // Schlaege pro Minute
    public int    schlaege;        // erkannte R-Zacken
    public float  amplitude;       // Spitze-Spitze in mV
    public float  streuungMs;      // Schwankung der Abstaende
    public String guete;           // Einschaetzung in Worten
    public float[] kurve;          // gefiltert, fuer die Anzeige
    public java.util.List<Integer> abstaende;   // R-zu-R in Millisekunden
    public RhythmusAnalyse.Ergebnis rhythmus;   // null, wenn die Guete nicht reicht
    /** Pulsankunftszeit R-Zacke -> Fuss der PPG-Welle, Median in ms; 0 wenn nicht bestimmbar. */
    public int patMs, patVon, patBis, patN;

    public static EkgAuswertung rechne(float[] roh, int anzahl) { return rechne(roh, null, anzahl); }

    public static EkgAuswertung rechne(float[] roh, int[] ppg, int anzahl) {
        EkgAuswertung e = new EkgAuswertung();
        if (anzahl < (int) (5 * FS)) { e.guete = "zu kurz"; return e; }

        // 1) Grundlinie abziehen (Hochpass ~0,3 Hz)
        float[] hp = new float[anzahl];
        float basis = roh[0];
        for (int i = 0; i < anzahl; i++) {
            basis += (roh[i] - basis) * 0.004f;
            hp[i] = roh[i] - basis;
        }
        // Einschwingen des Filters verwerfen
        int start = (int) FS;
        int n = anzahl - start;
        if (n < (int) (4 * FS)) { e.guete = "zu kurz"; return e; }
        float[] s = new float[n];
        System.arraycopy(hp, start, s, 0, n);
        e.kurve = s;

        float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
        for (float v : s) { lo = Math.min(lo, v); hi = Math.max(hi, v); }
        e.amplitude = hi - lo;

        // 2) Bandpass 5-25 Hz: dort sitzt die QRS-Energie. Grundliniendrift und
        //    T-Welle fallen weg - beides hat den Detektor vorher genarrt.
        float[] bp = new float[n];
        float tief = s[0];
        for (int i = 0; i < n; i++) { tief += (s[i] - tief) * 0.06f; bp[i] = s[i] - tief; }
        int gl = 8;
        float[] band = new float[n];
        float lauf0 = 0;
        for (int i = 0; i < n; i++) {
            lauf0 += bp[i];
            if (i >= gl) lauf0 -= bp[i - gl];
            band[i] = lauf0 / Math.min(i + 1, gl);
        }

        // 3) Huellkurve: ein Buckel je Herzschlag
        float[] huelle = new float[n];
        int Wh = (int) (0.05f * FS);
        float lauf1 = 0;
        for (int i = 0; i < n; i++) {
            lauf1 += band[i] * band[i];
            if (i >= Wh) lauf1 -= band[i - Wh] * band[i - Wh];
            huelle[i] = lauf1 / Math.min(i + 1, Wh);
        }
        float mit = 0; for (float v : huelle) mit += v; mit /= n;
        for (int i = 0; i < n; i++) huelle[i] -= mit;

        // 4) Autokorrelation findet die Grundperiode - robuster als Zackenzaehlen,
        //    weil einzelne verpasste oder doppelte Spitzen sie kaum verschieben.
        int lagMin = (int) (FS * 60 / 180), lagMax = (int) (FS * 60 / 40);
        float besteKorr = -Float.MAX_VALUE; int besterLag = 0;
        for (int lag = lagMin; lag < Math.min(lagMax, n / 2); lag += 2) {
            float k = 0;
            for (int i = 0; i + lag < n; i += 3) k += huelle[i] * huelle[i + lag];
            if (k > besteKorr) { besteKorr = k; besterLag = lag; }
        }
        if (besterLag == 0) { e.guete = "kein Rhythmus erkennbar"; return e; }
        float periode = besterLag / FS;

        // Schaerfe des Gipfels als Guetemass: ein verrauschtes Signal hat keinen
        float nebenMax = 0;
        for (int lag = lagMin; lag < Math.min(lagMax, n / 2); lag += 2) {
            if (Math.abs(lag - besterLag) < besterLag * 0.25f) continue;
            float k = 0;
            for (int i = 0; i + lag < n; i += 3) k += huelle[i] * huelle[i + lag];
            nebenMax = Math.max(nebenMax, k);
        }
        float schaerfe = besteKorr > 0 ? (besteKorr - Math.max(0, nebenMax)) / besteKorr : 0;

        // 5) Spitzen suchen, Sperrzeit aus der gefundenen Periode abgeleitet
        java.util.List<Integer> peaks = new java.util.ArrayList<>();
        float[] sortiert = huelle.clone();
        java.util.Arrays.sort(sortiert);
        float schwelle = sortiert[(int) (n * 0.90f)];
        int refr = (int) (periode * 0.6f * FS);
        int sperre = 0;
        for (int i = 1; i < n - 1; i++) {
            if (sperre > 0) { sperre--; continue; }
            if (huelle[i] > schwelle && huelle[i] >= huelle[i-1] && huelle[i] > huelle[i+1]) {
                peaks.add(i); sperre = refr;
            }
        }
        e.schlaege = peaks.size();
        e.puls = Math.round(60f / periode);

        // Abstaende zwischen den R-Zacken in Millisekunden. Sie dienen sowohl
        // der Guetebewertung als auch der Rhythmuspruefung weiter unten.
        e.abstaende = new java.util.ArrayList<>();
        for (int i = 0; i < peaks.size() - 1; i++) {
            e.abstaende.add(Math.round((peaks.get(i+1) - peaks.get(i)) * 1000f / FS));
        }
        if (peaks.size() > 3) {
            java.util.List<Float> rr = new java.util.ArrayList<>();
            for (int i = 0; i < peaks.size() - 1; i++) {
                float t = (peaks.get(i+1) - peaks.get(i)) / FS;
                if (t > periode * 0.5f && t < periode * 1.8f) rr.add(t);
            }
            if (rr.size() > 2) {
                float m2 = 0; for (float v : rr) m2 += v; m2 /= rr.size();
                float va = 0; for (float v : rr) va += (v - m2) * (v - m2);
                e.streuungMs = (float) Math.sqrt(va / rr.size()) * 1000f;
            }
        }

        android.util.Log.i("HeroldEkg", String.format(
                "Guete: Schaerfe %.2f, Amplitude %.2f mV, Streuung %.0f ms, Puls %d",
                schaerfe, e.amplitude, e.streuungMs, e.puls));

        // 6) Ehrliche Guetebewertung - lieber nichts anzeigen als etwas Falsches
        if (e.amplitude > 8f) {
            e.guete = "zu unruhig - bitte wiederholen"; e.puls = 0;
        } else if (schaerfe < 0.10f) {
            e.guete = "Signal zu schwach"; e.puls = 0;
        } else if (schaerfe < 0.20f) {
            e.guete = "unsicher";
        } else if (e.streuungMs > 200f) {
            e.guete = "brauchbar";
        } else {
            e.guete = "gut";
        }

        // 7) Rhythmus nur pruefen, wenn das Signal es hergibt. Aus einer
        //    verrauschten Kurve laesst sich keine Aussage ueber Vorhofflimmern
        //    ableiten - falsch erkannte Zacken sehen genauso aus wie Chaos.
        // 8) Pulsankunftszeit: von jeder R-Zacke bis zum Fuss der PPG-Welle
        //    (Minimum vor dem Anstieg, gesucht in den 420 ms danach). Beide
        //    Kanaele stammen aus demselben Datenpunkt, es gibt keinen Versatz.
        //    Das ist eine Laufzeit in Millisekunden - kein Blutdruck.
        if (ppg != null && e.puls > 0 && schaerfe >= 0.20f) {
            java.util.List<Integer> pats = new java.util.ArrayList<>();
            for (int r : peaks) {
                int b = Math.min(n - 1, r + (int) (0.42f * FS));
                int jmin = -1; int vmin = Integer.MAX_VALUE; int gefunden = 0;
                for (int j = r; j <= b; j++) {
                    if (ppg[j] < 0) continue;
                    gefunden++;
                    if (ppg[j] < vmin) { vmin = ppg[j]; jmin = j; }
                }
                if (jmin < 0 || gefunden < 6) continue;
                int vmax = Integer.MIN_VALUE;
                for (int j = jmin + 1; j <= b; j++) if (ppg[j] >= 0 && ppg[j] > vmax) vmax = ppg[j];
                if (vmax == Integer.MIN_VALUE || vmax - vmin < 3) continue;   // kein Anstieg -> kein Fuss
                int pat = Math.round((jmin - r) * 1000f / FS);
                if (pat >= 120 && pat <= 450) pats.add(pat);
            }
            if (pats.size() >= 5) {
                java.util.Collections.sort(pats);
                e.patN = pats.size();
                e.patMs = pats.get(pats.size() / 2);
                e.patVon = pats.get(pats.size() / 4);
                e.patBis = pats.get(3 * pats.size() / 4);
                android.util.Log.i("HeroldEkg", "PAT median=" + e.patMs + " ms IQR=" + e.patVon + "-" + e.patBis + " n=" + e.patN);
            }
        }

        if (e.puls > 0 && schaerfe >= 0.20f) {
            e.rhythmus = RhythmusAnalyse.pruefe(e.abstaende);
            android.util.Log.i("HeroldEkg", "Rhythmus: " + e.rhythmus.titel
                    + " streuung=" + String.format("%.3f", e.rhythmus.streuung)
                    + " pnn50=" + Math.round(e.rhythmus.pnn50)
                    + " entropie=" + String.format("%.2f", e.rhythmus.entropie)
                    + " kopplung=" + String.format("%.2f", e.rhythmus.kopplung)
                    + " n=" + e.rhythmus.schlaege);
        }
        return e;
    }
}
