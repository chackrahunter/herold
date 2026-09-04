package de.doncalvin.herold;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Bewertet die Regelmaessigkeit des Herzschlags aus den Abstaenden zwischen
 * den Schlaegen (RR- bzw. IBI-Werte in Millisekunden).
 *
 * Der schwierige Teil ist nicht, Unregelmaessigkeit zu messen - der schwierige
 * Teil ist, sie von der normalen Atemarrhythmie zu unterscheiden. Bei jungen
 * Menschen schwankt der Puls mit der Atmung um 20 % und mehr; ein Verfahren,
 * das nur auf die Streuung schaut, wuerde dabei staendig Alarm schlagen.
 *
 * Der Unterschied liegt in der Struktur:
 *   - Atemarrhythmie schwankt gleichmaessig mit dem Atemrhythmus. Aufeinander
 *     folgende Abstaende aehneln sich, die Reihe ist positiv autokorreliert.
 *   - Vorhofflimmern ist "unregelmaessig unregelmaessig". Jeder Abstand ist vom
 *     vorigen weitgehend unabhaengig, die Autokorrelation liegt nahe null und
 *     die Entropie der Verteilung ist hoch.
 *
 * Das Ergebnis beschreibt nur, wie gleichmaessig gemessen wurde. Es nennt
 * bewusst keine Krankheitsbilder und spricht nie eine Entwarnung aus:
 * Vorhofflimmern verlangt laut Leitlinie zwei Nachweise - unregelmaessige
 * Abstaende UND fehlende P-Wellen - und aus Schlagabstaenden allein ist nur
 * die eine Haelfte pruefbar. Schwach ausgepraegte Stoerungen entgehen einer
 * atmungsfest eingestellten Schwelle zu grossen Teilen, und zeitweise
 * auftretende Stoerungen koennen waehrend der Messung schlicht pausieren.
 * Eine falsche Beruhigung waere hier schaedlicher als ein Fehlalarm.
 */
public class RhythmusAnalyse {

    /** Mindestzahl brauchbarer Abstaende fuer eine belastbare Aussage. */
    private static final int MINDEST_ABSTAENDE = 30;

    public static final int REGELMAESSIG = 0;
    public static final int UNREGELMAESSIG = 1;
    public static final int ATEMARRHYTHMIE = 2;
    public static final int ZU_WENIG = 3;
    public static final int UNSICHER = 4;

    public static class Ergebnis {
        public int urteil = ZU_WENIG;
        public int schlaege;          // verwendbare Abstaende
        public int verworfen;         // als Extraschlag/Stoerung aussortiert
        public float puls;            // mittlere Frequenz in 1/min
        public float rmssd;           // ms
        public float streuung;        // Variationskoeffizient, 0..1
        public float pnn50;           // Anteil Abstandssprünge > 50 ms, in %
        public float entropie;        // 0..1, normierte Shannon-Entropie
        public float sd1, sd2;        // Poincare-Achsen in ms
        public float kopplung;        // 0 = ohne Muster, 1 = glatte Welle
        public float spektral;        // Anteil der staerksten Schwingung, 0..1
        public float atemPeriode;     // Schlaege je Atemzug, 0 wenn unklar
        public float atemzuege;       // Atemzuege je Minute, 0 wenn unklar
        public String titel = "";
        public String text = "";
    }

