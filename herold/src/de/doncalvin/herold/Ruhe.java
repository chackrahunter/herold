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
 * Bewegt sich der Arm? Vier Sekunden Beschleunigung, Standardabweichung des
 * Betrags - die Schwerkraft faellt dabei heraus, uebrig bleibt die Bewegung.
 *
 * Der Sensorhub sammelt die Werte selbst (Hardware-FIFO) und weckt den
 * Prozessor nur einmal am Ende; das ist der sparsamste Weg, Ruhe zu erkennen.
 *
 *   < 0,15 m/s²  ruhig       -> auch die lange Rhythmusmessung erlaubt
 *   < 0,50 m/s²  leicht      -> nur kurze Messungen (Puls, Temperatur)
 *   sonst        Bewegung    -> spaeter noch einmal
 */
public final class Ruhe {

    public interface Antwort { void ergebnis(float sigma); }

    private Ruhe() {}

    public static void messe(Context c, final Antwort antwort) {
        final SensorManager sm = c.getSystemService(SensorManager.class);
        final Sensor s = sm == null ? null : sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (s == null) { antwort.ergebnis(Float.NaN); return; }
        final float[] werte = new float[160];
        final int[] n = { 0 };
        final boolean[] erledigt = { false };
        final Handler h = new Handler(Looper.getMainLooper());

        final SensorEventListener l = new SensorEventListener() {
            @Override public void onSensorChanged(SensorEvent e) {
                if (n[0] < werte.length) {
                    float x = e.values[0], y = e.values[1], z = e.values[2];
                    werte[n[0]++] = (float) Math.sqrt(x * x + y * y + z * z);
                }
            }
            @Override public void onAccuracyChanged(Sensor s, int a) {}
        };
        // 25 Hz, Berichte duerfen bis zu 4 s im Sensorhub warten
        sm.registerListener(l, s, 40000, 4000000);
        h.postDelayed(() -> {
            if (erledigt[0]) return;
            erledigt[0] = true;
            try { sm.unregisterListener(l); } catch (Exception ignored) {}
            if (n[0] < 20) { antwort.ergebnis(Float.NaN); return; }
            float m = 0f; for (int i = 0; i < n[0]; i++) m += werte[i]; m /= n[0];
            float v = 0f; for (int i = 0; i < n[0]; i++) v += (werte[i] - m) * (werte[i] - m);
            float sigma = (float) Math.sqrt(v / n[0]);
            Log.i("HeroldRuhe", "sigma=" + String.format("%.3f", sigma) + " m/s² aus " + n[0] + " Werten");
            antwort.ergebnis(sigma);
        }, 4500);
    }
}
