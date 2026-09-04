# 06 — Werkzeuge

Drei kleine Skripte, die den Alltag mit einer Uhr ohne Telefon erträglich machen.

---

## `uhr.sh` — Verbindung zur Uhr

Der adb-Port der Uhr **wechselt bei jedem Neustart**. Rate ihn nicht, lass ihn
suchen.

```bash
./uhr.sh --id             # Transport-ID ausgeben (verbindet bei Bedarf)
./uhr.sh shell <befehl>   # Befehl auf der Uhr ausführen
./uhr.sh --shot bild.png  # Screenshot holen
./uhr.sh --log            # Meldungen der App zeigen
./uhr.sh --daemon         # Wächter starten (hält die Verbindung)
./uhr.sh --daemon-stop    # Wächter beenden
```

Warum eine **Transport-ID** statt des Gerätenamens? Die mDNS-Namen der Uhr
enthalten Punkte und Leerzeichen und brechen `adb -s`.

> **Warnung aus Erfahrung:** Lass nie einen vergessenen `--daemon` aus einer
> alten Sitzung laufen. Er verbindet endlos neu, ringt mit jedem anderen
> adb-Zugriff und die Uhr meldet im Sekundentakt „adb über WLAN verbunden".
> Bei komischem Verhalten **zuerst** `pgrep -fl uhr.sh`.

---

## `entruempeln.sh` — Bloatware umkehrbar entfernen

Entfernt Google- und Samsung-Apps, die ohne Google-Konto und ohne
Samsung-Telefon nutzlos sind. **Nichts wird gelöscht** — die Apps bleiben auf
der Systempartition und lassen sich zurückholen.

```bash
./entruempeln.sh --sichern          # Paketliste vorher sichern
./entruempeln.sh --probe            # zeigen, was passieren würde
./entruempeln.sh --weg              # ausführen (protokolliert alles)
./entruempeln.sh --zurueck <paket>  # eine App zurückholen
./entruempeln.sh --zurueck alle     # alles zurückholen
```

Entfernt u. a. Play Store, Maps, Google Messages, Assistant, Wallet, Samsung
Wallet, SmartThings, Find My Phone/Watch, Smart Switch, Samsung Cloud, Outlook.
**Bixby wird nur abgeschaltet**, nicht entfernt — nach Erfahrungsberichten
stört die Deinstallation das GPS.

Was bleibt: Wecker, Timer, Stoppuhr, Weltuhr, Kalender, Kontakte, Telefon,
Musik, Galerie, Samsung Health, Tastatur.

> Nach einem **Systemupdate** kommt vieles zurück. Einfach erneut ausführen.

---

## `apps.sh` — Apps aufspielen und prüfen

```bash
./apps.sh datei.apk            # installieren
./apps.sh --start <paket>      # starten, warten, Screenshot ziehen
./apps.sh --installer <paket>  # der App erlauben, selbst zu installieren
./apps.sh --weg <paket>        # entfernen
```

`--start` ist praktisch zum Prüfen: Es weckt den Bildschirm, startet die App,
macht einen Screenshot und zählt Abstürze — damit siehst du ohne Handgriff, ob
etwas läuft.

---

## Kleine Griffe, die oft gebraucht werden

```bash
# Bildschirm wecken (sonst ist der Screenshot schwarz)
adb -t <id> shell input keyevent KEYCODE_WAKEUP

# Benachrichtigungen: von links wischen. Von unten ist die App-Liste.
adb -t <id> shell input swipe 8 216 400 216

# Was läuft gerade im Vordergrund?
adb -t <id> shell dumpsys window | grep mCurrentFocus

# Abstürze zählen
adb -t <id> shell logcat -d -b crash | grep -c FATAL
```

`svc power stayon` wirkt **nur am Ladekabel**.
