package de.doncalvin.markt;

import com.aurora.gplayapi.data.models.App;

import java.io.Serializable;

/** Ein Eintrag im Laden - das, was die Oberflaeche von einer App wissen muss. */
public final class AppEintrag implements Serializable {
    public String paket = "", name = "", entwickler = "", version = "", symbolUrl = "",
            beschreibung = "", kurz = "", preis = "";
    public long versionCode, groesse;
    public float bewertung;
    public int offerType;
    public int restriction = 1;
    public String kategorie = "";
    public boolean kostenlos = true;
    /** Nur fuer "Meine Apps": installierte Version. */
    public long installiertCode = -1;
    /** true = genau diese versionCode installieren, nicht auf die neueste überschreiben. */
    public boolean festeVersion = false;

    public static AppEintrag von(App a) {
        AppEintrag e = new AppEintrag();
        e.paket = a.getPackageName();
        e.name = a.getDisplayName();
        e.entwickler = a.getDeveloperName();
        e.version = a.getVersionName();
        e.versionCode = a.getVersionCode();
        e.groesse = a.getSize();
        e.bewertung = a.getRating().getAverage();
        e.symbolUrl = a.getIconArtwork().getUrl();
        e.beschreibung = a.getDescription();
        e.kurz = a.getShortDescription();
        e.offerType = a.getOfferType();
        e.kostenlos = a.isFree();
        e.preis = a.getPrice();
        try { e.restriction = a.getRestriction().getRestriction(); } catch (Exception ignored) {}
        e.kategorie = a.getCategoryName();
        return e;
    }

    public String groesseText() {
        if (groesse <= 0) return "";
        if (groesse < 1024 * 1024) return (groesse / 1024) + " kB";
        return String.format(java.util.Locale.GERMANY, "%.0f MB", groesse / 1048576.0);
    }
}
