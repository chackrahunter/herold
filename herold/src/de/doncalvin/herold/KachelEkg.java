package de.doncalvin.herold;

/** Kachel: EKG. */
public class KachelEkg extends KachelBasis {
    @Override protected String art()   { return "EKG"; }
    @Override protected String titel() { return "EKG"; }
    @Override protected float von()   { return 40f; }
    @Override protected float bis()   { return 120f; }
    @Override protected int farbe()    { return Stil.EKG; }
}
