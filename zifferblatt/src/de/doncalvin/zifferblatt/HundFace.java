package de.doncalvin.zifferblatt;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.view.SurfaceHolder;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import java.util.TimeZone;

/**
 * Hund-Ziffernblatt - selbst gezeichnet, laeuft ohne Play und ohne Handy.
 *
 * Hintergrund ist die Hund-Grafik, oben im freien Himmel stehen Uhrzeit und
 * Datum. Im Ruhemodus (Display gedimmt) nur schwarzer Grund mit weisser Zeit,
 * das schont den Akku.
 */
public class HundFace extends CanvasWatchFaceService {

    @Override public Engine onCreateEngine() { return new Motor(); }

    private class Motor extends CanvasWatchFaceService.Engine {
        private Bitmap hund;
        private final Rect quelle = new Rect();
        private final Rect ziel = new Rect();
        private Paint zeit, datum, zeitAmbient, datumAmbient;
        private final Calendar cal = Calendar.getInstance();
        private SimpleDateFormat fmtDatum;
        private boolean ambient, lowBit, burnIn, registriert;

        private final BroadcastReceiver zoneEmpfang = new BroadcastReceiver() {
            @Override public void onReceive(Context c, Intent i) {
                cal.setTimeZone(TimeZone.getDefault()); invalidate();
            }
        };

        @Override public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);
            setWatchFaceStyle(new WatchFaceStyle.Builder(HundFace.this)
                    .setAcceptsTapEvents(false)
                    .setHideNotificationIndicator(false)
                    .build());
            hund = BitmapFactory.decodeResource(getResources(), R.drawable.hund);
            quelle.set(0, 0, hund.getWidth(), hund.getHeight());

            zeit = new Paint(Paint.ANTI_ALIAS_FLAG);
            zeit.setColor(0xFF20242B); zeit.setTextAlign(Paint.Align.CENTER);
            zeit.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            datum = new Paint(Paint.ANTI_ALIAS_FLAG);
            datum.setColor(0xFF3A4451); datum.setTextAlign(Paint.Align.CENTER);
            datum.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            zeitAmbient = new Paint(Paint.ANTI_ALIAS_FLAG);
            zeitAmbient.setColor(0xFFFFFFFF); zeitAmbient.setTextAlign(Paint.Align.CENTER);
            zeitAmbient.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
            datumAmbient = new Paint(Paint.ANTI_ALIAS_FLAG);
            datumAmbient.setColor(0xFFB8C0CC); datumAmbient.setTextAlign(Paint.Align.CENTER);

            fmtDatum = new SimpleDateFormat("EEE d. MMM", Locale.GERMAN);
        }

        @Override public void onDestroy() {
            entregistrieren();
            if (hund != null) hund.recycle();
            super.onDestroy();
        }

        @Override public void onPropertiesChanged(Bundle p) {
            super.onPropertiesChanged(p);
            lowBit = p.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            burnIn = p.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override public void onAmbientModeChanged(boolean a) {
            super.onAmbientModeChanged(a);
            ambient = a;
            boolean aa = !(a && lowBit);
            zeitAmbient.setAntiAlias(aa); datumAmbient.setAntiAlias(aa);
            invalidate();
        }

        @Override public void onTimeTick() { super.onTimeTick(); invalidate(); }

        @Override public void onVisibilityChanged(boolean sichtbar) {
            super.onVisibilityChanged(sichtbar);
            if (sichtbar) {
                if (!registriert) { registriert = true;
                    registerReceiver(zoneEmpfang, new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED)); }
                cal.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else entregistrieren();
        }

        private void entregistrieren() {
            if (registriert) { registriert = false; try { unregisterReceiver(zoneEmpfang); } catch (Exception ignored) {} }
        }

        @Override public void onDraw(Canvas c, Rect bounds) {
            cal.setTimeInMillis(System.currentTimeMillis());
            int w = bounds.width(), h = bounds.height();
            String uhr = String.format(Locale.GERMANY, "%02d:%02d",
                    cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE));
            String dat = fmtDatum.format(cal.getTime());

            if (ambient) {
                c.drawColor(0xFF000000);
                zeitAmbient.setTextSize(w * 0.22f);
                datumAmbient.setTextSize(w * 0.058f);
                c.drawText(uhr, w / 2f, h * 0.5f, zeitAmbient);
                c.drawText(dat, w / 2f, h * 0.5f + w * 0.10f, datumAmbient);
            } else {
                c.drawColor(0xFFCDEBFB);
                ziel.set(0, 0, w, h);
                c.drawBitmap(hund, quelle, ziel, null);
                zeit.setTextSize(w * 0.21f);
                datum.setTextSize(w * 0.058f);
                c.drawText(uhr, w / 2f, h * 0.34f, zeit);
                c.drawText(dat, w / 2f, h * 0.41f, datum);
            }
        }
    }
}
