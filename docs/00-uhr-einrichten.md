# 00 — Die Uhr ohne Samsung-Handy einrichten

**Das ist der allererste Schritt.** Alles andere in diesem Repo setzt eine
eingerichtete Uhr voraus.

Samsung schreibt für die Galaxy Watch 4 und neuer offiziell vor: Einrichtung nur
über die **Galaxy-Wearable-App auf einem Android-Telefon**. Mit einem iPhone —
oder ganz ohne Telefon — kommt man am Willkommensbildschirm scheinbar nicht
vorbei.

Es gibt aber einen von Samsung selbst dokumentierten Weg vorbei an dieser Hürde.

---

## Der Weg (Galaxy Watch 4 und neuer, Wear OS)

1. Die Uhr mit der **Ein-/Aus-Taste** einschalten.
2. Auf dem **Willkommensbildschirm nach oben wischen**.
3. **Mehrfach oben auf das „Wear"-Symbol tippen** — so lange, bis die Meldung
   erscheint:

   > *„Please long-tap watch icon for more than 3 seconds"*

   Die Meldung verschwindet nach ein paar Sekunden wieder. Wenn nichts kommt:
   weiter tippen, es können gut zwei Dutzend Tipper sein.
4. Jetzt dasselbe Symbol **drücken und halten**, länger als **3 Sekunden**, bis
   **„Wird gestartet"** erscheint.
5. Die Uhr fährt die restliche Einrichtung allein durch (Sprache, Zeitzone,
   WLAN). Fertig — die Uhr läuft eigenständig.

Quelle: [Samsung Deutschland — Galaxy Watch ohne Smartphone einrichten](https://www.samsung.com/de/support/mobile-devices/galaxy-watch-ohne-smartphone-einrichten/)

---

## Was danach geht — und was nicht

**Von Haus aus** bleibt eine so eingerichtete Uhr karg: Uhrzeit, Fitness-
Aufzeichnung, Musiksteuerung. Keine Benachrichtigungen, kein Play Store, keine
Apps.

**Genau da setzt dieses Repo an:**

| Lücke | Lösung hier |
|---|---|
| Keine Benachrichtigungen vom iPhone | [`herold/`](../herold) — holt sie per ANCS direkt vom iPhone |
| Kein App-Laden ohne Google-Konto | [`markt/`](../markt) — anonyme Play-Anmeldung |
| Keine eigenen Ziffernblätter | [`zifferblatt/`](../zifferblatt) — Beispiel zum Selbstbauen |
| Voll mit unnützer Bloatware | [`werkzeuge/entruempeln.sh`](../werkzeuge) |

---

## Danach direkt erledigen

**1. Entwickleroptionen freischalten** (für alles Weitere nötig):
Einstellungen → *Info zur Uhr* → *Softwareinformationen* → mehrfach auf die
**Softwareversion** tippen, bis „Entwicklermodus aktiviert" erscheint.

**2. Debugging über WLAN einschalten:**
Einstellungen → *Entwickleroptionen* → *ADB-Debugging* und *Debugging über WLAN*.
Dort steht auch die Adresse (`IP:Port`), die du zum Verbinden brauchst.

> Der **Port wechselt bei jedem Neustart** der Uhr. Nicht raten —
> `werkzeuge/uhr.sh --id` sucht ihn per mDNS.

**3. Werksschutz-Modus abschalten** — sonst schlägt **jede** App-Installation
fehl (siehe [07-erkenntnisse.md](07-erkenntnisse.md)):

```bash
adb shell settings put global secure_frp_mode 0
adb shell settings put secure secure_frp_mode 0
```

---

## Stolpersteine

- **Ein Werksreset setzt alles zurück.** Danach beginnt die Prozedur oben von
  vorn — inklusive Entwickleroptionen und Werksschutz-Modus.
- **Ein Systemupdate** (z. B. auf ein neueres Wear OS) schaltet Debugging über
  WLAN ab, holt entfernte Bloatware zurück und kann den Werksschutz-Modus
  zurücksetzen. Einfach die drei Punkte oben erneut durchgehen.
- **Bluetooth zum iPhone** ist etwas anderes als die Samsung-Einrichtung: Die
  Uhr koppelt sich später ganz normal über die Bluetooth-Einstellungen des
  iPhones — dafür sorgt Herold. Details in [03-herold.md](03-herold.md).
