import de.doncalvin.herold.RhythmusAnalyse;
import java.util.*;

public class RhythmusEinzelfaelle {
    static Random r = new Random(42);

    static void zeig(String was, List<Integer> rr) {
        RhythmusAnalyse.Ergebnis e = RhythmusAnalyse.pruefe(rr);
        System.out.printf("%-26s -> %-16s puls=%3.0f streu=%.3f pnn50=%2.0f%% entr=%.2f kopp=%+.2f spektral=%.2f atem=%4.1f/min n=%d%n",
            was, e.titel, e.puls, e.streuung, e.pnn50, e.entropie, e.kopplung,
            e.spektral, e.atemzuege, e.schlaege);
    }

    public static void main(String[] a) {
        List<Integer> gleich = new ArrayList<>();
        for (int i = 0; i < 40; i++) gleich.add(1000 + (int) (r.nextGaussian() * 8));
        zeig("gleichmaessig", gleich);

        List<Integer> atem = new ArrayList<>();
        for (int i = 0; i < 40; i++)
            atem.add((int) (950 + 150 * Math.sin(2 * Math.PI * i / 5.0) + r.nextGaussian() * 15));
        zeig("Atemarrhythmie (jung)", atem);

        List<Integer> atem2 = new ArrayList<>();
        for (int i = 0; i < 40; i++)
            atem2.add((int) (900 + 220 * Math.sin(2 * Math.PI * i / 6.0) + r.nextGaussian() * 25));
        zeig("starke Atemarrhythmie", atem2);

        List<Integer> flimmern = new ArrayList<>();
        for (int i = 0; i < 40; i++) flimmern.add(500 + r.nextInt(600));
        zeig("Vorhofflimmern", flimmern);

        List<Integer> fl2 = new ArrayList<>();
        for (int i = 0; i < 40; i++) fl2.add((int) (750 + r.nextGaussian() * 140));
        zeig("Vorhofflimmern (fein)", fl2);

        // Nachgebildet: Ruhe, deutliche Atemarrhythmie (Verteilung.atem()).
        // Darf unter keinen Umständen als unregelmäßig gelten.
        int[] real = {703,886,1006,879,728,845,1048,854,793,884,947,829,
            777,931,948,772,819,1014,872,749,912,969,800,776,
            915,928,732,803,960,918,736,863,984,859,777,935,
            903,695,866,979,792,774,893,944,736,855,962,786,
            816,981,842,738,901,900,717,859};
        List<Integer> echt = new ArrayList<>();
        for (int v : real) echt.add(v);
        zeig("Atemarrhythmie, ruhig", echt);

        // Zweite nachgebildete Reihe, unruhiger: staerkere Wanderung.
        int[] real2 = {630,947,983,643,729,1074,754,665,1029,900,625,859,
            1025,686,729,1025,912,651,874,1033,720,709,1077,793,
            612,919,972,663,785,1039,814,611,917,1005,677,750,
            1025,807,664,936,943,620,833,1039,720,733,1072,789,
            648,982};
        List<Integer> echt2 = new ArrayList<>();
        for (int v : real2) echt2.add(v);
        zeig("Atemarrhythmie, unruhig", echt2);

        List<Integer> extra = new ArrayList<>(gleich);
        extra.set(10, 620); extra.set(11, 1380);
        extra.set(25, 640); extra.set(26, 1360);
        zeig("mit 2 Extraschlaegen", extra);
    }
}