    public static Ergebnis pruefe(List<Integer> abstaende) {
        Ergebnis e = new Ergebnis();
        if (abstaende == null) return zuWenig(e);

        // 1. Grobfilter: alles ausserhalb 30-200/min ist Messfehler.
        List<Float> roh = new ArrayList<>();
        for (Integer a : abstaende) {
            if (a != null && a >= 300 && a <= 2000) roh.add((float) (int) a);
        }
        if (roh.size() < 5) return zuWenig(e);

        // 2. Extraschlaege aussortieren. Eine Extrasystole erzeugt einen kurzen
        //    Abstand gefolgt von einer ausgleichenden Pause. Beide Werte sind
        //    echt, aber sie beschreiben keinen unregelmaessigen Grundrhythmus -
        //    wer sie drin laesst, misst bei jedem Stolperer Vorhofflimmern.
        float median = median(roh);
        boolean[] weg = new boolean[roh.size()];
        for (int i = 0; i + 1 < roh.size(); i++) {
            float kurz = roh.get(i), lang = roh.get(i + 1);
            // Kennzeichen einer Extrasystole: ein zu kurzer Schlag, danach eine
            // Pause, und beide zusammen ergeben wieder zwei normale Abstaende.
            boolean zuKurz = kurz < 0.85f * median;
            boolean danachPause = lang > 1.15f * median;
            boolean gleichtSichAus = Math.abs(kurz + lang - 2f * median) < 0.30f * median;
            if (zuKurz && danachPause && gleichtSichAus) {
                weg[i] = true; weg[i + 1] = true; i++;
            }
        }
        List<Float> rr = new ArrayList<>();
        for (int i = 0; i < roh.size(); i++) {
            if (!weg[i]) rr.add(roh.get(i)); else e.verworfen++;
        }
        e.schlaege = rr.size();
        if (rr.size() < MINDEST_ABSTAENDE) return zuWenig(e);

        // 3. Kennzahlen
        float mittel = mittel(rr);
        e.puls = 60000f / mittel;

        float summeQuadrate = 0f;
        int ueber50 = 0;
        for (int i = 1; i < rr.size(); i++) {
            float d = rr.get(i) - rr.get(i - 1);
            summeQuadrate += d * d;
            if (Math.abs(d) > 50f) ueber50++;
        }
        int paare = rr.size() - 1;
        e.rmssd = (float) Math.sqrt(summeQuadrate / paare);
        e.pnn50 = 100f * ueber50 / paare;

        float varianz = 0f;
        for (float v : rr) varianz += (v - mittel) * (v - mittel);
        float sd = (float) Math.sqrt(varianz / rr.size());
        e.streuung = sd / mittel;

        // Poincare: SD1 quer zur Diagonalen (kurzfristig), SD2 laengs (langfristig)
        e.sd1 = e.rmssd / (float) Math.sqrt(2.0);
        float sd2q = 2f * sd * sd - 0.5f * e.rmssd * e.rmssd;
        e.sd2 = sd2q > 0 ? (float) Math.sqrt(sd2q) : 0f;

        e.entropie = entropie(rr);
        e.kopplung = muster(rr, mittel);
        float[] sp = spektralAnteil(rr, mittel);
        e.spektral = sp[0];
        e.atemPeriode = sp[1];

        urteile(e);

        // Die Atemfrequenz ergibt sich aus Puls und Atemperiode. Sie wird nur
        // ausgewiesen, wenn ueberhaupt ein Muster gefunden wurde und das
        // Ergebnis im menschenmoeglichen Bereich liegt - sonst hat die
        // gefundene Schwingung mit Atmung nichts zu tun.
        // Bei unregelmaessigem Schlag wird nichts ausgewiesen: die Atmung
        // laesst sich aus Schlagabstaenden nur ablesen, weil der Sinusknoten
        // ihr folgt. Faellt dieser Zusammenhang weg, ist die gefundene
        // Schwingung Zufall, egal wie deutlich sie aussieht.
        if (e.atemPeriode > 1.5f && e.urteil != UNREGELMAESSIG) {
            float proMinute = e.puls / e.atemPeriode;
            if (proMinute >= 6f && proMinute <= 30f) e.atemzuege = proMinute;
        }
        return e;
    }

    /**
     * Normierte Shannon-Entropie ueber die Spruenge zwischen aufeinander
     * folgenden Abstaenden - auf einer festen Millisekunden-Skala, nicht auf
     * die eigene Streuung normiert. Sonst kommt bei jeder Glockenkurve derselbe
     * Wert heraus, egal wie gleichmaessig der Puls tatsaechlich war.
     *
     * Gleichmaessiger Schlag haeuft alle Spruenge im mittleren Fach (nahe 0),
     * Flimmern verteilt sie ueber die ganze Breite (nahe 1).
     */
    private static float entropie(List<Float> rr) {
        final int faecher = 16;
        final float spanne = 400f;              // -200 bis +200 ms
        int[] h = new int[faecher];
        float breite = spanne / faecher;
        for (int i = 1; i < rr.size(); i++) {
            float d = rr.get(i) - rr.get(i - 1);
            int f = (int) ((d + spanne / 2f) / breite);
            if (f < 0) f = 0;
            if (f >= faecher) f = faecher - 1;
            h[f]++;
        }
        int n = rr.size() - 1;
        double s = 0.0;
        for (int c : h) {
            if (c == 0) continue;
            double p = (double) c / n;
            s -= p * (Math.log(p) / Math.log(2));
        }
        return (float) (s / (Math.log(faecher) / Math.log(2)));
    }

