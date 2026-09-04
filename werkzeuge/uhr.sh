#!/bin/bash
# uhr.sh - haelt die adb-Verbindung zur Galaxy Watch offen.
#
#   ./uhr.sh --daemon        Waechter starten (verbindet endlos neu, schreibt PID-Datei)
#   ./uhr.sh --daemon-stop   Waechter beenden
#
# Achtung: ein vergessener Waechter aus einer alten Sitzung ringt mit jedem
# weiteren adb-Client um die Verbindung - die Uhr meldet dann alle paar
# Sekunden "adb ueber WLAN verbunden" und Verbindungen brechen sofort ab.
#   ./uhr.sh --id            Transport-ID der Uhr ausgeben
#   ./uhr.sh shell <cmd>     Befehl auf der Uhr ausfuehren
#   ./uhr.sh --shot <datei>  Screenshot holen
#   ./uhr.sh --log           Herold-Meldungen zeigen
export PATH=/opt/homebrew/bin:$PATH
MODELL=SM_R955F
LOG="$(dirname "$0")/uhr-waechter.log"

verbinde() {
  # Schon da?
  local tid
  tid=$(adb devices -l 2>/dev/null | grep "$MODELL" | tail -1 | grep -o 'transport_id:[0-9]*' | cut -d: -f2)
  if [ -n "$tid" ] && adb -t "$tid" shell true >/dev/null 2>&1; then echo "$tid"; return 0; fi

  # Tote Eintraege wegraeumen, dann per mDNS neu verbinden.
  # Achtung: Dienstnamen koennen Leerzeichen enthalten ("... (2)") - deshalb
  # NICHT ueber den Namen gehen, sondern ueber die letzte Spalte (IP:Port),
  # und nur Ports nehmen, die auch wirklich offen sind.
  adb disconnect >/dev/null 2>&1
  local ziel
  while read -r ziel; do
    [ -z "$ziel" ] && continue
    local host=${ziel%%:*} port=${ziel##*:}
    if nc -z -G1 -w1 "$host" "$port" 2>/dev/null; then
      adb connect "$ziel" >/dev/null 2>&1
    fi
  done < <(adb mdns services 2>/dev/null | grep "_adb-tls-connect" | awk '{print $NF}' | sort -u)

  tid=$(adb devices -l 2>/dev/null | grep "$MODELL" | tail -1 | grep -o 'transport_id:[0-9]*' | cut -d: -f2)
  if [ -n "$tid" ] && adb -t "$tid" shell true >/dev/null 2>&1; then
    # Bei jeder frischen Verbindung die Schlaf-Sperren neu setzen
    adb -t "$tid" shell svc power stayon true >/dev/null 2>&1
    adb -t "$tid" shell settings put global stay_on_while_plugged_in 7 >/dev/null 2>&1
    echo "$tid"; return 0
  fi
  return 1
}

if [ "${1:-}" != "--daemon" ] && [ "${1:-}" != "--daemon-stop" ] \
   && pgrep -f "uhr.sh --daemon" >/dev/null 2>&1; then
  echo "HINWEIS: ein uhr.sh --daemon laeuft (PID $(pgrep -f 'uhr.sh --daemon' | head -1)) - er stoert andere Verbindungen. Beenden mit: ./uhr.sh --daemon-stop" >&2
fi

case "$1" in
  --daemon-stop)
    if [ -f /tmp/herold_uhr_daemon.pid ] && kill "$(cat /tmp/herold_uhr_daemon.pid)" 2>/dev/null; then
      echo "Waechter beendet"; rm -f /tmp/herold_uhr_daemon.pid
    else
      pkill -f "uhr.sh --daemon" && echo "Waechter beendet (ohne PID-Datei)" || echo "kein Waechter aktiv"
    fi
    exit 0 ;;
  --daemon)
    echo $$ > /tmp/herold_uhr_daemon.pid
    trap 'rm -f /tmp/herold_uhr_daemon.pid' EXIT
    echo "$(date '+%F %T')  Waechter gestartet" >> "$LOG"
    weg=0
    while true; do
      if tid=$(verbinde); then
        if [ "$weg" = 1 ]; then
          echo "$(date '+%F %T')  wieder verbunden (transport $tid)" >> "$LOG"; weg=0
        fi
      else
        if [ "$weg" = 0 ]; then
          echo "$(date '+%F %T')  Uhr weg - versuche weiter" >> "$LOG"; weg=1
        fi
      fi
      sleep 5
    done
    ;;
  --id)    verbinde ;;
  --shot)  tid=$(verbinde) && adb -t "$tid" exec-out screencap -p > "$2" ;;
  --log)   tid=$(verbinde) && adb -t "$tid" logcat -d -t 400 2>/dev/null \
             | tr -d '\r' | grep -E "Herold *:" | grep -v WNoti | tail -20 | sed 's/.*Herold *: */  /' ;;
  *)       tid=$(verbinde) && exec adb -t "$tid" "$@" ;;
esac
