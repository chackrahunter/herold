[Deutsche Fassung](../01-bauen-ohne-gradle.md)

# 01 — Building without Gradle

All three apps are built without Gradle and without Android Studio. Every
project has a `build.sh` that calls the four Android SDK tools directly.

## Why avoid Gradle at all?

For a few hundred lines of Java, Gradle drags in half a gigabyte of
dependencies, a daemon and a long cold start. On a machine with little memory
you feel that. The direct route builds the same APK in **10–15 seconds**, is
fully traceable and has no hidden configuration: what is written in `build.sh`
is what happens — nothing more.

The price: there is **no dependency resolver**. Every library has to sit in
`libs/` by hand. The list in there *is* the truth.

## The chain

```
res/          --aapt2 compile-->  res.zip
res.zip       --aapt2 link----->  base.apk + R.java
src/ + R.java --javac---------->  *.class
*.class+libs  --d8------------->  classes.dex
base.apk+dex  --zip+zipalign-->   aligned APK
              --apksigner----->   finished, signed APK
```

In plain terms (shortened from `herold/build.sh`):

```bash
aapt2 compile --dir res -o build/res.zip
aapt2 link -o build/base.apk -I "$AJAR" --manifest AndroidManifest.xml \
     --java build/gen --min-sdk-version 30 --target-sdk-version 33 build/res.zip
javac -source 17 -target 17 -classpath "$CP" -d build/classes @build/sources.txt
jar cf build/classes.jar -C build/classes .
d8 --min-api 30 --lib "$AJAR" --output build/ build/classes.jar "${LIBS[@]}"
cp build/base.apk build/app-unsigned.apk
(cd build && zip -q app-unsigned.apk classes*.dex)
zipalign -f 4 build/app-unsigned.apk build/app-aligned.apk
apksigner sign --ks "$KS" --out build/app.apk build/app-aligned.apk
```

## Pitfalls that would otherwise cost you hours

**Libraries have to go into `javac` *and* into `d8`.**
The classpath alone is not enough — it compiles cleanly and then crashes at
runtime with `NoClassDefFoundError`. That is why the scripts collect
`libs/*.jar` once and hand them to both.

**Multiple DEX files.**
As soon as libraries come into play (Markt: Kotlin, OkHttp, Protobuf), `d8`
produces `classes.dex`, `classes2.dex`, … The zip step has to pack
`classes*.dex`, not just `classes.dex`. Otherwise half of it is missing at
runtime.

**Create the key only once.**
`build.sh` creates the keystore if it is missing — and **only** then. If a new
one is generated on every build, the signature changes and the watch refuses
the update with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. So the keystore does
**not** belong in the repo, but it very much belongs in your local backup.

**Keystore passwords:** `keytool` demands at least 6 characters. Shorter ones
abort the build with a rather unhelpful message.

**Do not filter errors away.**
When building, always judge the outcome by whether the APK exists — not by the
text of the output:

```bash
rm -f build/app.apk
./build.sh > /tmp/build.log 2>&1
[ -s build/app.apk ] || grep -iE 'error|fehler' /tmp/build.log
```

(`fehler` is the German word for "error"; the pattern catches both languages.)

**`d8` has the occasional hiccup.**
An `EOFException` on a valid JAR is usually just that — a hiccup. Simply build
again before you blame the library.

## Choose the target SDK deliberately

- **Herold/Markt:** `--target-sdk-version 33`
- **Zifferblatt:** `--target-sdk-version 29` — classic watch faces
  (`CanvasWatchFaceService`) are only treated as "legacy" below a certain
  target SDK, and otherwise do not show up in the picker.