    /**
     * Wie gut laesst sich ein Abstand aus seinen beiden Nachbarn vorhersagen?
     *
     * Das ist der eigentliche Unterscheider. Atemarrhythmie ist eine weiche
     * Welle - der mittlere Wert liegt ungefaehr zwischen seinen Nachbarn, der
     * Rest nach Abzug dieser Schaetzung ist klein. Vorhofflimmern ist von
     * Schlag zu Schlag unabhaengig, da hilft der Nachbar nicht weiter.
     *
     * Der Nenner 1,5 ist so gewaehlt, dass reines Rauschen genau 0 ergibt:
     * bei unabhaengigen Werten ist die Streuung des Restes das 1,5-fache der
     * Streuung der Reihe selbst.
     *
     * Anders als die Autokorrelation bei Verschiebung 1 haengt das kaum davon
     * ab, wie viele Schlaege auf einen Atemzug kommen.
     */
    private static float muster(List<Float> rr, float mittel) {
        // Die Atmung braucht je nach Puls drei bis acht Schlaege fuer einen Zug.
        // Wird nur der direkte Nachbar befragt, faellt das Mass bei kurzen
        // Atemperioden in sich zusammen - eine Welle mit Periode drei sieht
        // vom Nachbarn aus wie Rauschen. Darum wird jeder Abstand mit dem
        // Partner in Abstand d verglichen, d von 1 bis 8, und die staerkste
        // gefundene Regelmaessigkeit gewinnt: bei d gleich der Atemperiode
        // stehen beide Partner an derselben Stelle der Welle.
        double gesamt = 0.0;
        for (float v : rr) { double a = v - mittel; gesamt += a * a; }
        if (gesamt < 1e-6) return 0f;
        double gesamtJeWert = gesamt / rr.size();

        float beste = -1f;
        int maxD = Math.min(8, (rr.size() - 1) / 3);
        for (int d = 1; d <= maxD; d++) {
            double rest = 0.0; int zahl = 0;
            for (int i = d; i + d < rr.size(); i++) {
                double schaetzung = (rr.get(i - d) + rr.get(i + d)) / 2.0;
                double x = rr.get(i) - schaetzung;
                rest += x * x; zahl++;
            }
            if (zahl < 6) break;
            float w = (float) (1.0 - (rest / zahl) / (1.5 * gesamtJeWert));
            if (w > beste) beste = w;
        }
        return beste;
    }

