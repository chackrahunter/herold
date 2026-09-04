package de.doncalvin.herold;

import android.graphics.*;
import android.graphics.drawable.Icon;

/** Baut pro App ein eigenes Symbol: Anfangsbuchstabe in der Markenfarbe. */
public class AppLook {

    /** Bekannte Bundle-IDs -> Markenfarbe und Anzeigename. */
    public static int farbe(String appId) {
        if (appId == null) return 0xFF5B6BFF;
        String a = appId.toLowerCase();
        if (a.contains("whatsapp"))                 return 0xFF25D366;
        if (a.contains("ring"))                     return 0xFF1D8DE0;
        if (a.contains("telegram"))                 return 0xFF2AABEE;
        if (a.contains("signal"))                   return 0xFF3A76F0;
        if (a.contains("instagram"))                return 0xFFE1306C;
        if (a.contains("snapchat"))                 return 0xFFFFFC00;
        if (a.contains("tiktok") || a.contains("musically")) return 0xFFFE2C55;
        if (a.contains("discord"))                  return 0xFF5865F2;
        if (a.contains("spotify"))                  return 0xFF1DB954;
        if (a.contains("youtube"))                  return 0xFFFF0000;
        if (a.contains("gmail"))                    return 0xFFEA4335;
        if (a.contains("paypal"))                   return 0xFF003087;
        if (a.contains("amazon"))                   return 0xFFFF9900;
        if (a.contains("mobilesms") || a.contains("message")) return 0xFF34C759;
        if (a.contains("mobilephone") || a.contains("phone")) return 0xFF30D158;
        if (a.contains("mobilemail") || a.contains("mail"))   return 0xFF1A73E8;
        if (a.contains("mobilecal")  || a.contains("calendar")) return 0xFFFF3B30;
        if (a.contains("facebook"))                 return 0xFF1877F2;
        if (a.contains("threema"))                  return 0xFF2E7D32;
        if (a.contains("outlook"))                  return 0xFF0078D4;
        if (a.contains("teams"))                    return 0xFF6264A7;
        if (a.contains("stellium"))                 return 0xFF8B5CF6;
        // Unbekannt: Farbe aus der Bundle-ID ableiten, damit sie stabil bleibt
        int h = appId.hashCode();
        float[] hsv = { Math.abs(h % 360), 0.62f, 0.92f };
        return Color.HSVToColor(hsv);
    }

    /** Erzeugt ein rundes Symbol mit dem Anfangsbuchstaben. */
    public static Icon symbol(String name) {
        String buchstabe = (name == null || name.isEmpty())
                ? "?" : name.substring(0, 1).toUpperCase();

        int gr = 96;
        Bitmap bm = Bitmap.createBitmap(gr, gr, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bm);

        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setColor(Color.WHITE);
        p.setTextAlign(Paint.Align.CENTER);
        p.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        p.setTextSize(gr * 0.72f);

        Paint.FontMetrics fm = p.getFontMetrics();
        float y = gr / 2f - (fm.ascent + fm.descent) / 2f;
        c.drawText(buchstabe, gr / 2f, y, p);

        return Icon.createWithBitmap(bm);
    }
}
