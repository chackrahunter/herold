package de.doncalvin.herold;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.Calendar;

/**
 * Misst von allein - sparsam.
 *
 * Grundsatz: Jede Messung kostet Strom, und die teuerste (Rhythmus, 150 s
 * PPG) darf nicht oft laufen. Deshalb ein fester Plan statt "alle 30 Minuten
 * alles":
 *
 *   Hauttemperatur  12 s   alle 30 min, nachts (1-6 Uhr) jede volle Stunde
 *   Puls            30 s   alle 30 min, versetzt zur Temperatur
 *   Sauerstoff     ~5 s    2x taeglich (03:00, 15:00)
 *   Rhythmus       150 s   4x taeglich (05:00, 11:00, 17:00, 23:00), nur in Ruhe
 *
 * Vor jeder Messung: Uhr getragen? (Trage-Sensor, kostet nichts) und Arm
 * ruhig? (4 s Beschleunigung). Sonst wird verschoben, hoechstens dreimal.
 * Gemessen an den Zahlen der Recherche kostet dieser Plan rund 4 % Akku am
 * Tag; Rhythmus alle 30 Minuten waeren 13 %.
 *
 * Weckmechanismus: setExactAndAllowWhileIdle, nach jedem Feuern neu gesetzt.
 * setRepeating ist im Ruhezustand nicht befreit, JobScheduler hat einen
 * 15-Minuten-Boden und keinen genauen Zeitpunkt.
 */
public class MessPlaner extends BroadcastReceiver {

    private static final String TAG = "HeroldPlaner";
    public static final String ACTION_TAKT = "de.doncalvin.herold.MESSTAKT";
    private static final String PREF = "herold_planer";
    private static final long HALBE_STUNDE = 30 * 60 * 1000L;
    private static final long FUENF_MIN = 5 * 60 * 1000L;

    /** Ein- und ausschaltbar; Standard: an. */
    public static boolean aktiv(Context c) {
        return c.getSharedPreferences(PREF, Context.MODE_PRIVATE).getBoolean("aktiv", true);
    }
    public static void setzeAktiv(Context c, boolean an) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putBoolean("aktiv", an).apply();
        if (an) planen(c, 0); else abbrechen(c);
    }

    @Override
    public void onReceive(Context c, Intent i) {
        if (!aktiv(c)) return;
        SharedPreferences p = c.getSharedPreferences(PREF, Context.MODE_PRIVATE);
        int verschoben = i.getIntExtra("verschoben", 0);
        final String art = faelligeArt(p);
        Log.i(TAG, "Takt: faellig " + art + " (verschoben " + verschoben + "x)");
        if (art == null) { planen(c, 0); return; }

        // 1) Getragen? Sonst sofort naechster Takt - kostet Millisekunden.
        final boolean erzwingen = p.getBoolean("erzwingen", false);
        if (erzwingen) p.edit().putBoolean("erzwingen", false).apply();
        Traegt.frage(c, 1500, getragen -> {
            if (!getragen && !erzwingen) { Log.i(TAG, "abgelegt - uebersprungen"); planen(c, 0); return; }
            // 2) Ruhig? Rhythmus verlangt Stille, kurze Messungen vertragen leichte Bewegung.
            Ruhe.messe(c, sigma -> {
                float grenze = SensorService.PULS.equals(art) && p.getBoolean("rhythmus", false) ? 0.15f : 0.5f;
                if (!erzwingen && !Float.isNaN(sigma) && sigma > grenze) {
                    if (verschoben < 3) {
                        Log.i(TAG, "Bewegung (" + sigma + ") - in 5 min erneut");
                        planenIn(c, FUENF_MIN, verschoben + 1);
                    } else {
                        Log.i(TAG, "dreimal verschoben - ausgelassen");
                        markiere(p, art); planen(c, 0);
                    }
                    return;
                }
                // 3) Messen - still, ohne Anzeige.
                Intent start = new Intent(c, SensorService.class)
                        .setAction(SensorService.ACTION_START)
                        .putExtra("art", art)
                        .putExtra("hintergrund", true)
                        .putExtra("kurz", !p.getBoolean("rhythmus", false));
                c.startForegroundService(start);
                markiere(p, art);
                planen(c, 0);
            });
        });
    }

    /** Welche Messung ist jetzt dran? Reihenfolge nach Kosten: Temperatur, Puls, SpO2, Rhythmus. */
    private static String faelligeArt(SharedPreferences p) {
        long jetzt = System.currentTimeMillis();
        Calendar k = Calendar.getInstance(); k.setTimeInMillis(jetzt);
        int h = k.get(Calendar.HOUR_OF_DAY), m = k.get(Calendar.MINUTE);
        boolean nacht = h >= 1 && h < 6;

        // Rhythmus (lang) zu festen Stunden, einmal je Stunde-Slot
        if ((h == 5 || h == 11 || h == 17 || h == 23) && m < 30
                && jetzt - p.getLong("zuletzt_rhythmus", 0) > 5 * 3600 * 1000L) {
            p.edit().putBoolean("rhythmus", true).apply();
            return SensorService.PULS;
        }
        p.edit().putBoolean("rhythmus", false).apply();
        if ((h == 3 || h == 15) && m < 30 && jetzt - p.getLong("zuletzt_spo2", 0) > 11 * 3600 * 1000L)
            return SensorService.SPO2;
        long tempAbstand = nacht ? 60 * 60 * 1000L : HALBE_STUNDE;
        if (jetzt - p.getLong("zuletzt_temp", 0) >= tempAbstand - 60000) return SensorService.TEMP;
        if (!nacht && jetzt - p.getLong("zuletzt_puls", 0) >= HALBE_STUNDE - 60000) return SensorService.PULS;
        return null;
    }

    private static void markiere(SharedPreferences p, String art) {
        String k = SensorService.PULS.equals(art) ? (p.getBoolean("rhythmus", false) ? "zuletzt_rhythmus" : "zuletzt_puls")
                 : SensorService.SPO2.equals(art) ? "zuletzt_spo2" : "zuletzt_temp";
        p.edit().putLong(k, System.currentTimeMillis()).apply();
    }

    /** Naechsten Takt setzen: zur naechsten Viertelstunde, mindestens in 5 Minuten. */
    public static void planen(Context c, int verschoben) {
        long jetzt = System.currentTimeMillis();
        long viertel = 15 * 60 * 1000L;
        long naechste = ((jetzt / viertel) + 1) * viertel;
        planenIn(c, Math.max(FUENF_MIN, naechste - jetzt), verschoben);
    }

    private static void planenIn(Context c, long inMs, int verschoben) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Intent i = new Intent(c, MessPlaner.class).setAction(ACTION_TAKT).putExtra("verschoben", verschoben);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0x7A11, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        try {
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + inMs, pi);
        } catch (SecurityException e) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + inMs, pi);
        }
    }

    /** Nur fuer den Selbsttest: naechster Takt in wenigen Sekunden, optional ohne Trage-/Ruhepruefung. */
    public static void testInSekunden(Context c, int s, boolean erzwingen) {
        c.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit()
                .putBoolean("erzwingen", erzwingen).putLong("zuletzt_temp", 0).apply();
        planenIn(c, s * 1000L, 0);
    }

    public static void abbrechen(Context c) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        if (am == null) return;
        Intent i = new Intent(c, MessPlaner.class).setAction(ACTION_TAKT);
        am.cancel(PendingIntent.getBroadcast(c, 0x7A11, i, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE));
    }
}
