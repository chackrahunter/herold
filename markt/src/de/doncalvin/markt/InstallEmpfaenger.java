package de.doncalvin.markt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

/** Nimmt den Ausgang einer Installations-Sitzung entgegen und reicht ihn an den Ladedienst weiter. */
public class InstallEmpfaenger extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        int status = i.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
        String paket = i.getStringExtra("paket");
        String meldung = i.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            Intent frage = i.getParcelableExtra(Intent.EXTRA_INTENT);
            if (frage != null) {
                frage.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try { c.startActivity(frage); } catch (Exception e) { Log.w(Play.TAG, "Bestaetigung nicht startbar: " + e); }
            }
            Ladedienst.rueckfrage(paket);
            return;
        }
        Ladedienst.ergebnis(paket, status == PackageInstaller.STATUS_SUCCESS, meldung);
    }
}
