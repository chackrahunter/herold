package de.doncalvin.herold;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.PowerManager;
import android.util.Log;

import com.samsung.android.service.health.tracking.ConnectionListener;
import com.samsung.android.service.health.tracking.HealthTracker;
import com.samsung.android.service.health.tracking.HealthTrackerException;
import com.samsung.android.service.health.tracking.HealthTrackingService;
import com.samsung.android.service.health.tracking.data.DataPoint;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;
import com.samsung.android.service.health.tracking.data.TrackerUserProfile;
import com.samsung.android.service.health.tracking.data.ValueKey;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ein Dienst fuer alle Messungen ausser dem EKG: Sauerstoffsaettigung,
 * Hauttemperatur und Puls mit Schlagabstaenden.
 *
 * Gemessen wird im Dienst und nicht in der Anzeige, damit ein Tastendruck oder
 * ein dunkel werdendes Display die Messung nicht abbricht.
 *
 * Die Statuscodes der Sensoren sind von Samsung nicht als Konstanten
 * veroeffentlicht. Statt sie zu raten sammelt der Dienst alle Werte ein und
 * nimmt am Ende die gueltigen - und schreibt jeden gesehenen Status ins Log,
 * damit sich das Verhalten an echten Messungen nachvollziehen laesst.
 */
public class SensorService extends Service {

    public static final String ACTION_START   = "de.doncalvin.herold.MESSEN";
    public static final String ACTION_STOP    = "de.doncalvin.herold.MESSEN_STOP";
    public static final String ACTION_ZUSTAND = "de.doncalvin.herold.MESSZUSTAND";

    public static final String SPO2 = "SPO2";
    public static final String TEMP = "TEMP";
    public static final String PULS = "PULS";
    public static final String BIA  = "BIA";

    private static final String CH = "herold_messung";
    private static final int NOTIF = 0xE7;

    /**
     * Direkter Draht zur Anzeige. Messwerte kommen mit bis zu 100 Hz - ueber
     * Broadcasts gedrosselt saehe die Anzeige aus, als haenge sie. Dienst und
     * Anzeige laufen im selben Prozess, also geht der Wert direkt durch.
     */
    public interface LiveHoerer {
        void pulse(int[] rr, int anzahl);
        void sauerstoff(int prozent);
        /** Kommen Werte an, sind aber unbrauchbar (Uhr locker, Bewegung)? */
        void signal(boolean brauchbar);
    }
    public static volatile LiveHoerer live;

    /** Letztes Ergebnis, vom Ergebnisschirm gelesen. */
    public static volatile Messung letzte;

    public static class Messung {
        public String art = "";
        public String titel = "";
        public boolean erfolg;
        public String meldung = "";
        public long zeit;
        /** Beschriftung -> Wert, in Anzeigereihenfolge. */
        public final Map<String, String> werte = new LinkedHashMap<>();
        public RhythmusAnalyse.Ergebnis rhythmus;   // nur bei PULS
        public List<Integer> abstaende;             // nur bei PULS, fuer das Streubild
    }

    private HealthTrackingService dienst;
    private HealthTracker tracker;
    private PowerManager.WakeLock wach;
    private final Handler uhr = new Handler(Looper.getMainLooper());

    private String art = SPO2;
    private long beginn;
    private long gestartet;
    private int dauerMs = 30000;
    private boolean beendet;

