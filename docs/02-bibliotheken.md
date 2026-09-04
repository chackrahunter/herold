# 02 — Bibliotheken beschaffen

Im Repo liegen **keine** fremden JAR-Dateien. Sie gehören anderen, teils mit
Lizenzen, die eine Weiterverteilung einschränken. Hol sie dir einmal selbst —
das dauert ein paar Minuten.

Lege sie jeweils in den `libs/`-Ordner des Projekts.

---

## herold/libs/

**Samsung Health Sensor SDK** — Pflicht für EKG, SpO₂, Hauttemperatur und
Körperanalyse.

- `samsung-health-sensor.jar`
- Quelle: [Samsung Developer Portal](https://developer.samsung.com/health/sensor)
  (Anmeldung nötig, Lizenz beachten — **nicht weiterverteilen**)
- Zusätzlich muss auf der Uhr die App **Samsung Health Platform** installiert und
  aktuell sein, sonst antwortet der Sensordienst nicht.

**Wear-Kacheln (AndroidX)** — nur nötig, wenn du die Kacheln willst:

```
tiles-1.4.1.jar
tiles-proto-1.4.1.jar
protolayout-1.2.1.jar
protolayout-expression-1.2.1.jar
protolayout-proto-1.2.1.jar
protolayout-external-protobuf-1.2.1.jar
annotation-1.2.0.jar
concurrent-futures-1.1.0.jar
listenablefuture-1.0.jar
```

Diese stammen aus dem Google-Maven-Repository. Muster für eine AAR:

```bash
curl -sL -o t.aar https://dl.google.com/android/maven2/androidx/wear/tiles/tiles/1.4.1/tiles-1.4.1.aar
unzip -o t.aar classes.jar && mv classes.jar libs/tiles-1.4.1.jar
```

Reine JARs (z. B. `*-proto-*`) lädst du direkt.

---

## markt/libs/

Der Laden spricht mit Google Play über **gplayapi** (die Bibliothek hinter
Aurora Store). Nötig sind die Bibliothek und ihre Laufzeitabhängigkeiten:

```
gplayapi-3.6.4.jar          (aus com.auroraoss:gplayapi:3.6.4, AAR -> classes.jar)
kotlin-stdlib
kotlinx-coroutines-core-jvm
kotlinx-serialization-core-jvm
kotlinx-serialization-json-jvm
kotlin-parcelize-runtime
okhttp-jvm  +  okio-jvm
protobuf-javalite
gson
annotations (org.jetbrains)
error_prone_annotations
```

Die Datei **`markt/BIBLIOTHEKEN.txt`** listet die exakten Versionen, die
Download-URLs und die SHA-256-Prüfsummen, mit denen dieses Projekt gebaut wurde.
Halte dich daran, dann passt es.

Beispiel:

```bash
curl -sL -o g.aar https://repo1.maven.org/maven2/com/auroraoss/gplayapi/3.6.4/gplayapi-3.6.4.aar
unzip -o g.aar classes.jar && mv classes.jar libs/gplayapi-3.6.4.jar
```

> **Hinweis:** Die leeren „Wurzel"-Artefakte von OkHttp/Okio (wenige hundert
> Byte) nützen nichts — du brauchst die `-jvm`-Varianten.

---

## zifferblatt/libs/

**Wear-Support-Bibliothek** für das klassische `CanvasWatchFaceService`:

```bash
curl -sL -o w.aar https://dl.google.com/android/maven2/com/google/android/support/wearable/2.9.0/wearable-2.9.0.aar
unzip -o w.aar classes.jar && mv classes.jar libs/wearable-support-2.9.0.jar
```

Den „Provided"-Stub `com.google.android.wearable:wearable` brauchst du **nicht**
und darfst ihn auch nicht mit einpacken — er würde mit der Systembibliothek
kollidieren.

---

## Prüfen

```bash
for j in libs/*.jar; do unzip -t "$j" >/dev/null 2>&1 && echo "ok $j" || echo "KAPUTT $j"; done
```
