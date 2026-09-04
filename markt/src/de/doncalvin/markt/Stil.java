package de.doncalvin.markt;

import android.content.Context;
import android.view.animation.PathInterpolator;

/**
 * Farben, Masse und Bewegungskurven an einer Stelle.
 *
 * Leitgedanke: Der Bildschirm ist schwarz und leer. Farbe ist knapp und
 * bedeutet immer dasselbe - sie sagt "welche Messung", nie "wie gut". Ein
 * Ergebnis wird durch Form und Wortlaut unterschieden, nie durch Ampelfarbe.
 *
 * Die Kontrastwerte sind gegen den jeweiligen Untergrund gerechnet; wer eine
 * Farbe aendert, sollte das nachrechnen, sonst faellt Text unter die
 * Lesbarkeitsgrenze.
 */
public final class Stil {

    private Stil() {}

    // ---- Flaechen -------------------------------------------------------
    // Hoehe wird durch eine Stufe Helligkeit ausgedrueckt, nie durch Schatten.
    // Ein Schatten auf Schwarz ist unsichtbar und kostet nur Rechenzeit.

    public static final int GRUND        = 0xFF000000;
    public static final int FLAECHE_01   = 0xFF101419;   // Karte in Ruhe
    public static final int FLAECHE_02   = 0xFF1C222B;   // gedrueckt, Ergebniskasten, Ringspur
    public static final int FLAECHE_03   = 0xFF232B35;   // runde Tasten
    public static final int RAND_LEISE   = 0xFF2C3542;
    public static final int RAND_DEUTLICH= 0xFF3A4451;

    // ---- Text -----------------------------------------------------------

    public static final int TEXT_STARK   = 0xFFFFFFFF;   // Messwerte, Titel
    public static final int TEXT_MITTEL  = 0xFFC6CEDA;   // Fliesstext
    public static final int TEXT_SCHWACH = 0xFF97A1B0;   // Beschriftungen, Einheiten
    public static final int TEXT_LEISE   = 0xFF7B8694;   // Fussnoten, nur auf Schwarz

    // ---- Kennfarben je Messart -----------------------------------------
    // Diese Farben sagen, WAS gemessen wurde. Nie, wie es ausfiel.

    public static final int EKG          = 0xFFFF3B5B;
    public static final int SAUERSTOFF   = 0xFF39B8FF;
    public static final int KOERPER      = 0xFFFFAA33;
    public static final int TEMPERATUR   = 0xFFFF7847;
    /**
     * Puls und Rhythmus - bewusst kein Gruen.
     *
     * Der Punkt dieser Kennfarbe steht auf dem Startbildschirm neben genau dem
     * Eintrag, der spaeter das Rhythmusurteil zeigt. Gruen ist die einzige
     * Farbe, die auch ohne Text "alles in Ordnung" sagt - und das ist die eine
     * Aussage, die diese App nicht treffen darf.
     */
    public static final int PULS         = 0xFF8FA6FF;
    public static final int VERLAUF      = 0xFF9B7BFF;
    public static final int KOERPERDATEN = 0xFFC08040;
    public static final int IPHONE       = 0xFF6E7A8A;
    // ---- Markt ----------------------------------------------------------
    public static final int SUCHE        = 0xFF39B8FF;   // Suche
    public static final int BLATT        = 0xFF9B7BFF;   // Zifferblaetter
    public static final int UHRAPPS      = 0xFFFFAA33;   // Apps fuer die Uhr
    public static final int MEINE        = 0xFF8FA6FF;   // Installiert / Updates
    public static final int AKZENT       = 0xFFFF7847;   // Haupttaste (Installieren)

    /** Die EKG-Kurve bleibt gruen - Instrumentenkonvention, kein Urteil. */
    public static final int KURVE        = 0xFF4CFF7A;

    // ---- Befundfarben ---------------------------------------------------
    // Nur drei, alle unaufgeregt. Kein Gruen, kein Rot, kein Haken, kein
    // Ausrufezeichen. Der Unterschied ist ein Farbwechsel und ein anderer
    // Text - mehr nicht.

    public static final int BEFUND_GLEICHMAESSIG = 0xFF7E9CC4;   // Schieferblau
    public static final int BEFUND_AUFFAELLIG    = 0xFFFFAA33;   // Bernstein
    public static final int BEFUND_UNKLAR        = 0xFF6E7A8A;   // Grau


    // ---- Masse ----------------------------------------------------------
    // Das Display ist 216 x 216 dp, die Mitte liegt bei 108/108, der Radius
    // betraegt 108 dp. Rechtecke und Text muessen in einen Kreis mit Radius
    // 90 dp; ein Kreis darf bis 101 dp, weil er der Rundung folgt.

    public static final int RADIUS_SICHER = 90;
    public static final int RADIUS_KRANZ  = 101;
    public static final int RASTER        = 4;
    public static final int ECKE_KARTE    = 22;
    public static final int ECKE_KASTEN   = 18;
    public static final int TIPPBAR_MIN   = 48;

    // ---- Bewegung -------------------------------------------------------

    public static final int KURZ      = 120;   // Farbwechsel, Deckkraft
    public static final int WECHSEL   = 180;   // Zustandswechsel
    public static final int KOMMT     = 260;   // Element kommt oder geht
    public static final int GROSS     = 380;   // groessere Bewegung
    public static final int ZAHL      = 520;   // Zahl laeuft hoch
    public static final int VERSATZ   = 44;    // zwischen gestaffelten Elementen

    /** Etwas kommt ins Bild: bremst stark ab, setzt sich weich. */
    public static PathInterpolator ein()      { return new PathInterpolator(0.16f, 1f, 0.30f, 1f); }
    /** Etwas verlaesst das Bild: zieht an, verschwindet schnell. */
    public static PathInterpolator aus()      { return new PathInterpolator(0.40f, 0f, 1f, 1f); }
    /** Etwas veraendert sich an Ort und Stelle. */
    public static PathInterpolator standard() { return new PathInterpolator(0.40f, 0f, 0.20f, 1f); }
    /** Der eine wichtige Schritt je Bildschirm. */
    public static PathInterpolator betont()   { return new PathInterpolator(0.20f, 0f, 0f, 1f); }
    /** Nur fuer den Herzschlag: springt sofort an. */
    public static PathInterpolator anspringen() { return new PathInterpolator(0.05f, 0.70f, 0.10f, 1f); }

    public static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }
}
