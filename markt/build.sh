#!/bin/bash
# Markt - baut die APK ohne Gradle, direkt mit aapt2/d8/apksigner.
#   ./build.sh            nur bauen
#   ./build.sh --install  bauen + auf die Uhr schieben
#   ./build.sh --selftest bauen + installieren + starten + Screenshot pruefen
set -e
cd "$(dirname "$0")"

export PATH=/opt/homebrew/bin:$PATH
export JAVA_HOME=$(/usr/libexec/java_home -v 21)
SDK=/opt/homebrew/share/android-commandlinetools
BT=$(ls -d "$SDK"/build-tools/* 2>/dev/null | sort -V | tail -1)
AJAR="$SDK/platforms/android-33/android.jar"

[ -x "$BT/aapt2" ] || { echo "FEHLER: build-tools fehlen ($BT)"; exit 1; }
[ -f "$AJAR" ]     || { echo "FEHLER: android.jar fehlt ($AJAR)"; exit 1; }

rm -rf build && mkdir -p build/res build/gen build/classes

echo "[1/6] Ressourcen kompilieren"
"$BT/aapt2" compile --dir res -o build/res.zip

echo "[2/6] Ressourcen linken"
"$BT/aapt2" link -o build/base.apk \
  -I "$AJAR" \
  --manifest AndroidManifest.xml \
  --java build/gen \
  --min-sdk-version 30 --target-sdk-version 33 \
  build/res.zip

echo "[3/6] Java kompilieren"
find src build/gen -name '*.java' > build/sources.txt
# Alle Bibliotheken aus libs/ - das Samsung-SDK und die Kachel-Bibliotheken.
# Ohne Gradle gibt es keinen Abhaengigkeitsaufloeser; die Liste ist die Wahrheit.
LIBS=()
for j in libs/*.jar; do [ -f "$j" ] && LIBS+=("$j"); done
CP="$AJAR"
for j in "${LIBS[@]}"; do CP="$CP:$j"; done
"$JAVA_HOME/bin/javac" -source 17 -target 17 -nowarn \
  -classpath "$CP" -d build/classes @build/sources.txt

echo "[4/6] Dex erzeugen"
"$JAVA_HOME/bin/jar" cf build/classes.jar -C build/classes .
# Die Bibliotheken muessen mit in die APK, sonst fehlen die Klassen zur Laufzeit
"$BT/d8" --min-api 30 --lib "$AJAR" --output build/ build/classes.jar "${LIBS[@]}"

echo "[5/6] APK packen"
cp build/base.apk build/markt-unsigned.apk
(cd build && zip -q markt-unsigned.apk classes*.dex)
"$BT/zipalign" -f 4 build/markt-unsigned.apk build/markt-aligned.apk

echo "[6/6] Signieren"
KS=markt.keystore   # ausserhalb von build/, damit die Signatur stabil bleibt
if [ ! -f "$KS" ]; then          # nur einmal erzeugen - sonst aendert sich die Signatur
  "$JAVA_HOME/bin/keytool" -genkeypair -keystore "$KS" -storepass marktladen -keypass marktladen \
    -alias markt -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Markt" >/dev/null 2>&1
fi
"$BT/apksigner" sign --ks "$KS" --ks-pass pass:marktladen --key-pass pass:marktladen \
  --out build/markt.apk build/markt-aligned.apk

echo "FERTIG: $(pwd)/build/markt.apk ($(du -h build/markt.apk | cut -f1))"

# Transport-ID statt Geraetename: mDNS-Namen enthalten Punkte und brechen "adb -s".
watch_id() { "$(dirname "$0")/uhr.sh" --id; }

if [ "$1" = "--install" ] || [ "$1" = "--selftest" ]; then
  W=$(watch_id); [ -z "$W" ] && { echo "Keine Uhr verbunden"; exit 1; }
  echo "Installiere auf $W"
  adb -t "$W" install -r -g build/markt.apk
fi

if [ "$1" = "--selftest" ]; then
  W=$(watch_id)
  echo "--- Selbsttest ---"
  adb -t "$W" shell am start -n de.doncalvin.markt/.StartActivity >/dev/null
  sleep 3
  FG=$(adb -t "$W" shell dumpsys activity activities | tr -d '\r' | grep -o 'de.doncalvin.markt/[^ }]*' | head -1)
  echo "Vordergrund: ${FG:-NICHTS}"
  adb -t "$W" exec-out screencap -p > build/selftest.png
  echo "Screenshot:  build/selftest.png ($(du -h build/selftest.png | cut -f1))"
  echo "--- Log ---"
  adb -t "$W" logcat -d -s Markt:I | tail -15
  case "$FG" in
    de.doncalvin.markt*) echo "SELBSTTEST OK" ;;
    *) echo "SELBSTTEST FEHLGESCHLAGEN"; exit 1 ;;
  esac
fi
