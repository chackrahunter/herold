#!/bin/bash
# entruempeln.sh - Google- und Samsung-Ballast auf der Galaxy Watch reversibel entfernen.
#
#   ./entruempeln.sh --liste            alle Pakete + Nutzung + Startbarkeit anzeigen
#   ./entruempeln.sh --sichern          Paketliste vorher sichern (doku/pakete_vorher.txt)
#   ./entruempeln.sh --probe            zeigen, was --weg entfernen wuerde (nichts aendern)
#   ./entruempeln.sh --weg              entfernen (pm uninstall -k --user 0), Protokoll doku/entruempelt.txt
#   ./entruempeln.sh --zurueck <paket>  ein Paket wiederherstellen (cmd package install-existing)
#   ./entruempeln.sh --zurueck alle     alles aus dem Protokoll wiederherstellen
#
# Alles bleibt auf der System-Partition; nichts wird geloescht. Rueckweg immer moeglich.
export PATH=/opt/homebrew/bin:$PATH
cd "$(dirname "$0")"
TID=$(./uhr.sh --id 2>/dev/null | tail -1)
[ -z "$TID" ] && { echo "Uhr nicht erreichbar (laedt sie?)" >&2; exit 1; }
sh() { adb -t "$TID" shell "$@"; }
PROTOKOLL=doku/entruempelt.txt
mkdir -p doku

# Kandidaten: nur entfernt, wenn vorhanden. Kommentar = Grund.
WEG=(
  # --- Google ---
  com.android.vending                               # Play Store (ohne Google-Konto nutzlos)
  com.google.android.apps.maps                      # Google Maps
  com.google.android.apps.messaging                 # Google Messages (braucht Android-Handy)
  com.google.android.apps.walletnfcrel              # Google Wallet
  com.google.android.apps.youtube.music             # YouTube Music
  com.google.android.keep                           # Google Keep
  com.google.android.calendar                       # Google Kalender
  com.google.android.apps.fitness                   # Google Fit
  com.google.android.googlequicksearchbox           # Google App / Assistant
  com.google.android.apps.wearable.assistant        # Google Assistant (Wear)
  com.google.android.apps.wearable.retailattractloop # Laden-Demo
  com.google.android.marvin.talkback                # TalkBack (Screenreader)
  com.google.android.apps.wear.companion            # Wear-Begleiter (nur Android-Handy)
  # --- Samsung, ohne Samsung-Handy/-Konto nutzlos ---
  com.samsung.android.samsungpay.gear               # Samsung Wallet
  com.samsung.android.bixby.wakeup                  # Bixby Wake-up
  com.samsung.android.oneconnect                    # SmartThings
  com.samsung.android.watch.cameracontroller        # Kamera-Fernbedienung
  com.samsung.android.watch.findmyphone             # Find My Phone
  samsung.android.watch.findmywatch                 # Find My Watch (Samsung-Konto)
  com.samsung.android.shealthmonitor                # Health Monitor (Blutdruck/EKG nur mit Samsung-Handy)
  com.microsoft.office.outlook                      # Outlook
  com.sec.android.easyMover                         # Smart Switch
  com.samsung.android.wear.smartswitchassistant     # Smart Switch Helfer
  com.samsung.android.scloud                        # Samsung Cloud (Samsung-Konto)
  com.samsung.android.video.wearable                # Samsung Video
  com.samsung.sree                                  # Global-Goals-Ziffernblaetter
  com.samsung.sree.classic
  com.samsung.sree.countdown
  com.samsung.sree.spin
  com.samsung.sree.digital
  com.samsung.android.wear.tips                     # Tipps
  com.samsung.android.tips
  com.samsung.android.watch.samsungfree
  com.google.android.wearable.assistant             # Google Assistant (Wear)
  com.samsung.android.messaging                     # Samsung Messages (braucht Samsung-Handy)
  com.samsung.android.watch.findmywatch             # Find My Watch (Samsung-Konto)
  com.sds.emm.cloud.knox.samsung                    # Knox-Manage-Firmenclient
)
# Nur abschalten, nicht deinstallieren (XDA: Deinstallation stoert GPS)
ABSCHALTEN=( com.samsung.android.bixby.agent )

vorhanden() { sh "pm list packages --user 0 $1" | grep -qx "package:$1"; }

case "${1:-}" in
  --liste)
    echo "# Nutzerpakete (-3):"; sh "pm list packages -3" | sort
    echo; echo "# Startbare Apps (Launcher):"
    sh "cmd package query-activities --brief -a android.intent.action.MAIN -c android.intent.category.LAUNCHER" | grep -oE '[a-zA-Z0-9_.]+/[a-zA-Z0-9_.$]+' | sort -u
    echo; echo "# Zuletzt benutzt (usagestats):"
    sh "dumpsys usagestats" | grep -E 'package=|lastTimeUsed|totalTimeUsed' | head -0
    ;;
  --sichern)
    sh "pm list packages -u -f --user 0" | sort > doku/pakete_vorher.txt
    sh "pm list packages -d --user 0" | sort > doku/pakete_abgeschaltet_vorher.txt
    echo "gesichert: $(wc -l < doku/pakete_vorher.txt) Pakete -> doku/pakete_vorher.txt"
    ;;
  --probe|--weg)
    tun=$([ "$1" = "--weg" ] && echo ja || echo nein)
    for p in "${WEG[@]}"; do
      if vorhanden "$p"; then
        if [ $tun = ja ]; then
          erg=$(sh "pm uninstall -k --user 0 $p" | tr -d '\r')
          echo "$p  -> $erg"
          [ "$erg" = "Success" ] && echo "$(date +%F) uninstall $p" >> "$PROTOKOLL"
        else echo "WUERDE ENTFERNEN: $p"; fi
      fi
    done
    for p in "${ABSCHALTEN[@]}"; do
      if vorhanden "$p"; then
        if [ $tun = ja ]; then
          erg=$(sh "pm disable-user --user 0 $p" | tr -d '\r'); echo "$p  -> $erg"
          echo "$(date +%F) disable $p" >> "$PROTOKOLL"
        else echo "WUERDE ABSCHALTEN: $p"; fi
      fi
    done
    ;;
  --zurueck)
    if [ "$2" = "alle" ]; then
      while read -r d art p; do
        if [ "$art" = uninstall ]; then sh "cmd package install-existing --user 0 $p"; else sh "pm enable --user 0 $p"; fi
      done < "$PROTOKOLL"
    else
      sh "cmd package install-existing --user 0 $2"; sh "pm enable --user 0 $2" >/dev/null 2>&1
    fi ;;
  *) sed -n 2,12p "$0" ;;
esac
