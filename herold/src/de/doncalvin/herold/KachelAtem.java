package de.doncalvin.herold;

/**
 * Kachel: Atemfrequenz.
 *
 * Sie hat keine eigene Messung - der Wert faellt bei der Rhythmusmessung mit
 * ab, weil die Atmung den Puls moduliert. Ein Tipp startet deshalb dieselbe
 * Messung wie die Pulskachel.
 */
public class KachelAtem extends KachelBasis {
    @Override protected String art()   { return "ATEM"; }
    @Override protected String titel() { return "Atemfrequenz"; }
    @Override protected float von()   { return 6f; }
    @Override protected float bis()   { return 24f; }
    @Override protected int farbe()    { return 0xFF56C8D8; }
}
