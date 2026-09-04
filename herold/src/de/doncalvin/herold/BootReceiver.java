package de.doncalvin.herold;

import android.content.*;
import android.util.Log;

/** Startet Herold nach einem Neustart der Uhr wieder von selbst. */
public class BootReceiver extends BroadcastReceiver {
    @Override public void onReceive(Context c, Intent i) {
        String a = i.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_LOCKED_BOOT_COMPLETED.equals(a)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)
                || "android.intent.action.QUICKBOOT_POWERON".equals(a)) {
            Log.i("Herold", "Start durch: " + a);
            Waechter.starteDienst(c);
            Waechter.planen(c);
            if (MessPlaner.aktiv(c)) MessPlaner.planen(c, 0);
        }
    }
}
