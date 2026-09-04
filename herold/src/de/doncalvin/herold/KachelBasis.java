package de.doncalvin.herold;

import android.content.ComponentName;

import androidx.wear.protolayout.ActionBuilders;
import androidx.wear.protolayout.ColorBuilders;
import androidx.wear.protolayout.DimensionBuilders;
import androidx.wear.protolayout.LayoutElementBuilders;
import androidx.wear.protolayout.ModifiersBuilders;
import androidx.wear.protolayout.ResourceBuilders;
import androidx.wear.protolayout.TimelineBuilders;
import androidx.wear.tiles.RequestBuilders;
import androidx.wear.tiles.TileBuilders;
import androidx.wear.tiles.TileService;

import com.google.common.util.concurrent.ListenableFuture;

import java.util.List;
import java.util.concurrent.Executor;

/**
 * Gemeinsamer Unterbau fuer die Kacheln, die man neben dem Zifferblatt nach
 * links durchwischt.
 *
 * Eine Kachel ist kein Bildschirm, den man zeichnet - man schickt dem System
 * eine Beschreibung, und der Wirt malt sie. Deshalb gibt es hier kein Canvas
 * und keine Animation: was moeglich ist, gibt die Beschreibungssprache vor.
 *
 * Jede Kachel zeigt den zuletzt gemessenen Wert aus dem Verlauf. Ein Tipp
 * darauf startet die zugehoerige Messung.
 */
public abstract class KachelBasis extends TileService {

    /** Ausfuehrer, der sofort im aufrufenden Faden arbeitet - wir rechnen nichts Langes. */
    private static final Executor SOFORT = Runnable::run;

    /** Welche Messart diese Kachel zeigt (siehe SensorService). */
    protected abstract String art();

    /** Ueberschrift der Kachel. */
    protected abstract String titel();

    /** Kennfarbe der Messart. */
    protected abstract int farbe();

    /** Was steht da, wenn noch nie gemessen wurde? */
    protected String platzhalter() { return "—"; }

    /** Unteres und oberes Ende der Skala am Kachelrand. */
    protected float von() { return 40f; }
    protected float bis() { return 120f; }

    @Override
    protected ListenableFuture<TileBuilders.Tile> onTileRequest(
            RequestBuilders.TileRequest anfrage) {
        return erledigt(baue());
    }

    @Override
    protected ListenableFuture<ResourceBuilders.Resources> onTileResourcesRequest(
            RequestBuilders.ResourcesRequest anfrage) {
        return erledigt(new ResourceBuilders.Resources.Builder()
                .setVersion("1").build());
    }

    @Override
    protected void attachBaseContext(android.content.Context c) { super.attachBaseContext(c); }

    /** Fuer den Selbsttest: baut die Kachel, ohne dass der Wirt beteiligt ist. */
    TileBuilders.Tile testBaue() { return baue(); }

    private TileBuilders.Tile baue() {
        Verlauf.Eintrag letzte = juengste();

        String wert = letzte != null ? letzte.wert : platzhalter();
        String unten = letzte != null ? letzte.zeitText() : "noch nicht gemessen";
        if (letzte != null && letzte.zusatz != null && !letzte.zusatz.isEmpty()
                && !letzte.zusatz.equals("Haut") && !letzte.zusatz.equals("Puls")) {
            unten = letzte.zusatz;
        }

        LayoutElementBuilders.Column inhalt = new LayoutElementBuilders.Column.Builder()
                .setWidth(DimensionBuilders.expand())
                .addContent(zeile(titel().toUpperCase(java.util.Locale.GERMANY), 11f,
                        farbe(), false))
                .addContent(luft(10))
                .addContent(zeile(wert, 40f, Stil.TEXT_STARK, false))
                .addContent(luft(6))
                .addContent(zeile(unten, 10f, Stil.TEXT_SCHWACH, false))
                .build();

        ModifiersBuilders.Modifiers tippen = new ModifiersBuilders.Modifiers.Builder()
                .setClickable(new ModifiersBuilders.Clickable.Builder()
                        .setId("messen")
                        .setOnClick(new ActionBuilders.LaunchAction.Builder()
                                .setAndroidActivity(zielActivity())
                                .build())
                        .build())
                .build();

        // Ein Bogen am Rand macht aus der Zahl eine Anzeige: man sieht auf
        // einen Blick, wo der Wert in seinem ueblichen Bereich liegt, ohne
        // die Zahl zu lesen. Die Skala steht in von()/bis().
        float anteil = anteilVon(letzte);
        LayoutElementBuilders.Arc.Builder bogen = new LayoutElementBuilders.Arc.Builder()
                .setAnchorAngle(DimensionBuilders.degrees(0f))
                .setAnchorType(LayoutElementBuilders.ARC_ANCHOR_START)
                .addContent(new LayoutElementBuilders.ArcLine.Builder()
                        .setLength(DimensionBuilders.degrees(280f))
                        .setThickness(DimensionBuilders.dp(6f))
                        .setColor(ColorBuilders.argb(Stil.FLAECHE_02))
                        .setStrokeCap(new LayoutElementBuilders.StrokeCapProp.Builder()
                                .setValue(LayoutElementBuilders.STROKE_CAP_ROUND).build())
                        .build());
        if (anteil > 0f) {
            bogen.addContent(new LayoutElementBuilders.ArcLine.Builder()
                    .setLength(DimensionBuilders.degrees(-280f * anteil))
                    .setThickness(DimensionBuilders.dp(6f))
                    .setColor(ColorBuilders.argb(farbe()))
                    .setStrokeCap(new LayoutElementBuilders.StrokeCapProp.Builder()
                            .setValue(LayoutElementBuilders.STROKE_CAP_ROUND).build())
                    .build());
        }

        LayoutElementBuilders.Box rahmen = new LayoutElementBuilders.Box.Builder()
                .setWidth(DimensionBuilders.expand())
                .setHeight(DimensionBuilders.expand())
                .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
                .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
                .setModifiers(tippen)
                .addContent(new LayoutElementBuilders.Box.Builder()
                        .setWidth(DimensionBuilders.expand())
                        .setHeight(DimensionBuilders.expand())
                        .addContent(bogen.build())
                        .build())
                .addContent(inhalt)
                .build();

        return new TileBuilders.Tile.Builder()
                .setResourcesVersion("1")
                // Der Wirt darf die Kachel alle zehn Minuten neu anfordern.
                // Oefter waere Stromverschwendung: die Werte aendern sich nur,
                // wenn gemessen wurde, und dann stossen wir selbst an.
                .setFreshnessIntervalMillis(10 * 60 * 1000L)
                .setTileTimeline(new TimelineBuilders.Timeline.Builder()
                        .addTimelineEntry(new TimelineBuilders.TimelineEntry.Builder()
                                .setLayout(new LayoutElementBuilders.Layout.Builder()
                                        .setRoot(rahmen).build())
                                .build())
                        .build())
                .build();
    }

