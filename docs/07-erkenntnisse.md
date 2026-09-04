# 07 — Erkenntnisse

Das hier ist der wertvollste Teil des Repos: Dinge, die nirgends dokumentiert
sind und die jeweils Stunden gekostet haben. Wenn du an einer Galaxy Watch ohne
Google-Konto arbeitest, spart dir diese Seite den größten Teil davon.

---

## 1. Ohne diesen Schalter schlägt **jede** Installation fehl

**Symptom:** Jede App-Installation hängt oder bricht ab — bei Aurora Store
genauso wie bei einem eigenen Laden. Der Bestätigungsdialog erscheint gar nicht.

**Im Log:**
```
SecurityException: Can't install packages while in secure FRP
```

**Ursache:** Die Uhr steckt im **Werksschutz-Modus** (Factory Reset Protection),
obwohl die Einrichtung abgeschlossen ist — vermutlich, weil sie ohne
Google-Konto eingerichtet wurde. In diesem Zustand blockiert der PackageManager
alles.

**Lösung:**
```bash
adb shell settings put global secure_frp_mode 0
adb shell settings put secure secure_frp_mode 0
```

Danach läuft die Installation normal. Der Wert bleibt gesetzt; nach einem
Systemupdate lohnt eine erneute Prüfung. Markt setzt ihn beim Start selbst
zurück (braucht dafür `WRITE_SECURE_SETTINGS`, per `adb pm grant`).

> **Merke:** Bei „Installation hängt" **immer zuerst** `secure_frp_mode` prüfen —
> nicht den eigenen Installer verdächtigen.

---

## 2. Die Dienst-Benachrichtigung verschwinden lassen

Ein Vordergrunddienst muss eine Benachrichtigung zeigen. Auf One UI Watch half
**nichts** davon:

- `IMPORTANCE_MIN`
- `VISIBILITY_SECRET`
- `CATEGORY_SERVICE`
- `FOREGROUND_SERVICE_DEFERRED`

**Was funktioniert:** Die Benachrichtigung auf einen **gesperrten Kanal** legen
(`IMPORTANCE_NONE`). Dann ist sie zwar technisch vorhanden, taucht aber in der
Liste nicht auf.

```java
nm.createNotificationChannel(new NotificationChannel(
        "app_stumm", "Dienst (stumm)", NotificationManager.IMPORTANCE_NONE));
```

Ein bestehender Kanal lässt sich **nicht** nachträglich leiser stellen — bei
Bedarf löschen und mit **neuer ID** anlegen.

**Prüfen, ob wirklich nichts angezeigt wird** (verlässlicher als jede Geste):
```bash
adb shell cmd notification list        # taucht das eigene Paket auf?
```

---

## 3. Ein fremdes iPhone findet die Uhr nicht

**Symptom:** Das eigene iPhone zeigt die werbende Uhr, ein **anderes** iPhone
zeigt gar nichts — obwohl es nie mit der Uhr verbunden war.

**Zwei Ursachen, beide wichtig:**

**a) Der Name fehlte im Werbepaket.**
Ein BLE-Werbepaket fasst 31 Byte. Die 128-Bit-ANCS-Solicitation belegt 18, Flags
3 — es bleiben rund **8 Zeichen für den Namen**. „Galaxy Watch6 Classic (A5TR)"
passt nicht, also landete der Name in der **Scan-Antwort**. Ein Telefon, das das
Gerät schon kennt, zeigt es trotzdem (es hat den Namen im Zwischenspeicher) —
ein **fremdes** listet Geräte ohne Namen im Werbepaket nicht.

Lösung: kurzen Bluetooth-Namen setzen, dann passt er mit hinein:
```java
if (adapter.getName().length() > 8) adapter.setName("Herold");
new AdvertiseData.Builder()
    .setIncludeDeviceName(true)
    .addServiceSolicitationUuid(new ParcelUuid(ANCS_SERVICE))
    .build();
```
Wird das Paket doch zu groß, meldet `onStartFailure` Code **1** — dann ohne
Namen erneut versuchen (macht Herold automatisch).

**b) Die Uhr war nie klassisch sichtbar.**
```
ScanMode: SCAN_MODE_CONNECTABLE       # <- nicht auffindbar
```
iOS listet in den Bluetooth-Einstellungen praktisch nur **klassisch sichtbare**
Geräte. Reine BLE-Werbung reicht einem unbekannten Telefon nicht:

```bash
adb shell am start -a android.bluetooth.adapter.action.REQUEST_DISCOVERABLE \
    --ei android.bluetooth.adapter.extra.DISCOVERABLE_DURATION 300
```

**Und:** Ein verbundenes Gerät stoppt die Werbung. Beim Koppeln mit Telefon B
also Bluetooth an Telefon A ausschalten.

---

## 4. Ziffernblatt per adb aktiv setzen

```bash
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
  --es operation set-watchface \
  --ecn component <paket>/<klasse>
```

Antwort `Favorite Id=[..] Runtime=[..]` = gesetzt.

**`--ecn`, nicht `--es component`.** Mit `--es` antwortet die Uhr stur
„Either component name or watchface id are required." — der Extra muss ein
ComponentName sein.

---

## 5. Play liefert nicht alles an eine kontenlose Uhr

- Manche Apps (u. a. große Ziffernblatt-Anbieter) beantwortet Play mit
  **`AppNotSupported (code=2)`** — auch ältere, technisch passende Versionen.