    // Sammelbehaelter
    private final List<Integer> spo2Werte = new ArrayList<>();
    private final List<Float> tempObjekt = new ArrayList<>();
    private final List<Float> tempUmgebung = new ArrayList<>();
    private final List<Integer> abstaende = new ArrayList<>();
    private final List<Integer> pulsWerte = new ArrayList<>();
    private final Map<String, Float> koerper = new LinkedHashMap<>();
    private int biaStatus = Integer.MIN_VALUE;
    private float biaFortschritt = 0f;
    private long fehlerSeit = 0;
    private int geschlechtCode = Integer.MIN_VALUE;
    private boolean hintergrund;       // vom Planer gestartet: keine Anzeige, kurz, still
    private boolean flushAngefordert;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        if (i != null && ACTION_STOP.equals(i.getAction())) { aufhoeren(false, "abgebrochen"); return START_NOT_STICKY; }
        if (i != null && i.getStringExtra("art") != null) art = i.getStringExtra("art");
        if (i != null && i.hasExtra("geschlecht")) geschlechtCode = i.getIntExtra("geschlecht", 0);
        hintergrund = i != null && i.getBooleanExtra("hintergrund", false);
        boolean kurz = i != null && i.getBooleanExtra("kurz", false);

        // 150 s statt 65 s: gemessen an je 400 nachgebildeten Reihen steigt die
        // Trefferquote fuer unregelmaessige Rhythmen von 38 % (rund 40 Abstaende)
        // auf 96 % (rund 90) bzw. 100 % (rund 160) - bei unveraendert null
        // Fehlalarmen. Kuerzer messen heisst hier vor allem: haeufiger nichts
        // sagen koennen.
        dauerMs = PULS.equals(art) ? 150000 : TEMP.equals(art) ? 12000
                : BIA.equals(art) ? 40000 : 35000;
        // Im Hintergrund reicht fuer den Puls ein kurzes Fenster; die lange
        // Rhythmusmessung laeuft nur zu den festen Zeiten des Planers.
        if (hintergrund && kurz && PULS.equals(art)) dauerMs = 30000;

        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CH, "Messung", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIF, hinweis("Messung wird vorbereitet"));

        PowerManager pm = getSystemService(PowerManager.class);
        wach = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Herold:messung");
        wach.acquire(3 * 60 * 1000L);

        gestartet = System.currentTimeMillis();
        melde("VERBINDEN", 0);
        // Erst nachsehen, ob die Uhr ueberhaupt am Handgelenk ist. Sonst misst
        // sie die Ladeschale und legt die Zahl als Messwert ab.
        Traegt.frage(this, 1500, new Traegt.Antwort() {
            @Override public void ergebnis(boolean getragen) {
                if (beendet) return;
                if (!getragen) {
                    aufhoeren(false, "Die Uhr wird gerade nicht getragen. "
                            + "Bitte anlegen und noch einmal messen.");
                    return;
                }
                dienst = new HealthTrackingService(verbindung, SensorService.this);
                dienst.connectService();
            }
        });
        return START_NOT_STICKY;
    }

    private final ConnectionListener verbindung = new ConnectionListener() {
        @Override public void onConnectionSuccess() {
            try {
                tracker = holeTracker();
                tracker.setEventListener(ereignisse);
                beginn = System.currentTimeMillis();
                melde("MESSEN", 0);
                uhr.postDelayed(ablauf, 500);
            } catch (Throwable t) {
                Log.e("HeroldSensor", "Tracker nicht verfuegbar", t);
                aufhoeren(false, "Sensor nicht verfügbar");
            }
        }
        @Override public void onConnectionEnded() {}
        @Override public void onConnectionFailed(HealthTrackerException e) {
            aufhoeren(false, "Health Platform nicht erreichbar (" + e.getErrorCode() + ")");
        }
    };

    private HealthTrackerType typ() {
        if (SPO2.equals(art)) return HealthTrackerType.SPO2_ON_DEMAND;
        if (TEMP.equals(art)) return HealthTrackerType.SKIN_TEMPERATURE_ON_DEMAND;
        if (BIA.equals(art))  return HealthTrackerType.BIA_ON_DEMAND;
        return HealthTrackerType.HEART_RATE_CONTINUOUS;
    }

    /** Die Bioimpedanz braucht Koerperdaten, sonst laesst sich Widerstand
     *  nicht in Fett und Muskeln umrechnen. */
    private HealthTracker holeTracker() {
        if (!BIA.equals(art)) return dienst.getHealthTracker(typ());
        Profil pr = new Profil(this);
        int g = geschlechtCode != Integer.MIN_VALUE ? geschlechtCode : pr.geschlecht();
        TrackerUserProfile up = new TrackerUserProfile.Builder()
                .setHeight(pr.groesseCm())
                .setWeight(pr.gewichtKg())
                .setAge(pr.alter())
                .setGender(g)
                .build();
        Log.i("HeroldSensor", "BIA-Profil groesse=" + pr.groesseCm() + " gewicht="
                + pr.gewichtKg() + " alter=" + pr.alter() + " geschlecht=" + g);
        return dienst.getHealthTracker(HealthTrackerType.BIA_ON_DEMAND, up);
    }

    /** Tickt jede halbe Sekunde, meldet den Fortschritt und beendet die Messung. */
    private final Runnable ablauf = new Runnable() {
        @Override public void run() {
            if (beendet) return;
            int vergangen = (int) (System.currentTimeMillis() - beginn);
            if (vergangen >= dauerMs) {
                // Bei ausgeschaltetem Bildschirm haelt die Health Platform die
                // Werte bis zu zehn Minuten zurueck. flush() holt sie sofort;
                // ausgewertet wird erst in onFlushCompleted - oder nach zwei
                // Sekunden, falls der Sensor nichts zurueckgibt.
                if (!flushAngefordert && tracker != null) {
                    flushAngefordert = true;
                    try { tracker.flush(); } catch (Throwable t) { auswerten(); return; }
                    uhr.postDelayed(SensorService.this::auswerten, 2000);
                    return;
                }
                auswerten(); return;
            }
            // Bei der Koerperanalyse wartet die Uhr auf Hautkontakt; erst dann
            // laeuft die Zeit. Nach zwei Minuten ohne Kontakt wird abgebrochen.
            if (BIA.equals(art) && istBiaFehler(biaStatus)) {
                if (System.currentTimeMillis() - gestartet > 120000) {
                    aufhoeren(false, biaHinweis(biaStatus));
                    return;
                }
                melde("WARTEN", 0, biaHinweis(biaStatus));
                uhr.postDelayed(this, 500);
                return;
            }
            int anteil = BIA.equals(art) && biaFortschritt > 0f
                    ? Math.round(biaFortschritt * 100f)
                    : vergangen * 100 / dauerMs;
            melde("MESSEN", Math.min(99, anteil));
            uhr.postDelayed(this, 500);
        }
    };

    private final HealthTracker.TrackerEventListener ereignisse =
            new HealthTracker.TrackerEventListener() {
        @Override public void onDataReceived(List<DataPoint> punkte) {
            for (DataPoint p : punkte) {
                try { sammle(p); } catch (Throwable ignored) {}
            }
        }
        @Override public void onFlushCompleted() { uhr.post(SensorService.this::auswerten); }
        @Override public void onError(HealthTracker.TrackerError e) {
            Log.e("HeroldSensor", "Sensorfehler: " + e);
            aufhoeren(false, "Sensorfehler: " + e);
        }
    };

    private void sammle(DataPoint p) {   // laeuft im Sensor-Thread
        if (SPO2.equals(art)) {
            // Die Statuscodes stehen in keiner Dokumentation, sie sind an einer
            // echten Messung abgelesen: 0 = rechnet noch, 2 = gueltiger Wert,
            // -6 = kein brauchbares Signal mehr.
            Integer st = p.getValue(ValueKey.SpO2Set.STATUS);
            Integer v  = p.getValue(ValueKey.SpO2Set.SPO2);
            if (st != null && st == 2 && v != null && v >= 70 && v <= 100) {
                spo2Werte.add(v);
                LiveHoerer lh = live;
                if (lh != null && spo2Werte.size() % 10 == 0) lh.sauerstoff(median(spo2Werte));
                Integer hr = p.getValue(ValueKey.SpO2Set.HEART_RATE);
                if (hr != null && hr > 30 && hr < 220) pulsWerte.add(hr);
                // Sobald genug gueltige Werte da sind, muss niemand weiter
                // stillhalten - der Sensor liefert 100 Werte je Sekunde.
                if (spo2Werte.size() >= 300) uhr.post(this::auswerten);
            }

        } else if (TEMP.equals(art)) {
            Float o = p.getValue(ValueKey.SkinTemperatureSet.OBJECT_TEMPERATURE);
            Float u = p.getValue(ValueKey.SkinTemperatureSet.AMBIENT_TEMPERATURE);
            Log.i("HeroldSensor", "Temp objekt=" + o + " umgebung=" + u);
            if (o != null && o > 20f && o < 45f) tempObjekt.add(o);
            if (u != null && u > -20f && u < 60f) tempUmgebung.add(u);

        } else if (BIA.equals(art)) {
            Integer st = p.getValue(ValueKey.BiaSet.STATUS);
            if (st != null) biaStatus = st;
            merke(p, ValueKey.BiaSet.BODY_FAT_RATIO,        "fettAnteil");
            merke(p, ValueKey.BiaSet.BODY_FAT_MASS,         "fettMasse");
            merke(p, ValueKey.BiaSet.SKELETAL_MUSCLE_RATIO, "muskelAnteil");
            merke(p, ValueKey.BiaSet.SKELETAL_MUSCLE_MASS,  "muskelMasse");
            merke(p, ValueKey.BiaSet.TOTAL_BODY_WATER,      "wasser");
            merke(p, ValueKey.BiaSet.BASAL_METABOLIC_RATE,  "grundumsatz");
            merke(p, ValueKey.BiaSet.FAT_FREE_MASS,         "fettfrei");
            merke(p, ValueKey.BiaSet.BODY_IMPEDANCE_MAGNITUDE, "widerstand");
            Float fortschritt = p.getValue(ValueKey.BiaSet.PROGRESS);
            Log.i("HeroldSensor", "BIA status=" + st + " fortschritt=" + fortschritt
                    + " werte=" + koerper);
            // Ein Fehlerstatus waehrend der Messung ist normal - beim Anlegen
            // der Finger kommt regelmaessig 7, 8 oder 9. Samsung bricht erst
            // ab, wenn derselbe Fehler drei Sekunden am Stueck anliegt. Wer
            // beim ersten Fehlercode aufgibt, bekommt fast nie ein Ergebnis.
            if (st != null) {
                if (istBiaFehler(st)) {
                    if (fehlerSeit == 0) fehlerSeit = System.currentTimeMillis();
                    beginn = System.currentTimeMillis();   // Zeit laeuft nicht
                } else {
                    fehlerSeit = 0;
                }
            }
            // Nicht beim ersten Wert aufhoeren. Der Sensor liefert sofort eine
            // vorlaeufige Zahl und verfeinert sie danach - wer gleich abbricht,
            // nimmt die schlechteste. Erst wenn der Fortschritt voll ist, steht
            // das Ergebnis. Als Rueckfalle greift die Zeitgrenze.
            if (fortschritt != null) {
                biaFortschritt = fortschritt > 1.5f ? fortschritt / 100f : fortschritt;
                if (biaFortschritt >= 0.999f && koerper.containsKey("fettAnteil"))
                    uhr.post(this::auswerten);
            }

        } else {
            Integer hr = p.getValue(ValueKey.HeartRateSet.HEART_RATE);
            Integer hs = p.getValue(ValueKey.HeartRateSet.HEART_RATE_STATUS);
            // -3 heisst laut Samsung: Uhr wird nicht getragen. Dann sofort Schluss.
            if (hs != null && hs == -3) { uhr.post(() -> aufhoeren(false, "Uhr nicht getragen")); return; }
            if (hr != null && hr > 30 && hr < 220 && hs != null && hs == 1) pulsWerte.add(hr);

            List<Integer> ibi = p.getValue(ValueKey.HeartRateSet.IBI_LIST);
            List<Integer> ist = p.getValue(ValueKey.HeartRateSet.IBI_STATUS_LIST);
            if (ibi != null) {
                int vorher = abstaende.size();
                for (int k = 0; k < ibi.size(); k++) {
                    // Status 0 heisst brauchbar; alles andere waere geraten
                    boolean gut = ist == null || k >= ist.size() || ist.get(k) == 0;
                    if (gut && ibi.get(k) != null) abstaende.add(ibi.get(k));
                }
                LiveHoerer lh = live;
                // Der Sensor liefert, aber alles ist als Fehler markiert: das ist
                // eine lockere Uhr oder Bewegung, kein Defekt. Das soll die
                // Anzeige sagen, statt stumm auf "-" zu bleiben.
                if (lh != null && !ibi.isEmpty()) lh.signal(abstaende.size() > vorher);
                if (lh != null && abstaende.size() > vorher) {
                    int n = abstaende.size() - vorher;
                    int[] neu = new int[n];
                    for (int k = 0; k < n; k++) neu[k] = abstaende.get(vorher + k);
                    lh.pulse(neu, n);
                }
            }
        }
    }

    /**
     * Statuscodes der Bioimpedanzmessung. Sie stehen in Samsungs
     * API-Referenz; die Fehlermenge entspricht der, die Samsungs eigenes
     * Beispiel als Fehler behandelt.
     */
    private static boolean istBiaFehler(int st) {
        switch (st) {
            case 2: case 4: case 7: case 8: case 9: case 10:
            case 11: case 13: case 14: case 15: case 17: case 18:
                return true;
            default:
                return false;
        }
    }

    private static String biaHinweis(int st) {
        switch (st) {
            case 7:  return "Mittelfinger auf die obere Taste legen";
            case 8:  return "Ringfinger auf die untere Taste legen";
            case 9:  return "Beide Tasten gleichzeitig berühren";
            case 4:
            case 10: return "Uhr sitzt zu locker — Band enger stellen";
            case 15: return "Nur die Tasten berühren, nicht den Rahmen";
            case 2:  return "Sensorfehler — bitte noch einmal versuchen";
            default: return "Ruhig halten, Arme vom Körper weg";
        }
    }

    /** Nimmt einen Wert nur, wenn er wirklich geliefert wurde und nicht null ist. */
    private void merke(DataPoint p, ValueKey<Float> schluessel, String name) {
        try {
            Float v = p.getValue(schluessel);
            if (v != null && !v.isNaN() && v != 0f) koerper.put(name, v);
        } catch (Throwable ignored) {}
    }

    private void auswerten() {
        if (beendet) return;
        Messung m = new Messung();
        m.art = art;
        m.zeit = System.currentTimeMillis();

        if (SPO2.equals(art)) {
            m.titel = "Sauerstoffsättigung";
            if (spo2Werte.size() >= 3) {
                int med = median(spo2Werte);
                m.erfolg = true;
                m.werte.put("Sauerstoff", med + " %");
                if (!pulsWerte.isEmpty()) m.werte.put("Puls", median(pulsWerte) + " /min");
                m.meldung = med >= 95 ? "Im üblichen Bereich."
                          : med >= 90 ? "Etwas niedrig. Bei wiederholt niedrigen Werten ärztlich abklären."
                          : "Niedrig. Bitte wiederholen und bei Beschwerden ärztlichen Rat einholen.";
            } else {
                m.meldung = "Kein stabiles Signal. Uhr fester anlegen, Arm ruhig "
                          + "auf den Tisch legen und nicht sprechen.";
            }

        } else if (TEMP.equals(art)) {
            m.titel = "Hauttemperatur";
            if (!tempObjekt.isEmpty()) {
                m.erfolg = true;
                float h = medianF(tempObjekt);
                m.werte.put("Haut", String.format("%.1f °C", h));
                if (!tempUmgebung.isEmpty())
                    m.werte.put("Umgebung", String.format("%.1f °C", medianF(tempUmgebung)));
                m.meldung = "Hauttemperatur am Handgelenk. Sie liegt normalerweise "
                          + "deutlich unter der Körperkerntemperatur und hängt stark "
                          + "von der Umgebung ab — sie ersetzt kein Fieberthermometer.";
            } else {
                m.meldung = "Keine Messwerte. Uhr am Handgelenk tragen und ruhig halten.";
            }

        } else if (BIA.equals(art)) {
            m.titel = "Körperanalyse";
            Float fett = koerper.get("fettAnteil");
            if (fett != null && fett > 0f) {
                m.erfolg = true;
                m.werte.put("Körperfett", eins(fett) + " %");
                if (koerper.get("muskelAnteil") != null)
                    m.werte.put("Skelettmuskel", eins(koerper.get("muskelAnteil")) + " %");
                if (koerper.get("muskelMasse") != null)
                    m.werte.put("Muskelmasse", eins(koerper.get("muskelMasse")) + " kg");
                if (koerper.get("fettMasse") != null)
                    m.werte.put("Fettmasse", eins(koerper.get("fettMasse")) + " kg");
                if (koerper.get("wasser") != null)
                    m.werte.put("Körperwasser", eins(koerper.get("wasser")) + " l");
                if (koerper.get("fettfrei") != null)
                    m.werte.put("Fettfreie Masse", eins(koerper.get("fettfrei")) + " kg");
                if (koerper.get("grundumsatz") != null)
                    m.werte.put("Grundumsatz", Math.round(koerper.get("grundumsatz")) + " kcal");
                if (koerper.get("widerstand") != null)
                    m.werte.put("Widerstand", Math.round(koerper.get("widerstand")) + " Ω");
                m.meldung = "Gemessen wird der elektrische Widerstand zwischen den "
                          + "beiden Tasten; Fett und Muskeln rechnet die Uhr daraus "
                          + "mit Größe, Gewicht, Alter und Geschlecht hoch. Das "
                          + "Ergebnis schwankt mit Tageszeit, Trinkmenge und "
                          + "Hautfeuchte — für einen Verlauf immer zur selben "
                          + "Tageszeit messen.";
            } else {
                m.meldung = biaHinweis(biaStatus) + ".\n\nSo geht es: Uhr fest "
                          + "anlegen, beide Arme auf Brusthöhe halten, Achseln "
                          + "offen. Mit dem Mittelfinger der anderen Hand die "
                          + "obere Taste berühren, mit dem Ringfinger die untere. "
                          + "Den Gehäuserahmen dabei nicht anfassen und die Hände "
                          + "nicht aneinanderlegen.";
            }

        } else {
            m.titel = "Puls & Rhythmus";
            if (!pulsWerte.isEmpty()) {
                m.erfolg = true;
                m.werte.put("Puls", median(pulsWerte) + " /min");
            }
            m.rhythmus = RhythmusAnalyse.pruefe(abstaende);
            m.abstaende = new ArrayList<>(abstaende);
            if (m.rhythmus != null && m.rhythmus.atemzuege > 0f)
                m.werte.put("Atemzüge", Math.round(m.rhythmus.atemzuege) + " /min");
            m.werte.put("Schlagabstände", String.valueOf(abstaende.size()));
            if (m.rhythmus != null && m.rhythmus.schlaege > 0) {
                m.erfolg = true;
                m.werte.put("Schwankung", Math.round(m.rhythmus.streuung * 1000) / 10f + " %");
                m.werte.put("RMSSD", Math.round(m.rhythmus.rmssd) + " ms");
                m.werte.put("pNN50", Math.round(m.rhythmus.pnn50) + " %");
            }
            if (!m.erfolg) m.meldung = "Kein brauchbares Signal. Uhr fester anlegen und ruhig sitzen.";
        }

        letzte = m;
        if (m.erfolg && !m.werte.isEmpty()) {
            Verlauf v = new Verlauf(this);
            Map.Entry<String, String> erster = m.werte.entrySet().iterator().next();
            String zusatz = m.rhythmus != null ? m.rhythmus.titel : erster.getKey();
            if (hintergrund) zusatz = zusatz + " · automatisch";
            long stempel = m.zeit;
            v.schreibe(stempel, art, erster.getValue(), zusatz);
            // Alle Einzelwerte dazu, damit die Messung im Verlauf antippbar bleibt
            Messdetail det = new Messdetail(stempel, art);
            det.werte.putAll(m.werte);
            det.abstaende = m.abstaende;
            if (m.rhythmus != null) {
                det.befundTitel = m.rhythmus.titel; det.befundText = m.rhythmus.text;
                det.befundUrteil = m.rhythmus.urteil;
            } else if (m.meldung != null && !m.meldung.isEmpty()) {
                det.befundTitel = "Hinweis"; det.befundText = m.meldung;
            }
            det.schreibe(this);
            // Die Atemfrequenz faellt bei der Rhythmusmessung mit ab. Sie
            // bekommt einen eigenen Eintrag, damit sie eine eigene Kachel und
            // einen eigenen Verlauf haben kann.
            if (m.rhythmus != null && m.rhythmus.atemzuege > 0f) {
                v.schreibe("ATEM", Math.round(m.rhythmus.atemzuege) + " /min", "in Ruhe");
            }
            KachelBasis.auffrischen(this);
        }
        Log.i("HeroldSensor", "Ergebnis " + art + " erfolg=" + m.erfolg + " " + m.werte);
        if (PULS.equals(art)) {
            Log.i("HeroldSensor", "IBI roh " + abstaende);
            if (m.rhythmus != null) Log.i("HeroldSensor", "Rhythmus " + m.rhythmus.titel
                    + " spektral=" + String.format("%.2f", m.rhythmus.spektral)
                    + " entropie=" + String.format("%.2f", m.rhythmus.entropie)
                    + " muster=" + String.format("%.2f", m.rhythmus.kopplung)
                    + " verworfen=" + m.rhythmus.verworfen);
        }
        aufhoeren(true, m.meldung);
    }

    private void aufhoeren(boolean fertig, String meldung) {
        if (beendet) return;
        beendet = true;
        uhr.removeCallbacksAndMessages(null);
        try { if (tracker != null) tracker.unsetEventListener(); } catch (Exception ignored) {}
        try { if (dienst != null) dienst.disconnectService(); } catch (Exception ignored) {}
        melde(fertig ? "FERTIG" : "FEHLER", 100, meldung);
        stopForeground(true);
        stopSelf();
    }

    private void melde(String phase, int fortschritt) { melde(phase, fortschritt, null); }

    private void melde(String phase, int fortschritt, String meldung) {
        sendBroadcast(new Intent(ACTION_ZUSTAND).setPackage(getPackageName())
                .putExtra("phase", phase)
                .putExtra("art", art)
                .putExtra("fortschritt", fortschritt)
                .putExtra("meldung", meldung));
    }

    private Notification hinweis(String text) {
        return new Notification.Builder(this, CH)
                .setSmallIcon(R.drawable.herold_icon)
                .setContentTitle("Herold")
                .setContentText(text)
                .setOngoing(true)
                .build();
    }

    @Override public void onDestroy() {
        uhr.removeCallbacksAndMessages(null);
        try { if (wach != null && wach.isHeld()) wach.release(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    private static int median(List<Integer> v) {
        List<Integer> k = new ArrayList<>(v);
        java.util.Collections.sort(k);
        return k.get(k.size() / 2);
    }

    private static String eins(float f) { return String.format("%.1f", f); }

    private static float medianF(List<Float> v) {
        List<Float> k = new ArrayList<>(v);
        java.util.Collections.sort(k);
        return k.get(k.size() / 2);
    }
}
