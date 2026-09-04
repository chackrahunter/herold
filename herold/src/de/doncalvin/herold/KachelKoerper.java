package de.doncalvin.herold;

/** Kachel: Koerperanalyse. */
public class KachelKoerper extends KachelBasis {
    @Override protected String art()   { return SensorService.BIA; }
    @Override protected String titel() { return "Körperanalyse"; }
    @Override protected float von()   { return 10f; }
    @Override protected float bis()   { return 45f; }
    @Override protected int farbe()    { return Stil.KOERPER; }
}
