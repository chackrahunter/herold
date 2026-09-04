package de.doncalvin.herold;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.os.ParcelUuid;
import android.util.Log;

/**
 * Stufe A der Kontakte-Sonde: nur lesen, kein Funkverkehr.
 *
 * Beantwortet, ob die App unter der Adresse, die sie sieht, den klassischen
 * Bluetooth-Kontext des iPhones findet - insbesondere die Dienst-UUIDs fuer
 * Telefonbuch (PBAP 0x112F) und Freisprechen (HFP-AG 0x111F). Ohne diese
 * Antwort ist jeder weitere Schritt blind.
 */
public final class Sonde {
    private static final String TAG = "HeroldSonde";

    /**
     * Stufe B: eine SDP-Abfrage erzwingt eine klassische (BR/EDR-)Verbindung
     * zum iPhone. Kommt ACTION_UUID mit einer Liste zurueck, die 0x112F (PBAP)
     * enthaelt, ist der Weg zum Telefonbuch offen. Kommt nichts, ist die
     * klassische Verbindung blockiert. Es wird nichts gekoppelt, nichts
     * geloescht, kein Socket geoeffnet - nur gefragt.
     */
    public static void stufeB(final Context c) {
        try {
            BluetoothManager bm = c.getSystemService(BluetoothManager.class);
            BluetoothAdapter a = bm == null ? null : bm.getAdapter();
            if (a == null) { Log.w(TAG, "B: kein Adapter"); return; }
            BluetoothDevice ziel = null;
            for (BluetoothDevice d : a.getBondedDevices()) { ziel = d; break; }
            if (ziel == null) { Log.w(TAG, "B: kein gebondetes Geraet"); return; }
            final BluetoothDevice dev = ziel;
            final long start = System.currentTimeMillis();
            android.content.BroadcastReceiver rx = new android.content.BroadcastReceiver() {
                @Override public void onReceive(Context ctx, android.content.Intent i) {
                    BluetoothDevice d = i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    android.os.Parcelable[] u = i.getParcelableArrayExtra(BluetoothDevice.EXTRA_UUID);
                    StringBuilder b = new StringBuilder();
                    boolean pbap = false, hfpAg = false;
                    if (u != null) for (android.os.Parcelable p : u) {
                        String s = p.toString().toLowerCase();
                        b.append(s, 4, 8).append(' ');
                        if (s.startsWith("0000112f")) pbap = true;
                        if (s.startsWith("0000111f")) hfpAg = true;
                    }
                    Log.i(TAG, "B: ACTION_UUID nach " + (System.currentTimeMillis() - start) + " ms von "
                            + (d == null ? "?" : d.getAddress()) + " uuids=" + (u == null ? "null" : u.length)
                            + " [" + b.toString().trim() + "] PBAP=" + pbap + " HFP_AG=" + hfpAg);
                    try { ctx.unregisterReceiver(this); } catch (Exception ignored) {}
                }
            };
            c.registerReceiver(rx, new android.content.IntentFilter(BluetoothDevice.ACTION_UUID),
                    Context.RECEIVER_EXPORTED);
            boolean ok = dev.fetchUuidsWithSdp();
            Log.i(TAG, "B: fetchUuidsWithSdp(" + dev.getAddress() + ") = " + ok + " - warte auf ACTION_UUID");
            new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                try { c.unregisterReceiver(rx); Log.w(TAG, "B: nach 30 s kein ACTION_UUID - klassischer Weg blockiert"); }
                catch (Exception ignored) { /* schon abgemeldet = Antwort kam */ }
            }, 30000);
        } catch (Throwable t) {
            Log.e(TAG, "B: Fehler", t);
        }
    }

    public static void stufeA(Context c) {
        try {
            BluetoothManager bm = c.getSystemService(BluetoothManager.class);
            BluetoothAdapter a = bm == null ? null : bm.getAdapter();
            if (a == null) { Log.w(TAG, "A: kein Bluetooth-Adapter"); return; }
            int n = 0;
            for (BluetoothDevice d : a.getBondedDevices()) {
                n++;
                ParcelUuid[] u = d.getUuids();
                StringBuilder b = new StringBuilder();
                boolean pbap = false, hfpAg = false;
                if (u != null) for (ParcelUuid p : u) {
                    String s = p.toString().toLowerCase();
                    b.append(s, 4, 8).append(' ');
                    if (s.startsWith("0000112f")) pbap = true;
                    if (s.startsWith("0000111f")) hfpAg = true;
                }
                Log.i(TAG, "A: " + d.getAddress() + " typ=" + d.getType()
                        + " (1=classic 2=LE 3=dual) bond=" + d.getBondState()
                        + " klasse=" + d.getBluetoothClass()
                        + " uuids=" + (u == null ? "null" : u.length)
                        + " [" + b.toString().trim() + "]"
                        + " PBAP=" + pbap + " HFP_AG=" + hfpAg);
            }
            if (n == 0) Log.w(TAG, "A: keine gebondeten Geraete sichtbar");
        } catch (Throwable t) {
            Log.e(TAG, "A: Fehler", t);
        }
    }
}
