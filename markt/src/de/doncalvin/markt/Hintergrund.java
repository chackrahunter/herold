package de.doncalvin.markt;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Arbeit im Hintergrund, Ergebnis auf dem Hauptthread. Ein Faden reicht, Play mag keine Salven. */
public final class Hintergrund {
    private Hintergrund() {}
    private static final ExecutorService FADEN = Executors.newSingleThreadExecutor();
    private static final Handler HAUPT = new Handler(Looper.getMainLooper());

    public interface Danach<T> { void fertig(T t); }
    public interface Fehler { void fehler(Exception e); }

    public static <T> void tue(final Callable<T> arbeit, final Danach<T> danach, final Fehler fehler) {
        FADEN.execute(() -> {
            try {
                final T t = arbeit.call();
                HAUPT.post(() -> danach.fertig(t));
            } catch (final Exception e) {
                HAUPT.post(() -> fehler.fehler(e));
            }
        });
    }

    public static void spaeter(Runnable r, long ms) { HAUPT.postDelayed(r, ms); }
    public static void haupt(Runnable r) { HAUPT.post(r); }
}
