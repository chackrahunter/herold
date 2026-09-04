# 04 — Markt: App-Laden ohne Google-Konto

Ohne Google-Konto gibt es auf der Uhr keinen Play Store — und damit keine Apps.
Markt schließt diese Lücke: Er meldet sich **anonym** bei Google Play an und
installiert Apps direkt auf der Uhr.

## Wie die anonyme Anmeldung funktioniert

Markt nutzt **gplayapi**, dieselbe Bibliothek wie Aurora Store:

1. Markt sammelt die echten Eigenschaften der Uhr (33 Pflichtfelder: Modell,
   Bildschirm, ABIs, Systemmerkmale …) — siehe `Geraet.java`.
2. Diese gehen an Auroras **Token-Dienst** (`https://auroraoss.com/api/auth`),
   der ein anonymes Play-Konto zurückgibt.
3. `AuthHelper.build(...)` macht daraus eine vollständige Anmeldung
   (Geräte-Check-in, Konfiguration hochladen).
4. Die Anmeldung wird lokal gespeichert, damit nicht bei jedem Start ein neues
   Gerät bei Google registriert wird.

Weil die echten Eigenschaften der Uhr hochgeladen werden, liefert Play von
selbst die **Uhr-Varianten** der Apps aus (z. B. den Samsung Browser für Wear OS).

## Nur Wear-OS-Apps zeigen — gemessen, nicht geraten

Die naheliegenden Wege funktionieren **nicht**:

- Die **native Play-Suche** liefert unter einem Uhr-Profil **null Treffer**.
- `restriction`, `compatibility` und selbst ein erfolgreicher Auslieferungstest
  unterscheiden **nicht** zwischen Handy- und Uhr-App (Play liefert auch
  WhatsApp an die Uhr aus).

Was zuverlässig funktioniert, ist **Googles eigene Wear-Kategorie**:

- `/store/apps/category/ANDROID_WEAR` — Apps für die Uhr
- `/store/apps/category/WATCH_FACE` — Ziffernblätter

Markt blättert diese Kategorien über die Web-Helfer tief durch und baut daraus
einen **Katalog von ~1000 Uhr-Apps**, der auf der Uhr zwischengespeichert wird
(täglich frisch). Die Browse-Listen speisen sich daraus — dort landet garantiert
keine Handy-App.

Die **Suche** geht zusätzlich über die Play-Web-Suche, damit sich auch Apps
finden lassen, die nicht in den Kategorien stehen.

## Installieren

Über eine `PackageInstaller`-Sitzung (`Installer.java`, `Ladedienst.java`):
herunterladen → Sitzung öffnen → APK hineinschreiben → bestätigen.

**Einmal pro App bestätigt der Nutzer auf der Uhr** („Möchtest du diese App
installieren?"). Das schreibt Android jedem Laden außer dem Play Store vor.

> **Wichtig:** Ohne den Werksschutz-Modus abzuschalten schlägt **jede**
> Installation fehl. Siehe [07-erkenntnisse.md](07-erkenntnisse.md).

## Updates

„Meine Apps" fragt alle selbst installierten Apps in **einem** Aufruf
(`bulkDetails`) bei Play ab, vergleicht die Versionen und bietet einzelne
Updates oder „Alle aktualisieren" an.

## Bestimmte Version installieren

`market://details?id=<paket>&vc=<versionCode>` installiert gezielt eine ältere
Version — nützlich, wenn die neueste ein zu neues Wear OS verlangt.

## Grenzen

- **Bezahl-Apps** gehen nicht: Ohne Konto lässt sich nichts kaufen.
- Manche Apps liefert Play an ein kontenloses Gerät **gar nicht** aus
  (`AppNotSupported`). Markt zeigt dann „Für diese Uhr nicht verfügbar".
- Apps, die **Google-Push (FCM)** brauchen, funktionieren nur eingeschränkt:
  Ohne Google-Geräteregistrierung kommen keine Push-Nachrichten an.
