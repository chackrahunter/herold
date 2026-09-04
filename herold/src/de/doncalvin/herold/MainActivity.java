package de.doncalvin.herold;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Kopplung mit dem iPhone und Zustand des Dienstes.
 *
 * Alles andere laeuft im Hintergrund: Benachrichtigungen erscheinen von
 * selbst, Musik bedient man im eingebauten Player. Diese Seite sagt nur,
 * ob die Verbindung steht, was darueber laeuft, und bietet Koppeln und
 * Loesen an - das Loesen bewusst leise, nicht rot.
 */
public class MainActivity extends Activity {

    private static final String[] PERMS = {
            Manifest.permission.BLUETOOTH_ADVERTISE,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.POST_NOTIFICATIONS
    };

    private KopplungView bild;
    private TextView status, hinweis, fuss;
    private LinearLayout chips;
    private final Handler uhr = new Handler(Looper.getMainLooper());

    private final BroadcastReceiver empfang = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String t = i.getStringExtra("text");
            if (t != null) zeigeStatus(t);
        }
    };

    private final Runnable tick = new Runnable() {
        @Override public void run() {
            if (fuss != null) fuss.setText(laufzeit());
            uhr.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        requestPermissions(PERMS, 1);
        startForegroundService(new Intent(this, AncsService.class));

        KurvenListe roller = new KurvenListe(this);
        roller.setBackgroundColor(Stil.GRUND);
        LinearLayout s = new LinearLayout(this);
        s.setOrientation(LinearLayout.VERTICAL);
        s.setGravity(Gravity.CENTER_HORIZONTAL);
        s.setClipChildren(false);
        int rand = Stil.dp(this, 18);
        s.setPadding(rand, Stil.dp(this, 40), rand, Stil.dp(this, 56));

        TextView titel = text("IPHONE", 13f, Stil.IPHONE, Gravity.CENTER, true);
        titel.setLetterSpacing(0.06f);
        s.addView(titel);

        bild = new KopplungView(this);
        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Stil.dp(this, 62));
        bp.topMargin = Stil.dp(this, 4);
        s.addView(bild, bp);

        status = text("Verbinde…", 14.5f, Stil.TEXT_STARK, Gravity.CENTER, true);
        s.addView(status);

        // Drei Chips passen nicht nebeneinander in den Kreis - die lange
        // "Benachrichtigungen" bekommt eine eigene Reihe.
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.VERTICAL);
        chips.setGravity(Gravity.CENTER_HORIZONTAL);
        chips.setPadding(0, Stil.dp(this, 6), 0, Stil.dp(this, 12));
        LinearLayout reihe1 = new LinearLayout(this); reihe1.setGravity(Gravity.CENTER_HORIZONTAL);
        reihe1.addView(chip("Benachrichtigungen"));
        LinearLayout reihe2 = new LinearLayout(this); reihe2.setGravity(Gravity.CENTER_HORIZONTAL);
        reihe2.setPadding(0, Stil.dp(this, 4), 0, 0);
        reihe2.addView(chip("Anrufe")); reihe2.addView(chip("Musik"));
        chips.addView(reihe1); chips.addView(reihe2);
        s.addView(chips);

        s.addView(aktion("Koppeln", Stil.PULS, true, v -> {
            startForegroundService(new Intent(this, AncsService.class).setAction(AncsService.ACTION_KOPPELN));
            zeigeStatus("Sichtbar für das iPhone");
            hinweis.setText("iPhone: Einstellungen → Bluetooth → Galaxy Watch antippen");
        }));
        s.addView(luft(8));
        s.addView(aktion("Kopplung lösen", Stil.RAND_DEUTLICH, false, v -> {
            startForegroundService(new Intent(this, AncsService.class).setAction(AncsService.ACTION_TRENNEN));
            zeigeStatus("Kopplung gelöst");
            hinweis.setText("Am iPhone zusätzlich „Gerät ignorieren“ wählen");
        }));

        hinweis = text("", 10.5f, Stil.TEXT_SCHWACH, Gravity.CENTER, false);
        hinweis.setPadding(0, Stil.dp(this, 10), 0, 0);
        s.addView(hinweis);

        fuss = text(laufzeit(), 9f, Stil.TEXT_LEISE, Gravity.CENTER, false);
        fuss.setPadding(0, Stil.dp(this, 8), 0, 0);
        s.addView(fuss);

        roller.addView(s, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(roller);
        Rad.anBinden(roller);
        Rad.fokusHolen(roller);
        roller.post(roller::auftritt);
    }

    private void zeigeStatus(String t) {
        String u = t.toLowerCase();
        boolean v = AncsService.verbunden
                || ((u.contains("verbunden") || u.startsWith("aktiv")) && !u.contains("nicht") && !u.contains("gel"));
        // Der Dienst redet ausfuehrlich ("Aktiv - Benachrichtigungen laufen");
        // hier reicht der Kern, der Rest steht in den Chips darunter.
        status.setText(v ? "Verbunden mit iPhone" : t);
        bild.setzeVerbunden(v);
        chips.setAlpha(v ? 1f : 0.35f);
    }

    private String laufzeit() {
        long seit = AncsService.dienstSeit;
        if (seit <= 0) return "Dienst wird gestartet";
        long min = (System.currentTimeMillis() - seit) / 60000L;
        return "Dienst läuft seit " + (min >= 60 ? (min / 60) + " h " + (min % 60) + " min" : min + " min");
    }

    private TextView text(String t, float g, int farbe, int lage, boolean medium) {
        TextView v = new TextView(this);
        v.setText(t); v.setTextSize(g); v.setTextColor(farbe); v.setGravity(lage);
        if (medium) v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    private View chip(String n) {
        TextView t = text(n, 9.5f, Stil.TEXT_MITTEL, Gravity.CENTER, false);
        int px = Stil.dp(this, 8), py = Stil.dp(this, 4);
        t.setPadding(px, py, px, py);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(Stil.FLAECHE_02);
        hg.setCornerRadius(Stil.dp(this, 20));
        t.setBackground(hg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.leftMargin = lp.rightMargin = Stil.dp(this, 3);
        t.setLayoutParams(lp);
        return t;
    }

    /** Aktionskarte: 48 dp hoch, Rand in Akzentfarbe; die leise Variante ohne Betonung. */
    private View aktion(String n, int rand, boolean betont, View.OnClickListener tun) {
        TextView t = text(n, 13f, betont ? Stil.TEXT_STARK : Stil.TEXT_SCHWACH, Gravity.CENTER, true);
        GradientDrawable hg = new GradientDrawable();
        hg.setColor(betont ? Stil.FLAECHE_01 : Stil.GRUND);
        hg.setCornerRadius(Stil.dp(this, 24));
        hg.setStroke(Stil.dp(this, 2), betont ? MessKarte.mitAlpha(rand, 0xB3) : rand);
        t.setBackground(hg);
        t.setClickable(true);
        t.setOnClickListener(tun);
        t.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Stil.dp(this, Stil.TIPPBAR_MIN)));
        return t;
    }

    private View luft(int h) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Stil.dp(this, h)));
        return v;
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(empfang, new IntentFilter(AncsService.ACTION_STATUS), Context.RECEIVER_NOT_EXPORTED);
        startForegroundService(new Intent(this, AncsService.class).setAction(AncsService.ACTION_STATUS_ABFRAGE));
        bild.setzeVerbunden(AncsService.verbunden);
        uhr.post(tick);
    }

    @Override protected void onPause() {
        super.onPause();
        uhr.removeCallbacks(tick);
        try { unregisterReceiver(empfang); } catch (Exception ignored) {}
        if (bild != null) bild.anhalten();
    }
}
