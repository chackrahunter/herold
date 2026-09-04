package de.doncalvin.herold;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Calendar;

/**
 * Koerperdaten der Traegerin. Die Bioimpedanzmessung rechnet Widerstand nur
 * mithilfe von Groesse, Gewicht, Alter und Geschlecht in Fett und Muskeln um -
 * ohne diese Angaben gibt es kein Ergebnis.
 *
 * Gespeichert wird ausschliesslich auf der Uhr. Nichts davon verlaesst das
 * Geraet: Herold sendet keine Messwerte irgendwohin.
 *
 * Statt des Alters liegt das Geburtsdatum hier - so stimmt die Zahl auch noch
 * in zwei Jahren, ohne dass jemand daran denken muss.
 */
public class Profil {

    private static final String DATEI = "herold_profil";

    public static final int UNBEKANNT = -1;

    private final SharedPreferences p;

    public Profil(Context c) {
        p = c.getSharedPreferences(DATEI, Context.MODE_PRIVATE);
    }

    public boolean vollstaendig() {
        return groesseCm() > 0 && gewichtKg() > 0 && geburtsjahr() > 1900
                && geschlecht() != UNBEKANNT;
    }

    public int groesseCm()  { return p.getInt("groesse", 0); }
    public float gewichtKg(){ return p.getFloat("gewicht", 0f); }
    public int geburtsjahr(){ return p.getInt("jahr", 0); }
    public int geburtsmonat(){ return p.getInt("monat", 1); }
    public int geburtstag() { return p.getInt("tag", 1); }

    /** 0 oder 1 - welche Zahl weiblich bedeutet, klaert {@link #geschlechtName}. */
    public int geschlecht() { return p.getInt("geschlecht", UNBEKANNT); }

    public void setzeGroesse(int cm)   { p.edit().putInt("groesse", cm).apply(); }
    public void setzeGewicht(float kg) { p.edit().putFloat("gewicht", kg).apply(); }
    public void setzeGeschlecht(int g) { p.edit().putInt("geschlecht", g).apply(); }

    public void setzeGeburtstag(int tag, int monat, int jahr) {
        p.edit().putInt("tag", tag).putInt("monat", monat).putInt("jahr", jahr).apply();
    }

    /** Alter in vollen Jahren, aus dem Geburtsdatum zum heutigen Tag. */
    public int alter() {
        if (geburtsjahr() < 1900) return 0;
        Calendar heute = Calendar.getInstance();
        int a = heute.get(Calendar.YEAR) - geburtsjahr();
        int monatJetzt = heute.get(Calendar.MONTH) + 1;
        int tagJetzt = heute.get(Calendar.DAY_OF_MONTH);
        if (monatJetzt < geburtsmonat()
                || (monatJetzt == geburtsmonat() && tagJetzt < geburtstag())) {
            a--;   // Geburtstag steht dieses Jahr noch aus
        }
        return Math.max(0, a);
    }

    public String geschlechtName() {
        int g = geschlecht();
        if (g == UNBEKANNT) return "nicht gesetzt";
        return g == WEIBLICH ? "weiblich" : "männlich";
    }

    /**
     * Welche Zahl die Health Platform fuer weiblich erwartet, steht in keiner
     * Dokumentation und laesst sich dem SDK nicht ansehen - TrackerUserProfile
     * reicht den Wert nur durch. Die Zuordnung ist deshalb an einer echten
     * Messung abgelesen: bei falscher Codierung faellt der Koerperfettanteil
     * einer Frau um mehrere Punkte zu niedrig aus.
     */
    public static final int WEIBLICH = 0;
    public static final int MAENNLICH = 1;

    /** Einmalige Vorbelegung, damit niemand alles von Hand tippen muss. */
    public void vorbelegen(int cm, float kg, int tag, int monat, int jahr, int g) {
        if (vollstaendig()) return;
        setzeGroesse(cm);
        setzeGewicht(kg);
        setzeGeburtstag(tag, monat, jahr);
        setzeGeschlecht(g);
    }
}
