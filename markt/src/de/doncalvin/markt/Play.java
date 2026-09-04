package de.doncalvin.markt;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.aurora.gplayapi.Constants;
import com.aurora.gplayapi.data.models.App;
import com.aurora.gplayapi.data.models.AuthData;
import com.aurora.gplayapi.data.models.PlayFile;
import com.aurora.gplayapi.data.models.StreamBundle;
import com.aurora.gplayapi.data.models.StreamCluster;
import com.aurora.gplayapi.data.serializers.PropertiesSerializer;
import com.aurora.gplayapi.helpers.AppDetailsHelper;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import com.aurora.gplayapi.helpers.AuthHelper;
import com.aurora.gplayapi.helpers.PurchaseHelper;
import com.aurora.gplayapi.helpers.SearchHelper;
import com.aurora.gplayapi.helpers.TopChartsHelper;
import com.aurora.gplayapi.helpers.web.WebCategoryStreamHelper;
import com.aurora.gplayapi.helpers.web.WebSearchHelper;
import com.aurora.gplayapi.helpers.web.WebStreamHelper;
import com.aurora.gplayapi.helpers.contracts.StreamContract;
import com.aurora.gplayapi.network.DefaultHttpClient;
import com.aurora.gplayapi.network.IHttpClient;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import kotlinx.serialization.json.Json;

/**
 * Google Play ohne Google-Konto.
 *
 * Aurora betreibt einen "Token-Dienst", der anonyme Play-Konten verteilt. Wir
 * schicken ihm das Geraeteprofil der Uhr und bekommen E-Mail plus Token; daraus
 * baut gplayapi die volle Anmeldung (Check-in, Geraetekonfiguration). Das
 * Ergebnis wird gespeichert, damit nicht bei jedem Start ein neues Geraet bei
 * Google angemeldet wird. Alle Aufrufe hier sind blockierend - nur aus dem
 * Hintergrund verwenden.
 */
public final class Play {
    private Play() {}
    static final String TAG = "Markt";
    static final String TOKEN_DIENST = "https://auroraoss.com/api/auth";
    static final String KENNUNG = "com.aurora.store-4.8.4-76";
    static final String KAT_UHR_APPS = "/store/apps/category/ANDROID_WEAR";
    static final String KAT_ZIFFERBLAETTER = "/store/apps/category/WATCH_FACE";
    static IHttpClient netz() { return DefaultHttpClient.INSTANCE; }

    private static AuthData auth;

    static synchronized AuthData anmelden(Context c) throws Exception {
        if (auth != null) return auth;
        SharedPreferences sp = c.getSharedPreferences("play", Context.MODE_PRIVATE);
        String alt = sp.getString("auth", null);
        if (alt != null) {
            try {
                AuthData a = Json.Default.decodeFromString(AuthData.Companion.serializer(), alt);
                if (AuthHelper.INSTANCE.isValid(a, "com.sec.android.app.sbrowser")) { auth = a; return a; }
                Log.i(TAG, "gespeicherte Anmeldung abgelaufen");
            } catch (Exception e) { Log.w(TAG, "gespeicherte Anmeldung unbrauchbar: " + e); }
        }
        Properties p = Geraet.eigenschaften(c);
        String antwort = post(TOKEN_DIENST, Json.Default.encodeToString(PropertiesSerializer.INSTANCE, p));
        JSONObject j = new JSONObject(antwort);
        String email = j.getString("email");
        String token = j.optString("authToken", j.optString("auth", ""));
        if (token.isEmpty()) throw new Exception("Token-Dienst ohne Token: " + antwort.substring(0, Math.min(120, antwort.length())));
        AuthData a = AuthHelper.INSTANCE.build(email, token, AuthHelper.Token.AUTH, true, p, Locale.getDefault());
        sp.edit().putString("auth", Json.Default.encodeToString(AuthData.Companion.serializer(), a)).apply();
        auth = a;
        Log.i(TAG, "anonym angemeldet als " + email);
        return a;
    }

    static synchronized void vergessen(Context c) {
        auth = null;
        c.getSharedPreferences("play", Context.MODE_PRIVATE).edit().remove("auth").apply();
    }

