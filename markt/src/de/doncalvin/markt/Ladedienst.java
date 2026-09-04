package de.doncalvin.markt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.net.wifi.WifiManager;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import com.aurora.gplayapi.data.models.PlayFile;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Holt eine App von Play und installiert sie. Ein Auftrag nach dem anderen.
 * Haelt WLAN und Prozessor wach, solange geladen wird - die Uhr schaltet das
 * WLAN sonst ab, sobald das Display dunkel wird.
 */
public class Ladedienst extends Service {
    public static final String ACTION_STAND = "de.doncalvin.markt.STAND";
    private static final String KANAL = "markt_laden";
    private static final int NOTIF = 4100;

    private static final Map<String, String> STAND = new HashMap<>();
    private static final Map<String, Integer> PROZENT = new HashMap<>();
    private static CountDownLatch warten; private static boolean erfolg; private static String fehlerText;

    private final ArrayDeque<AppEintrag> schlange = new ArrayDeque<>();
    private Thread arbeiter; private WifiManager.WifiLock wlan; private PowerManager.WakeLock wach;

    static synchronized String stand(String paket) { return STAND.get(paket); }
    static synchronized int prozent(String paket) { Integer p = PROZENT.get(paket); return p == null ? 0 : p; }

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public void onCreate() {
        super.onCreate();
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel(KANAL, "Markt: Laden", NotificationManager.IMPORTANCE_LOW));
        startForeground(NOTIF, meldung("Markt", "wird vorbereitet", 0));
        WifiManager wm = (WifiManager) getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        wlan = wm.createWifiLock(WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Markt:wlan");
        wach = getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Markt:wach");
    }

    @Override
    public int onStartCommand(Intent i, int flags, int id) {
        AppEintrag e = i == null ? null : (AppEintrag) i.getSerializableExtra("eintrag");
        if (e != null) {
            synchronized (schlange) { schlange.add(e); }
            setze(e.paket, "wartet", 0, false);
            if (arbeiter == null || !arbeiter.isAlive()) { arbeiter = new Thread(this::arbeiten, "Markt-Laden"); arbeiter.start(); }
        }
        return START_NOT_STICKY;
    }

