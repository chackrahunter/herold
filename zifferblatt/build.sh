#!/bin/bash
# build.sh - Hund-Ziffernblatt ohne Gradle: aapt2 -> javac -> d8 -> apksigner.
set -e
cd "$(dirname "$0")"
export PATH=/opt/homebrew/bin:$PATH
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
SDK=/opt/homebrew/share/android-commandlinetools
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
AJAR="$SDK/platforms/android-33/android.jar"
[ -x "$BT/aapt2" ] || { echo "FEHLER: build-tools fehlen"; exit 1; }

rm -rf build && mkdir -p build/gen build/classes
echo "[1/6] Ressourcen"
"$BT/aapt2" compile --dir res -o build/res.zip
echo "[2/6] Linken"
"$BT/aapt2" link -o build/base.apk -I "$AJAR" --manifest AndroidManifest.xml \
  --java build/gen --min-sdk-version 26 --target-sdk-version 29 build/res.zip
echo "[3/6] Java"
find src build/gen -name '*.java' > build/sources.txt
LIBS=(); for j in libs/*.jar; do [ -f "$j" ] && LIBS+=("$j"); done
CP="$AJAR"; for j in "${LIBS[@]}"; do CP="$CP:$j"; done
"$JAVA_HOME/bin/javac" -source 17 -target 17 -nowarn -classpath "$CP" -d build/classes @build/sources.txt
echo "[4/6] Dex"
"$JAVA_HOME/bin/jar" cf build/classes.jar -C build/classes .
"$BT/d8" --min-api 26 --lib "$AJAR" --output build/ build/classes.jar "${LIBS[@]}"
echo "[5/6] Packen"
cp build/base.apk build/hund-unsigned.apk
(cd build && zip -q hund-unsigned.apk classes.dex)
"$BT/zipalign" -f 4 build/hund-unsigned.apk build/hund-aligned.apk
echo "[6/6] Signieren"
KS=hund.keystore
if [ ! -f "$KS" ]; then
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KS" -storepass hundface -keypass hundface \
    -alias hund -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Hund" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:hundface --key-pass pass:hundface \
  --out build/hund.apk build/hund-aligned.apk
echo "FERTIG: build/hund.apk ($(du -h build/hund.apk|cut -f1))"
