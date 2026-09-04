[Deutsche Fassung](../03-herold.md)

# 03 — Herold: bridge to the iPhone and health measurements

Herold is the heart of the project. It does two things Samsung does not provide
for on an iPhone: **fetching notifications** and **using the sensors**.

## Notifications over ANCS

Apple documents the **Apple Notification Center Service (ANCS)**: a BLE device
may pair with the iPhone and then receives its notifications. That is exactly
what Herold uses — the watch presents itself as a BLE device asking for ANCS.

How it works:
1. The watch advertises over BLE with the **ANCS solicitation UUID** and its
   name.
2. On the iPhone, the user pairs the watch in the Bluetooth settings.
3. Once the link is encrypted, iOS grants access to the ANCS service.
4. Herold subscribes to the notifications, fetches title/text/app and shows
   them on the watch.

The service (`AncsService`) runs as a foreground service so Android does not
kill it, and on top of that a watchdog checks it every ten minutes.

> Two pitfalls are covered in detail in
> [07-findings.md](07-findings.md): why a different iPhone does not find the
> watch, and how to make the service notification invisible.

## Health measurements

Through the **Samsung Health Sensor SDK**:

| Measurement | What happens |
|---|---|
| **ECG** | 30 s of cardiac waveform, 500 Hz. Waveform, heart rate, rhythm analysis, Poincaré plot. |
| **Heart rate & rhythm** | 150 s of beat intervals (IBI); from those, the rhythm analysis and the respiration rate. |
| **Oxygen (SpO₂)** | 35 s, ends earlier once there are enough valid values. |
| **Skin temperature** | 12 s, plus the deviation from the personal baseline. |
| **Body composition (BIA)** | 40 s, needs height/weight/age/sex in the profile. |
| **Respiration rate** | from the spectrum of the beat intervals. |

### The rhythm analysis

The most interesting part, and the one where the most can go wrong. From the
beat intervals it computes:

- **Spread** and **pNN50** — does the heartbeat vary at all?
- **Entropy** of the interval changes (fixed 25 ms bins) — is the variation
  chaotic?
- **Neighbour prediction** across several intervals and **spectral band power**
  — is there a *pattern* in the variation?

The difference between "irregular" and "respiratory arrhythmia" is exactly that
pattern: while breathing, the pulse varies a lot, but **rhythmically**. Without
that distinction, any naive analysis raises a false alarm on a healthy young
person.

The thresholds are not guesses. They were read off from 400 simulated series
each (`test/Verteilung.java`) and checked against real recordings.

**The wording is deliberate:** no disease name, no all-clear, no green check
mark. The app says what it measured — not what it means.

### Pulse arrival time (instead of blood pressure)

Blood pressure is **not** possible this way: the sensor sits behind a signature
permission, and Samsung Health Monitor demands a Samsung phone.

What does work: in the ECG data stream, the SDK delivers the **PPG green
channel** in the same data point (100 Hz). The distance between the R peak and
the foot of the pulse wave yields the **pulse arrival time** — a transit time,
not a blood pressure. That is exactly how the app words it, too.

## Background measurements

`MessPlaner` ("measurement scheduler") measures on its own, without ruining the
battery:

- Skin temperature and heart rate roughly every 30 minutes; at night,
  temperature hourly
- Oxygen twice a day, the long rhythm measurement four times a day
- **Check first:** is the watch being worn? Is the arm still? If not, postpone.
- With the screen dark, call `flush()` so batched sensor values arrive

## Power

The Wi-Fi lock, the wake lock and the adb watchdog run **only on the charger**.
The charging state is read from the battery intent, not from
`BatteryManager.isCharging()` — that one wrongly reports "no" during wireless
charging.

## Interface

Black background, drawn cards, a list that follows the curve of the display
(`KurvenListe`, "curved list"), and the rotating bezel as a scroll wheel (`Rad`,
"wheel") — no AndroidX, everything drawn by hand. Colours and motion curves live
in one place, `Stil.java` ("style").

## Self-test

```bash
cd herold && ./build.sh --selftest      # build, install, launch, screenshot
./test/oberflaeche.sh                   # click through every screen and check it
./test/pruefen.sh                       # rhythm analysis against test data
```
