[Deutsche Fassung](../02-bibliotheken.md)

# 02 — Getting the libraries

This repo contains **no** third-party JAR files. They belong to other people,
and some come with licenses that restrict redistribution. Fetch them yourself,
once — it only takes a few minutes.

Put each of them into the project's `libs/` folder.

---

## herold/libs/

**Samsung Health Sensor SDK** — required for ECG, SpO₂, skin temperature and
body composition.

- `samsung-health-sensor.jar`
- Source: [Samsung Developer Portal](https://developer.samsung.com/health/sensor)
  (sign-in required, mind the license — **do not redistribute**)
- On top of that, the **Samsung Health Platform** app must be installed and up
  to date on the watch, otherwise the sensor service does not respond.

**Wear tiles (AndroidX)** — only needed if you want the tiles:

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

These come from the Google Maven repository. Pattern for an AAR:

```bash
curl -sL -o t.aar https://dl.google.com/android/maven2/androidx/wear/tiles/tiles/1.4.1/tiles-1.4.1.aar
unzip -o t.aar classes.jar && mv classes.jar libs/tiles-1.4.1.jar
```

Plain JARs (for example `*-proto-*`) are downloaded directly.

---

## markt/libs/

The store talks to Google Play through **gplayapi** (the library behind Aurora
Store). You need the library itself and its runtime dependencies:

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

The file **`markt/BIBLIOTHEKEN.txt`** ("libraries") lists the exact versions,
the download URLs and the SHA-256 checksums this project was built with. Stick
to that list and it will fit together.

Example:

```bash
curl -sL -o g.aar https://repo1.maven.org/maven2/com/auroraoss/gplayapi/3.6.4/gplayapi-3.6.4.aar
unzip -o g.aar classes.jar && mv classes.jar libs/gplayapi-3.6.4.jar
```

> **Note:** The empty "root" artifacts of OkHttp/Okio (a few hundred bytes) are
> useless — you need the `-jvm` variants.

---

## zifferblatt/libs/

**Wear support library** for the classic `CanvasWatchFaceService`:

```bash
curl -sL -o w.aar https://dl.google.com/android/maven2/com/google/android/support/wearable/2.9.0/wearable-2.9.0.aar
unzip -o w.aar classes.jar && mv classes.jar libs/wearable-support-2.9.0.jar
```

You do **not** need the "provided" stub `com.google.android.wearable:wearable`,
and you must not bundle it either — it would collide with the system library.

---

## Checking

```bash
for j in libs/*.jar; do unzip -t "$j" >/dev/null 2>&1 && echo "ok $j" || echo "KAPUTT $j"; done
```