    /**
     * Anteil der Schwankung, den die staerkste einzelne Schwingung erklaert.
     *
     * Atmung erzeugt eine Schwingung mit einer bestimmten Laenge - deren
     * Anteil ist gross. Bei Vorhofflimmern verteilt sich die Schwankung
     * gleichmaessig auf alle Schwingungslaengen, kein Anteil ragt heraus.
     *
     * Gemessen an je 300 nachgebildeten Reihen: gleichmaessig 0,14,
     * Atemarrhythmie 0,74, Flimmern 0,14 (Mittelwerte). An einer echten
     * Messung mit deutlicher Atemarrhythmie: 0,30.
     */
    private static float[] spektralAnteil(List<Float> rr, float mittel) {
        int n = rr.size();
        double gesamt = 0.0;
        double[] x = new double[n];
        for (int i = 0; i < n; i++) { x[i] = rr.get(i) - mittel; gesamt += x[i] * x[i]; }
        if (gesamt < 1e-6) return new float[]{0f, 0f};
        int m = n / 2;
        double[] leistung = new double[m + 2];
        for (int k = 1; k <= m; k++) {
            double re = 0.0, im = 0.0;
            for (int i = 0; i < n; i++) {
                double w = 2 * Math.PI * k * i / n;
                re += x[i] * Math.cos(w);
                im += x[i] * Math.sin(w);
            }
            leistung[k] = 2 * (re * re + im * im) / n;
        }
        // Nicht die einzelne staerkste Frequenz, sondern sie mit ihren beiden
        // Nachbarn. Die Atemfrequenz ist nicht konstant - ueber zweieinhalb
        // Minuten wandert sie, und die Schwingung verteilt sich dadurch auf
        // benachbarte Frequenzen. Wer nur die Spitze misst, verliert sie
        // ausgerechnet bei den laengeren Aufnahmen.
        double beste = 0.0;
        int besterK = 0;
        for (int k = 1; k <= m; k++) {
            double summe = leistung[k]
                    + (k > 1 ? leistung[k - 1] : 0.0)
                    + (k < m ? leistung[k + 1] : 0.0);
            if (summe > beste) { beste = summe; besterK = k; }
        }
        // Die Lage der Spitze verraet, wie viele Schlaege auf eine Schwingung
        // kommen. Ist diese Schwingung die Atmung, ist das die Atemperiode.
        // Genauer als das blosse Fach wird es, wenn man die Nachbarn mit
        // ihrem Gewicht einbezieht (Schwerpunkt der drei Faecher).
        // --- Atemfrequenz: eigene, strengere Suche ---------------------
        // Fuer das Rhythmusurteil zaehlt, wie viel Anteil die staerkste
        // Schwingung am Ganzen hat. Fuer die Atemfrequenz zaehlt etwas
        // anderes: ob sich im Atembereich ueberhaupt eine Schwingung vom
        // Rauschen abhebt. Eine Reihe ohne jede Ordnung hat auch eine
        // staerkste Frequenz - sie bedeutet nur nichts.
        double[] sortiert = new double[m];
        System.arraycopy(leistung, 1, sortiert, 0, m);
        java.util.Arrays.sort(sortiert);
        double rauschen = sortiert[m / 2];          // Median als Grundrauschen

        int vonK = Math.max(1, (int) Math.ceil(n / 12.0));   // bis 12 Schlaege je Atemzug
        int bisK = Math.min(m, (int) Math.floor(n / 2.0));   // ab 2 Schlaegen
        int atemK = 0; double atemBeste = 0.0;
        for (int k = vonK; k <= bisK; k++) {
            if (leistung[k] > atemBeste) { atemBeste = leistung[k]; atemK = k; }
        }
        float periode = 0f;
        // Der Gipfel muss das Grundrauschen deutlich ueberragen, sonst ist er
        // Zufall. Der Faktor 5 ist an den Testreihen abgelesen.
        if (atemK > 0 && rauschen > 0 && atemBeste / rauschen > 5.0) {
            double links  = atemK > 1 ? leistung[atemK - 1] : 0.0;
            double rechts = atemK < m ? leistung[atemK + 1] : 0.0;
            double summe = links + leistung[atemK] + rechts;
            double schwerpunkt = summe > 0 ? atemK + (rechts - links) / summe : atemK;
            if (schwerpunkt > 0.5) periode = (float) (n / schwerpunkt);
        }
        return new float[]{ (float) (beste / gesamt), periode };
    }

