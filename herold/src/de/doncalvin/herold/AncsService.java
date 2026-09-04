package de.doncalvin.herold;

import android.app.*;
import android.bluetooth.*;
import android.bluetooth.le.*;
import android.content.*;
import android.os.*;
import android.graphics.drawable.Icon;
import android.media.MediaMetadata;
import android.media.session.MediaSession;
import android.media.session.PlaybackState;
import android.media.VolumeProvider;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.AudioFormat;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

/**
 * Herold - holt Benachrichtigungen per ANCS direkt vom iPhone.
 *
 * ANCS (Apple Notification Center Service) ist ein von Apple dokumentierter
 * BLE-Dienst. Das iPhone ist dabei GATT-Server, die Uhr ist Client.
 * Auf dem iPhone muss KEINE App installiert werden - nur einmal koppeln.
 */
public class AncsService extends Service {

    private static final String TAG = "Herold";
    public  static final String ACTION_STATUS = "de.doncalvin.herold.STATUS";
    /** Fuer die Anzeige: haengt gerade ein iPhone dran, und seit wann laeuft der Dienst? */
    public static volatile boolean verbunden;
    public static volatile long dienstSeit;

    // --- ANCS UUIDs (Apple-Spezifikation) ---
    private static final UUID ANCS_SERVICE =
            UUID.fromString("7905F431-B5CE-4E99-A40F-4B1E122D00D0");
    private static final UUID NOTIFICATION_SOURCE =
            UUID.fromString("9FBF120D-6301-42D9-8C58-25E699A21DBD");
    private static final UUID CONTROL_POINT =
            UUID.fromString("69D1D8F3-45E1-49A8-9821-9BBDFDAAD9D9");
    private static final UUID DATA_SOURCE =
            UUID.fromString("22EAC6E9-24D6-4BB5-BE44-B36ACE7C7BFB");
    // --- AMS: Apple Media Service (Musiksteuerung) ---
    private static final UUID AMS_SERVICE =
            UUID.fromString("89D3502B-0F36-433A-8EF4-C502AD55F8DC");
    private static final UUID AMS_REMOTE_COMMAND =
            UUID.fromString("9B3C81D8-57B1-4A8A-B8DF-0E56F7CA51C2");
    private static final UUID AMS_ENTITY_UPDATE =
            UUID.fromString("2F7CABCE-808D-411F-9A0C-BB92BA96C102");

    private static final byte AMS_PLAY = 0, AMS_PAUSE = 1, AMS_TOGGLE = 2,
                              AMS_NEXT = 3, AMS_PREV = 4,
                              AMS_VOL_UP = 5, AMS_VOL_DOWN = 6;

    public static final String ACTION_MEDIA  = "de.doncalvin.herold.MEDIA";
    public static final String ACTION_KOPPELN = "de.doncalvin.herold.KOPPELN";
    public static final String ACTION_TRENNEN = "de.doncalvin.herold.TRENNEN";
    public static final String ACTION_STATUS_ABFRAGE = "de.doncalvin.herold.STATUS_ABFRAGE";
    /** Vom Waechter bei Ladekabel rein/raus: Halter und adb-Wache umschalten. */
    public static final String ACTION_STROM = "de.doncalvin.herold.STROM";
    public static final int CMD_TOGGLE = 2, CMD_NEXT = 3, CMD_PREV = 4,
                            CMD_VOL_UP = 5, CMD_VOL_DOWN = 6;
    private static final String CH_MEDIA = "herold_musik";

    private static final UUID CCCD =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    // --- ANCS Attribut-IDs ---
    private static final byte ATTR_APP_ID   = 0;
    private static final byte ATTR_TITLE    = 1;
    private static final byte ATTR_MESSAGE  = 3;

    private static final byte CMD_GET_NOTIFICATION_ATTRIBUTES = 0;
    private static final byte CMD_GET_APP_ATTRIBUTES          = 1;
    private static final byte CMD_PERFORM_ACTION              = 2;

    private static final byte ATTR_POSITIVE_LABEL = 6;
    private static final byte ATTR_NEGATIVE_LABEL = 7;

    private static final int CATEGORY_INCOMING_CALL = 1;
    private static final int FLAG_POSITIVE_ACTION   = 8;    // annehmen moeglich
    private static final int FLAG_NEGATIVE_ACTION   = 16;   // ablehnen moeglich

    public static final String ACTION_ANNEHMEN = "de.doncalvin.herold.ANNEHMEN";
    public static final String ACTION_ABLEHNEN = "de.doncalvin.herold.ABLEHNEN";
    private static final String CH_CALL = "herold_anruf";
    private static final byte APP_ATTR_DISPLAY_NAME           = 0;

    private static final byte EVENT_ADDED   = 0;
    // EventFlags laut ANCS-Spezifikation
    private static final int FLAG_SILENT       = 1;
    private static final int FLAG_PRE_EXISTING = 4;
    private static final byte EVENT_REMOVED = 2;

    /**
     * Kanal fuer die Pflicht-Benachrichtigung des Vordergrunddienstes.
     * Wichtigkeit "keine" = gesperrter Kanal: Android verlangt die Meldung,
     * zeigt sie aber nirgends. Leise Stufen (MIN, SECRET, Dienst-Kategorie)
     * reichten One UI nicht - die Karte stand weiter in der Liste.
     * Der alte Kanal "herold_status" wird geloescht, sonst bleibt er sichtbar.
     */
    private static final String CH_STATUS = "herold_stumm";
    private static final String CH_NOTIFY = "herold_notify";

    private BluetoothAdapter adapter;
    private BluetoothLeAdvertiser advertiser;
    private BluetoothGattServer gattServer;
    private BluetoothGatt gatt;
    private BluetoothGattCharacteristic controlPoint;

