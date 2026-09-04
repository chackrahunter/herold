package de.doncalvin.herold;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Log;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.*;
import java.util.concurrent.*;
import javax.net.ssl.*;
import java.security.cert.*;
import java.security.KeyStore;

/**
 * Holt das echte App-Icon zu einer iOS-Bundle-ID.
 *
 * ANCS uebertraegt keine Bilder - aber Apple betreibt eine oeffentliche
 * Suchschnittstelle, die zu jeder Bundle-ID das App-Store-Icon liefert.
 * Ergebnisse werden auf der Uhr zwischengespeichert, damit jede App nur
 * einmal nachgeschlagen wird.
 */
public class IconHolen {

    private static final String TAG = "Herold";
    private static final ExecutorService pool = Executors.newSingleThreadExecutor();
    private static final Map<String, Bitmap> speicher = new ConcurrentHashMap<>();
    private static final Set<String> ohneTreffer = Collections.synchronizedSet(new HashSet<>());

    public interface Fertig { void icon(Bitmap b); }

    /**
     * Die Uhr hat ohne gekoppeltes Telefon eine falsche Systemzeit (Jahre daneben).
     * Dadurch gilt JEDES gueltige Zertifikat als "noch nicht gueltig" und der
     * Handshake scheitert mit "Chain validation failed".
     *
     * Diese Fabrik prueft die Zertifikatskette ganz normal weiter - Aussteller,
     * Signatur, Vertrauensanker - und laesst NUR die Datumspruefung aus.
     * Bewusst eng gehalten: sie wird ausschliesslich zum Laden oeffentlicher
     * App-Symbole benutzt, nie fuer Zugangsdaten.
     */
    private static SSLSocketFactory nachsichtigeFabrik;

    private static synchronized SSLSocketFactory fabrik() throws Exception {
        if (nachsichtigeFabrik != null) return nachsichtigeFabrik;

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(
                TrustManagerFactory.getDefaultAlgorithm());
        tmf.init((KeyStore) null);
        final X509TrustManager standard = (X509TrustManager) tmf.getTrustManagers()[0];

        X509TrustManager nachsichtig = new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] k, String t)
                    throws CertificateException { standard.checkClientTrusted(k, t); }

            @Override public void checkServerTrusted(X509Certificate[] kette, String t)
                    throws CertificateException {
                try {
                    standard.checkServerTrusted(kette, t);
                } catch (CertificateException e) {
                    if (!nurDatumsproblem(e)) throw e;   // alles andere bleibt ein Fehler
                    Log.i(TAG, "Zertifikat nur wegen Uhrzeit beanstandet - akzeptiert");
                }
            }

            @Override public X509Certificate[] getAcceptedIssuers() {
                return standard.getAcceptedIssuers();
            }
        };

        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[]{ nachsichtig }, null);
        nachsichtigeFabrik = ctx.getSocketFactory();
        return nachsichtigeFabrik;
    }

    /** Wahr, wenn die Beanstandung ausschliesslich an notBefore/notAfter liegt. */
    private static boolean nurDatumsproblem(Throwable t) {
        while (t != null) {
            if (t instanceof CertificateExpiredException
                    || t instanceof CertificateNotYetValidException) return true;
            String m = t.getMessage();
            if (m != null) {
                String s = m.toLowerCase();
                if (s.contains("not yet valid") || s.contains("expired")
                        || s.contains("validity check failed")) return true;
            }
            t = t.getCause();
        }
        return false;
    }

    private static void nachsichtigMachen(HttpURLConnection c) {
        if (c instanceof HttpsURLConnection) {
            try { ((HttpsURLConnection) c).setSSLSocketFactory(fabrik()); }
            catch (Exception e) { Log.i(TAG, "TLS-Fabrik nicht setzbar: " + e); }
        }
    }

    /** Liefert sofort aus dem Speicher, sonst wird im Hintergrund geladen. */
    public static Bitmap ausSpeicher(String bundleId) {
        return bundleId == null ? null : speicher.get(bundleId);
    }

    public static void hole(Context ctx, String bundleId, Fertig callback) {
        if (bundleId == null || bundleId.isEmpty()) return;
        Bitmap da = speicher.get(bundleId);
        if (da != null) { callback.icon(da); return; }
        if (ohneTreffer.contains(bundleId)) return;

        pool.execute(() -> {
            try {
                Log.i(TAG, "suche Icon fuer " + bundleId);
                Bitmap b = vonPlatte(ctx, bundleId);
                if (b == null) {
                    String url = artworkUrl(bundleId);
                    Log.i(TAG, "  artworkUrl = " + url);
                    if (url == null) { ohneTreffer.add(bundleId); return; }
                    b = lade(url);
                    Log.i(TAG, "  Bild geladen: " + (b != null));
                    if (b != null) aufPlatte(ctx, bundleId, b);
                }
                if (b != null) {
                    speicher.put(bundleId, b);
                    Log.i(TAG, "Icon geladen fuer " + bundleId);
                    callback.icon(b);
                } else {
                    ohneTreffer.add(bundleId);
                }
            } catch (Exception e) {
                Log.i(TAG, "Icon fehlgeschlagen fuer " + bundleId + ": " + e);
                ohneTreffer.add(bundleId);
            }
        });
    }

    /** Fragt Apples Suchschnittstelle nach der Bildadresse. */
    private static String artworkUrl(String bundleId) throws IOException {
        // Ohne Laenderangabe: der US-Katalog ist der vollstaendigste.
        String u = "https://itunes.apple.com/lookup?bundleId="
                 + URLEncoder.encode(bundleId, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        nachsichtigMachen(c);
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        c.setRequestProperty("User-Agent", "Herold/1.0");
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream()))) {
            String z; while ((z = r.readLine()) != null) sb.append(z);
        } finally { c.disconnect(); }

        String j = sb.toString();
        // Groesstes verfuegbares Bild bevorzugen
        for (String feld : new String[]{"artworkUrl512", "artworkUrl100", "artworkUrl60"}) {
            int i = j.indexOf("\"" + feld + "\":\"");
            if (i < 0) continue;
            int a = i + feld.length() + 4;
            int e = j.indexOf('"', a);
            if (e > a) return j.substring(a, e).replace("\\/", "/");
        }
        return null;
    }

    private static Bitmap lade(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        nachsichtigMachen(c);
        c.setConnectTimeout(8000); c.setReadTimeout(8000);
        try (InputStream in = c.getInputStream()) {
            Bitmap roh = BitmapFactory.decodeStream(in);
            if (roh == null) return null;
            return Bitmap.createScaledBitmap(roh, 128, 128, true);
        } finally { c.disconnect(); }
    }

    private static File datei(Context ctx, String bundleId) {
        File d = new File(ctx.getCacheDir(), "appicons");
        if (!d.exists()) d.mkdirs();
        return new File(d, bundleId.replaceAll("[^A-Za-z0-9._-]", "_") + ".png");
    }

    private static Bitmap vonPlatte(Context ctx, String bundleId) {
        File f = datei(ctx, bundleId);
        return f.exists() ? BitmapFactory.decodeFile(f.getAbsolutePath()) : null;
    }

    private static void aufPlatte(Context ctx, String bundleId, Bitmap b) {
        try (FileOutputStream o = new FileOutputStream(datei(ctx, bundleId))) {
            b.compress(Bitmap.CompressFormat.PNG, 100, o);
        } catch (Exception ignored) {}
    }
}
