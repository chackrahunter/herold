package de.doncalvin.herold;

import android.app.Activity;
import android.hardware.*;
import android.os.Bundle;
import android.util.Log;
import java.util.List;

/** Testet, welche Sensoren eine normale App tatsaechlich lesen darf. */
public class ProbeActivity extends Activity implements SensorEventListener {

    private static final String T = "HeroldProbe";
    private SensorManager sm;

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        sm = (SensorManager) getSystemService(SENSOR_SERVICE);

        List<Sensor> all = sm.getSensorList(Sensor.TYPE_ALL);
        Log.i(T, "=== sichtbare Sensoren: " + all.size() + " ===");
        for (Sensor s : all) {
            String n = s.getStringType();
            if (n != null && (n.contains("ecg") || n.contains("hr") || n.contains("bp")
                    || n.contains("bia") || n.contains("spo2") || n.contains("heart")
                    || n.contains("ppg") || n.contains("ihrn"))) {
                Log.i(T, "SICHTBAR: " + s.getName() + " | " + n + " | type=" + s.getType());
            }
        }

        // Gezielt versuchen zu abonnieren
        tryRegister("com.samsung.sensor.ecg");
        tryRegister("com.samsung.sensor.hr_raw");
        tryRegister("com.samsung.sensor.hrm_bp");
        tryRegister("com.samsung.sensor.ppg_ihrn");
        tryRegister("com.samsung.sensor.spo2manual");
        tryRegister("com.samsung.sensor.bia");
        registerStandard(Sensor.TYPE_HEART_RATE, "heart_rate");
    }

    private void tryRegister(String stringType) {
        Sensor found = null;
        for (Sensor s : sm.getSensorList(Sensor.TYPE_ALL)) {
            if (stringType.equals(s.getStringType())) { found = s; break; }
        }
        if (found == null) { Log.i(T, "NICHT SICHTBAR: " + stringType); return; }
        boolean ok = sm.registerListener(this, found, SensorManager.SENSOR_DELAY_NORMAL);
        Log.i(T, (ok ? "ABONNIERT: " : "ABGELEHNT:  ") + stringType);
    }

    private void registerStandard(int type, String label) {
        Sensor s = sm.getDefaultSensor(type);
        if (s == null) { Log.i(T, "NICHT SICHTBAR: " + label); return; }
        boolean ok = sm.registerListener(this, s, SensorManager.SENSOR_DELAY_NORMAL);
        Log.i(T, (ok ? "ABONNIERT: " : "ABGELEHNT:  ") + label + " (" + s.getName() + ")");
    }

    @Override public void onSensorChanged(SensorEvent e) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < Math.min(e.values.length, 6); i++) sb.append(e.values[i]).append(" ");
        Log.i(T, "DATEN " + e.sensor.getStringType() + " -> " + sb);
    }
    @Override public void onAccuracyChanged(Sensor s, int a) { }
    @Override protected void onDestroy() { super.onDestroy(); sm.unregisterListener(this); }
}
