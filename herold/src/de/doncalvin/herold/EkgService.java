package de.doncalvin.herold;

import android.app.*;
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
import com.samsung.android.service.health.tracking.data.ValueKey;

import java.util.List;
import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;

/**
 * Fuehrt die EKG-Messung im Hintergrund durch.
 *
 * Grund: Die obere Taste der Uhr ist zugleich die Elektrode UND die
 * Ein-/Aus-Taste. Eine App kann KEY_POWER nicht abfangen - gemessen auf
 * /dev/input/event1. Wer fuer besseren Hautkontakt draufdrueckt, schickt die
 * Uhr also ins Dösen. Laeuft die Messung in einem Vordergrunddienst, macht
 * das nichts: Sie laeuft weiter und das Ergebnis liegt bereit.
 */
public class EkgService extends Service {

    private static final String TAG = "HeroldEkg";
    private static final String CH = "herold_ekg";
    private static final int NOTIF = 700001;
    private static final int FS = 500, DAUER_S = 30;
    private static final float RUHE_GRENZE_MV = 8f;
    private static final int RUHE_NOETIG = 3;

    public static final String ACTION_ZUSTAND = "de.doncalvin.herold.EKG_ZUSTAND";
    public static final String ACTION_START   = "de.doncalvin.herold.EKG_START";
    public static final String ACTION_STOP    = "de.doncalvin.herold.EKG_STOP";

    public enum Phase { VERBINDEN, WARTEN, MESSEN, FERTIG, FEHLER }

    private HealthTrackingService dienst;
    private HealthTracker tracker;
    private PowerManager.WakeLock wach;
    private final Handler ui = new Handler(Looper.getMainLooper());

    private volatile Phase phase = Phase.VERBINDEN;
    private volatile int kontakt = -1, schlechteInFolge = 0, ruhigeSekunden = 0;
    private volatile String meldung = "";
    /** Der Dienst beendet sich selbst: nach Ergebnis/Fehler kurz danach,
     *  spaetestens aber ZEITWAECHTER_MS nach dem letzten Start - sonst
     *  liefe der EKG-Sensor weiter, wenn niemand mehr hinschaut. */
    private static final long ZEITWAECHTER_MS = 4 * 60 * 1000L;
    private final android.os.Handler ende = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable schluss = () -> { try { stopForeground(true); } catch (Exception ignored) {} stopSelf(); };
    private void endeIn(long ms) { ende.removeCallbacks(schluss); ende.postDelayed(schluss, ms); }

    private final float[] aufnahme = new float[FS * DAUER_S];
    private final int[] ppgAufnahme = new int[FS * DAUER_S];   // PPG aus demselben Datenpunkt, -1 = Fuellwert
    private volatile int geschrieben = 0;
    private final float[] fenster = new float[FS];
    private int fZeiger = 0, fGefuellt = 0;
    private long letztePruefung = 0;
    private volatile EkgAuswertung ergebnis;
    private PrintWriter mitschrift;
    // Kleiner Ringpuffer, aus dem die Anzeige live gefuettert wird


    public static float[] letzteKurve;   // fuer die Anzeige nach der Messung
    public static EkgAuswertung letztesErgebnis;   // vollstaendig, fuer den Ergebnisschirm

    /** Dienst und Anzeige laufen im selben Prozess - die Kurve geht direkt
     *  durch, statt ueber Broadcasts. Das war der Grund fuer die ruckelnde
     *  Darstellung: gedrosselte Meldungen alle 400 ms statt fluessiger Werte. */
    public interface KurvenHoerer { void wert(float mv); }
    public static volatile KurvenHoerer hoerer;
    private String bixbyVorher = null;   // Ausgangszustand zum Zuruecksetzen

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        java.util.Arrays.fill(ppgAufnahme, -1);
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(
                CH, "EKG-Messung", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIF, hinweis("EKG wird vorbereitet"));

        PowerManager pm = getSystemService(PowerManager.class);
        wach = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Herold:ekg");
        wach.acquire(3 * 60 * 1000L);

        try {
            File f = new File(getExternalFilesDir(null), "ekg.csv");
            mitschrift = new PrintWriter(new FileWriter(f, false));
            mitschrift.println("phase,lead,mv,ppg,seq");
        } catch (Exception ignored) {}

        bixbySperren(true);
        endeIn(ZEITWAECHTER_MS);

