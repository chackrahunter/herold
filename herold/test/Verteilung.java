import de.doncalvin.herold.RhythmusAnalyse;
import java.util.*;

/** Wie gut trennen die Kennzahlen wirklich? 400 Durchläufe je Rhythmusart. */
public class Verteilung {

    static List<Integer> gleich(Random r, int n) {
        List<Integer> v = new ArrayList<>();
        int grund = 800 + r.nextInt(400);
        for (int i = 0; i < n; i++) v.add(grund + (int) (r.nextGaussian() * 12));
        return v;
    }

    /** Atemarrhythmie: Welle mit 3..8 Schlägen je Atemzug, Tiefe 8..30 %. */
    static List<Integer> atem(Random r, int n) {
        List<Integer> v = new ArrayList<>();
        int grund = 800 + r.nextInt(400);
        double periode = 3.0 + r.nextDouble() * 5.0;
        double tiefe = grund * (0.08 + r.nextDouble() * 0.22);
        double phase = r.nextDouble() * Math.PI * 2;
        // Echtes Atmen haelt keinen festen Takt. Ohne diese Wanderung ist die
        // Nachbildung zu leicht - eine reine Sinuswelle laesst sich viel
        // einfacher als rhythmisch erkennen als echte Messdaten.
        for (int i = 0; i < n; i++) {
            periode += r.nextGaussian() * 0.06;
            periode = Math.max(2.6, Math.min(9.0, periode));
            phase += 2 * Math.PI / periode;
            v.add((int) (grund + tiefe * Math.sin(phase) + r.nextGaussian() * 25));
        }
        return v;
    }

    /** Vorhofflimmern: jeder Abstand unabhängig, Streuung 10..25 %. */
    static List<Integer> flimmern(Random r, int n) {
        List<Integer> v = new ArrayList<>();
        int grund = 600 + r.nextInt(400);
        double streu = grund * (0.10 + r.nextDouble() * 0.15);
        for (int i = 0; i < n; i++) {
            int x = (int) (grund + r.nextGaussian() * streu);
            v.add(Math.max(320, Math.min(1900, x)));
        }
        return v;
    }

    static void lauf(String name, int art) {
        Random r = new Random(art * 7919 + 13);
        int[] zaehler = new int[5];
        double[] kopp = new double[400];
        double[] entr = new double[400];
        double[] spek = new double[400];
        for (int t = 0; t < 400; t++) {
            int n = 32 + r.nextInt(20);
            List<Integer> rr = art == 0 ? gleich(r, n) : art == 1 ? atem(r, n) : flimmern(r, n);
            RhythmusAnalyse.Ergebnis e = RhythmusAnalyse.pruefe(rr);
            if (e.urteil < 5) zaehler[e.urteil]++;
            kopp[t] = e.kopplung;
            spek[t] = e.spektral;
            entr[t] = e.entropie;
        }
        Arrays.sort(kopp); Arrays.sort(entr); Arrays.sort(spek);
        System.out.printf("%-22s regelm=%3d unregelm=%3d atem=%3d zuwenig=%3d | "
                + "unsicher=%3d | spektral p5=%.2f p50=%.2f p95=%.2f%n",
                name, zaehler[0], zaehler[1], zaehler[2], zaehler[3],
                zaehler[4], spek[20], spek[200], spek[380]);
    }

    public static void main(String[] a) {
        lauf("gleichmäßig", 0);
        lauf("Atemarrhythmie", 1);
        lauf("Vorhofflimmern", 2);
    }
}
