# 05 — Ein Ziffernblatt selbst bauen

`zifferblatt/` ist ein vollständiges, lauffähiges Beispiel: eine eigene
Zeichnung als Hintergrund, Uhrzeit und Datum darüber, dazu ein sparsamer
Ruhemodus. Es zeigt den kompletten Weg — zeichnen, bauen, aufspielen, aktiv
setzen — ohne Gradle und ohne Konto.

## Welche Art Ziffernblatt?

Es gibt zwei Wege:

| | klassisch (`CanvasWatchFaceService`) | Watch Face Format (WFF) |
|---|---|---|
| Zeichnen | freier Canvas, voller Code | XML, feste Bausteine |
| Bibliothek | Wear-Support-JAR nötig | keine |
| Wear OS | läuft als Sideload auch auf 4/5/6 | ab Wear OS 4 |
| Freiheit | **volle** | begrenzt |

Dieses Beispiel nimmt den **klassischen** Weg: Er ist zwar veraltet, läuft als
Sideload aber zuverlässig und erlaubt beliebiges Zeichnen. Wichtig ist dabei
`--target-sdk-version 29`, sonst behandelt Wear OS es nicht als Legacy-Face und
es taucht nicht in der Auswahl auf.

## Aufbau

```
zifferblatt/
├── AndroidManifest.xml          WallpaperService + WATCH_FACE-Kategorie + Vorschau
├── build.sh
├── res/
│   ├── drawable-nodpi/hund.png     Hintergrundbild (eigene Zeichnung)
│   ├── drawable-nodpi/preview.png  Vorschau für die Auswahl
│   ├── values/strings.xml
│   └── xml/watch_face.xml          <wallpaper/>
└── src/de/doncalvin/zifferblatt/HundFace.java
```

Das Manifest ist der Teil, den man leicht falsch macht:

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

## Zeichnen

`HundFace.onDraw()` malt im Betrieb das Bild bildschirmfüllend und schreibt
Uhrzeit und Datum in den freien oberen Bereich. Im **Ruhemodus** nur schwarzer
Grund mit weißer Uhrzeit — das spart Akku und ist auf OLED richtig.

Die Grafik entstand als SVG und wurde mit `rsvg-convert` zu PNG gerechnet:

```bash
rsvg-convert -w 450 -h 450 bild.svg -o res/drawable-nodpi/hund.png
```

Für die Vorschau lohnt es sich, eine Beispielzeit fest einzuzeichnen — dann
sieht die Auswahl auf der Uhr nach echtem Ziffernblatt aus.

## Aufspielen und aktiv setzen

```bash
cd zifferblatt && ./build.sh
adb -t <id> install -r build/zifferblatt.apk
```

Und dann — der Griff, den man nirgends dokumentiert findet — das Ziffernblatt
per adb **aktiv setzen**:

```bash
adb -t <id> shell am broadcast \
  -a com.google.android.wearable.app.DEBUG_SURFACE \
  --es operation set-watchface \
  --ecn component de.doncalvin.zifferblatt/.HundFace
```

Antwortet die Uhr mit `Favorite Id=[..] Runtime=[..]`, sitzt es.

> **Wichtig:** `--ecn` (ComponentName-Extra), **nicht** `--es component`. Mit
> `--es` antwortet die Uhr stur „Either component name or watchface id are
> required."

## Rechtliches zur Grafik

Die mitgelieferte Zeichnung ist eine eigene, generische Hundefigur. Wer ein
Ziffernblatt mit einer **geschützten Figur** bauen will: Das ist Sache der
Rechteinhaber — lade dir solche Blätter über die lizenzierten Anbieter
(z. B. Facer) statt sie nachzuzeichnen.
