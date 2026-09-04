# 03 — Herold: Brücke zum iPhone und Gesundheitsmessungen

Herold ist der Kern des Projekts. Er macht zwei Dinge, die Samsung an einem
iPhone nicht vorsieht: **Benachrichtigungen holen** und **die Sensoren nutzen**.

## Benachrichtigungen per ANCS

Apple dokumentiert den **Apple Notification Center Service (ANCS)**: Ein
BLE-Gerät darf sich mit dem iPhone koppeln und bekommt dann dessen
Benachrichtigungen. Genau das nutzt Herold — die Uhr tritt als BLE-Gerät auf,
das ANCS anfragt.

Ablauf:
1. Die Uhr wirbt per BLE mit der **ANCS-Solicitation-UUID** und ihrem Namen.
2. Am iPhone koppelt der Nutzer die Uhr in den Bluetooth-Einstellungen.
3. Nach dem Verschlüsseln der Strecke gibt iOS den ANCS-Dienst frei.
4. Herold abonniert die Benachrichtigungen, holt Titel/Text/App und zeigt sie
   auf der Uhr.

Der Dienst (`AncsService`) läuft als Vordergrunddienst, damit Android ihn nicht
beendet, und wird zusätzlich alle zehn Minuten von einem Wächter geprüft.

> Zwei Fallstricke stehen ausführlich in
> [07-erkenntnisse.md](07-erkenntnisse.md): Warum ein fremdes iPhone die Uhr
> nicht findet, und wie man die Dienst-Benachrichtigung unsichtbar bekommt.

## Gesundheitsmessungen

Über das **Samsung Health Sensor SDK**:

| Messung | Was passiert |
|---|---|
| **EKG** | 30 s Herzstromkurve, 500 Hz. Kurve, Puls, Rhythmusanalyse, Poincaré-Bild. |
| **Puls & Rhythmus** | 150 s Schlagabstände (IBI), daraus die Rhythmusanalyse und die Atemfrequenz. |
| **Sauerstoff (SpO₂)** | 35 s, endet früher bei genug gültigen Werten. |
| **Hauttemperatur** | 12 s, plus Abweichung vom persönlichen Grundwert. |
| **Körperanalyse (BIA)** | 40 s, braucht Größe/Gewicht/Alter/Geschlecht im Profil. |
| **Atemfrequenz** | aus dem Spektrum der Schlagabstände. |

### Die Rhythmusanalyse

Der interessanteste Teil, und der, bei dem am meisten schiefgehen kann. Aus den
Schlagabständen werden berechnet:

- **Streuung** und **pNN50** — schwankt der Herzschlag überhaupt?
- **Entropie** der Abstandsänderungen (feste 25-ms-Klassen) — ist die Schwankung
  chaotisch?
- **Nachbarvorhersage** über mehrere Abstände und **spektrale Bandleistung** —
  steckt ein *Muster* in der Schwankung?

Der Unterschied zwischen „unregelmäßig" und „Atemarrhythmie" ist genau dieses
Muster: Beim Atmen schwankt der Puls stark, aber **rhythmisch**. Ohne diese
Unterscheidung meldet jede naive Analyse bei einem gesunden jungen Menschen
Fehlalarm.

Die Schwellen sind nicht geraten, sondern an je 400 nachgebildeten Messreihen
abgelesen (`test/Verteilung.java`) und gegen echte Aufzeichnungen geprüft.

**Wortlaut ist Absicht:** kein Krankheitsname, keine Entwarnung, kein grüner
Haken. Die App sagt, was sie gemessen hat — nicht, was es bedeutet.

### Pulsankunftszeit (statt Blutdruck)

Blutdruck ist auf diesem Weg **nicht** möglich: Der Sensor steckt hinter einer
Signatur-Berechtigung, und Samsung Health Monitor verlangt ein Samsung-Telefon.

Was geht: Im EKG-Datenstrom liefert das SDK im selben Datenpunkt den
**PPG-Grünkanal** mit (100 Hz). Aus dem Abstand zwischen R-Zacke und dem Fuß der
Pulswelle ergibt sich die **Pulsankunftszeit** — eine Laufzeit, kein Blutdruck.
Genau so steht es auch in der App.

## Hintergrundmessungen

`MessPlaner` misst von allein, ohne den Akku zu ruinieren:

- Hauttemperatur und Puls etwa alle 30 Minuten, nachts stündlich Temperatur
- Sauerstoff zweimal täglich, die lange Rhythmusmessung viermal täglich
- **Vorher prüfen:** Wird die Uhr getragen? Ist der Arm ruhig? Sonst verschieben.
- Bei dunklem Bildschirm `flush()`, damit gestapelte Sensorwerte ankommen

## Strom

WLAN-Halter, Wach-Halter und die adb-Wache laufen **nur am Ladekabel**. Der
Ladezustand wird über den Batterie-Intent gelesen, nicht über
`BatteryManager.isCharging()` — das meldet beim drahtlosen Laden falsch „nein".

## Oberfläche

Schwarzer Grund, gezeichnete Karten, eine Liste, die der Rundung des Displays
folgt (`KurvenListe`), und die drehbare Lünette als Scrollrad (`Rad`) — ohne
AndroidX, alles selbst gezeichnet. Farben und Bewegungskurven liegen zentral in
`Stil.java`.

## Selbsttest

```bash
cd herold && ./build.sh --selftest      # bauen, installieren, starten, Screenshot
./test/oberflaeche.sh                   # alle Bildschirme durchklicken und prüfen
./test/pruefen.sh                       # Rhythmusanalyse gegen Testdaten
```
