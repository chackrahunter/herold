package de.doncalvin.herold;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Sorgt dafuer, dass der iPhone-Dienst wirklich immer laeuft.
 *
 * Der Dienst allein reicht nicht. START_STICKY holt ihn zurueck, wenn ihn das
 * System wegen Speichermangel abschiesst - aber nicht, wenn Samsungs
 * Ruhemodus die App schlafen legt oder wenn sie aus einem anderen Grund
 * stillsteht. Deshalb klingelt hier ein Wecker, der regelmaessig nachsieht
 * und den Dienst notfalls wieder anwirft.
 *
 * Der Wecker traegt sich nach jedem Klingeln selbst neu ein. Ein wiederholender
 * Wecker waere bequemer, wird von Android aber im Ruhezustand gestreckt;
 * setExactAndAllowWhileIdle kommt durch.
 *
 * Was auch das nicht ueberlebt: ein ausdrueckliches Beenden durch den Nutzer
 * ("App stoppen" in den Einstellungen). Dabei loescht Android auch alle Wecker.
 * Erst ein Neustart der Uhr oder ein Oeffnen der App bringt ihn dann zurueck -
 * das ist so vorgesehen und laesst sich ohne Systemrechte nicht umgehen.
 */
public class Waechter extends BroadcastReceiver {

    private static final String TAG = "HeroldWaechter";
    public static final String ACTION_PRUEFEN = "de.doncalvin.herold.WAECHTER";

    /** Alle zehn Minuten nachsehen. Das kostet kaum Strom - der Wecker weckt
     *  nur einen Empfaenger, der in Millisekunden fertig ist. */
    private static final long ABSTAND_MS = 10 * 60 * 1000L;

    @Override
    public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        Log.i(TAG, "geweckt durch " + a);
        starteDienst(c);
        if (Intent.ACTION_POWER_CONNECTED.equals(a) || Intent.ACTION_POWER_DISCONNECTED.equals(a)) {
            try { c.startForegroundService(new Intent(c, AncsService.class).setAction(AncsService.ACTION_STROM)); }
            catch (Throwable ignored) {}
        }
        planen(c);            // gleich den naechsten Weckruf setzen
        if (MessPlaner.aktiv(c)) MessPlaner.planen(c, 0);   // Messtakt mit absichern
    }

    /** Startet den Dienst. Laeuft er schon, ist das ein billiger Aufruf. */
    public static void starteDienst(Context c) {
        try {
            c.startForegroundService(new Intent(c, AncsService.class));
        } catch (Throwable t) {
            Log.w(TAG, "Dienst liess sich nicht starten", t);
        }
    }

    /** Traegt den naechsten Weckruf ein. */
    public static void planen(Context c) {
        try {
            AlarmManager am = c.getSystemService(AlarmManager.class);
            if (am == null) return;
            long wann = System.currentTimeMillis() + ABSTAND_MS;
            am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, wann, absicht(c));
        } catch (Throwable t) {
            Log.w(TAG, "Weckruf liess sich nicht eintragen", t);
        }
    }

    private static PendingIntent absicht(Context c) {
        Intent i = new Intent(c, Waechter.class).setAction(ACTION_PRUEFEN);
        return PendingIntent.getBroadcast(c, 0x4E17, i,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }
}