        dienst = new HealthTrackingService(verbindung, this);
        dienst.connectService();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int id) {
        if (intent != null && ACTION_STOP.equals(intent.getAction())) {
            ende.removeCallbacks(schluss); schluss.run(); return START_NOT_STICKY;
        }
        if (intent != null && ACTION_START.equals(intent.getAction())) { neuStarten(); endeIn(ZEITWAECHTER_MS); }
        melde();
        return START_STICKY;
    }

    private void neuStarten() {
        phase = Phase.WARTEN; geschrieben = 0; ruhigeSekunden = 0;
        fGefuellt = 0; ergebnis = null; kontakt = -1;
    }

    /**
     * Die obere Taste ist zugleich Elektrode und Ein-/Aus-Taste; langes Druecken
     * startet Bixby. Waehrend der Messung wird das unterbunden und danach exakt
     * so wiederhergestellt, wie es vorher war.
     */
    private void bixbySperren(boolean an) {
        try {
            android.content.ContentResolver cr = getContentResolver();
            if (an) {
                bixbyVorher = android.provider.Settings.Global.getString(
                        cr, "setting_bixby_launching_disallowed");
                android.provider.Settings.Global.putString(
                        cr, "setting_bixby_launching_disallowed", "1");
                Log.i(TAG, "Bixby-Langdruck waehrend der Messung gesperrt (vorher: "
                        + bixbyVorher + ")");
            } else {
                android.provider.Settings.Global.putString(
                        cr, "setting_bixby_launching_disallowed",
                        bixbyVorher == null ? "0" : bixbyVorher);
                Log.i(TAG, "Bixby-Langdruck wiederhergestellt");
            }
        } catch (Exception e) {
            Log.i(TAG, "Bixby-Einstellung nicht aenderbar: " + e);
        }
    }

    private final ConnectionListener verbindung = new ConnectionListener() {
        @Override public void onConnectionSuccess() {
            try {
                if (!dienst.getTrackingCapability().getSupportHealthTrackerTypes()
                        .contains(HealthTrackerType.ECG_ON_DEMAND)) {
                    phase = Phase.FEHLER; endeIn(8000); meldung = "Kein EKG-Sensor"; melde(); return;
                }
                tracker = dienst.getHealthTracker(HealthTrackerType.ECG_ON_DEMAND);
                tracker.setEventListener(ereignisse);
                phase = Phase.WARTEN; melde();
            } catch (Exception e) {
                phase = Phase.FEHLER; endeIn(8000); meldung = String.valueOf(e.getMessage()); melde();
            }
        }
        @Override public void onConnectionEnded() { }
        @Override public void onConnectionFailed(HealthTrackerException e) {
            phase = Phase.FEHLER; endeIn(8000); meldung = "Verbindung fehlgeschlagen (" + e.getErrorCode() + ")";
            melde();
        }
    };

    private final HealthTracker.TrackerEventListener ereignisse =
            new HealthTracker.TrackerEventListener() {
        @Override public void onDataReceived(List<DataPoint> punkte) {
            for (DataPoint p : punkte) {
                Integer lead = p.getValue(ValueKey.EcgSet.LEAD_OFF);
                Float mv = p.getValue(ValueKey.EcgSet.ECG_MV);
                // Der EKG-Strom traegt den PPG-Wert mit (100 Hz: jeder 5. Punkt
                // echt, dazwischen -1). Beide Kanaele aus demselben Datenpunkt -
                // damit gibt es keinen Zeitversatz, den man erst herausrechnen
                // muesste. Grundlage fuer die Pulsankunftszeit.
                Integer ppg = null, seq = null;
                try { ppg = p.getValue(ValueKey.EcgSet.PPG_GREEN); seq = p.getValue(ValueKey.EcgSet.SEQUENCE); } catch (Throwable ignored) {}
                if (lead != null && lead >= 0) {
                    if (lead == 0) { schlechteInFolge = 0; kontakt = 0; }
                    else if (++schlechteInFolge > 15) kontakt = lead;
                }
                if (mv != null) {
                    if (mitschrift != null) mitschrift.println(phase + "," + lead + "," + mv + "," + ppg + "," + seq);
                    KurvenHoerer h = hoerer;
                    if (h != null) h.wert(mv);
                    verarbeite(mv, ppg);
                }
            }
            melde();
        }
        @Override public void onFlushCompleted() { }
        @Override public void onError(HealthTracker.TrackerError f) {
            phase = Phase.FEHLER; endeIn(8000); meldung = String.valueOf(f); melde();
        }
    };

    private void verarbeite(float mv, Integer ppg) {
        if (phase == Phase.WARTEN) {
            fenster[fZeiger] = mv; fZeiger = (fZeiger + 1) % FS;
            if (fGefuellt < FS) fGefuellt++;
            long jetzt = System.currentTimeMillis();
            if (fGefuellt == FS && jetzt - letztePruefung > 1000) {
                letztePruefung = jetzt;
                float mit = 0; for (float v : fenster) mit += v; mit /= FS;
                float lo = Float.MAX_VALUE, hi = -Float.MAX_VALUE;
                for (float v : fenster) { lo = Math.min(lo, v - mit); hi = Math.max(hi, v - mit); }
                boolean ruhig = kontakt == 0 && (hi - lo) < RUHE_GRENZE_MV;
                ruhigeSekunden = ruhig ? ruhigeSekunden + 1 : 0;
                if (ruhigeSekunden >= RUHE_NOETIG) { geschrieben = 0; phase = Phase.MESSEN; }
            }
        } else if (phase == Phase.MESSEN) {
            if (kontakt != 0) {
                phase = Phase.WARTEN; ruhigeSekunden = 0; fGefuellt = 0; geschrieben = 0;
                return;
            }
            if (geschrieben < ppgAufnahme.length) ppgAufnahme[geschrieben] = (ppg == null ? -1 : ppg);
            if (geschrieben < aufnahme.length) aufnahme[geschrieben++] = mv;
            if (geschrieben >= aufnahme.length) {
                phase = Phase.FERTIG;
                ergebnis = EkgAuswertung.rechne(aufnahme, ppgAufnahme, geschrieben);
                letzteKurve = ergebnis.kurve;
                letztesErgebnis = ergebnis;
                endeIn(3000);                 // Ergebnis ist gesichert, Sensor darf aus
                if (ergebnis.puls > 0) {
                    long stempel = System.currentTimeMillis();
                    new Verlauf(this).schreibe(stempel, "EKG", ergebnis.puls + " /min",
                            ergebnis.rhythmus != null ? ergebnis.rhythmus.titel : ergebnis.guete);
                    Messdetail det = new Messdetail(stempel, "EKG");
                    det.werte.put("Puls", ergebnis.puls + " /min");
                    det.werte.put("Schläge", String.valueOf(ergebnis.schlaege));
                    det.werte.put("Signalgüte", String.valueOf(ergebnis.guete));
                    det.werte.put("Ausschlag", String.format("%.2f mV", ergebnis.amplitude));
                    det.abstaende = ergebnis.abstaende;
                    if (ergebnis.rhythmus != null) {
                        det.befundTitel = ergebnis.rhythmus.titel; det.befundText = ergebnis.rhythmus.text;
                        det.befundUrteil = ergebnis.rhythmus.urteil;
                        if (ergebnis.rhythmus.atemzuege > 0f)
                            det.werte.put("Atemzüge", Math.round(ergebnis.rhythmus.atemzuege) + " /min");
                    }
                    det.schreibe(this);
                    KachelBasis.auffrischen(this);
                }
                if (mitschrift != null) mitschrift.flush();
                Log.i(TAG, "Ergebnis: " + ergebnis.puls + "/min, " + ergebnis.schlaege
                        + " Schlaege, " + ergebnis.guete);
            }
        }
    }

    private long letzteMeldung = 0;
    private void melde() {
        long jetzt = System.currentTimeMillis();
        if (phase != Phase.FERTIG && jetzt - letzteMeldung < 400) return;
        letzteMeldung = jetzt;

        Intent i = new Intent(ACTION_ZUSTAND).setPackage(getPackageName())
                .putExtra("phase", phase.name())
                .putExtra("kontakt", kontakt)
                .putExtra("ruhig", ruhigeSekunden)
                .putExtra("rest", (aufnahme.length - geschrieben) / FS)
                .putExtra("meldung", meldung);
        if (ergebnis != null) {
            i.putExtra("puls", ergebnis.puls).putExtra("schlaege", ergebnis.schlaege)
             .putExtra("guete", ergebnis.guete).putExtra("streuung", ergebnis.streuungMs);
        }
        sendBroadcast(i);
        getSystemService(NotificationManager.class).notify(NOTIF, hinweis(kurztext()));
    }

    private String kurztext() {
        switch (phase) {
            case WARTEN:  return kontakt == 0
                    ? "Signal beruhigt sich (" + ruhigeSekunden + "/" + RUHE_NOETIG + ")"
                    : "Finger auf die obere Taste";
            case MESSEN:  return "Messung: noch " + (aufnahme.length - geschrieben) / FS + " s";
            case FERTIG:  return ergebnis != null ? ergebnis.puls + " /min · " + ergebnis.guete : "fertig";
            case FEHLER:  return meldung;
            default:      return "verbinde…";
        }
    }

    private Notification hinweis(String text) {
        PendingIntent auf = PendingIntent.getActivity(this, 0,
                new Intent(this, EkgActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this, CH)
                .setSmallIcon(android.R.drawable.ic_menu_info_details)
                .setContentTitle("EKG")
                .setContentText(text)
                .setContentIntent(auf)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build();
    }

    @Override
    public void onDestroy() {
        ende.removeCallbacks(schluss);
        try { if (tracker != null) tracker.unsetEventListener(); } catch (Exception ignored) {}
        try { if (dienst != null) dienst.disconnectService(); } catch (Exception ignored) {}
        try { if (mitschrift != null) { mitschrift.flush(); mitschrift.close(); } } catch (Exception ignored) {}
        try { if (wach != null && wach.isHeld()) wach.release(); } catch (Exception ignored) {}
        bixbySperren(false);
        super.onDestroy();
    }
}
