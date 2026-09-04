package de.doncalvin.herold;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Wird die Uhr gerade getragen?
 *
 * Die Uhr hat dafuer zwei Sensoren: den regulaeren
 * TYPE_LOW_LATENCY_OFFBODY_DETECT und einen stromsparenden von Samsung. Beide
 * melden nur bei Aenderung, kosten also im Ruhezustand fast nichts - das ist
 * genau richtig fuer Messungen, die von allein im Hintergrund laufen sollen.
 *
 * Ohne diese Pruefung misst die Uhr auch an der Ladeschale weiter und legt
 * Zahlen ab, die niemandem gehoeren. Das faellt nicht einmal auf: die
 * Hauttemperatur liefert dann einfach die Temperatur der Unterlage.
 */
public class Traegt {

    public interface Antwort { void ergebnis(boolean getragen); }

    private static final String TAG = "HeroldTraegt";

    /**
     * Fragt einmal nach. Der Sensor meldet nur bei Aenderung, liefert beim
     * Anmelden aber den aktuellen Stand nach; kommt binnen der Frist nichts,
     * gilt die Uhr als getragen - lieber einmal zu viel messen als eine
     * gewollte Messung verweigern.
     */
    public static void frage(Context c, final long fristMs, final Antwort antwort) {
        SensorManager sm = c.getSystemService(SensorManager.class);
        Sensor s = null;
        if (sm != null) {
            s = sm.getDefaultSensor(Sensor.TYPE_LOW_LATENCY_OFFBODY_DETECT);
            if (s == null) {
                // Samsungs stromsparende Ausfuehrung, falls die regulaere fehlt
                for (Sensor kandidat : sm.getSensorList(Sensor.TYPE_ALL)) {
                    String t = kandidat.getStringType();
                    if (t != null && t.contains("offbody")) { s = kandidat; break; }
                }
            }
        }
        if (sm == null || s == null) {
            Log.i(TAG, "kein Trage-Sensor vorhanden");
            antwort.ergebnis(true);
            return;
        }

        final SensorManager manager = sm;
        final Handler uhr = new Handler(Looper.getMainLooper());
        final boolean[] erledigt = { false };

        final SensorEventListener hoerer = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) {
                if (erledigt[0]) return;
                erledigt[0] = true;
                boolean getragen = e.values.length > 0 && e.values[0] >= 0.5f;
                Log.i(TAG, "Sensor meldet " + e.values[0] + " -> "
                        + (getragen ? "getragen" : "abgelegt"));
                manager.unregisterListener(this);
                uhr.removeCallbacksAndMessages(null);
                antwort.ergebnis(getragen);
            }
            @Override public void onAccuracyChanged(Sensor s, int a) {}
        };

        manager.registerListener(hoerer, s, SensorManager.SENSOR_DELAY_FASTEST);

        uhr.postDelayed(new Runnable() {
            @Override public void run() {
                if (erledigt[0]) return;
                erledigt[0] = true;
                Log.i(TAG, "keine Antwort binnen " + fristMs + " ms - gilt als getragen");
                try { manager.unregisterListener(hoerer); } catch (Exception ignored) {}
                antwort.ergebnis(true);
            }
        }, fristMs);
    }
}
