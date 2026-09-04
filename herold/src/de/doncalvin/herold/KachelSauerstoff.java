package de.doncalvin.herold;

/** Kachel: Sauerstoffsaettigung. */
public class KachelSauerstoff extends KachelBasis {
    @Override protected String art()   { return SensorService.SPO2; }
    @Override protected String titel() { return "Sauerstoff"; }
    @Override protected float von()   { return 88f; }
    @Override protected float bis()   { return 100f; }
    @Override protected int farbe()    { return Stil.SAUERSTOFF; }
}
