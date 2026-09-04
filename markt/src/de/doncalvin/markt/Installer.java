package de.doncalvin.markt;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.os.Build;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

/**
 * Installation ueber den Paket-Installer des Systems (Sitzung).
 * Beim ersten Mal fragt das System einmal nach - das ist der eine Dialog, den
 * Android jedem Laden ausser dem Play Store vorschreibt.
 */
public final class Installer {
    private Installer() {}
    public static final String ACTION_STATUS = "de.doncalvin.markt.INSTALL_STATUS";

    public static int installieren(Context c, String paket, List<File> dateien) throws IOException {
        PackageInstaller pi = c.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams p = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        p.setAppPackageName(paket);
        p.setInstallReason(PackageManager.INSTALL_REASON_USER);
        long gesamt = 0; for (File f : dateien) gesamt += f.length();
        if (gesamt > 0) p.setSize(gesamt);
        if (Build.VERSION.SDK_INT >= 31) p.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED);
        if (Build.VERSION.SDK_INT >= 33) p.setPackageSource(PackageInstaller.PACKAGE_SOURCE_STORE);
        int id = pi.createSession(p);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            for (File f : dateien) {
                try (InputStream in = new FileInputStream(f); OutputStream o = s.openWrite(f.getName(), 0, f.length())) {
                    byte[] puffer = new byte[65536]; int n;
                    while ((n = in.read(puffer)) > 0) o.write(puffer, 0, n);
                    s.fsync(o);
                }
            }
            Intent i = new Intent(c, InstallEmpfaenger.class).setAction(ACTION_STATUS).putExtra("paket", paket);
            PendingIntent pe = PendingIntent.getBroadcast(c, id, i, PendingIntent.FLAG_MUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            s.commit(pe.getIntentSender());
        } catch (IOException e) {
            try { pi.abandonSession(id); } catch (Exception ignored) {}
            throw e;
        }
        return id;
    }
}