    private static String post(String url, String body) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(20000); h.setReadTimeout(30000);
        h.setRequestMethod("POST"); h.setDoOutput(true);
        h.setRequestProperty("User-Agent", KENNUNG);
        h.setRequestProperty("Content-Type", "application/json");
        try (OutputStream o = h.getOutputStream()) { o.write(body.getBytes(StandardCharsets.UTF_8)); }
        int code = h.getResponseCode();
        InputStream in = code < 400 ? h.getInputStream() : h.getErrorStream();
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] puffer = new byte[8192]; int n;
        if (in != null) while ((n = in.read(puffer)) > 0) bo.write(puffer, 0, n);
        String text = bo.toString("UTF-8");
        if (code != 200) throw new Exception("Token-Dienst antwortet " + code + (code == 429 ? " (ausgelastet, spaeter noch einmal)" : ""));
        return text;
    }

    /** Fuehrt einen Play-Aufruf aus; bei abgelaufener Anmeldung einmal neu anmelden. */
    interface Aufruf<T> { T mit(AuthData a) throws Exception; }
    static <T> T mit(Context c, Aufruf<T> f) throws Exception {
        try { return f.mit(anmelden(c)); }
        catch (Exception e) {
            String m = String.valueOf(e.getMessage());
            if (m.contains("401") || m.contains("Unauthorized") || m.contains("403")) {
                Log.w(TAG, "neu anmelden nach: " + m);
                vergessen(c);
                return f.mit(anmelden(c));
            }
            throw e;
        }
    }

    /** Suche: die native Suche liefert unter dem Uhr-Profil nichts, also ueber die Web-Suche. */
    static List<AppEintrag> suchen(Context c, String frage) throws Exception {
        return mit(c, a -> {
            StreamBundle b = new WebSearchHelper().using(netz()).with(Locale.GERMANY).searchResults(frage, "");
            Log.i(TAG, "WebSuche '" + frage + "': " + beschreibe(b));
            return sammeln(b);
        });
    }

    /** Apps aus Plays Smartwatch-Kategorie - reine Uhr-Apps, ohne Handy-Ballast. */
    static List<AppEintrag> fuerUhr(Context c) throws Exception { return ausKategorie(c, KAT_UHR_APPS); }
    /** Ziffernblaetter aus Plays Watch-Face-Kategorie. */
    static List<AppEintrag> zifferblaetter(Context c) throws Exception { return ausKategorie(c, KAT_ZIFFERBLAETTER); }

    private static List<AppEintrag> ausKategorie(Context c, String pfad) throws Exception {
        return mit(c, a -> {
            StreamBundle b = new WebCategoryStreamHelper().using(netz()).with(Locale.GERMANY).fetch(pfad);
            Log.i(TAG, "Kategorie " + pfad + ": " + beschreibe(b));
            return sammeln(b);
        });
    }

    /** Mehrere Apps in einem Aufruf abfragen (bulkDetails) - für den Update-Check. */
    static java.util.Map<String, AppEintrag> bulkDetails(Context c, java.util.List<String> pakete) throws Exception {
        return mit(c, a -> {
            java.util.Map<String, AppEintrag> m = new java.util.HashMap<>();
            java.util.List<App> apps = new AppDetailsHelper(a).getAppByPackageName(pakete);
            for (App app : apps) m.put(app.getPackageName(), AppEintrag.von(app));
            return m;
        });
    }

    /**
     * Installierte, selbst hinzugefügte Apps mit verfügbarem Update.
     * Vergleicht die installierte Version mit Plays neuester (bulkDetails).
     */
    static List<AppEintrag> updates(Context c) throws Exception {
        PackageManager pm = c.getPackageManager();
        java.util.Map<String, Long> installiert = new java.util.HashMap<>();
        for (PackageInfo p : pm.getInstalledPackages(0)) {
            if (p.applicationInfo == null) continue;
            if ((p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (c.getPackageName().equals(p.packageName)) continue;
            installiert.put(p.packageName, p.getLongVersionCode());
        }
        List<AppEintrag> raus = new ArrayList<>();
        if (installiert.isEmpty()) return raus;
        java.util.Map<String, AppEintrag> neu = bulkDetails(c, new ArrayList<>(installiert.keySet()));
        for (java.util.Map.Entry<String, Long> e : installiert.entrySet()) {
            AppEintrag d = neu.get(e.getKey());
            if (d != null && d.versionCode > e.getValue()) { d.installiertCode = e.getValue(); raus.add(d); }
        }
        return raus;
    }

    // ---- Vollstaendiger Uhr-Katalog -------------------------------------
    // Plays Wear-Kategorien tief durchblaettert. Das ist Googles eigene Liste
    // aller Wear-OS-Apps; danach ist die Suche vollstaendig und sofort.
    private static volatile List<AppEintrag> katalogSpeicher;

    /** Bereits geladener Katalog oder null - blockiert nie. */
    static List<AppEintrag> katalogImSpeicher() { return katalogSpeicher; }

    static List<AppEintrag> katalog(Context c) throws Exception {
        if (katalogSpeicher != null) return katalogSpeicher;
        List<AppEintrag> platte = katalogVonPlatte(c);
        if (platte != null) { katalogSpeicher = platte; return platte; }
        return katalogNeu(c);
    }

    /** Baut den Katalog neu und legt ihn auf der Platte ab. Dauert beim ersten Mal. */
    static synchronized List<AppEintrag> katalogNeu(Context c) throws Exception {
        if (katalogSpeicher != null) return katalogSpeicher;
        java.util.LinkedHashMap<String, AppEintrag> map = new java.util.LinkedHashMap<>();
        sammleKategorie(c, KAT_UHR_APPS, map);
        sammleKategorie(c, KAT_ZIFFERBLAETTER, map);
        List<AppEintrag> l = new ArrayList<>(map.values());
        Log.i(TAG, "Katalog neu: " + l.size() + " Uhr-Apps");
        katalogAufPlatte(c, l);
        katalogSpeicher = l;
        return l;
    }

    private static void sammleKategorie(Context c, String pfad, java.util.LinkedHashMap<String, AppEintrag> map) throws Exception {
        mit(c, a -> {
            WebStreamHelper w = new WebStreamHelper().using(netz()).with(Locale.GERMANY);
            WebCategoryStreamHelper cs = new WebCategoryStreamHelper().using(netz()).with(Locale.GERMANY);
            StreamBundle b = cs.fetch(pfad);
            int bundelRunden = 0;
            while (b != null && !b.getStreamClusters().isEmpty()) {
                for (StreamCluster k : b.getStreamClusters().values()) {
                    schluck(k, map);
                    String weiter = k.getClusterNextPageUrl();
                    int r = 0;
                    while (weiter != null && !weiter.isEmpty() && r++ < 5) {
                        StreamCluster n = w.nextStreamCluster(k.getId(), weiter);
                        if (n.getClusterAppList().isEmpty()) break;
                        schluck(n, map);
                        weiter = n.getClusterNextPageUrl();
                    }
                }
                String bWeiter = b.getStreamNextPageUrl();
                if (bWeiter == null || bWeiter.isEmpty() || bundelRunden++ >= 4) break;
                b = w.nextStreamBundle(b.getId(), StreamContract.Category.APPLICATION, bWeiter);
            }
            Log.i(TAG, "Kategorie " + pfad + " gesammelt, Stand " + map.size());
            return 0;
        });
    }

    private static void schluck(StreamCluster k, java.util.LinkedHashMap<String, AppEintrag> map) {
        for (App app : k.getClusterAppList()) {
            AppEintrag e = AppEintrag.von(app);
            if (!map.containsKey(e.paket)) map.put(e.paket, e);
        }
    }

    private static java.io.File katalogDatei(Context c) { return new java.io.File(c.getFilesDir(), "uhrkatalog.json"); }

    private static void katalogAufPlatte(Context c, List<AppEintrag> l) {
        try {
            org.json.JSONArray arr = new org.json.JSONArray();
            for (AppEintrag e : l) {
                org.json.JSONObject o = new org.json.JSONObject();
                o.put("p", e.paket); o.put("n", e.name); o.put("d", e.entwickler); o.put("i", e.symbolUrl);
                o.put("v", e.versionCode); o.put("g", e.groesse); o.put("b", e.bewertung);
                o.put("o", e.offerType); o.put("k", e.kostenlos); o.put("pr", e.preis); o.put("ku", e.kurz);
                arr.put(o);
            }
            org.json.JSONObject wurzel = new org.json.JSONObject();
            wurzel.put("zeit", System.currentTimeMillis()); wurzel.put("apps", arr);
            java.io.FileWriter fw = new java.io.FileWriter(katalogDatei(c)); fw.write(wurzel.toString()); fw.close();
        } catch (Exception e) { Log.w(TAG, "Katalog speichern: " + e); }
    }

    private static List<AppEintrag> katalogVonPlatte(Context c) {
        try {
            java.io.File f = katalogDatei(c);
            if (!f.exists()) return null;
            StringBuilder sb = new StringBuilder(); java.io.BufferedReader r = new java.io.BufferedReader(new java.io.FileReader(f));
            String z; while ((z = r.readLine()) != null) sb.append(z); r.close();
            org.json.JSONObject wurzel = new org.json.JSONObject(sb.toString());
            if (System.currentTimeMillis() - wurzel.optLong("zeit") > 24L * 3600 * 1000) return null; // taeglich frisch
            org.json.JSONArray arr = wurzel.getJSONArray("apps");
            List<AppEintrag> l = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                org.json.JSONObject o = arr.getJSONObject(i);
                AppEintrag e = new AppEintrag();
                e.paket = o.getString("p"); e.name = o.optString("n"); e.entwickler = o.optString("d"); e.symbolUrl = o.optString("i");
                e.versionCode = o.optLong("v"); e.groesse = o.optLong("g"); e.bewertung = (float) o.optDouble("b");
                e.offerType = o.optInt("o"); e.kostenlos = o.optBoolean("k", true); e.preis = o.optString("pr"); e.kurz = o.optString("ku");
                l.add(e);
            }
            Log.i(TAG, "Katalog von Platte: " + l.size());
            return l.isEmpty() ? null : l;
        } catch (Exception e) { Log.w(TAG, "Katalog laden: " + e); return null; }
    }

    /** Suche im Uhr-Katalog nach Name oder Entwickler - vollstaendig und sofort. */
    static List<AppEintrag> katalogSuche(Context c, String frage) throws Exception {
        String f = frage.toLowerCase(Locale.GERMANY).trim();
        List<AppEintrag> treffer = new ArrayList<>();
        for (AppEintrag e : katalog(c))
            if (e.name.toLowerCase(Locale.GERMANY).contains(f) || e.entwickler.toLowerCase(Locale.GERMANY).contains(f) || e.paket.toLowerCase(Locale.GERMANY).contains(f))
                treffer.add(e);
        return treffer;
    }

    static AppEintrag details(Context c, String paket) throws Exception {
        return mit(c, a -> {
            App app = new AppDetailsHelper(a).getAppByPackageName(paket);
            StringBuilder geraete = new StringBuilder();
            for (com.aurora.gplayapi.data.models.ActiveDevice d : app.getCompatibility())
                geraete.append(d.getName()).append("/");
            Log.i(TAG, "MESS " + paket + " | restr=" + app.getRestriction().getRestriction()
                    + " | kat=" + app.getCategoryName() + " | compat=" + app.getCompatibility().size()
                    + " | geraete=" + geraete);
            return AppEintrag.von(app);
        });
    }

    static List<PlayFile> dateien(Context c, AppEintrag e) throws Exception {
        return mit(c, a -> new PurchaseHelper(a).purchase(e.paket, e.versionCode, e.offerType,
                null, null, null, Constants.PatchFormat.GZIPPED_BSDIFF));
    }

    private static String beschreibe(StreamBundle b) {
        StringBuilder sb = new StringBuilder("'" + b.getStreamTitle() + "' " + b.getStreamClusters().size() + " Gruppen, weiter=" + b.getStreamNextPageUrl());
        for (StreamCluster k : b.getStreamClusters().values())
            sb.append(" | '").append(k.getClusterTitle()).append("' ").append(k.getClusterAppList().size());
        return sb.toString();
    }

    private static List<AppEintrag> sammeln(StreamBundle b) {
        List<AppEintrag> l = new ArrayList<>();
        java.util.Set<String> gesehen = new java.util.HashSet<>();
        for (StreamCluster k : b.getStreamClusters().values())
            for (App app : k.getClusterAppList())
                if (gesehen.add(app.getPackageName())) {
                    Log.i(TAG, "TREFFER " + app.getPackageName() + " | " + app.getDisplayName() + " | restr=" + app.getRestriction().getRestriction());
                    l.add(AppEintrag.von(app));
                }
        return l;
    }
}
