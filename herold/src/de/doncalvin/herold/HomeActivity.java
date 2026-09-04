package de.doncalvin.herold;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.List;

/**
 * Startbildschirm.
 *
 * Die Liste folgt der Rundung des Displays (siehe KurvenListe), die Karten
 * sind selbst gezeichnet (MessKarte) und tragen den zuletzt gemessenen Wert.
 * Beim Oeffnen kommen sie nacheinander herein - das zeigt die Reihenfolge und
 * macht den Aufbau lesbar, statt alles auf einmal hinzustellen.
 */
public class HomeActivity extends Activity {

    private KurvenListe roller;
    private LinearLayout spalte;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        // Einmalige Vorbelegung mit den bekannten Werten; ueberschreibt nichts,
        // Körperdaten einmalig vorbelegen. Die Körperanalyse (BIA) braucht
        // Größe, Gewicht, Alter und Geschlecht, sonst liefert der Sensor nichts.
        // Hier stehen neutrale Platzhalter - eigene Werte im Bildschirm
        // "Körperdaten" eintragen, die überschreiben diese Vorgabe.
        new Profil(this).vorbelegen(170, 70f, 1, 1, 1990, Profil.WEIBLICH);

        roller = new KurvenListe(this);
        roller.setBackgroundColor(Stil.GRUND);

        spalte = new LinearLayout(this);
        spalte.setOrientation(LinearLayout.VERTICAL);
        spalte.setClipChildren(false);
        int rand = Stil.dp(this, 14);
        spalte.setPadding(rand, Stil.dp(this, 30), rand, Stil.dp(this, 62));

        spalte.addView(kopf());

        Verlauf v = new Verlauf(this);
        karte("EKG", "30 s Herzstromkurve", Stil.EKG, MessKarte.Z_KURVE,
                letzter(v, "EKG"), EkgActivity.class, null);
        karte("Puls & Rhythmus", "Gleichmäßigkeit, Atmung", Stil.PULS, MessKarte.Z_HERZ,
                letzter(v, "PULS"), null, SensorService.PULS);
        karte("Sauerstoff", "SpO₂ im Blut", Stil.SAUERSTOFF, MessKarte.Z_TROPFEN,
                letzter(v, "SPO2"), null, SensorService.SPO2);
        karte("Atemfrequenz", "Züge pro Minute", 0xFF56C8D8, MessKarte.Z_LUNGE,
                letzter(v, "ATEM"), null, SensorService.PULS);
        karte("Hauttemperatur", "am Handgelenk", Stil.TEMPERATUR, MessKarte.Z_THERMO,
                letzter(v, "TEMP"), null, SensorService.TEMP);
        karte("Körperanalyse", "Fett, Muskeln, Wasser", Stil.KOERPER, MessKarte.Z_KOERPER,
                letzter(v, "BIA"), null, SensorService.BIA);
        karte("Verlauf", "frühere Messungen", Stil.VERLAUF, MessKarte.Z_UHR,
                "", VerlaufActivity.class, null);
        karte("Körperdaten", "Größe, Gewicht, Alter", Stil.KOERPERDATEN, MessKarte.Z_PERSON,
                "", ProfilActivity.class, null);
        karte("iPhone", "Kopplung & Status", Stil.IPHONE, MessKarte.Z_TELEFON,
                "", MainActivity.class, null);

        roller.addView(spalte, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(roller);

        Rad.anBinden(roller);
        Rad.fokusHolen(roller);
        roller.post(roller::auftritt);

        if (getIntent() != null && getIntent().getBooleanExtra("selbsttest", false)) {
            radSelbsttest(roller);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("kacheltest", false)) {
            kachelSelbsttest();
        }
        if (getIntent() != null && getIntent().getBooleanExtra("sondeA", false)) {
            Sonde.stufeA(this);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("sondeB", false)) {
            Sonde.stufeB(this);
        }
        if (getIntent() != null && getIntent().getBooleanExtra("planertest", false)) {
            // Messtakt in 5 s ausloesen - zum Pruefen der ganzen Kette
            MessPlaner.setzeAktiv(this, true);
            MessPlaner.testInSekunden(this, 5, getIntent().getBooleanExtra("erzwingen", false));
        }
    }

    @Override protected void onResume() {
        super.onResume();
        if (roller != null) roller.post(roller::formen);
    }

