package de.doncalvin.markt;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.os.Build;
import android.util.DisplayMetrics;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

/**
 * Das Geraeteprofil, das Google Play bekommt - die echte Uhr, nichts vorgetaeuscht.
 *
 * Play sucht die passende App-Variante anhand dieser Angaben aus. Steht hier
 * "android.hardware.type.watch" und 432x432, bekommt die Uhr die Wear-Fassung.
 * Die Schluessel sind die 33 Pflichtfelder von gplayapi (DeviceInfoProvider).
 */
public final class Geraet {
    private Geraet() {}

    // Play-Store-Kennung, die Play als Absender erwartet (wie Aurora ohne Play Store)
    static final long   GSF_VORGABE      = 203019037L;
    static final long   VENDING_VORGABE  = 82151710L;
    static final String VENDING_TEXT     = "21.5.17-21 [0] [PR] 326734551";

    public static Properties eigenschaften(Context c) {
        Properties p = new Properties();
        p.setProperty("UserReadableName", Build.MANUFACTURER + " " + Build.MODEL);
        p.setProperty("Build.HARDWARE", Build.HARDWARE);
        String radio = Build.getRadioVersion();
        p.setProperty("Build.RADIO", radio != null && !radio.isEmpty() ? radio : "unknown");
        p.setProperty("Build.FINGERPRINT", Build.FINGERPRINT);
        p.setProperty("Build.BRAND", Build.BRAND);
        p.setProperty("Build.DEVICE", Build.DEVICE);
        p.setProperty("Build.VERSION.SDK_INT", String.valueOf(Build.VERSION.SDK_INT));
        p.setProperty("Build.VERSION.RELEASE", Build.VERSION.RELEASE);
        p.setProperty("Build.MODEL", Build.MODEL);
        p.setProperty("Build.MANUFACTURER", Build.MANUFACTURER);
        p.setProperty("Build.PRODUCT", Build.PRODUCT);
        p.setProperty("Build.ID", Build.ID);
        p.setProperty("Build.BOOTLOADER", Build.BOOTLOADER);

        Configuration k = c.getResources().getConfiguration();
        p.setProperty("TouchScreen", String.valueOf(k.touchscreen));
        p.setProperty("Keyboard", String.valueOf(k.keyboard));
        p.setProperty("Navigation", String.valueOf(k.navigation));
        p.setProperty("ScreenLayout", String.valueOf(k.screenLayout & 15));
        p.setProperty("HasHardKeyboard", String.valueOf(k.keyboard == Configuration.KEYBOARD_QWERTY));
        p.setProperty("HasFiveWayNavigation", String.valueOf(k.navigation == Configuration.NAVIGATIONHIDDEN_YES));

        DisplayMetrics m = c.getResources().getDisplayMetrics();
        p.setProperty("Screen.Density", String.valueOf(m.densityDpi));
        p.setProperty("Screen.Width", String.valueOf(m.widthPixels));
        p.setProperty("Screen.Height", String.valueOf(m.heightPixels));
        p.setProperty("Platforms", String.join(",", Build.SUPPORTED_ABIS));

        PackageManager pm = c.getPackageManager();
        List<String> merkmale = new ArrayList<>();
        FeatureInfo[] fi = pm.getSystemAvailableFeatures();
        if (fi != null) for (FeatureInfo f : fi) if (f != null && f.name != null) merkmale.add(f.name);
        p.setProperty("Features", String.join(",", merkmale));

        List<String> sprachen = new ArrayList<>();
        for (String l : c.getAssets().getLocales()) sprachen.add(l.replace("-", "_"));
        p.setProperty("Locales", String.join(",", sprachen));

        String[] libs = pm.getSystemSharedLibraryNames();
        p.setProperty("SharedLibraries", libs == null ? "" : String.join(",", libs));

        ActivityManager am = (ActivityManager) c.getSystemService(Context.ACTIVITY_SERVICE);
        p.setProperty("GL.Version", String.valueOf(am.getDeviceConfigurationInfo().reqGlEsVersion));
        p.setProperty("GL.Extensions", glErweiterungen());
        p.setProperty("Client", "android-google");

        // Wie Aurora: feste Play-Kennung, unabhaengig davon, was auf der Uhr liegt
        p.setProperty("GSF.version", String.valueOf(GSF_VORGABE));
        p.setProperty("Vending.version", String.valueOf(VENDING_VORGABE));
        p.setProperty("Vending.versionString", VENDING_TEXT);
        p.setProperty("Roaming", "mobile-notroaming");
        p.setProperty("TimeZone", "UTC-10");
        p.setProperty("CellOperator", "310");
        p.setProperty("SimOperator", "38");
        return p;
    }

    private static long versionCode(PackageManager pm, String paket, long vorgabe) {
        try { return pm.getPackageInfo(paket, 0).getLongVersionCode(); } catch (Exception e) { return vorgabe; }
    }
    private static String versionName(PackageManager pm, String paket, String vorgabe) {
        try { String v = pm.getPackageInfo(paket, 0).versionName; return v != null ? v : vorgabe; }
        catch (Exception e) { return vorgabe; }
    }

    /** Liest die GL-Erweiterungen ueber einen kurzlebigen EGL-Kontext (wie Aurora). */
    static String glErweiterungen() {
        EGLDisplay d = EGL14.EGL_NO_DISPLAY; EGLContext ctx = EGL14.EGL_NO_CONTEXT; EGLSurface s = EGL14.EGL_NO_SURFACE;
        try {
            d = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] v = new int[2];
            if (!EGL14.eglInitialize(d, v, 0, v, 1)) return "";
            int[] attr = { EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE };
            EGLConfig[] cfg = new EGLConfig[1]; int[] n = new int[1];
            if (!EGL14.eglChooseConfig(d, attr, 0, cfg, 0, 1, n, 0) || n[0] == 0) return "";
            s = EGL14.eglCreatePbufferSurface(d, cfg[0], new int[]{ EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE }, 0);
            ctx = EGL14.eglCreateContext(d, cfg[0], EGL14.EGL_NO_CONTEXT, new int[]{ EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE }, 0);
            if (!EGL14.eglMakeCurrent(d, s, s, ctx)) return "";
            String ext = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            if (ext == null) return "";
            String[] teile = ext.trim().split("\\s+");
            java.util.Arrays.sort(teile);
            return String.join(",", teile);
        } catch (Throwable t) {
            return "";
        } finally {
            try {
                EGL14.eglMakeCurrent(d, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (s != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(d, s);
                if (ctx != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(d, ctx);
                if (d != EGL14.EGL_NO_DISPLAY) EGL14.eglTerminate(d);
            } catch (Throwable ignored) {}
        }
    }
}
