package de.doncalvin.markt;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.graphics.Outline;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Die App-Seite: Symbol, Name, Entwickler, drei Kennzahlen, eine Haupttaste.
 * Waehrend Download und Installation zeigt ein duenner Balken den Fortschritt;
 * die Zustaende kommen als Broadcast vom Ladedienst.
 */
public class AppActivity extends Activity {
    private AppEintrag e; private String paket;
    private TextView taste, zweit, stand, name, entwickler, kennzahlen, text;
    private Balken balken; private ImageView bild; private LinearLayout spalte; private KurvenListe roller;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        e = (AppEintrag) getIntent().getSerializableExtra("eintrag");
        if (e != null) paket = e.paket;
        long festeVc = 0;
        if (getIntent().getData() != null) {
            if (paket == null) paket = getIntent().getData().getQueryParameter("id");
            String vc = getIntent().getData().getQueryParameter("vc");
            if (vc != null) try { festeVc = Long.parseLong(vc); } catch (Exception ignored) {}
        }
        if (paket == null) { finish(); return; }
        if (festeVc > 0) {
            if (e == null) { e = new AppEintrag(); e.paket = paket; }
            e.versionCode = festeVc; e.festeVersion = true; e.name = paket;
        }

        KurvenListe[] r = new KurvenListe[1];
        spalte = Oberflaeche.liste(this, r); roller = r[0];
        spalte.setGravity(Gravity.CENTER_HORIZONTAL);

