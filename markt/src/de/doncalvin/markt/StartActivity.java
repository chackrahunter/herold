package de.doncalvin.markt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

/** Startbildschirm: vier Wege in den Laden, kein Konto, kein Schnickschnack. */
public class StartActivity extends Activity {
    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        KurvenListe[] r = new KurvenListe[1];
        LinearLayout spalte = Oberflaeche.liste(this, r);
        spalte.addView(Oberflaeche.kopf(this, "Markt", "ohne Konto"));

        spalte.addView(karte("suche", "Suchen", "alle Wear-OS-Apps", Stil.SUCHE,
                new Intent(this, SucheActivity.class)), Oberflaeche.kartenMass(this));
        spalte.addView(karte("apps", "Für die Uhr", "Apps für Wear OS", Stil.UHRAPPS,
                new Intent(this, ListeActivity.class).putExtra("modus", ListeActivity.UHR)), Oberflaeche.kartenMass(this));
        spalte.addView(karte("blatt", "Zifferblätter", "Watch Faces", Stil.BLATT,
                new Intent(this, ListeActivity.class).putExtra("modus", ListeActivity.BLAETTER)), Oberflaeche.kartenMass(this));
        spalte.addView(karte("meine", "Meine Apps", "installiert, Updates", Stil.MEINE,
                new Intent(this, ListeActivity.class).putExtra("modus", ListeActivity.MEINE)), Oberflaeche.kartenMass(this));
        spalte.addView(Oberflaeche.fuss(this, "Quelle: Google Play\nanonymer Zugang über Aurora"));
        r[0].post(r[0]::auftritt);
        // Werksschutz-Modus aus, sonst blockiert das System jede Installation
        try { android.provider.Settings.Global.putInt(getContentResolver(), "secure_frp_mode", 0); } catch (Exception ignored) {}
        // Uhr-Katalog im Hintergrund vorbereiten (fuer die Suche)
        Hintergrund.tue(() -> Play.katalog(this), n -> {}, e -> {});
    }

    private StartKachel karte(String glyph, String t, String u, int farbe, final Intent ziel) {
        StartKachel k = new StartKachel(this, glyph, farbe, t, u);
        k.setOnClickListener(v -> { startActivity(ziel); overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out); });
        return k;
    }
}
