#!/bin/bash
# Ruft jeden Bildschirm der App auf der Uhr auf und prueft, ob er sich ohne
# Absturz aufbaut. Legt von jedem ein Bild ab.
#
#   ./test/oberflaeche.sh [zielverzeichnis]
#
# Der Test misst nicht - er oeffnet nur die Anzeigen. Messungen kosten Strom
# und brauchen einen Traeger; die werden einzeln geprueft.

set -u
UHR="${HEROLD_UHR:-}"   # leer lassen: Adresse per ../werkzeuge/uhr.sh --id ermitteln
PAKET=de.doncalvin.herold
ZIEL="${1:-$(cd "$(dirname "$0")/.." && pwd)/doku/bilder}"
mkdir -p "$ZIEL"

adb -s "$UHR" get-state >/dev/null 2>&1 || { echo "Uhr nicht erreichbar ($UHR)"; exit 1; }

AKKU=$(adb -s "$UHR" shell dumpsys battery 2>/dev/null | awk '/level/{print $2}')
echo "Uhr erreichbar, Akku ${AKKU}%"
[ "${AKKU:-100}" -lt 10 ] && echo "  Achtung: wenig Akku"

# Name -> Activity, optional mit Zusatzangaben
SCHIRME=(
  "start|.HomeActivity|"
  "verlauf|.VerlaufActivity|"
  "koerperdaten|.ProfilActivity|"
  "iphone|.MainActivity|"
  "ergebnis|.ErgebnisActivity|--ez selbsttest true"
)

fehler=0
adb -s "$UHR" shell input keyevent KEYCODE_WAKEUP >/dev/null 2>&1

for eintrag in "${SCHIRME[@]}"; do
  name="${eintrag%%|*}"
  rest="${eintrag#*|}"
  activity="${rest%%|*}"
  zusatz="${rest#*|}"

  adb -s "$UHR" shell am force-stop "$PAKET" >/dev/null 2>&1
  adb -s "$UHR" logcat -c >/dev/null 2>&1
  # shellcheck disable=SC2086
  adb -s "$UHR" shell am start -n "$PAKET/$activity" $zusatz >/dev/null 2>&1
  sleep 3

  absturz=$(adb -s "$UHR" logcat -d 2>/dev/null \
            | grep -E "FATAL EXCEPTION|AndroidRuntime.*$PAKET" | head -3)
  oben=$(adb -s "$UHR" shell dumpsys activity activities 2>/dev/null \
         | grep -oE "$PAKET/[A-Za-z.]*Activity" | head -1)

  adb -s "$UHR" exec-out screencap -p > "$ZIEL/$name.png" 2>/dev/null
  groesse=$(wc -c < "$ZIEL/$name.png" 2>/dev/null | tr -d ' ')

  if [ -n "$absturz" ]; then
    echo "  FEHLER  $name — Absturz"
    echo "$absturz" | sed 's/^/          /'
    fehler=$((fehler+1))
  elif [ -z "$oben" ]; then
    echo "  FEHLER  $name — Bildschirm kam nicht nach vorn"
    fehler=$((fehler+1))
  elif [ "${groesse:-0}" -lt 5000 ]; then
    echo "  FEHLER  $name — Bild leer (${groesse} B)"
    fehler=$((fehler+1))
  else
    echo "  ok      $name  (${groesse} B)"
  fi
done

adb -s "$UHR" shell am force-stop "$PAKET" >/dev/null 2>&1
echo
if [ "$fehler" -eq 0 ]; then
  echo "Alle ${#SCHIRME[@]} Bildschirme bauen sich auf. Bilder in $ZIEL"
else
  echo "$fehler von ${#SCHIRME[@]} Bildschirmen fehlerhaft. Bilder in $ZIEL"
fi
exit "$fehler"