        int g = Stil.dp(this, 56);
        bild = new ImageView(this); bild.setScaleType(ImageView.ScaleType.CENTER_CROP);
        bild.setBackgroundColor(Stil.FLAECHE_03);
        bild.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Stil.dp(AppActivity.this, 14)); }
        });
        bild.setClipToOutline(true);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(g, g); bp.bottomMargin = Stil.dp(this, 8);
        spalte.addView(bild, bp);

        name = new TextView(this); name.setTextColor(Stil.TEXT_STARK); name.setTextSize(17); name.setGravity(Gravity.CENTER);
        name.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        spalte.addView(name);
        entwickler = new TextView(this); entwickler.setTextColor(Stil.TEXT_SCHWACH); entwickler.setTextSize(11.5f); entwickler.setGravity(Gravity.CENTER);
        spalte.addView(entwickler);
        kennzahlen = new TextView(this); kennzahlen.setTextColor(Stil.TEXT_MITTEL); kennzahlen.setTextSize(11.5f); kennzahlen.setGravity(Gravity.CENTER);
        kennzahlen.setPadding(0, Stil.dp(this, 6), 0, Stil.dp(this, 12));
        spalte.addView(kennzahlen);

        taste = taste(Stil.AKZENT, Stil.TEXT_STARK); spalte.addView(taste, tastenMass());
        balken = new Balken(this); LinearLayout.LayoutParams bm = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Stil.dp(this, 3));
        bm.topMargin = Stil.dp(this, 8); bm.leftMargin = Stil.dp(this, 20); bm.rightMargin = Stil.dp(this, 20);
        spalte.addView(balken, bm); balken.setVisibility(View.GONE);
        stand = new TextView(this); stand.setTextColor(Stil.TEXT_SCHWACH); stand.setTextSize(11); stand.setGravity(Gravity.CENTER);
        stand.setPadding(0, Stil.dp(this, 6), 0, 0); spalte.addView(stand);
        zweit = taste(Stil.FLAECHE_03, Stil.TEXT_MITTEL); LinearLayout.LayoutParams zm = tastenMass(); zm.topMargin = Stil.dp(this, 8);
        spalte.addView(zweit, zm); zweit.setVisibility(View.GONE);

        text = new TextView(this); text.setTextColor(Stil.TEXT_MITTEL); text.setTextSize(12); text.setLineSpacing(0, 1.15f);
        text.setPadding(Stil.dp(this, 4), Stil.dp(this, 16), Stil.dp(this, 4), 0);
        spalte.addView(text);

        if (e != null) fuellen(); else { name.setText(paket); stand.setText("lädt…"); }
        final long behalteVc = (e != null && e.festeVersion) ? e.versionCode : 0;
        final boolean fest = (e != null) && e.festeVersion;
        Hintergrund.tue(() -> Play.details(this, paket), d -> {
            if (fest && behalteVc > 0) { d.versionCode = behalteVc; d.festeVersion = true; }
            e = d; fuellen();
        }, ex -> { if (e == null || e.versionCode <= 0) stand.setText("Kein Zugang:\n" + ListeActivity.kurz(ex)); });
    }

    private TextView taste(int grund, int schrift) {
        TextView t = new TextView(this);
        t.setTextColor(schrift); t.setTextSize(14); t.setGravity(Gravity.CENTER);
        t.setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        GradientDrawable g = new GradientDrawable(); g.setColor(grund); g.setCornerRadius(Stil.dp(this, 24));
        t.setBackground(g); t.setMinHeight(Stil.dp(this, Stil.TIPPBAR_MIN)); t.setClickable(true);
        return t;
    }
    private LinearLayout.LayoutParams tastenMass() {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.leftMargin = Stil.dp(this, 10); p.rightMargin = Stil.dp(this, 10);
        return p;
    }

    private long installiert() {
        try { PackageInfo p = getPackageManager().getPackageInfo(paket, 0); return p.getLongVersionCode(); }
        catch (Exception ex) { return -1; }
    }

    private void fuellen() {
        name.setText(e.name.isEmpty() ? paket : e.name);
        entwickler.setText(e.entwickler);
        Bilder.lade(e.symbolUrl, bild);
        StringBuilder k = new StringBuilder();
        if (e.bewertung > 0) k.append(String.format(java.util.Locale.GERMANY, "★ %.1f", e.bewertung));
        if (!e.groesseText().isEmpty()) k.append(k.length() > 0 ? "  ·  " : "").append(e.groesseText());
        if (!e.version.isEmpty()) k.append(k.length() > 0 ? "  ·  " : "").append("v").append(e.version);
        kennzahlen.setText(k);
        String t = e.kurz.isEmpty() ? e.beschreibung : e.kurz;
        text.setText(t.length() > 600 ? t.substring(0, 600) + "…" : t);
        tastenSetzen();
        roller.post(roller::formen);
    }

    private void tastenSetzen() {
        long inst = installiert();
        String laufend = Ladedienst.stand(paket);
        if (laufend != null) { balken.setVisibility(View.VISIBLE); balken.setzen(Ladedienst.prozent(paket)); stand.setText(laufend); taste.setText("läuft…"); taste.setAlpha(0.5f); taste.setOnClickListener(null); zweit.setVisibility(View.GONE); return; }
        taste.setAlpha(1f);
        if (inst >= 0 && e.versionCode > inst) {
            taste.setText("Aktualisieren"); taste.setOnClickListener(v -> laden());
            zweit.setText("Öffnen"); zweit.setOnClickListener(v -> oeffnen()); zweit.setVisibility(View.VISIBLE);
        } else if (inst >= 0) {
            taste.setText("Öffnen"); taste.setOnClickListener(v -> oeffnen());
            zweit.setText("Entfernen"); zweit.setOnClickListener(v -> entfernen()); zweit.setVisibility(View.VISIBLE);
            stand.setText("installiert");
        } else if (!e.kostenlos && !e.preis.isEmpty()) {
            taste.setText(e.preis); taste.setAlpha(0.5f); taste.setOnClickListener(null);
            stand.setText("kostenpflichtig – ohne Konto nicht möglich");
        } else {
            taste.setText("Installieren"); taste.setOnClickListener(v -> laden());
            zweit.setVisibility(View.GONE);
        }
    }

    private void laden() {
        balken.setVisibility(View.VISIBLE); balken.setzen(0); stand.setText("wird vorbereitet…");
        taste.setText("läuft…"); taste.setAlpha(0.5f); taste.setOnClickListener(null);
        startForegroundService(new Intent(this, Ladedienst.class).putExtra("eintrag", e));
    }
    private void oeffnen() {
        Intent i = getPackageManager().getLaunchIntentForPackage(paket);
        if (i != null) startActivity(i); else stand.setText("hat keinen Startbildschirm (z. B. Zifferblatt: lange auf die Uhr drücken)");
    }
    private void entfernen() {
        startActivity(new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + paket)));
    }

    private final BroadcastReceiver empfang = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            if (!paket.equals(i.getStringExtra("paket"))) return;
            String s = i.getStringExtra("stand"); int p = i.getIntExtra("prozent", -1);
            boolean fertig = i.getBooleanExtra("fertig", false);
            stand.setText(s);
            if (p >= 0) { balken.setVisibility(View.VISIBLE); balken.setzen(p); }
            if (fertig) { balken.setVisibility(View.GONE); tastenSetzen(); }
        }
    };

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(empfang, new IntentFilter(Ladedienst.ACTION_STAND), Context.RECEIVER_NOT_EXPORTED);
        if (e != null) tastenSetzen();
    }
    @Override protected void onPause() { super.onPause(); try { unregisterReceiver(empfang); } catch (Exception ignored) {} }

    /** Ein duenner Strich, der sich fuellt. Kein Kreis, kein Prozentzeichen - der Text daneben sagt, was passiert. */
    static class Balken extends View {
        private float anteil; private final android.graphics.Paint spur = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG), fuell = new android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG);
        Balken(Context c) { super(c); spur.setColor(Stil.FLAECHE_03); fuell.setColor(Stil.AKZENT); }
        void setzen(int prozent) { anteil = Math.max(0, Math.min(100, prozent)) / 100f; invalidate(); }
        @Override protected void onDraw(android.graphics.Canvas cv) {
            float h = getHeight(), w = getWidth();
            cv.drawRoundRect(0, 0, w, h, h / 2, h / 2, spur);
            if (anteil > 0) cv.drawRoundRect(0, 0, Math.max(h, w * anteil), h, h / 2, h / 2, fuell);
        }
    }
}