    /** Tippen startet die Messung - beim EKG dessen eigener Bildschirm. */
    private ActionBuilders.AndroidActivity zielActivity() {
        // Die Atemfrequenz wird bei der Rhythmusmessung mitermittelt.
        String ziel = "ATEM".equals(art()) ? SensorService.PULS : art();
        boolean ekg = "EKG".equals(ziel);
        ActionBuilders.AndroidActivity.Builder b = new ActionBuilders.AndroidActivity.Builder()
                .setPackageName(getPackageName())
                .setClassName(ekg
                        ? EkgActivity.class.getName()
                        : MessActivity.class.getName());
        if (!ekg) {
            b.addKeyToExtraMapping("art",
                    new ActionBuilders.AndroidStringExtra.Builder().setValue(ziel).build());
        }
        return b.build();
    }

    /**
     * Wo liegt der zuletzt gemessene Wert auf der Skala? Die Zahl steckt im
     * gespeicherten Text ("97 %", "34,2 grad C"), also wird sie herausgeloest -
     * lieber das, als jeden Wert doppelt zu speichern.
     */
    private float anteilVon(Verlauf.Eintrag e) {
        if (e == null || e.wert == null) return 0f;
        StringBuilder z = new StringBuilder();
        for (char c : e.wert.toCharArray()) {
            if (Character.isDigit(c)) z.append(c);
            else if ((c == ',' || c == '.') && z.length() > 0) z.append('.');
            else if (z.length() > 0) break;
        }
        if (z.length() == 0) return 0f;
        try {
            float w = Float.parseFloat(z.toString());
            float a = (w - von()) / (bis() - von());
            return Math.max(0f, Math.min(1f, a));
        } catch (NumberFormatException ex) {
            return 0f;
        }
    }

    private Verlauf.Eintrag juengste() {
        try {
            List<Verlauf.Eintrag> l = new Verlauf(this).lies(art());
            return l.isEmpty() ? null : l.get(0);
        } catch (Throwable t) {
            return null;
        }
    }

    // ---- Bausteine der Beschreibungssprache ----------------------------

    private LayoutElementBuilders.Text zeile(String s, float groesse, int farbe, boolean fett) {
        return new LayoutElementBuilders.Text.Builder()
                .setText(s)
                .setFontStyle(new LayoutElementBuilders.FontStyle.Builder()
                        .setSize(DimensionBuilders.sp(groesse))
                        .setColor(ColorBuilders.argb(farbe))
                        .setWeight(fett
                                ? LayoutElementBuilders.FONT_WEIGHT_BOLD
                                : LayoutElementBuilders.FONT_WEIGHT_NORMAL)
                        .build())
                .setMaxLines(1)
                .build();
    }

    private LayoutElementBuilders.Spacer luft(float dp) {
        return new LayoutElementBuilders.Spacer.Builder()
                .setHeight(DimensionBuilders.dp(dp)).build();
    }

    private static <T> ListenableFuture<T> erledigt(final T wert) {
        return new ListenableFuture<T>() {
            @Override public void addListener(Runnable r, Executor e) { e.execute(r); }
            @Override public boolean cancel(boolean b) { return false; }
            @Override public boolean isCancelled() { return false; }
            @Override public boolean isDone() { return true; }
            @Override public T get() { return wert; }
            @Override public T get(long t, java.util.concurrent.TimeUnit u) { return wert; }
        };
    }

    /** Stoesst nach einer Messung an, dass alle Kacheln neu gezeichnet werden. */
    public static void auffrischen(android.content.Context c) {
        try {
            androidx.wear.tiles.TileUpdateRequester u = TileService.getUpdater(c);
            for (Class<?> k : new Class<?>[]{ KachelPuls.class, KachelEkg.class,
                    KachelSauerstoff.class, KachelTemperatur.class, KachelKoerper.class,
                    KachelAtem.class }) {
                @SuppressWarnings("unchecked")
                Class<? extends TileService> kk = (Class<? extends TileService>) k;
                u.requestUpdate(kk);
            }
        } catch (Throwable t) {
            android.util.Log.w("HeroldKachel", "Auffrischen fehlgeschlagen", t);
        }
    }
}