    private final Map<Integer, String[]> pending = new HashMap<>();
    private final Map<String, String> appNamen = new HashMap<>();   // Bundle-ID -> Anzeigename
    private final java.util.Set<String> kanaeleAngelegt = new java.util.HashSet<>();
    private final ByteArrayBuilder dsBuffer = new ByteArrayBuilder();
    private int notifyId = 1000;
    private boolean verbindet = false;      // verhindert doppelte Verbindungsversuche
    private int discoverVersuche = 0;
    // BLE laesst nur EINEN Schreibvorgang gleichzeitig zu -> Anfragen der Reihe nach.
    /** Ein Schreibauftrag: wohin und was. GATT laesst nur einen gleichzeitig zu. */
    private static final class Befehl {
        final BluetoothGattCharacteristic ziel; final byte[] daten;
        Befehl(BluetoothGattCharacteristic z, byte[] d) { ziel = z; daten = d; }
    }
    private final java.util.ArrayDeque<Befehl> warteschlange = new java.util.ArrayDeque<>();
    private BluetoothGattCharacteristic amsRemote, amsEntity;
    private String titel = "", interpret = "", album = "";
    private int spielzustand = 0;
    private float dauer = 0f, position = 0f, lautstaerke = 0.5f;
    private VolumeProvider lautstaerkeRegler;
    private AudioFocusRequest fokusAnfrage;
    private boolean hatFokus = false;
    private AudioTrack stilleSpur;
    private int letzteUhrLautstaerke = -1;
    private boolean eigeneAenderung = false;
    private BroadcastReceiver lautstaerkeHorcher;
    private static final int MITTE = 7;   // Ruhepunkt, damit oben und unten Luft bleibt
    private MediaSession sitzung;
    // Merkt sich je UID, ob es ein Anruf ist und welche Aktionen erlaubt sind
    private final Map<Integer, int[]> meldungsInfo = new HashMap<>();
    private boolean schreibtGerade = false;
    private final Handler adbTakt = new Handler(Looper.getMainLooper());
    private int adbAusZaehler = 0, wifiAusZaehler = 0;
    private android.net.wifi.WifiManager.WifiLock wlanHalter;
    private PowerManager.WakeLock wachHalter;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        Waechter.planen(this);   // naechsten Weckruf sicherstellen
        if (intent != null && ACTION_STROM.equals(intent.getAction())) { stromAnpassen(); return START_STICKY; }
        // Mehrfaches Starten darf keine zweite Kopplung ausloesen.
        if (intent != null && ACTION_KOPPELN.equals(intent.getAction())) {
            Log.i(TAG, "Kopplung vom Nutzer angestossen");
            if (advertiser == null) startAdvertising();
            status("Sichtbar - jetzt am iPhone koppeln");
            return START_STICKY;
        }
        if (intent != null && ACTION_TRENNEN.equals(intent.getAction())) {
            Log.i(TAG, "Kopplung wird geloest");
            try {
                for (BluetoothDevice d : adapter.getBondedDevices()) {
                    // versteckte Methode - offiziell gibt es kein Entkoppeln fuer Apps
                    d.getClass().getMethod("removeBond").invoke(d);
                    Log.i(TAG, "entkoppelt: " + d.getAddress());
                }
            } catch (Exception e) { Log.i(TAG, "Entkoppeln nicht moeglich: " + e); }
            if (gatt != null) { try { gatt.close(); } catch (Exception ignored) {} gatt = null; }
            status("Kopplung geloest");
            return START_STICKY;
        }
        if (intent != null && ACTION_STATUS_ABFRAGE.equals(intent.getAction())) {
            try {
                boolean da = !adapter.getBondedDevices().isEmpty();
                status(da ? (gatt != null ? "Verbunden mit iPhone" : "Gekoppelt - verbinde…")
                          : "Noch nicht gekoppelt");
            } catch (SecurityException ignored) {}
            return START_STICKY;
        }
        if (intent != null && ACTION_MEDIA.equals(intent.getAction())) {
            int cmd = intent.getIntExtra("cmd", -1);
            if (cmd >= 0 && amsRemote != null) {
                Log.i(TAG, "Musikbefehl " + cmd);
                einreihen(amsRemote, new byte[]{ (byte) cmd });
            }
            return START_STICKY;
        }
        if (intent != null && ACTION_ANNEHMEN.equals(intent.getAction())) {
            int uid = intent.getIntExtra("uid", -1);
            if (uid != -1) { fuehreAktionAus(uid, true);
                getSystemService(NotificationManager.class).cancel(anrufNotifId(uid)); }
            return START_STICKY;
        }
        if (intent != null && ACTION_ABLEHNEN.equals(intent.getAction())) {
            int uid = intent.getIntExtra("uid", -1);
            if (uid != -1) { fuehreAktionAus(uid, false);
                getSystemService(NotificationManager.class).cancel(anrufNotifId(uid)); }
            return START_STICKY;
        }
        if (intent != null && "ZEIT".equals(intent.getAction())) {
            long ms = intent.getLongExtra("ms", 0);
            if (ms > 0) setzeZeit(ms);
            return START_STICKY;
        }
        if (intent != null && "TEST".equals(intent.getAction())) {
            String app = intent.getStringExtra("app");
            String tit = intent.getStringExtra("titel");
            Log.i(TAG, "TEST-Meldung fuer " + app);
            showNotification(tit == null ? "Test" : tit, "Test-Benachrichtigung", app);
            return START_STICKY;
        }
        // Wieder aktiv: Ursache der Abbrueche war das einschlafende WLAN, nicht
        // diese Wache. Sie greift erst beim zweiten Fehlschlag und sorgt dafuer,
        // dass das WLAN-Debugging nach einem Neustart von selbst wiederkommt.
        return START_STICKY;   // Android startet den Dienst neu, falls er abgeschossen wird
    }

    /**
     * Wear OS schaltet das WLAN-Debugging von sich aus wieder ab.
     * Herold laeuft ohnehin dauerhaft - also schaltet er es alle 20 Sekunden
     * wieder ein. Moeglich, weil WRITE_SECURE_SETTINGS die Stufe
     * "development" hat und per adb vergeben werden darf.
     */
    /**
     * Wear OS legt das WLAN schlafen, sobald es niemand benutzt - dann bricht auch
     * die adb-Verbindung weg und die Uhr wirbt mit einem toten Port weiter.
     * Ein WifiLock im Hochleistungsmodus plus ein PartialWakeLock halten die
     * Funkstrecke offen, solange Herold laeuft.
     */
    /**
     * Meldet einen Medienplayer beim System an. Dadurch erscheint die Steuerung
     * in der eingebauten Musikkarte der Uhr statt als eigene Benachrichtigung -
     * inklusive Lünette und Systemknoepfen.
     */
    private void starteMediaSession() {
        if (sitzung != null) return;
        sitzung = new MediaSession(this, "Herold");
        sitzung.setCallback(new MediaSession.Callback() {
            @Override public void onPlay()             { amsBefehl(AMS_PLAY); }
            @Override public void onPause()            { amsBefehl(AMS_PAUSE); }
            @Override public void onSkipToNext()       { amsBefehl(AMS_NEXT); }
            @Override public void onSkipToPrevious()   { amsBefehl(AMS_PREV); }
            @Override public void onStop()             { amsBefehl(AMS_PAUSE); }
        });
        // Ohne das regeln die Uhrtasten die Uhr selbst statt das iPhone.
        // RELATIVE, weil AMS nur "lauter"/"leiser" kennt, keinen absoluten Wert.
        lautstaerkeRegler = new VolumeProvider(
                VolumeProvider.VOLUME_CONTROL_RELATIVE, 15, 8) {
            @Override public void onAdjustVolume(int richtung) {
                if (richtung > 0)      amsBefehl(AMS_VOL_UP);
                else if (richtung < 0) amsBefehl(AMS_VOL_DOWN);
                // Anzeige mitziehen; den echten Wert meldet das iPhone gleich selbst.
                int neu = Math.max(0, Math.min(15, getCurrentVolume() + richtung));
                setCurrentVolume(neu);
            }
            @Override public void onSetVolumeTo(int wert) {
                // AMS kann nicht absolut setzen - schrittweise annaehern.
                int diff = wert - getCurrentVolume();
                for (int i = 0; i < Math.abs(diff) && i < 15; i++)
                    amsBefehl(diff > 0 ? AMS_VOL_UP : AMS_VOL_DOWN);
                setCurrentVolume(wert);
            }
        };
        // Wear OS verlangt einen Intent zum Oeffnen. Der soll aber den EINGEBAUTEN
        // Player der Uhr aufrufen, nicht Herold - sonst landet man beim Antippen
        // der Musikkarte in meiner App statt im Systemplayer.
        Intent player = new Intent()
                .setClassName("com.samsung.android.wearable.media.sessions",
                              "com.google.android.clockwork.media.umo.UmoActivity")
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent ziel;
        try {
            ziel = PendingIntent.getActivity(this, 0, player,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        } catch (Exception e) {   // Falls der Systemplayer fehlt: eigener Bildschirm
            ziel = PendingIntent.getActivity(this, 0, new Intent(this, MainActivity.class),
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        }
        sitzung.setSessionActivity(ziel);
        sitzung.setPlaybackToRemote(lautstaerkeRegler);
        sitzung.setActive(true);
        Log.i(TAG, "MediaSession angemeldet (Lautstaerke wird ans iPhone geleitet)");
    }

    /** Sagt dem iPhone, welche Musikdaten es schicken soll. Erst nach beiden Abos! */
    private void amsEntitaetenAnfordern() {
        einreihen(amsEntity, new byte[]{ 0, 0, 1, 2 });      // Player: Name, Zustand, Lautstaerke
        einreihen(amsEntity, new byte[]{ 2, 0, 1, 2, 3 });   // Track: Interpret, Album, Titel, Dauer
        Log.i(TAG, "AMS: Entitaeten angefordert");
    }

    /**
     * Ohne Audio-Fokus schickt Android die Lautstaerketasten an den lokalen
     * Musikstream der Uhr statt an unsere Session. Wir spielen zwar selbst
     * keinen Ton - der laeuft auf dem iPhone - aber der Fokus entscheidet,
     * wer die Tasten bekommt.
     */
    private void fokusHolen(boolean an) {
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        try {
            if (an && !hatFokus) {
                if (fokusAnfrage == null) {
                    fokusAnfrage = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                            .setAudioAttributes(new AudioAttributes.Builder()
                                    .setUsage(AudioAttributes.USAGE_MEDIA)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                    .build())
                            .setWillPauseWhenDucked(false)
                            .setOnAudioFocusChangeListener(f -> { })
                            .build();
                }
                int r = am.requestAudioFocus(fokusAnfrage);
                hatFokus = (r == AudioManager.AUDIOFOCUS_REQUEST_GRANTED);
                Log.i(TAG, "Audio-Fokus angefordert: " + (hatFokus ? "erhalten" : "abgelehnt"));
            } else if (!an && hatFokus && fokusAnfrage != null) {
                am.abandonAudioFocusRequest(fokusAnfrage);
                hatFokus = false;
                Log.i(TAG, "Audio-Fokus abgegeben");
            }
        } catch (Exception e) {
            Log.i(TAG, "Fokus-Fehler: " + e);
        }
    }

    /**
     * Android leitet Lautstaerke nur an eine Session, wenn es die App als aktiv
     * abspielend kennt. Herold gibt selbst keinen Ton aus - der Ton laeuft auf
     * dem iPhone. Deshalb laeuft hier eine lautlose Spur mit, damit das System
     * uns als Medienplayer sieht und die Lautstaerketasten hierher schickt.
     */
    private void stilleSpurLaufenLassen(boolean an) {
        try {
            if (an) {
                if (stilleSpur != null) return;
                int rate = 8000;
                int min = AudioTrack.getMinBufferSize(rate,
                        AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                if (min <= 0) min = 4096;
                stilleSpur = new AudioTrack.Builder()
                        .setAudioAttributes(new AudioAttributes.Builder()
                                .setUsage(AudioAttributes.USAGE_MEDIA)
                                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                                .build())
                        .setAudioFormat(new AudioFormat.Builder()
                                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                .setSampleRate(rate)
                                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                                .build())
                        .setBufferSizeInBytes(min * 2)
                        .setTransferMode(AudioTrack.MODE_STATIC)
                        .build();
                short[] stille = new short[min];        // lauter Nullen = kein Ton
                stilleSpur.write(stille, 0, stille.length);
                stilleSpur.setLoopPoints(0, stille.length, -1);   // endlos
                stilleSpur.setVolume(0f);
                stilleSpur.play();
                Log.i(TAG, "stille Spur laeuft - System sieht Herold als Player");
            } else if (stilleSpur != null) {
                stilleSpur.stop(); stilleSpur.release(); stilleSpur = null;
                Log.i(TAG, "stille Spur beendet");
            }
        } catch (Exception e) {
            Log.i(TAG, "stille Spur fehlgeschlagen: " + e);
        }
    }

    /**
     * Samsungs Systemregler verstellt stur die Lautstaerke der Uhr und ruft den
     * VolumeProvider der Session nie auf (gemessen). Also drehen wir es um:
     * Wir horchen auf die Aenderung des Uhr-Streams und leiten sie ans iPhone
     * weiter. Danach setzen wir die Uhr wieder auf die Mitte, damit man in
     * beide Richtungen weiterdrehen kann und nicht am Anschlag haengenbleibt.
     * Hoerbar ist die Uhr nicht - der Ton laeuft auf dem iPhone.
     */
    private void lautstaerkeUmleiten() {
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        letzteUhrLautstaerke = am.getStreamVolume(AudioManager.STREAM_MUSIC);

        lautstaerkeHorcher = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                int typ = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_TYPE", -1);
                if (typ != AudioManager.STREAM_MUSIC) return;
                if (eigeneAenderung) { eigeneAenderung = false; return; }

                int neu = i.getIntExtra("android.media.EXTRA_VOLUME_STREAM_VALUE", -1);
                if (neu < 0) neu = am.getStreamVolume(AudioManager.STREAM_MUSIC);
                if (letzteUhrLautstaerke < 0) { letzteUhrLautstaerke = neu; return; }

                int schritte = neu - letzteUhrLautstaerke;
                letzteUhrLautstaerke = neu;
                if (schritte == 0) return;

                Log.i(TAG, "Uhr-Lautstaerke " + (schritte > 0 ? "+" : "") + schritte
                        + " -> leite ans iPhone weiter");
                for (int k = 0; k < Math.min(Math.abs(schritte), 5); k++) {
                    amsBefehl(schritte > 0 ? AMS_VOL_UP : AMS_VOL_DOWN);
                }

                // Kein Zuruecksetzen mehr: Der Anzeigewert soll die echte
                // iPhone-Lautstaerke sein. Das iPhone meldet sie gleich zurueck,
                // und dann gleichen wir den Uhr-Stream daran an.
            }
        };
        IntentFilter f = new IntentFilter("android.media.VOLUME_CHANGED_ACTION");
        registerReceiver(lautstaerkeHorcher, f, Context.RECEIVER_EXPORTED);

        Log.i(TAG, "Lautstaerke-Umleitung aktiv (Uhr <-> iPhone)");
    }

    /** Zeigt die echte iPhone-Lautstaerke auf dem Regler der Uhr an. */
    private void lautstaerkeSpiegeln(float anteil) {
        AudioManager am = getSystemService(AudioManager.class);
        if (am == null) return;
        try {
            int max = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC);
            int soll = Math.max(0, Math.min(max, Math.round(anteil * max)));
            int ist  = am.getStreamVolume(AudioManager.STREAM_MUSIC);
            if (soll == ist) { letzteUhrLautstaerke = ist; return; }
            eigeneAenderung = true;              // eigene Aenderung nicht zurueckleiten
            letzteUhrLautstaerke = soll;
            am.setStreamVolume(AudioManager.STREAM_MUSIC, soll, 0);
            Log.i(TAG, "Regler auf iPhone-Wert gesetzt: " + soll + "/" + max);
        } catch (Exception e) {
            Log.i(TAG, "Spiegeln fehlgeschlagen: " + e);
        }
    }

    private void amsBefehl(byte cmd) {
        if (amsRemote == null) { Log.i(TAG, "AMS nicht verfuegbar"); return; }
        Log.i(TAG, "Musikbefehl " + cmd + " ans iPhone");
        einreihen(amsRemote, new byte[]{ cmd });
    }

    /** Haengt die Uhr am Ladekabel? */
    private boolean laedt() {
        // BatteryManager.isCharging() meldet auf dieser Uhr beim drahtlosen
        // Laden faelschlich "nein". Der klebende Batterie-Intent sagt die
        // Wahrheit: plugged != 0 heisst Kabel oder Ladeschale.
        try {
            Intent b = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (b == null) return false;
            int plugged = b.getIntExtra(android.os.BatteryManager.EXTRA_PLUGGED, 0);
            int status  = b.getIntExtra(android.os.BatteryManager.EXTRA_STATUS, -1);
            return plugged != 0 || status == android.os.BatteryManager.BATTERY_STATUS_CHARGING
                                || status == android.os.BatteryManager.BATTERY_STATUS_FULL;
        } catch (Exception e) { return false; }
    }

    /**
     * WLAN-Halter, Wachhalter und adb-Wache kosten gemessen 67-70 mA bei
     * dunklem Bildschirm - gegen 7,5 mA Nennmittel. Sie sind nur fuer die
     * Entwicklung ueber adb da. Deshalb: nur am Ladekabel. Die Verbindung
     * zum iPhone laeuft ueber Bluetooth LE und braucht davon nichts; ihre
     * Ereignisse wecken den Prozess von selbst.
     */
    private void stromAnpassen() {
        if (laedt()) { haltWlanWach(); adbWachhalten(); }
        else { halterLoslassen(); adbTakt.removeCallbacksAndMessages(null); Log.i(TAG, "ohne Kabel: Halter und adb-Wache aus"); }
    }

    private void halterLoslassen() {
        try { if (wlanHalter != null && wlanHalter.isHeld()) wlanHalter.release(); } catch (Exception ignored) {}
        try { if (wachHalter != null && wachHalter.isHeld()) wachHalter.release(); } catch (Exception ignored) {}
        wlanHalter = null; wachHalter = null;
    }

    private void haltWlanWach() {
        try {
            android.net.wifi.WifiManager wm =
                    (android.net.wifi.WifiManager) getApplicationContext()
                            .getSystemService(Context.WIFI_SERVICE);
            if (wm != null && wlanHalter == null) {
                wlanHalter = wm.createWifiLock(
                        android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF, "Herold:wlan");
                wlanHalter.setReferenceCounted(false);
                wlanHalter.acquire();
                Log.i(TAG, "WLAN-Halter aktiv");
            }
            PowerManager pm = getSystemService(PowerManager.class);
            if (pm != null && wachHalter == null) {
                wachHalter = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Herold:wach");
                wachHalter.setReferenceCounted(false);
                wachHalter.acquire();
                Log.i(TAG, "Wach-Halter aktiv");
            }
        } catch (Exception e) {
            Log.i(TAG, "Halter fehlgeschlagen: " + e);
        }
    }

    private void setzeZeit(long ms) {
        try {
            android.app.AlarmManager am = getSystemService(android.app.AlarmManager.class);
            am.setTime(ms);
            Log.i(TAG, "Zeit gesetzt auf " + new java.util.Date(ms));
        } catch (SecurityException e) {
            Log.i(TAG, "Zeit setzen verweigert: " + e);
        } catch (Exception e) {
            Log.i(TAG, "Zeit setzen Fehler: " + e);
        }
    }

    private void adbWachhalten() {
        adbTakt.removeCallbacksAndMessages(null);
        adbTakt.post(new Runnable() {
            @Override public void run() {
                try {
                    android.content.ContentResolver cr = getContentResolver();
                    int adb  = android.provider.Settings.Global.getInt(cr, "adb_enabled", 0);
                    int wifi = android.provider.Settings.Global.getInt(cr, "adb_wifi_enabled", 0);
                    // Nur eingreifen, wenn der Wert ZWEIMAL hintereinander aus war.
                    // Jedes Schreiben startet den Debug-Dienst mit neuem Port neu -
                    // zu haeufiges Schreiben erzeugt genau die Abbrueche, die es verhindern soll.
                    if (adb != 1)  adbAusZaehler++;  else adbAusZaehler = 0;
                    if (wifi != 1) wifiAusZaehler++; else wifiAusZaehler = 0;

                    if (adbAusZaehler >= 2) {
                        android.provider.Settings.Global.putInt(cr, "adb_enabled", 1);
                        Log.i(TAG, "ADB war aus - wieder eingeschaltet");
                        adbAusZaehler = 0;
                    }
                    if (wifiAusZaehler >= 2) {
                        android.provider.Settings.Global.putInt(cr, "adb_wifi_enabled", 1);
                        Log.i(TAG, "WLAN-Debugging war aus - wieder eingeschaltet");
                        wifiAusZaehler = 0;
                    }
                    // Bildschirm-Wachhalten nur setzen, wenn es abweicht
                    int wach = android.provider.Settings.Global.getInt(cr, "stay_on_while_plugged_in", 0);
                    if (wach != 7) {
                        android.provider.Settings.Global.putInt(cr, "stay_on_while_plugged_in", 7);
                        Log.i(TAG, "Wachhalten am Ladegeraet wieder gesetzt");
                    }
                } catch (SecurityException e) {
                    Log.i(TAG, "ADB-Wache: Berechtigung fehlt (pm grant WRITE_SECURE_SETTINGS)");
                } catch (Exception e) {
                    Log.i(TAG, "ADB-Wache: " + e);
                }
                adbTakt.postDelayed(this, 20000);
            }
        });
    }

    @Override
    public void onCreate() {
        super.onCreate();
        dienstSeit = System.currentTimeMillis();
        createChannels();
        startForeground(1, statusNotification("Warte auf iPhone"));

        stromAnpassen();
        starteMediaSession();
        lautstaerkeUmleiten();

        BluetoothManager bm = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
        adapter = bm.getAdapter();

        registerReceiver(bondReceiver,
                new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED),
                Context.RECEIVER_EXPORTED);

        try {
            openGattServer(bm);
            // Wichtig: Ist das iPhone bereits gekoppelt, NICHT neu werben - sonst
            // bietet die Uhr eine zweite Kopplung an und die bestehende geht kaputt.
            if (!verbindeMitBekanntemGeraet()) {
                startAdvertising();
                status("Warte auf Kopplung am iPhone");
            }
        } catch (SecurityException e) {
            status("Berechtigung fehlt");
            Log.e(TAG, "permissions", e);
        }
    }

    /* Leerer GATT-Server: damit iOS uns als Zubehoer sieht und wir die
       verbindende Gegenstelle mitbekommen. */
    private void openGattServer(BluetoothManager bm) {
        gattServer = bm.openGattServer(this, new BluetoothGattServerCallback() {
            @Override
            public void onConnectionStateChange(BluetoothDevice device, int st, int newState) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.i(TAG, "iPhone verbunden: " + device.getAddress());
                    verbunden = true;
                    status("iPhone verbunden");
                    if (device.getBondState() == BluetoothDevice.BOND_BONDED) {
                        connectAsClient(device);
                    } else {
                        try { device.createBond(); } catch (SecurityException ignored) {}
                    }
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    verbunden = false;
                    status("Getrennt - warte");
                }
            }
        });
    }

    private void startAdvertising() {
        advertiser = adapter.getBluetoothLeAdvertiser();
        if (advertiser == null) { status("BLE-Werbung nicht moeglich"); return; }

        // Beim Koppeln zaehlt Sichtbarkeit, nicht Sparsamkeit: schnellster Takt und
        // volle Sendeleistung. Das laeuft nur, solange die Uhr ungekoppelt ist -
        // danach wird die Werbung ohnehin abgeschaltet.
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_HIGH)
                .setConnectable(true)
                .build();

        // Die Solicitation-UUID ist der Trick: sie sagt iOS "ich haette gern ANCS".
        //
        // Wichtig fuer ein FREMDES iPhone: iOS listet in den Bluetooth-Einstellungen
        // nur Geraete, deren Name im Werbepaket steht. Ein Handy, das die Uhr schon
        // kennt, zeigt sie auch ohne - ein neues nicht. Das Paket fasst aber nur
        // 31 Byte: Flags (3) + 128-Bit-Solicitation (18) lassen ~8 Zeichen Name.
        // Darum bekommt die Uhr einen kurzen Bluetooth-Namen, dann passt er hinein.
        try {
            String n = adapter.getName();
            if (n == null || n.length() > KURZNAME.length()) adapter.setName(KURZNAME);
        } catch (Exception ignored) {}

        starteWerbung(settings, true);
    }

    /** Kurzer Bluetooth-Name, damit er neben der ANCS-Kennung ins Werbepaket passt. */
    private static final String KURZNAME = "Herold";

    /**
     * @param mitName Name ins Werbepaket legen. Wird das Paket zu gross,
     *                faellt es automatisch auf den Namen in der Scan-Antwort zurueck.
     */
    private void starteWerbung(AdvertiseSettings settings, final boolean mitName) {
        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(mitName)
                .addServiceSolicitationUuid(new android.os.ParcelUuid(ANCS_SERVICE))
                .build();

        AdvertiseData scanResponse = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .build();

        advertiser.startAdvertising(settings, data, scanResponse, new AdvertiseCallback() {
            @Override public void onStartFailure(int errorCode) {
                // 1 = Daten zu gross: ohne Namen im Werbepaket erneut versuchen
                if (errorCode == 1 && mitName) {
                    Log.w(TAG, "Werbepaket zu gross - ohne Namen erneut");
                    starteWerbung(settings, false);
                    return;
                }
                Log.e(TAG, "advertise failed " + errorCode);
                status("Werbung fehlgeschlagen: " + advErr(errorCode));
            }
            @Override public void onStartSuccess(AdvertiseSettings s) {
                Log.i(TAG, "advertising" + (mitName ? " (mit Name)" : " (Name nur in Scan-Antwort)"));
            }
        });
    }

    private static String advErr(int c) {
        switch (c) {
            case 1: return "Daten zu gross";
            case 2: return "zu viele Werber";
            case 3: return "laeuft bereits";
            case 4: return "interner Fehler";
            case 5: return "nicht unterstuetzt";
            default: return "Code " + c;
        }
    }

    private final BroadcastReceiver bondReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
            int state = i.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);
            if (d != null && state == BluetoothDevice.BOND_BONDED) {
                status("Gekoppelt - verbinde");
                connectAsClient(d);
            }
        }
    };

    private void connectAsClient(BluetoothDevice device) {
        if (device == null || verbindet) return;
        verbindet = true;
        // Eine alte, halbtote Verbindung zuerst wegraeumen - sonst blockiert sie die neue.
        if (gatt != null) {
            try { gatt.close(); } catch (Exception ignored) {}
            gatt = null;
            controlPoint = null;
        }
        Log.i(TAG, "verbinde als GATT-Client mit " + device.getAddress());
        try {
            // autoConnect=false: direkter Versuch ueber die bereits bestehende Funkstrecke.
            gatt = device.connectGatt(this, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } catch (SecurityException e) { status("Berechtigung fehlt"); }
    }

    /**
     * Sucht ein bereits gekoppeltes iPhone und verbindet sich damit.
     * @return true, wenn eines gefunden wurde - dann ist keine Werbung noetig.
     */
    private boolean verbindeMitBekanntemGeraet() {
        try {
            for (BluetoothDevice d : adapter.getBondedDevices()) {
                Log.i(TAG, "bekannt: " + d.getName() + " / " + d.getAddress()
                        + " typ=" + d.getType());
                if (d.getType() == BluetoothDevice.DEVICE_TYPE_DUAL
                        || d.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
                    status("Verbinde mit " + (d.getName() != null ? d.getName() : "iPhone"));
                    connectAsClient(d);
                    return true;
                }
            }
        } catch (SecurityException ignored) {}
        return false;
    }

    /** Werbung nachtraeglich starten, falls die Kopplung doch verloren ging. */
    private void werbenFallsNoetig() {
        try {
            if (!adapter.getBondedDevices().isEmpty()) return;   // noch gekoppelt -> nichts tun
        } catch (SecurityException ignored) { return; }
        if (advertiser == null) startAdvertising();
    }

    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override public void onConnectionStateChange(BluetoothGatt g, int s, int newState) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                verbindet = false;
                discoverVersuche = 0;
                status("Verbunden - suche ANCS");
                // Kurz warten: iOS gibt ANCS erst nach dem Verschluesseln der Strecke frei.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    try { g.discoverServices(); } catch (SecurityException ignored) {}
                }, 1500);
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i(TAG, "GATT getrennt (status=" + s + ") - baue neu auf");
                verbindet = false;
                status("Verbindung weg - verbinde neu");
                BluetoothDevice dev = g.getDevice();
                try { g.close(); } catch (Exception ignored) {}
                gatt = null;
                controlPoint = null;
                // Nach kurzer Pause erneut versuchen - sonst bleibt es fuer immer tot.
                new Handler(Looper.getMainLooper()).postDelayed(() -> {
                    connectAsClient(dev);
                    werbenFallsNoetig();
                }, 3000);
            }
        }

        @Override public void onServicesDiscovered(BluetoothGatt g, int st) {
            BluetoothGattService svc = g.getService(ANCS_SERVICE);
            if (svc == null) {
                discoverVersuche++;
                Log.i(TAG, "ANCS noch nicht da (Versuch " + discoverVersuche
                        + ", " + g.getServices().size() + " Dienste sichtbar)");
                if (discoverVersuche <= 6) {
                    status("suche ANCS (" + discoverVersuche + ")");
                    cacheLeeren(g);     // veraltete Dienstliste verwerfen
                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
                        try { g.discoverServices(); } catch (SecurityException ignored) {}
                    }, 2500);
                } else {
                    status("iPhone bietet kein ANCS");
                }
                return;
            }
            Log.i(TAG, "ANCS gefunden");
            discoverVersuche = 0;
            controlPoint = svc.getCharacteristic(CONTROL_POINT);

            // AMS liegt auf derselben Verbindung - kein zweiter Bond noetig.
            BluetoothGattService ams = g.getService(AMS_SERVICE);
            if (ams != null) {
                amsRemote = ams.getCharacteristic(AMS_REMOTE_COMMAND);
                amsEntity = ams.getCharacteristic(AMS_ENTITY_UPDATE);
                Log.i(TAG, "AMS gefunden (Musiksteuerung verfuegbar)");
            } else {
                Log.i(TAG, "AMS nicht vorhanden");
            }

            // Nur EINE GATT-Operation gleichzeitig: erst MTU aushandeln,
            // das Abonnieren startet in onMtuChanged.
            boolean angefragt = false;
            try { angefragt = g.requestMtu(512); } catch (SecurityException ignored) {}
            if (!angefragt) subscribe(g, svc.getCharacteristic(DATA_SOURCE));
        }

        @Override public void onMtuChanged(BluetoothGatt g, int mtu, int st) {
            Log.i(TAG, "MTU jetzt " + mtu + " (status " + st + ") - starte Abos");
            BluetoothGattService svc = g.getService(ANCS_SERVICE);
            if (svc != null) subscribe(g, svc.getCharacteristic(DATA_SOURCE));
        }

        @Override public void onDescriptorWrite(BluetoothGatt g, BluetoothGattDescriptor d, int st) {
            // Nacheinander abonnieren: erst Data Source, dann Notification Source.
            UUID fertig = d.getCharacteristic().getUuid();
            if (fertig.equals(DATA_SOURCE)) {
                BluetoothGattService svc = g.getService(ANCS_SERVICE);
                subscribe(g, svc.getCharacteristic(NOTIFICATION_SOURCE));
            } else if (fertig.equals(NOTIFICATION_SOURCE)) {
                status("Aktiv - Benachrichtigungen laufen");
                if (amsEntity != null) subscribe(g, amsEntity);   // weiter mit Musik
            } else if (fertig.equals(AMS_ENTITY_UPDATE)) {
                // Noch NICHT die Entitaeten anfordern: erst das zweite Abo abschliessen.
                // Ein Deskriptor-Schreibvorgang und ein Charakteristik-Schreibvorgang
                // duerfen sich nicht ueberlappen - GATT laesst nur eine Operation zu.
                if (amsRemote != null) subscribe(g, amsRemote);
                else amsEntitaetenAnfordern();
            } else if (fertig.equals(AMS_REMOTE_COMMAND)) {
                // Jetzt ist die Funkstrecke frei fuer die Entity-Anfragen.
                amsEntitaetenAnfordern();
            }
        }

        /* Bis Android 12 */
        @Override public void onCharacteristicChanged(BluetoothGatt g,
                                                      BluetoothGattCharacteristic ch) {
            byte[] v = ch.getValue();
            if (v != null) verteile(ch.getUuid(), v);
        }

        /* Ab Android 13 wird NUR noch diese Variante aufgerufen. */
        @Override public void onCharacteristicChanged(BluetoothGatt g,
                                                      BluetoothGattCharacteristic ch,
                                                      byte[] value) {
            verteile(ch.getUuid(), value);
        }

        @Override public void onCharacteristicWrite(BluetoothGatt g,
                                                    BluetoothGattCharacteristic ch, int st) {
            Log.i(TAG, "ControlPoint geschrieben, status=" + st);
            schreibvorgangFertig();
        }
    };

    /** Android merkt sich die Dienstliste. Nach dem Verschluesseln ist sie veraltet. */
    private void cacheLeeren(BluetoothGatt g) {
        try {
            java.lang.reflect.Method m = g.getClass().getMethod("refresh");
            Object r = m.invoke(g);
            Log.i(TAG, "Dienst-Cache geleert: " + r);
        } catch (Exception e) {
            Log.i(TAG, "Cache leeren nicht moeglich: " + e);
        }
    }

    private void verteile(UUID uuid, byte[] v) {
        if (v == null) return;
        Log.i(TAG, "Daten von " + (uuid.equals(NOTIFICATION_SOURCE) ? "NotificationSource"
                 : uuid.equals(DATA_SOURCE) ? "DataSource" : uuid.toString())
                 + " (" + v.length + " Byte)");
        if (uuid.equals(NOTIFICATION_SOURCE)) onNotificationSource(v);
        else if (uuid.equals(DATA_SOURCE))    onDataSource(v);
        else if (uuid.equals(AMS_ENTITY_UPDATE)) onAmsUpdate(v);
        else if (uuid.equals(AMS_REMOTE_COMMAND)) {
            StringBuilder sb = new StringBuilder();
            for (byte x : v) sb.append(x & 0xFF).append(' ');
            Log.i(TAG, "AMS unterstuetzte Kommandos: " + sb);
        }
    }

    /** Entity Update: [EntityID][AttributeID][Flags][Wert als UTF-8] */
    private void onAmsUpdate(byte[] v) {
        if (v.length < 3) return;
        int entity = v[0] & 0xFF, attr = v[1] & 0xFF;
        String wert = "";
        try { wert = new String(v, 3, v.length - 3, "UTF-8"); } catch (Exception ignored) {}

        if (entity == 2) {                      // Track
            if (attr == 0) interpret = wert;
            else if (attr == 1) album = wert;
            else if (attr == 2) titel = wert;
        } else if (entity == 0 && attr == 2) {  // Player/Lautstaerke, 0.0 bis 1.0
            try {
                lautstaerke = Float.parseFloat(wert);
                if (lautstaerkeRegler != null)
                    lautstaerkeRegler.setCurrentVolume(Math.round(lautstaerke * 15));
                Log.i(TAG, "iPhone-Lautstaerke: " + Math.round(lautstaerke * 100) + "%");
                lautstaerkeSpiegeln(lautstaerke);
            } catch (Exception ignored) {}
            return;
        } else if (entity == 2 && attr == 3) {  // Track/Dauer in Sekunden
            try { dauer = Float.parseFloat(wert); } catch (Exception ignored) {}
        } else if (entity == 0 && attr == 1) {  // Player/PlaybackInfo "Zustand,Rate,Zeit"
            String[] teile = wert.split(",");
            if (teile.length > 0) {
                try { spielzustand = (int) Float.parseFloat(teile[0]); } catch (Exception ignored) {}
            }
            if (teile.length > 2) {
                try { position = Float.parseFloat(teile[2]); } catch (Exception ignored) {}
            }
        } else {
            return;
        }
        Log.i(TAG, "Musik: " + interpret + " - " + titel + "  (Zustand " + spielzustand + ")");
        sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName())
                .putExtra("titel", titel).putExtra("interpret", interpret));
        zeigeMusik();
    }

    private void subscribe(BluetoothGatt g, BluetoothGattCharacteristic ch) {
        if (ch == null) return;
        try {
            g.setCharacteristicNotification(ch, true);
            BluetoothGattDescriptor d = ch.getDescriptor(CCCD);
            if (d != null) {
                if (Build.VERSION.SDK_INT >= 33) {
                    g.writeDescriptor(d, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                } else {
                    d.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
                    g.writeDescriptor(d);
                }
                Log.i(TAG, "abonniere " + ch.getUuid());
            }
        } catch (SecurityException ignored) {}
    }

    /** Notification Source: 8 Byte - EventID, Flags, Category, Count, UID(4) */
    private void onNotificationSource(byte[] v) {
        if (v.length < 8) return;
        byte eventId  = v[0];
        int flags     = v[1] & 0xFF;
        int category  = v[2] & 0xFF;
        int uid = ByteBuffer.wrap(v, 4, 4).order(ByteOrder.LITTLE_ENDIAN).getInt();

        if (eventId == EVENT_REMOVED) {
            pending.remove(uid);
            int[] weg = meldungsInfo.remove(uid);
            if (weg != null && weg[0] == CATEGORY_INCOMING_CALL) {
                // Anruf vorbei (angenommen, abgelehnt oder aufgelegt) -> Anzeige schliessen
                Log.i(TAG, "Anruf beendet, UID " + uid);
                getSystemService(NotificationManager.class).cancel(anrufNotifId(uid));
            }
            return;
        }
        if (eventId != EVENT_ADDED) return;

        meldungsInfo.put(uid, new int[]{ category, flags });

        // Beim Verbinden liefert iOS alle bereits vorhandenen Meldungen nach.
        // Ohne diesen Filter prasseln beim Start dutzende alte Hinweise herein.
        if ((flags & FLAG_PRE_EXISTING) != 0 && category != CATEGORY_INCOMING_CALL) {
            Log.i(TAG, "uebersprungen (alt): uid=" + uid);
            return;
        }
        if ((flags & FLAG_SILENT) != 0 && category != CATEGORY_INCOMING_CALL) {
            Log.i(TAG, "uebersprungen (lautlos): uid=" + uid);
            return;
        }
        Log.i(TAG, "neue Meldung: uid=" + uid + " flags=" + flags + " kategorie=" + category);

        requestAttributes(uid);
    }

    private void requestAttributes(int uid) {
        int[] info = meldungsInfo.get(uid);
        boolean istAnruf = info != null && info[0] == CATEGORY_INCOMING_CALL;

        ByteBuffer b = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN);
        b.put(CMD_GET_NOTIFICATION_ATTRIBUTES);
        b.putInt(uid);
        b.put(ATTR_APP_ID);
        b.put(ATTR_TITLE);   b.putShort((short) 64);
        b.put(ATTR_MESSAGE); b.putShort((short) 255);
        if (istAnruf) {
            // Nur bei Anrufen: die Beschriftungen der beiden Aktionen mitholen.
            // Achtung: 6 und 7 haben KEINEN Laengenparameter.
            b.put(ATTR_POSITIVE_LABEL);
            b.put(ATTR_NEGATIVE_LABEL);
        }
        byte[] cmd = new byte[b.position()];
        System.arraycopy(b.array(), 0, cmd, 0, cmd.length);
        einreihen(cmd);
    }

    /** Anruf annehmen (positiv) oder abweisen (negativ). 6 Byte, siehe ANCS-Spezifikation. */
    private void fuehreAktionAus(int uid, boolean annehmen) {
        int[] info = meldungsInfo.get(uid);
        if (info != null) {
            int flags = info[1];
            boolean erlaubt = annehmen ? (flags & FLAG_POSITIVE_ACTION) != 0
                                       : (flags & FLAG_NEGATIVE_ACTION) != 0;
            if (!erlaubt) {
                Log.i(TAG, "Aktion nicht erlaubt fuer UID " + uid + " (flags=" + flags + ")");
                return;
            }
        }
        ByteBuffer b = ByteBuffer.allocate(6).order(ByteOrder.LITTLE_ENDIAN);
        b.put(CMD_PERFORM_ACTION);
        b.putInt(uid);
        b.put((byte) (annehmen ? 0 : 1));
        Log.i(TAG, (annehmen ? "ANNEHMEN" : "ABLEHNEN") + " fuer UID " + uid);
        einreihen(b.array());
    }

    private void einreihen(byte[] cmd) { einreihen(controlPoint, cmd); }

    private void einreihen(BluetoothGattCharacteristic ziel, byte[] cmd) {
        if (ziel == null) return;
        synchronized (warteschlange) {
            if (warteschlange.size() > 30) return;   // Notbremse gegen Fluten
            warteschlange.add(new Befehl(ziel, cmd));
        }
        sendeNaechste();
    }

    /** Nimmt die naechste UID aus der Warteschlange - aber nur, wenn gerade frei ist. */
    private void sendeNaechste() {
        Befehl auftrag;
        synchronized (warteschlange) {
            if (schreibtGerade || warteschlange.isEmpty()) return;
            if (gatt == null) { warteschlange.clear(); return; }
            auftrag = warteschlange.poll();
            schreibtGerade = true;
        }
        try {
            boolean ancs = auftrag.ziel == controlPoint;
            Log.i(TAG, "sende " + (ancs ? "ANCS" : "AMS") + " 0x"
                    + Integer.toHexString(auftrag.daten[0] & 0xFF)
                    + " (" + auftrag.daten.length + " Byte, noch " + warteschlange.size() + ")");
            if (ancs && auftrag.daten[0] == CMD_GET_NOTIFICATION_ATTRIBUTES) dsBuffer.clear();
            if (Build.VERSION.SDK_INT >= 33) {
                gatt.writeCharacteristic(auftrag.ziel, auftrag.daten,
                        BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            } else {
                auftrag.ziel.setValue(auftrag.daten);
                gatt.writeCharacteristic(auftrag.ziel);
            }
        } catch (SecurityException e) {
            synchronized (warteschlange) { schreibtGerade = false; }
        }
    }

    private void schreibvorgangFertig() {
        synchronized (warteschlange) { schreibtGerade = false; }
        new Handler(Looper.getMainLooper()).postDelayed(this::sendeNaechste, 120);
    }

    /** Data Source liefert die Antwort in Haeppchen - hier zusammensetzen und parsen. */
    private void onDataSource(byte[] chunk) {
        dsBuffer.append(chunk);
        byte[] all = dsBuffer.peek();
        if (all.length < 5) return;

        String appId = null, title = null, message = null;
        String posLabel = null, negLabel = null;

        ByteBuffer b = ByteBuffer.wrap(all).order(ByteOrder.LITTLE_ENDIAN);
        b.get();                             // CommandID
        final int uidAntwort = b.getInt();   // UID, auf die sich die Antwort bezieht

        try {
            while (b.remaining() >= 3) {
                byte attrId = b.get();
                int len = b.getShort() & 0xFFFF;
                if (b.remaining() < len) return;   // noch unvollstaendig
                byte[] raw = new byte[len];
                b.get(raw);
                String s = new String(raw, "UTF-8");
                if (attrId == ATTR_APP_ID)  appId = s;
                if (attrId == ATTR_TITLE)   title = s;
                if (attrId == ATTR_MESSAGE) message = s;
                if (attrId == ATTR_POSITIVE_LABEL) posLabel = s;
                if (attrId == ATTR_NEGATIVE_LABEL) negLabel = s;
            }
        } catch (Exception e) { return; }

        dsBuffer.clear();
        if (title == null && message == null) return;
        int[] info = meldungsInfo.get(uidAntwort);
        if (info != null && info[0] == CATEGORY_INCOMING_CALL) {
            Log.i(TAG, "ANRUF von " + title + "  [" + appId + "]  ja=\"" + posLabel
                    + "\" nein=\"" + negLabel + "\"");
            zeigeAnruf(uidAntwort, title != null && !title.isEmpty() ? title : "Unbekannt",
                       appId, posLabel, negLabel, info[1]);
            return;
        }
        Log.i(TAG, "ZEIGE: " + title + " / " + message + "  [" + appId + "]");
        showNotification(title != null && !title.isEmpty() ? title : appName(appId),
                         message == null ? "" : message, appId);
    }

    private String appName(String appId) {
        if (appId == null || appId.isEmpty()) return "iPhone";
        String merk = appNamen.get(appId);
        if (merk != null) return merk;                 // echter Name von iOS
        int i = appId.lastIndexOf('.');
        String roh = (i >= 0 && i < appId.length() - 1) ? appId.substring(i + 1) : appId;
        // "MobileSMS" -> "Nachrichten" u.ae. fuer Apples eigene Apps
        if (roh.equalsIgnoreCase("MobileSMS"))   return "Nachrichten";
        if (roh.equalsIgnoreCase("MobilePhone")) return "Telefon";
        if (roh.equalsIgnoreCase("mobilemail"))  return "Mail";
        if (roh.equalsIgnoreCase("mobilecal"))   return "Kalender";
        return roh.substring(0, 1).toUpperCase() + roh.substring(1);
    }

    private void showNotification(String title, String text, String appId) {
        final int id = notifyId++;
        zeige(id, title, text, appId, IconHolen.ausSpeicher(appId));

        // Fehlt das echte Icon noch, im Hintergrund holen und die Meldung ersetzen.
        if (IconHolen.ausSpeicher(appId) == null) {
            IconHolen.hole(this, appId, bm ->
                new Handler(Looper.getMainLooper())
                        .post(() -> zeige(id, title, text, appId, bm)));
        }
    }

    /** Legt fuer jede iPhone-App einen eigenen Kanal an, damit die Uhr nach
     *  Quelle gruppiert statt alles unter "Herold" zusammenzuwerfen. */
    private String kanalFuer(String appId) {
        String quelle = appName(appId);
        String id = "app_" + (appId == null || appId.isEmpty() ? "sonstige" : appId);
        if (kanaeleAngelegt.add(id)) {
            NotificationChannel k = new NotificationChannel(
                    id, quelle, NotificationManager.IMPORTANCE_HIGH);
            k.setGroup("iphone");
            k.setShowBadge(true);
            getSystemService(NotificationManager.class).createNotificationChannel(k);
            Log.i(TAG, "Kanal angelegt fuer " + quelle);
        }
        return id;
    }

    private void zeige(int id, String title, String text, String appId, android.graphics.Bitmap icon) {
        String quelle = appName(appId);
        String gruppe = (appId == null || appId.isEmpty()) ? "sonstige" : appId;
        Notification.Builder b = new Notification.Builder(this, kanalFuer(appId))
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(quelle)                     // Quell-App sichtbar machen
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setColor(AppLook.farbe(appId))
                .setColorized(true)
                .setGroup(gruppe)                       // pro App buendeln
                .setAutoCancel(true);

        if (icon != null) {
            b.setSmallIcon(android.graphics.drawable.Icon.createWithBitmap(icon));
            b.setLargeIcon(icon);
        } else {
            b.setSmallIcon(AppLook.symbol(quelle));   // Notbehelf: Buchstabe
        }
        getSystemService(NotificationManager.class).notify(id, b.build());
    }

    private static final int MUSIK_ID = 800001;

    private Notification.Action medienAktion(int symbol, String text, byte cmd) {
        PendingIntent pi = PendingIntent.getService(this, 7000 + cmd,
                new Intent(this, AncsService.class).setAction(ACTION_MEDIA)
                        .putExtra("cmd", (int) cmd),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        return new Notification.Action.Builder(
                Icon.createWithResource(this, symbol), text, pi).build();
    }

    /** Uebertraegt den Zustand vom iPhone in die Medienkarte der Uhr. */
    private void zeigeMusik() {
        if (sitzung == null) return;

        boolean laeuft = spielzustand == 1;
        fokusHolen(laeuft);
        stilleSpurLaufenLassen(laeuft);

        if (titel == null || titel.isEmpty()) {
            sitzung.setPlaybackState(new PlaybackState.Builder()
                    .setState(PlaybackState.STATE_STOPPED, 0, 0f)
                    .setActions(PlaybackState.ACTION_PLAY)
                    .build());
            sitzung.setActive(false);
            getSystemService(NotificationManager.class).cancel(MUSIK_ID);
            return;
        }
        sitzung.setActive(true);

        MediaMetadata.Builder md = new MediaMetadata.Builder()
                .putString(MediaMetadata.METADATA_KEY_TITLE, titel)
                .putString(MediaMetadata.METADATA_KEY_ARTIST, interpret)
                .putString(MediaMetadata.METADATA_KEY_ALBUM, album)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_TITLE, titel)
                .putString(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE, interpret);
        md.putLong(MediaMetadata.METADATA_KEY_DURATION,
                   dauer > 0 ? (long) (dauer * 1000) : -1);
        sitzung.setMetadata(md.build());

        sitzung.setPlaybackState(new PlaybackState.Builder()
                .setActions(PlaybackState.ACTION_PLAY
                          | PlaybackState.ACTION_PAUSE
                          | PlaybackState.ACTION_PLAY_PAUSE
                          | PlaybackState.ACTION_SKIP_TO_NEXT
                          | PlaybackState.ACTION_SKIP_TO_PREVIOUS)
                .setState(laeuft ? PlaybackState.STATE_PLAYING : PlaybackState.STATE_PAUSED,
                          (long) (position * 1000), laeuft ? 1f : 0f,
                          android.os.SystemClock.elapsedRealtime())
                .build());

        // MediaStyle-Notification, damit die Uhr die Session auch anzeigt
        Notification.Action zurueck = medienAktion(android.R.drawable.ic_media_previous,
                "Zurueck", AMS_PREV);
        Notification.Action mitte = laeuft
                ? medienAktion(android.R.drawable.ic_media_pause, "Pause", AMS_PAUSE)
                : medienAktion(android.R.drawable.ic_media_play,  "Play",  AMS_PLAY);
        Notification.Action weiter = medienAktion(android.R.drawable.ic_media_next,
                "Weiter", AMS_NEXT);

        Notification n = new Notification.Builder(this, CH_MEDIA)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(titel)
                .setContentText(interpret)
                .setSubText(album)
                .setContentIntent(sitzung.getController().getSessionActivity())
                .setCategory(Notification.CATEGORY_TRANSPORT)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .addAction(zurueck).addAction(mitte).addAction(weiter)
                .setStyle(new Notification.MediaStyle()
                        .setMediaSession(sitzung.getSessionToken())
                        .setShowActionsInCompactView(0, 1, 2))
                .build();
        getSystemService(NotificationManager.class).notify(MUSIK_ID, n);
    }

    private static int anrufNotifId(int uid) { return 900000 + (uid & 0xFFFF); }

    private void zeigeAnruf(int uid, String wer, String appId,
                            String posLabel, String negLabel, int flags) {
        PendingIntent ja = PendingIntent.getService(this, uid * 2,
                new Intent(this, AncsService.class).setAction(ACTION_ANNEHMEN).putExtra("uid", uid),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        PendingIntent nein = PendingIntent.getService(this, uid * 2 + 1,
                new Intent(this, AncsService.class).setAction(ACTION_ABLEHNEN).putExtra("uid", uid),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder b = new Notification.Builder(this, CH_CALL)
                .setSmallIcon(android.R.drawable.sym_action_call)
                .setContentTitle(wer)
                .setContentText("Eingehender Anruf")
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setColor(AppLook.farbe(appId))
                .setColorized(true);

        android.graphics.Bitmap icon = IconHolen.ausSpeicher(appId);
        if (icon != null) b.setLargeIcon(icon);

        if ((flags & FLAG_POSITIVE_ACTION) != 0) {
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.sym_action_call),
                    (posLabel != null && !posLabel.isEmpty()) ? posLabel : "Annehmen", ja).build());
        }
        if ((flags & FLAG_NEGATIVE_ACTION) != 0) {
            b.addAction(new Notification.Action.Builder(
                    Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                    (negLabel != null && !negLabel.isEmpty()) ? negLabel : "Ablehnen", nein).build());
        }
        getSystemService(NotificationManager.class).notify(anrufNotifId(uid), b.build());
    }

    private void createChannels() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        nm.createNotificationChannelGroup(
                new android.app.NotificationChannelGroup("iphone", "iPhone"));
        try { nm.deleteNotificationChannel("herold_status"); } catch (Exception ignored) {}
        nm.createNotificationChannel(new NotificationChannel(
                CH_STATUS, "Herold Dienst (stumm)", NotificationManager.IMPORTANCE_NONE));
        nm.createNotificationChannel(new NotificationChannel(
                CH_NOTIFY, "iPhone", NotificationManager.IMPORTANCE_HIGH));
        NotificationChannel anruf = new NotificationChannel(
                CH_CALL, "Anrufe", NotificationManager.IMPORTANCE_HIGH);
        anruf.enableVibration(true);
        nm.createNotificationChannel(anruf);
        nm.createNotificationChannel(new NotificationChannel(
                CH_MEDIA, "Musik", NotificationManager.IMPORTANCE_LOW));
    }

    /**
     * Die Pflicht-Benachrichtigung des Vordergrunddienstes - so leise, wie
     * Android sie zulaesst: leisester Kanal, stumm, als Dienst markiert,
     * Anzeige aufgeschoben, nur lokal. Und sie wird genau einmal gesetzt;
     * der Zustand geht ueber den Broadcast an die Anzeige, nicht ueber
     * staendig neue Meldungen.
     */
    private Notification statusNotification(String text) {
        Notification.Builder b = new Notification.Builder(this, CH_STATUS)
                .setSmallIcon(R.drawable.herold_icon)
                .setContentTitle("Herold")
                .setContentText("läuft im Hintergrund")
                .setCategory(Notification.CATEGORY_SERVICE)
                .setVisibility(Notification.VISIBILITY_SECRET)
                .setOngoing(true)
                .setDefaults(0)
                .setLocalOnly(true)
                .setShowWhen(false);
        if (android.os.Build.VERSION.SDK_INT >= 31)
            b.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_DEFERRED);
        return b.build();
    }

    private void status(String text) {
        Log.i(TAG, "status: " + text);
        // absichtlich kein nm.notify(1, ...): jede neue Meldung wuerde die
        // Statuskarte wieder in die Liste holen
        sendBroadcast(new Intent(ACTION_STATUS).setPackage(getPackageName())
                        .putExtra("text", text));
    }

    @Override
    public void onTaskRemoved(Intent wurzel) {
        // Wer die App aus der Uebersicht wischt, will die Anzeige weg - nicht
        // die Benachrichtigungen vom iPhone. Der Dienst kommt sofort zurueck.
        Waechter.starteDienst(this);
        Waechter.planen(this);
        super.onTaskRemoved(wurzel);
    }

    @Override
    public void onDestroy() {
        try {
            if (advertiser != null) advertiser.stopAdvertising(new AdvertiseCallback() {});
            if (gatt != null) gatt.close();
            if (gattServer != null) gattServer.close();
            unregisterReceiver(bondReceiver);
            if (lautstaerkeHorcher != null) unregisterReceiver(lautstaerkeHorcher);
            adbTakt.removeCallbacksAndMessages(null);
            fokusHolen(false);
            stilleSpurLaufenLassen(false);
            if (sitzung != null) { sitzung.setActive(false); sitzung.release(); sitzung = null; }
            if (wlanHalter != null && wlanHalter.isHeld()) wlanHalter.release();
            if (wachHalter != null && wachHalter.isHeld()) wachHalter.release();
        } catch (Exception ignored) {}
        super.onDestroy();
    }

    /** Kleiner wachsender Puffer fuer die Data-Source-Haeppchen. */
    private static class ByteArrayBuilder {
        private byte[] buf = new byte[0];
        void append(byte[] b) {
            byte[] n = new byte[buf.length + b.length];
            System.arraycopy(buf, 0, n, 0, buf.length);
            System.arraycopy(b, 0, n, buf.length, b.length);
            buf = n;
        }
        byte[] peek() { return buf; }
        void clear() { buf = new byte[0]; }
    }
}
