# Galaxy Watch am iPhone — ohne Google, ohne Samsung-Handy

Eine Galaxy Watch 6 Classic an einem **iPhone** betreiben, **ohne Google-Konto**,
**ohne Samsung-Telefon** und ohne Play Store — mit selbst gebauten Wear-OS-Apps.

Samsung sieht diese Kombination nicht vor: Eine Galaxy Watch ab Modell 4 lässt
sich offiziell nur mit einem Android-Telefon einrichten und verwalten. Dieses
Projekt macht sie trotzdem nutzbar — Benachrichtigungen vom iPhone,
Gesundheitsmessungen, ein eigener App-Laden und eigene Ziffernblätter.

Alles ist **ohne Gradle** gebaut: `aapt2 → javac → d8 → apksigner`, aufgerufen
von einem einzigen `build.sh` je Projekt. Kein Android Studio nötig.

> **Sprache:** Code, Kommentare und Doku sind auf Deutsch. Das ist Absicht —
> das Projekt ist für den Eigenbedarf entstanden und dort ist Deutsch die
> Arbeitssprache.

---

## Was drin ist

| Ordner | Was es macht |
|---|---|
| **`herold/`** | Die Brücke zum iPhone. Holt Benachrichtigungen per **ANCS** direkt vom iPhone auf die Uhr und bietet Gesundheitsmessungen: EKG, Puls & Rhythmus, Sauerstoff, Hauttemperatur, Körperanalyse, Atemfrequenz — mit Kacheln, Verlauf und Hintergrundmessungen. |
| **`markt/`** | Ein **App-Laden ohne Google-Konto**. Meldet sich anonym bei Google Play an und installiert Apps direkt auf der Uhr. Zeigt gezielt nur Wear-OS-Apps und Ziffernblätter. |
| **`zifferblatt/`** | Ein vollständiges **Beispiel-Ziffernblatt** (eigene Zeichnung), das zeigt, wie man eine Watch Face ohne Gradle baut und per Kabel aufspielt. |
| **`werkzeuge/`** | Hilfsskripte: adb-Verbindung halten, Bloatware **umkehrbar** entfernen, Apps installieren und prüfen. |
| **`docs/`** | Ausführliche Anleitungen — und vor allem die **Erkenntnisse**, die viel Zeit gekostet haben. |

### So sieht es aus

| Laden: Start | Laden: Suche | Laden: App-Seite | Beispiel-Ziffernblatt |
|---|---|---|---|
| ![Start](bilder/markt-start.png) | ![Suche](bilder/markt-suche.png) | ![App](bilder/markt-app.png) | ![Ziffernblatt](bilder/zifferblatt.png) |

---

## Voraussetzungen

**Hardware**
- Samsung Galaxy Watch 4 oder neuer (entwickelt und getestet auf **Galaxy Watch 6 Classic, SM-R955F**)
- Ein iPhone (für die Benachrichtigungsbrücke) — oder gar kein Telefon
- Ein Mac oder Linux-Rechner zum Bauen

**Software**
- Android SDK **Command Line Tools** (`aapt2`, `d8`, `zipalign`, `apksigner`) und `platforms/android-33`
- **JDK 21**
- `adb` mit WLAN-Debugging zur Uhr
- Für die Grafiken im Zifferblatt: `rsvg-convert` (optional)

**Bibliotheken sind bewusst NICHT im Repo.** Sie gehören anderen und werden
nicht mitverteilt. Wie du sie bekommst, steht in
[`docs/02-bibliotheken.md`](docs/02-bibliotheken.md).

---

## Schnellstart

```bash
git clone https://github.com/<dein-konto>/wearos-ohne-google.git
cd wearos-ohne-google
```

1. **Bibliotheken holen** — siehe [`docs/02-bibliotheken.md`](docs/02-bibliotheken.md).
   Sie kommen in den jeweiligen `libs/`-Ordner (`herold/libs/`, `markt/libs/`, `zifferblatt/libs/`).
2. **Pfade prüfen:** In jedem `build.sh` stehen oben `SDK=` und `JAVA_HOME`.
   Passe sie an dein System an.
3. **Uhr verbinden:** WLAN-Debugging auf der Uhr einschalten, dann

   ```bash
   ./werkzeuge/uhr.sh --id     # findet die Uhr und gibt die Transport-ID aus
   ```
4. **Bauen und aufspielen:**

   ```bash
   cd herold && ./build.sh --install
   ```

Beim ersten Bauen wird automatisch ein Signaturschlüssel erzeugt. Er bleibt
lokal und ist **nicht** im Repo — sonst könnte jeder Updates für deine App
signieren.

---

## Wichtige Hinweise

**Gesundheitsmessungen sind kein Medizinprodukt.**
Herold liest die Sensoren der Uhr aus und rechnet Werte aus. Es stellt **keine
Diagnose**, nennt **keine Krankheitsnamen** und gibt **keine Entwarnung**. Die
Rhythmusanalyse sagt bewusst nur, ob der Herzschlag gleichmäßig war — nie, ob
etwas „in Ordnung" ist. Bei Beschwerden zum Arzt, nicht zur Uhr.

**Das Entrümpeln ist umkehrbar.**
`werkzeuge/entruempeln.sh` entfernt Apps nur für den Nutzer (`pm uninstall --user 0`).
Nichts wird von der Systempartition gelöscht, jede App lässt sich zurückholen.
Trotzdem: vorher `--sichern`, dann `--probe`, erst dann `--weg`.

**Ein Systemupdate kann Eingriffe zurücksetzen.**
Nach einem Wear-OS-Update kommt entfernte Bloatware oft zurück, und
Einstellungen wie der Werksschutz-Modus springen zurück. Einfach erneut ausführen.

**Rechtliches.**
Der Code steht unter MIT. Er nutzt aber fremde Dienste und Bibliotheken
(Google Play, Samsung Health Sensor SDK, gplayapi). Prüfe selbst, ob deine
Nutzung deren Bedingungen entspricht. Ziffernblätter mit geschützten Figuren
gehören ihren Rechteinhabern — hier ist keine dabei.

---

## Doku

- [01 — Bauen ohne Gradle](docs/01-bauen-ohne-gradle.md)
- [02 — Bibliotheken beschaffen](docs/02-bibliotheken.md)
- [03 — Herold: Brücke zum iPhone + Gesundheit](docs/03-herold.md)
- [04 — Markt: App-Laden ohne Google-Konto](docs/04-markt.md)
- [05 — Zifferblatt selbst bauen](docs/05-zifferblatt.md)
- [06 — Werkzeuge](docs/06-werkzeuge.md)
- [07 — Erkenntnisse (die teuer erkauften)](docs/07-erkenntnisse.md)

---

## Lizenz

MIT — siehe [LICENSE](LICENSE).
