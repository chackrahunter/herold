package de.doncalvin.herold;

/** Kachel: Hauttemperatur. */
public class KachelTemperatur extends KachelBasis {
    @Override protected String art()   { return SensorService.TEMP; }
    @Override protected String titel() { return "Hauttemperatur"; }
    @Override protected float von()   { return 30f; }
    @Override protected float bis()   { return 38f; }
    @Override protected int farbe()    { return Stil.TEMPERATUR; }
}
