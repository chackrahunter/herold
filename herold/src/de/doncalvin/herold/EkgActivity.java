package de.doncalvin.herold;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * Zeigt nur an - gemessen wird im EkgService. Dadurch ueberlebt die Messung
 * einen Druck auf die obere Taste, die zugleich Elektrode und Ein-/Aus-Taste ist.
 *
 * Die Kurve fuellt den Bildschirm; darueber liegen nur Titel, Puls und Hinweis.
 * Ein Rahmen um die Kurve waere auf einem runden Display ohnehin abgeschnitten.
 */
public class EkgActivity extends Activity {

    private TextView titel, pulsAnzeige, hinweis;
    private KurvenView kurve;
    private boolean gewechselt;

    private final BroadcastReceiver empfang = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) {
            String phase = i.getStringExtra("phase");
            if (phase == null || gewechselt) return;

            switch (phase) {
                case "WARTEN":
                    if (i.getIntExtra("kontakt", -1) != 0) {
                        titel.setText("Finger auflegen");
                        hinweis.setText("Uhr tragen, Finger der anderen Hand\nauf die obere Taste");
                    } else {
                        titel.setText("Halten…");
                        hinweis.setText("Signal beruhigt sich ("
                                + i.getIntExtra("ruhig", 0) + "/3)");
                    }
                    pulsAnzeige.setText("");
                    break;
                case "MESSEN":
                    titel.setText("Aufzeichnung läuft");
                    hinweis.setText("noch " + i.getIntExtra("rest", 0) + " s – ruhig halten");
                    int p = kurve != null ? kurve.puls() : 0;
                    pulsAnzeige.setText(p > 0 ? p + " /min" : "");
                    break;
                case "FERTIG":
                    EkgService.hoerer = null;      // Standbild nicht mehr ueberschreiben
                    titel.setText("Fertig");
                    hinweis.setText("wird ausgewertet…");
                    if (kurve != null && EkgService.letzteKurve != null) {
                        float[] k = EkgService.letzteKurve;
                        int mitte = Math.max(0, k.length / 2 - 1250);
                        kurve.standbild(k, mitte, Math.min(2500, k.length - mitte));
                    }
                    gewechselt = true;
                    titel.postDelayed(() -> {
                        startActivity(new Intent(EkgActivity.this, ErgebnisActivity.class));
                        overridePendingTransition(0, 0);
                        finish();
                    }, 1600);
                    break;
                case "FEHLER":
                    titel.setText("Fehler");
                    hinweis.setText(String.valueOf(i.getStringExtra("meldung")));
                    break;
                default:
                    titel.setText("Verbinde…");
                    hinweis.setText("");
            }
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        FrameLayout wurzel = new FrameLayout(this);
        wurzel.setBackgroundColor(Stil.GRUND);

        kurve = new KurvenView(this);
        FrameLayout.LayoutParams kp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Stil.dp(this, 116));
        kp.gravity = Gravity.CENTER_VERTICAL;
        wurzel.addView(kurve, kp);

        titel = new TextView(this);
        titel.setText("Verbinde…");
        titel.setTextSize(15f);
        titel.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        titel.setTextColor(Stil.TEXT_STARK);
        titel.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        tp.topMargin = Stil.dp(this, 42);
        wurzel.addView(titel, tp);

        pulsAnzeige = new TextView(this);
        pulsAnzeige.setTextSize(12f);
        pulsAnzeige.setTextColor(Stil.EKG);
        pulsAnzeige.setGravity(Gravity.CENTER);
        pulsAnzeige.setLetterSpacing(0.04f);
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
        pp.topMargin = Stil.dp(this, 64);
        wurzel.addView(pulsAnzeige, pp);

        hinweis = new TextView(this);
        hinweis.setTextSize(10.5f);
        hinweis.setTextColor(Stil.TEXT_SCHWACH);
        hinweis.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams hp = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hp.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = Stil.dp(this, 34);
        hp.leftMargin = hp.rightMargin = Stil.dp(this, 24);
        wurzel.addView(hinweis, hp);

        setContentView(wurzel);

        kurve.setAlpha(0f);
        kurve.animate().alpha(1f).setDuration(Stil.GROSS)
                .setInterpolator(Stil.ein()).start();

        // Direkter Draht zum Dienst: jeder Messwert landet sofort in der Kurve
        EkgService.hoerer = mv -> { if (kurve != null) kurve.hinzu(mv); };

        startForegroundService(new Intent(this, EkgService.class)
                .setAction(EkgService.ACTION_START));
    }

    @Override protected void onResume() {
        super.onResume();
        registerReceiver(empfang, new IntentFilter(EkgService.ACTION_ZUSTAND),
                Context.RECEIVER_NOT_EXPORTED);
    }

    @Override protected void onPause() {
        super.onPause();
        try { unregisterReceiver(empfang); } catch (Exception ignored) {}
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        EkgService.hoerer = null;      // Anzeige weg -> Dienst misst still weiter
    }
}