    private static Ergebnis urteile(Ergebnis e) {
        // Die Schwellen sind nicht geschaetzt, sondern an je 400 nachgebildeten
        // Messungen dreier Rhythmusarten abgelesen - mit wandernder statt fester
        // Atemfrequenz, sonst faellt die Nachbildung zu leicht aus. Die
        // Bandleistung liegt bei Atemarrhythmie ab 0,57, bei musterlosen Reihen
        // bis 0,45; dazwischen wird nichts behauptet.
        boolean schwankt = e.streuung > 0.08f && e.pnn50 > 25f;
        boolean chaotisch = e.entropie > 0.65f;
        // Zwei voneinander unabhaengige Wege, ein Muster zu finden. Einzeln
        // kippt jeder von beiden bei Messrauschen - an einer echten Messung
        // lagen sie bei 0,30 und 0,64, beide knapp an der Grenze. Darum: ein deutliches Signal genuegt, sonst muessen beide
        // schwaecheren zusammen dafuer sprechen.
        boolean hatMuster = e.spektral > 0.55f
                         || e.kopplung > 0.75f
                         || (e.spektral > 0.48f && e.kopplung > 0.50f);
        // Fehlendes Muster allein genuegt nicht fuer eine Meldung. Zwischen
        // "klar rhythmisch" und "klar musterlos" liegt ein Bereich, in dem
        // beide Masse nichts Belastbares hergeben - zwei echte Messungen
        // landeten dort einmal knapp darueber und einmal knapp darunter. Dort wird nichts behauptet.
        boolean klarOhneMuster = e.spektral < 0.42f && e.kopplung < 0.40f;

        if (schwankt && chaotisch && klarOhneMuster) {
            e.urteil = UNREGELMAESSIG;
            e.titel = "Unregelmäßig";
            e.text = "Die Abstände zwischen den Schlägen schwankten stark und "
                   + "ohne erkennbares Muster. Dafür gibt es viele mögliche "
                   + "Gründe, harmlose wie behandlungsbedürftige — welcher hier "
                   + "zutrifft, kann diese Messung nicht sagen. Zeigen Sie das "
                   + "Ergebnis bei Gelegenheit einer Ärztin oder einem Arzt, "
                   + "und bei Schwindel, Luftnot oder anhaltendem Herzstolpern "
                   + "zeitnah.";
        } else if (schwankt && chaotisch && !hatMuster) {
            e.urteil = UNSICHER;
            e.titel = "Nicht sicher beurteilbar";
            e.text = "Die Abstände schwankten deutlich, aber die Messung reicht "
                   + "nicht aus, um zu unterscheiden, ob das der Atmung folgt "
                   + "oder nicht. Bitte in Ruhe wiederholen: sitzen, Arm "
                   + "auflegen, ruhig atmen und nicht sprechen.";
        } else if (schwankt && hatMuster) {
            e.urteil = ATEMARRHYTHMIE;
            e.titel = "Gleichmäßig, atemabhängig";
            e.text = "Der Puls schwankte im Takt der Atmung — ein regelmäßiges "
                   + "Muster, das bei jüngeren Menschen besonders deutlich ist. "
                   + "Eine einzelne Messung über wenige Minuten kann "
                   + "Rhythmusstörungen nicht ausschließen.";
        } else {
            e.urteil = REGELMAESSIG;
            e.titel = "Gleichmäßig";
            e.text = "Gleichmäßiger Schlag während dieser Messung. Eine "
                   + "einzelne kurze Aufnahme kann Rhythmusstörungen nicht "
                   + "ausschließen — Störungen treten oft nur zeitweise auf.";
        }
        if (e.verworfen > 0) {
            e.text += "\n\n" + e.verworfen + " einzelne Schläge fielen aus dem "
                    + "Takt und wurden nicht mitgerechnet.";
        }
        return e;
    }

    private static Ergebnis zuWenig(Ergebnis e) {
        e.urteil = ZU_WENIG;
        e.titel = "Zu wenig Daten";
        e.text = "Für eine Aussage werden mindestens " + MINDEST_ABSTAENDE
               + " saubere Herzschläge gebraucht. Bitte ruhig sitzen, den Arm "
               + "auflegen und die Messung wiederholen.";
        return e;
    }

    private static float mittel(List<Float> v) {
        float s = 0f;
        for (float x : v) s += x;
        return s / v.size();
    }

    private static float median(List<Float> v) {
        List<Float> k = new ArrayList<>(v);
        Collections.sort(k);
        return k.get(k.size() / 2);
    }

    /** Aus Peak-Zeitpunkten (Sekunden) die Abstaende in Millisekunden bilden. */
    public static List<Integer> ausPeaks(List<Float> sekunden) {
        List<Integer> a = new ArrayList<>();
        for (int i = 1; i < sekunden.size(); i++) {
            a.add(Math.round((sekunden.get(i) - sekunden.get(i - 1)) * 1000f));
        }
        return a;
    }
}
