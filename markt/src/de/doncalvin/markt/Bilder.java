package de.doncalvin.markt;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** App-Symbole aus dem Netz, im Speicher und auf der Platte gemerkt. */
public final class Bilder {
    private Bilder() {}
    private static final LruCache<String, Bitmap> SPEICHER = new LruCache<>(40);
    private static final ExecutorService FADEN = Executors.newFixedThreadPool(2);

    public static void lade(final String url, final ImageView ziel) {
        if (url == null || url.isEmpty()) return;
        ziel.setTag(url);
        Bitmap b = SPEICHER.get(url);
        if (b != null) { ziel.setImageBitmap(b); return; }
        final File ordner = new File(ziel.getContext().getCacheDir(), "bilder");
        FADEN.execute(() -> {
            Bitmap bild = null;
            try {
                ordner.mkdirs();
                File f = new File(ordner, Integer.toHexString(url.hashCode()) + ".png");
                if (!f.exists()) {
                    HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
                    h.setConnectTimeout(15000); h.setReadTimeout(15000);
                    try (InputStream in = h.getInputStream(); FileOutputStream o = new FileOutputStream(f)) {
                        byte[] p = new byte[8192]; int n; while ((n = in.read(p)) > 0) o.write(p, 0, n);
                    }
                }
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true; BitmapFactory.decodeFile(f.getPath(), o);
                int probe = 1; while (o.outWidth / probe > 160) probe *= 2;
                o = new BitmapFactory.Options(); o.inSampleSize = probe;
                bild = BitmapFactory.decodeFile(f.getPath(), o);
                if (bild == null) f.delete();
            } catch (Exception ignored) {}
            if (bild == null) return;
            SPEICHER.put(url, bild);
            final Bitmap fertig = bild;
            ziel.post(() -> { if (url.equals(ziel.getTag())) ziel.setImageBitmap(fertig); });
        });
    }
}