    private void arbeiten() {
        try { wlan.acquire(); wach.acquire(30 * 60 * 1000L); } catch (Exception ignored) {}
        try {
            while (true) {
                AppEintrag e;
                synchronized (schlange) { e = schlange.poll(); }
                if (e == null) break;
                einen(e);
            }
        } finally {
            try { if (wlan.isHeld()) wlan.release(); } catch (Exception ignored) {}
            try { if (wach.isHeld()) wach.release(); } catch (Exception ignored) {}
            Hintergrund.haupt(() -> { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); });
        }
    }

    private void einen(AppEintrag e) {
        String p = e.paket;
        File ordner = new File(getCacheDir(), "ladung/" + p);
        try {
            setze(p, "fragt bei Play an…", 0, false);
            if (!e.festeVersion && e.versionCode <= 0) e = Play.details(this, p);
            Log.i(Play.TAG, "Laden: " + p + " vCode=" + e.versionCode + " fest=" + e.festeVersion + " offer=" + e.offerType);
            List<PlayFile> dateien = Play.dateien(this, e);
            if (dateien.isEmpty()) throw new Exception("Play liefert keine Datei");
            long gesamt = 0; for (PlayFile f : dateien) gesamt += f.getSize();
            if (ordner.exists()) loeschen(ordner);
            ordner.mkdirs();
            long bisher = 0; List<File> lokal = new ArrayList<>();
            for (PlayFile f : dateien) {
                if (f.getType() == PlayFile.Type.OBB || f.getType() == PlayFile.Type.PATCH) continue;
                String name = f.getName().isEmpty() ? (f.getType() == PlayFile.Type.BASE ? "base.apk" : f.getId() + ".apk") : f.getName();
                if (!name.endsWith(".apk")) name = name + ".apk";
                File ziel = new File(ordner, name);
                bisher = herunterladen(f.getUrl(), ziel, bisher, gesamt, p);
                lokal.add(ziel);
            }
            setze(p, "wird installiert…", 100, false);
            synchronized (Ladedienst.class) { warten = new CountDownLatch(1); erfolg = false; fehlerText = null; }
            Installer.installieren(this, p, lokal);
            boolean fertig = warten.await(10, TimeUnit.MINUTES);
            if (!fertig) throw new Exception("keine Antwort vom Installer");
            if (!erfolg) throw new Exception(fehlerText == null ? "Installation abgelehnt" : fehlerText);
            setze(p, "installiert", 100, true);
            loeschen(ordner);
        } catch (Exception ex) {
            Log.w(Play.TAG, "Laden fehlgeschlagen fuer " + p + ": " + ex, ex);
            String m = String.valueOf(ex.getMessage());
            if (m.contains("AppNotSupported")) m = "Für diese Uhr nicht verfügbar";
            else if (m.contains("AppNotPurchased")) m = "Nicht kostenlos – ohne Konto nicht möglich";
            else if (m.contains("429")) m = "Play ist ausgelastet, später noch einmal";
            setze(p, "Fehler: " + (m.length() > 90 ? m.substring(0, 90) : m), -1, true);
        }
    }

    private long herunterladen(String url, File ziel, long bisher, long gesamt, String p) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(20000); h.setReadTimeout(60000);
        int code = h.getResponseCode();
        if (code == 403 || code == 410) throw new Exception("Download-Link abgelaufen (" + code + ")");
        if (code != 200) throw new Exception("Download antwortet " + code);
        long letzteMeldung = 0;
        try (InputStream in = h.getInputStream(); FileOutputStream o = new FileOutputStream(ziel)) {
            byte[] puffer = new byte[65536]; int n;
            while ((n = in.read(puffer)) > 0) {
                o.write(puffer, 0, n); bisher += n;
                long jetzt = System.currentTimeMillis();
                if (jetzt - letzteMeldung > 400) {
                    letzteMeldung = jetzt;
                    int proz = gesamt > 0 ? (int) (bisher * 100 / gesamt) : 0;
                    setze(p, "lädt " + (bisher / 1048576) + " von " + (gesamt / 1048576) + " MB", Math.min(99, proz), false);
                }
            }
        }
        return bisher;
    }

    private static void loeschen(File f) {
        File[] k = f.listFiles(); if (k != null) for (File x : k) loeschen(x);
        f.delete();
    }

    static void ergebnis(String paket, boolean ok, String text) {
        synchronized (Ladedienst.class) { erfolg = ok; fehlerText = text; if (warten != null) warten.countDown(); }
    }
    static void rueckfrage(String paket) {
        synchronized (Ladedienst.class) { STAND.put(paket, "bitte auf der Uhr bestätigen"); }
    }

    private void setze(String paket, String text, int prozent, boolean fertig) {
        synchronized (Ladedienst.class) {
            if (fertig) { STAND.remove(paket); PROZENT.remove(paket); } else { STAND.put(paket, text); if (prozent >= 0) PROZENT.put(paket, prozent); }
        }
        sendBroadcast(new Intent(ACTION_STAND).setPackage(getPackageName())
                .putExtra("paket", paket).putExtra("stand", text).putExtra("prozent", prozent).putExtra("fertig", fertig));
        try { getSystemService(NotificationManager.class).notify(NOTIF, meldung(paket, text, prozent)); } catch (Exception ignored) {}
    }

    private Notification meldung(String titel, String text, int prozent) {
        PendingIntent auf = PendingIntent.getActivity(this, 0, new Intent(this, StartActivity.class), PendingIntent.FLAG_IMMUTABLE);
        Notification.Builder b = new Notification.Builder(this, KANAL)
                .setSmallIcon(android.R.drawable.stat_sys_download_done)
                .setContentTitle(titel).setContentText(text).setContentIntent(auf)
                .setOngoing(true).setOnlyAlertOnce(true).setLocalOnly(true);
        if (prozent >= 0 && prozent < 100) b.setProgress(100, prozent, false);
        return b.build();
    }
}