- **Ohne Google-Geräteregistrierung gibt es kein FCM.** Prüfen:
  ```bash
  adb shell content query --uri content://com.google.android.gsf.gservices \
      --where "name='android_id'"
  ```
  Kommt nichts zurück, hat die Uhr **keine** Google-Geräte-ID. Dann erreichen
  **Push-Nachrichten die Uhr nie** — Apps, die Inhalte „ans Gerät senden"
  (Ziffernblätter, Cloud-Sync), funktionieren nicht, egal wie oft man drückt.
  Ohne Konto lässt sich das nicht nachholen.

Das ist die härteste Grenze des ganzen Projekts. Wer solche Dienste braucht,
kommt um ein Google-Konto nicht herum.

---

## 6. Play-Suche und die Wear-Kategorien

- Die **native** Play-Suche liefert unter einem Uhr-Profil **null** Treffer.
- `restriction`, `compatibility` und ein erfolgreicher Auslieferungstest
  unterscheiden **nicht** zwischen Handy- und Uhr-App — Play liefert auch
  WhatsApp an die Uhr aus.
- Verlässlich ist nur **Googles eigene Kategorie**:
  `/store/apps/category/ANDROID_WEAR` und `/store/apps/category/WATCH_FACE`,
  über die Web-Helfer abgerufen.

---

## 7. Der Ladezustand lügt

`BatteryManager.isCharging()` meldet beim **drahtlosen** Laden „nein". Richtig
ist der klebrige Batterie-Intent:

```java
Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
int status  = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
boolean laedt = plugged != 0
        || status == BatteryManager.BATTERY_STATUS_CHARGING
        || status == BatteryManager.BATTERY_STATUS_FULL;
```

---

## 8. Sensoren: Statuscodes, die man kennen muss

- **Puls-Status `-3`** = Uhr wird nicht getragen. Messung sofort abbrechen.
- **IBI-Genauigkeit `ERROR`** = lockere Uhr oder Bewegung. Zeigt der Puls lange
  „–", liegt es fast immer daran — nicht am Code. Die App sagt es dem Nutzer.
- **BIA-Fehlercodes:** 4 = Elektrode am Handgelenk, 7/8 = Finger neben der
  oberen/unteren Taste, 9 = beide, 10 = zu locker. Warten statt abbrechen.
- **SpO₂:** 2 = gültig, −6 = Signal verloren, 0 = rechnet noch.
- **`AMBIENT`-Temperatur** ist die **Gehäusetemperatur** des Sensorchips, nicht
  die Raumluft. Wärmefluss-Modelle für die Körperkerntemperatur scheitern daran.
  Ehrlicher Weg: Abweichung vom persönlichen Grundwert aus mehreren Nächten.
- **Blutdruck ist nicht möglich:** Der Sensor steckt hinter einer
  Signatur-Berechtigung, Samsung Health Monitor verlangt ein Samsung-Telefon.
  Was geht: Im EKG-Datenpunkt kommt der **PPG-Grünkanal** mit (100 Hz) — daraus
  die **Pulsankunftszeit**. Das ist eine Laufzeit, kein Blutdruck, und sollte
  auch so benannt werden.

---

## 9. Rhythmus: Atemarrhythmie ist kein Fehlalarm

Eine naive Analyse („schwankt stark → unregelmäßig") meldet bei jungen, gesunden
Menschen Fehlalarm: Beim Atmen schwankt der Puls stark, aber **rhythmisch**.

Der Unterschied liegt im **Muster**: Nachbarvorhersage über mehrere Abstände und
spektrale Bandleistung. Ist die Schwankung vorhersagbar, ist es Atmung. Ist sie
chaotisch **und** ohne Muster, ist sie unregelmäßig. Dazwischen gehört ein
ehrliches „unsicher".

Schwellen nicht raten — an vielen nachgebildeten Messreihen ablesen und gegen
echte Aufzeichnungen prüfen (`herold/test/`).

---

## 10. Kleinkram, der Zeit frisst

- **adb-Port wechselt** bei jedem Neustart der Uhr. Über mDNS suchen.
- Ein vergessener **`uhr.sh --daemon`** verbindet endlos neu, ringt mit jedem
  anderen adb-Zugriff und löst auf der Uhr ein Benachrichtigungs-Gewitter aus.
  Bei Verbindungsproblemen **zuerst den eigenen Rechner prüfen**:
  `pgrep -fl uhr.sh`.
- **Screenshots** brauchen vorher `KEYCODE_WAKEUP`, sonst sind sie schwarz.
  `svc power stayon` wirkt nur am Ladekabel.
- **Benachrichtigungen** öffnet der Wisch **von links**; von unten kommt die
  App-Liste.
- **`am force-stop`** tötet den Benachrichtigungsdienst. Danach kommt er erst
  durch Öffnen der App oder einen Neustart zurück — nie so liegen lassen.
- **Mehrere DEX-Dateien:** `zip … classes*.dex`, nicht `classes.dex`.
- **Klassische Ziffernblätter** brauchen `--target-sdk-version 29`.
- **Systemupdates** setzen Bloatware, den Werksschutz-Modus und WLAN-Debugging
  zurück. Selbst installierte Apps überleben.
- **Automatik-Tipper aufpassen:** Ein Skript, das jeden Installations-Dialog
  bestätigt, bestätigt auch **stehengebliebene** Dialoge früherer Läufe — dann
  wird scheinbar die falsche App installiert.
