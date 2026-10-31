package de.doncalvin.markt;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.util.Log;

/** Nimmt den Ausgang einer Installations-Sitzung entgegen und reicht ihn an den Ladedienst weiter. */
public class InstallEmpfaenger extends BroadcastReceiver {
    private static final String KANAL_FRAGE = "markt_frage";
    private static final int RUECKFRAGE = 4101;

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
                // Laeuft der Markt nur noch als Dienst im Hintergrund, blockiert Android den Dialog
                // ("Background activity launch blocked") - ohne Meldung haengt die Sitzung dann ewig.
                meldeRueckfrage(c, paket, frage);
            }
            Ladedienst.rueckfrage(paket);
            return;
        }
        try { c.getSystemService(NotificationManager.class).cancel(RUECKFRAGE); } catch (Exception ignored) {}
        Ladedienst.ergebnis(paket, status == PackageInstaller.STATUS_SUCCESS, meldung);
    }

    /** Meldung zum Antippen - ein Antippen darf den Dialog auch aus dem Hintergrund oeffnen. */
    private static void meldeRueckfrage(Context c, String paket, Intent frage) {
        try {
            NotificationManager nm = c.getSystemService(NotificationManager.class);
            nm.createNotificationChannel(new NotificationChannel(KANAL_FRAGE, "Markt: Bestätigungen", NotificationManager.IMPORTANCE_HIGH));
            PendingIntent pi = PendingIntent.getActivity(c, 1, frage,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            Notification n = new Notification.Builder(c, KANAL_FRAGE)
                    .setSmallIcon(android.R.drawable.stat_sys_download_done)
                    .setContentTitle("Installation bestätigen")
                    .setContentText("Tippen, um " + paket + " zu installieren")
                    .setContentIntent(pi)
                    .setAutoCancel(true)
                    .build();
            nm.notify(RUECKFRAGE, n);
        } catch (Exception e) { Log.w(Play.TAG, "Rueckfrage-Meldung fehlgeschlagen: " + e); }
    }
}
