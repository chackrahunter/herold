package de.doncalvin.herold;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.util.Log;

import com.samsung.android.service.health.tracking.ConnectionListener;
import com.samsung.android.service.health.tracking.HealthTrackerException;
import com.samsung.android.service.health.tracking.HealthTrackingService;
import com.samsung.android.service.health.tracking.data.HealthTrackerType;

/** Fragt einmalig ab, welche Sensoren diese Uhr freigibt, und schreibt es ins Log. */
public class SensorProbe extends Service {

    private HealthTrackingService dienst;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent i, int f, int id) {
        dienst = new HealthTrackingService(new ConnectionListener() {
            @Override public void onConnectionSuccess() {
                try {
                    StringBuilder b = new StringBuilder();
                    for (HealthTrackerType t : dienst.getTrackingCapability()
                            .getSupportHealthTrackerTypes()) {
                        b.append(t.name()).append(' ');
                    }
                    Log.i("HeroldTest", "SENSOREN version="
                            + dienst.getTrackingCapability().getVersion()
                            + " -> " + b);
                } catch (Throwable t) {
                    Log.e("HeroldTest", "SENSOREN Fehler", t);
                }
                stopSelf();
            }
            @Override public void onConnectionEnded() { stopSelf(); }
            @Override public void onConnectionFailed(HealthTrackerException e) {
                Log.e("HeroldTest", "SENSOREN Verbindung fehlgeschlagen: " + e.getErrorCode());
                stopSelf();
            }
        }, this);
        dienst.connectService();
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        try { if (dienst != null) dienst.disconnectService(); } catch (Exception ignored) {}
        super.onDestroy();
    }
}
