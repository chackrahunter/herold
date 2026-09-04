package de.doncalvin.markt;

import android.content.Context;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.widget.ScrollView;

/**
 * Macht die drehbare Lünette und die Krone zum Scrollrad.
 *
 * Ohne AndroidX gibt es kein WearableRecyclerView, das das von allein koennte.
 * Die Uhr schickt das Drehen als MotionEvent mit ACTION_SCROLL von der Quelle
 * SOURCE_ROTARY_ENCODER. Damit die Ereignisse ueberhaupt ankommen, muss die
 * Ansicht den Fokus haben - das ist der Teil, den man leicht uebersieht.
 */
public final class Rad {

    private Rad() {}

    public static void anBinden(final ScrollView roller) {
        final Context c = roller.getContext();
        roller.setFocusable(true);
        roller.setFocusableInTouchMode(true);
        roller.requestFocus();

        roller.setOnGenericMotionListener(new View.OnGenericMotionListener() {
            @Override
            public boolean onGenericMotion(View v, MotionEvent e) {
                if (e.getAction() != MotionEvent.ACTION_SCROLL) return false;
                if (!e.isFromSource(InputDevice.SOURCE_ROTARY_ENCODER)) return false;

                float faktor = ViewConfiguration.get(c).getScaledVerticalScrollFactor();
                // Im Uhrzeigersinn liefert die Achse negative Werte; nach unten
                // scrollen bedeutet positives scrollBy, daher das Minus.
                int weg = Math.round(-e.getAxisValue(MotionEvent.AXIS_SCROLL) * faktor);
                roller.scrollBy(0, weg);
                return true;
            }
        });
    }

    /** Fokus nach dem Aufbau der Ansicht sicherstellen. */
    public static void fokusHolen(final ScrollView roller) {
        roller.post(new Runnable() {
            @Override public void run() { roller.requestFocus(); }
        });
    }
}
