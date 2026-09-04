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

        // Echte Messung: 18 Jahre, Ruhe, deutliche Atemarrhythmie.
        // Darf unter keinen Umständen als unregelmäßig gelten.
        int[] real = {1024,1041,1092,968,1070,1096,954,962,1149,1170,1000,1105,
            1194,970,1024,1143,951,1059,1182,1149,1027,1147,1176,965,959,1042,
            1026,915,1069,1174,993,1076,1120,985,966,1118,1037,893,1036,1099,
            972,1084,1155,1048,919,1086,1146,971,1080,1150,1041,962,1123,1152,977,1080};
        List<Integer> echt = new ArrayList<>();
        for (int v : real) echt.add(v);
        zeig("ECHT: 18 J., Atmung", echt);

        // Zweite echte Messung derselben Person, unruhiger aufgezeichnet.
        int[] real2 = {1070,1016,1022,1104,1096,935,1042,1087,949,1042,1157,998,
            1066,1257,1146,899,919,1103,1122,926,970,1075,1185,946,1072,1203,
            1011,1045,1145,980,1105,1171,963,1110,1248,1247,1222,1256,1088,1233,
            1120,1082,1211,1118,976,1085,1121,972,1060,1102};
        List<Integer> echt2 = new ArrayList<>();
        for (int v : real2) echt2.add(v);
        zeig("ECHT: dieselbe, unruhig", echt2);

        List<Integer> extra = new ArrayList<>(gleich);
        extra.set(10, 620); extra.set(11, 1380);
        extra.set(25, 640); extra.set(26, 1360);
        zeig("mit 2 Extraschlaegen", extra);
    }
}
