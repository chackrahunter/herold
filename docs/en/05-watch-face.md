[Deutsche Fassung](../05-zifferblatt.md)

# 05 — Building your own watch face

`zifferblatt/` is a complete, working example: a hand-drawn image as the
background, time and date on top of it, plus a frugal ambient mode. It shows
the whole path — draw it, build it, install it, make it active — without Gradle
and without an account.

## Which kind of watch face?

There are two routes:

| | classic (`CanvasWatchFaceService`) | Watch Face Format (WFF) |
|---|---|---|
| Drawing | free-form canvas, all in code | XML, fixed building blocks |
| Library | Wear support JAR required | none |
| Wear OS | runs sideloaded on 4/5/6 as well | Wear OS 4 and up |
| Freedom | **full** | limited |

This example takes the **classic** route. It is deprecated, but it runs
reliably when sideloaded and lets you draw whatever you want. The important
part is `--target-sdk-version 29` — without it, Wear OS does not treat the app
as a legacy face and it never shows up in the picker.

## Layout

```
zifferblatt/
├── AndroidManifest.xml          WallpaperService + WATCH_FACE category + preview
├── build.sh
├── res/
│   ├── drawable-nodpi/hund.png     background image (own drawing)
│   ├── drawable-nodpi/preview.png  preview for the picker
│   ├── values/strings.xml
│   └── xml/watch_face.xml          <wallpaper/>
└── src/de/doncalvin/zifferblatt/HundFace.java
```

The manifest is the part that is easy to get wrong:

```xml
<uses-feature android:name="android.hardware.type.watch" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<uses-library android:name="com.google.android.wearable" android:required="false" />

<service android:name=".HundFace"
         android:permission="android.permission.BIND_WALLPAPER"
         android:exported="true">
    <meta-data android:name="android.service.wallpaper" android:resource="@xml/watch_face" />
    <meta-data android:name="com.google.android.wearable.watchface.preview"
               android:resource="@drawable/preview" />
    <intent-filter>
        <action android:name="android.service.wallpaper.WallpaperService" />
        <category android:name="com.google.android.wearable.watchface.category.WATCH_FACE" />
    </intent-filter>
</service>
```

## Drawing

While the screen is awake, `HundFace.onDraw()` paints the image across the full
display and writes time and date into the free area at the top. In **ambient
mode** it draws nothing but a black background with the time in white — that
saves battery and is the right thing to do on OLED.

The artwork was made as an SVG and rendered to PNG with `rsvg-convert`:

```bash
rsvg-convert -w 450 -h 450 bild.svg -o res/drawable-nodpi/hund.png
```

For the preview image it pays to draw a fixed sample time into it — then the
picker on the watch looks like a real watch face.

## Installing and making it active

```bash
cd zifferblatt && ./build.sh
adb -t <id> install -r build/zifferblatt.apk
```

And then — the trick that is documented nowhere — **make the watch face
active** over adb:

```bash
adb -t <id> shell am broadcast \
  -a com.google.android.wearable.app.DEBUG_SURFACE \
  --es operation set-watchface \
  --ecn component de.doncalvin.zifferblatt/.HundFace
```

If the watch answers with `Favorite Id=[..] Runtime=[..]`, it took.

> **Important:** `--ecn` (a ComponentName extra), **not** `--es component`.
> With `--es` the watch stubbornly answers "Either component name or watchface
> id are required."

## Legal note on the artwork

The drawing shipped here is our own, generic dog figure. If you want to build a
watch face with a **protected character**: that is the rights holders' call —
get faces like that from the licensed providers (Facer, for example) instead of
redrawing them.