    /** Kopfzeile: Name und der jüngste Messwert als Einzeiler. */
    private View kopf() {
        LinearLayout k = new LinearLayout(this);
        k.setOrientation(LinearLayout.VERTICAL);
        k.setGravity(Gravity.CENTER_HORIZONTAL);
        k.setPadding(0, 0, 0, Stil.dp(this, 12));

        TextView t = new TextView(this);
        t.setText("Herold");
        t.setTextSize(20f);
        t.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        t.setTextColor(Stil.TEXT_STARK);
        t.setGravity(Gravity.CENTER);
        t.setLetterSpacing(0.04f);
        k.addView(t);

        List<Verlauf.Eintrag> alle = new Verlauf(this).lies();
        TextView u = new TextView(this);
        u.setTextSize(10f);
        u.setTextColor(Stil.TEXT_LEISE);
        u.setGravity(Gravity.CENTER);
        u.setText(alle.isEmpty()
                ? "noch nichts gemessen"
                : "zuletzt " + alle.get(0).artName() + " · " + alle.get(0).zeitText());
        k.addView(u);
        return k;
    }

    private String letzter(Verlauf v, String art) {
        List<Verlauf.Eintrag> l = v.lies(art);
        return l.isEmpty() ? "" : l.get(0).wert;
    }

    private void karte(String titel, String unten, int farbe, int symbol, String wert,
                       final Class<?> ziel, final String messart) {
        MessKarte k = new MessKarte(this);
        k.setze(titel, unten, farbe, symbol).setzeWert(wert);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Stil.dp(this, 62));
        lp.bottomMargin = Stil.dp(this, 8);
        k.setOnClickListener(x -> {
            if (messart != null) {
                startActivity(new Intent(this, MessActivity.class).putExtra("art", messart));
            } else if (ziel != null) {
                startActivity(new Intent(this, ziel));
            }
        });
        spalte.addView(k, lp);
    }

    // ---- Selbsttests ---------------------------------------------------

    /**
     * Prueft, ob ein Drehen der Luenette wirklich scrollt. Die Ereignisse
     * lassen sich von aussen nicht einspeisen (/dev/input ist schreibgeschuetzt),
     * darum baut der Test sie hier nach - gleiche Quelle, gleiche Achse.
     */
    private void radSelbsttest(final KurvenListe roller) {
        roller.postDelayed(new Runnable() {
            @Override public void run() {
                int vorher = roller.getScrollY();
                roller.dispatchGenericMotionEvent(drehung(-3f));
                int runter = roller.getScrollY();
                roller.dispatchGenericMotionEvent(drehung(+3f));
                int wieder = roller.getScrollY();
                boolean ok = runter > vorher && wieder < runter;
                Log.i("HeroldTest", "RAD " + (ok ? "OK" : "FEHLER")
                        + " start=" + vorher + " nachRechts=" + runter
                        + " nachLinks=" + wieder + " fokus=" + roller.hasFocus());
            }
        }, 1400);
    }

    private static MotionEvent drehung(float wert) {
        MotionEvent.PointerProperties[] p = { new MotionEvent.PointerProperties() };
        p[0].id = 0;
        MotionEvent.PointerCoords[] k = { new MotionEvent.PointerCoords() };
        k[0].setAxisValue(MotionEvent.AXIS_SCROLL, wert);
        long t = android.os.SystemClock.uptimeMillis();
        return MotionEvent.obtain(t, t, MotionEvent.ACTION_SCROLL, 1, p, k,
                0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_ROTARY_ENCODER, 0);
    }

    /** Ruft jede Kachel auf und prueft, ob sie eine gueltige Beschreibung liefert. */
    private void kachelSelbsttest() {
        Class<?>[] alle = { KachelPuls.class, KachelEkg.class, KachelSauerstoff.class,
                            KachelTemperatur.class, KachelKoerper.class, KachelAtem.class };
        int gut = 0;
        for (Class<?> k : alle) {
            try {
                KachelBasis kachel = (KachelBasis) k.getDeclaredConstructor().newInstance();
                kachel.attachBaseContext(getApplicationContext());
                androidx.wear.tiles.TileBuilders.Tile t = kachel.testBaue();
                int bytes = t.toProto().getSerializedSize();
                int eintraege = t.getTileTimeline() == null ? 0
                        : t.getTileTimeline().getTimelineEntries().size();
                Log.i("HeroldTest", "KACHEL " + k.getSimpleName()
                        + " ok proto=" + bytes + "B eintraege=" + eintraege);
                if (bytes > 0 && eintraege > 0) gut++;
            } catch (Throwable e) {
                Log.e("HeroldTest", "KACHEL " + k.getSimpleName() + " FEHLER", e);
            }
        }
        Log.i("HeroldTest", "KACHELN " + gut + "/" + alle.length + " in Ordnung");
    }
}
