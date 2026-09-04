package de.doncalvin.markt;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/** Ergebnisliste: Suche, Zifferblaetter, Uhr-Apps oder die eigenen Apps. */
public class ListeActivity extends Activity {
    public static final String SUCHE = "suche", BLAETTER = "blaetter", UHR = "uhr", MEINE = "meine";
    private LinearLayout spalte; private KurvenListe roller; private TextView stand;
    private String modus, frage;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        modus = getIntent().getStringExtra("modus"); if (modus == null) modus = UHR;
        frage = getIntent().getStringExtra("frage");
        KurvenListe[] r = new KurvenListe[1];
        spalte = Oberflaeche.liste(this, r); roller = r[0];
        String titel = SUCHE.equals(modus) ? "Suche" : BLAETTER.equals(modus) ? "Zifferblätter" : MEINE.equals(modus) ? "Meine Apps" : "Für die Uhr";
        spalte.addView(Oberflaeche.kopf(this, titel, SUCHE.equals(modus) ? frage : null));
        stand = Oberflaeche.hinweis(this, "lädt…");
        spalte.addView(stand);
        laden();
    }

    private void laden() {
        if (MEINE.equals(modus)) { meine(); return; }
        Hintergrund.tue(() -> {
            if (SUCHE.equals(modus)) return Play.suchen(this, frage);
            if (BLAETTER.equals(modus)) return Play.zifferblaetter(this);
            return Play.fuerUhr(this);
        }, this::zeige,
           e -> stand.setText("Kein Zugang:\n" + kurz(e)));
    }

    /** Meine Apps: installierte anzeigen, mit Play anreichern (Symbol, Update-Stand). */
    private void meine() {
        stand.setText("prüfe auf Updates…");
        Hintergrund.tue(() -> {
            List<AppEintrag> inst = installierte();
            if (!inst.isEmpty()) {
                try {
                    java.util.List<String> pk = new ArrayList<>();
                    for (AppEintrag e : inst) pk.add(e.paket);
                    java.util.Map<String, AppEintrag> pd = Play.bulkDetails(this, pk);
                    for (AppEintrag e : inst) {
                        AppEintrag d = pd.get(e.paket);
                        if (d != null) {
                            if (!d.symbolUrl.isEmpty()) e.symbolUrl = d.symbolUrl;
                            if (!d.name.isEmpty()) e.name = d.name;
                            e.entwickler = d.entwickler.isEmpty() ? e.entwickler : d.entwickler;
                            e.versionCode = d.versionCode; e.bewertung = d.bewertung; e.offerType = d.offerType; e.kostenlos = d.kostenlos;
                        }
                    }
                } catch (Exception ignored) {}
            }
            // Updates zuerst, dann alphabetisch
            inst.sort((x, y) -> {
                boolean ux = x.versionCode > x.installiertCode, uy = y.versionCode > y.installiertCode;
                if (ux != uy) return ux ? -1 : 1;
                return x.name.compareToIgnoreCase(y.name);
            });
            return inst;
        }, this::zeigeMeine, e -> zeige(installierte()));
    }

    private void zeigeMeine(final List<AppEintrag> liste) {
        if (liste.isEmpty()) { stand.setText("Noch nichts installiert."); return; }
        spalte.removeView(stand);
        final List<AppEintrag> updates = new ArrayList<>();
        for (AppEintrag e : liste) if (e.versionCode > e.installiertCode) updates.add(e);
        if (!updates.isEmpty()) {
            StartKachel alle = new StartKachel(this, "meine", Stil.AKZENT,
                    "Alle aktualisieren", updates.size() + (updates.size() == 1 ? " App" : " Apps"));
            alle.setOnClickListener(v -> {
                for (AppEintrag e : updates) startForegroundService(new Intent(this, Ladedienst.class).putExtra("eintrag", e));
                stand.setText("Updates werden geladen…"); spalte.addView(stand, 1);
            });
            spalte.addView(alle, Oberflaeche.kartenMass(this));
        }
        for (final AppEintrag e : liste) {
            Kachel k = new Kachel(this).app(e);
            boolean update = e.versionCode > e.installiertCode;
            k.stand(update ? "Update" : "installiert");
            k.setOnClickListener(v -> {
                startActivity(new Intent(this, AppActivity.class).putExtra("eintrag", e));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
            spalte.addView(k, Oberflaeche.kartenMass(this));
        }
        roller.post(roller::auftritt);
    }


    static String kurz(Exception e) {
        String m = e.getMessage(); if (m == null || m.isEmpty()) m = e.getClass().getSimpleName();
        return m.length() > 140 ? m.substring(0, 140) + "…" : m;
    }

    private void zeige(List<AppEintrag> liste) {
        if (liste.isEmpty()) {
            stand.setText(MEINE.equals(modus) ? "Noch nichts installiert."
                    : SUCHE.equals(modus) ? "Nichts gefunden für „" + frage + "“."
                    : "Nichts gefunden.");
            return;
        }
        spalte.removeView(stand);
        for (final AppEintrag e : liste) {
            Kachel k = new Kachel(this).app(e);
            if (e.installiertCode >= 0) k.stand("installiert");
            k.setOnClickListener(v -> {
                startActivity(new Intent(this, AppActivity.class).putExtra("eintrag", e));
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
            spalte.addView(k, Oberflaeche.kartenMass(this));
        }
        roller.post(roller::auftritt);
    }

    /** Alles, was kein Systembestandteil ist - das sind die Apps, die der Laden verwalten darf. */
    private List<AppEintrag> installierte() {
        List<AppEintrag> l = new ArrayList<>();
        PackageManager pm = getPackageManager();
        for (PackageInfo p : pm.getInstalledPackages(0)) {
            if (p.applicationInfo == null) continue;
            if ((p.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            if (getPackageName().equals(p.packageName)) continue;
            AppEintrag e = new AppEintrag();
            e.paket = p.packageName;
            e.name = String.valueOf(pm.getApplicationLabel(p.applicationInfo));
            e.version = p.versionName == null ? "" : p.versionName;
            e.installiertCode = p.getLongVersionCode();
            e.entwickler = "Version " + e.version;
            l.add(e);
        }
        l.sort((x, y) -> x.name.compareToIgnoreCase(y.name));
        return l;
    }
}
