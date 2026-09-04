package de.doncalvin.herold;

/** Kachel: Puls und Gleichmaessigkeit des Schlags. */
public class KachelPuls extends KachelBasis {
    @Override protected String art()   { return SensorService.PULS; }
    @Override protected String titel() { return "Puls"; }
    @Override protected float von()   { return 40f; }
    @Override protected float bis()   { return 120f; }
    @Override protected int farbe()    { return Stil.PULS; }
}
