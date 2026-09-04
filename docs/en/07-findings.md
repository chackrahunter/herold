[Deutsche Fassung](../07-erkenntnisse.md)

# 07 — Findings

This is the most valuable part of the repo: things that are documented nowhere
and that each cost hours. If you are working on a Galaxy Watch without a Google
account, this page saves you most of those hours.

---

## 1. Without this switch, **every** installation fails

**Symptom:** every app installation hangs or aborts — with Aurora Store just as
much as with a store of your own. The confirmation dialog never even appears.

**In the log:**
```
SecurityException: Can't install packages while in secure FRP
```

**Cause:** the watch is stuck in **factory reset protection** mode, even though
setup is finished — probably because it was set up without a Google account. In
that state the PackageManager blocks everything.

**Fix:**
```bash
adb shell settings put global secure_frp_mode 0
adb shell settings put secure secure_frp_mode 0
```

After that, installing works normally. The value stays set; after a system
update it is worth checking again. Markt resets it itself on startup (it needs
`WRITE_SECURE_SETTINGS` for that, granted with `adb pm grant`).

> **Remember:** when "the installation hangs", **always check** `secure_frp_mode`
> **first** — do not suspect your own installer.

---

## 2. Making the service notification disappear

A foreground service has to show a notification. On One UI Watch, **none** of
these helped:

- `IMPORTANCE_MIN`
- `VISIBILITY_SECRET`
- `CATEGORY_SERVICE`
- `FOREGROUND_SERVICE_DEFERRED`

**What works:** put the notification on a **blocked channel**
(`IMPORTANCE_NONE`). It is then technically there, but it does not show up in
the list.

```java
nm.createNotificationChannel(new NotificationChannel(
        "app_stumm", "Dienst (stumm)", NotificationManager.IMPORTANCE_NONE));
```

An existing channel **cannot** be turned down afterwards — delete it if you
need to and create it again with a **new ID**.

**Check that nothing is really shown** (more reliable than any gesture on the
watch):
```bash
adb shell cmd notification list        # does your own package show up?
```

---

## 3. Someone else's iPhone does not find the watch

**Symptom:** your own iPhone shows the advertising watch, a **different** iPhone
shows nothing at all — even though it was never connected to the watch.

**Two causes, and both matter:**

**a) The name was missing from the advertising packet.**
A BLE advertising packet holds 31 bytes. The 128-bit ANCS solicitation takes 18
of them, the flags 3 — that leaves about **8 characters for the name**. "Galaxy
Watch6 Classic (A5TR)" does not fit, so the name ended up in the **scan
response**. A phone that already knows the device shows it anyway (it has the
name cached) — a phone that does **not** know it will not list devices without a
name in the advertising packet.

Fix: set a short Bluetooth name, then it fits in as well:
```java
if (adapter.getName().length() > 8) adapter.setName("Herold");
new AdvertiseData.Builder()
    .setIncludeDeviceName(true)
    .addServiceSolicitationUuid(new ParcelUuid(ANCS_SERVICE))
    .build();
```
If the packet still comes out too big, `onStartFailure` reports code **1** —
then try again without the name (Herold does this automatically).

**b) The watch was never discoverable over classic Bluetooth.**
```
ScanMode: SCAN_MODE_CONNECTABLE       # <- not discoverable
```
In its Bluetooth settings, iOS lists practically only devices that are
**discoverable over classic Bluetooth**. Plain BLE advertising is not enough for
a phone that does not know the watch:

```bash
adb shell am start -a android.bluetooth.adapter.action.REQUEST_DISCOVERABLE \
    --ei android.bluetooth.adapter.extra.DISCOVERABLE_DURATION 300
```

**And:** a connected device stops the advertising. So when pairing with phone B,
turn Bluetooth off on phone A.

---

## 4. Setting a watch face active over adb

```bash
adb shell am broadcast -a com.google.android.wearable.app.DEBUG_SURFACE \
  --es operation set-watchface \
  --ecn component <paket>/<klasse>
```

A reply of `Favorite Id=[..] Runtime=[..]` means it is set.

**`--ecn`, not `--es component`.** With `--es` the watch stubbornly answers
"Either component name or watchface id are required." — the extra has to be a
ComponentName.

---

## 5. Play does not deliver everything to a watch without an account

- For some apps (big watch face vendors among them) Play answers with
  **`AppNotSupported (code=2)`** — even for older versions that technically fit.
- **Without a Google device registration there is no FCM.** Check:
  ```bash
  adb shell content query --uri content://com.google.android.gsf.gservices \
      --where "name='android_id'"
  ```
  If nothing comes back, the watch has **no** Google device ID. Then **push
  messages never reach the watch** — apps that "send content to the device"
  (watch faces, cloud sync) do not work, no matter how often you press. Without
  an account there is no way to add this later.

This is the hardest limit in the whole project. If you need services like that,
there is no way around a Google account.

---

## 6. Play search and the Wear categories

- The **native** Play search returns **zero** hits under a watch profile.
- `restriction`, `compatibility` and a successful delivery test do **not**
  distinguish between a phone app and a watch app — Play will deliver WhatsApp
  to the watch too.
- The only reliable thing is **Google's own category**:
  `/store/apps/category/ANDROID_WEAR` and `/store/apps/category/WATCH_FACE`,
  fetched through the web helpers.

---

## 7. The charging state lies

`BatteryManager.isCharging()` reports "no" while charging **wirelessly**. The
sticky battery intent is the one that is right:

```java
Intent i = registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
int plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
int status  = i.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
boolean laedt = plugged != 0
        || status == BatteryManager.BATTERY_STATUS_CHARGING
        || status == BatteryManager.BATTERY_STATUS_FULL;
```

---

## 8. Sensors: status codes you have to know

- **Heart rate status `-3`** = the watch is not being worn. Stop the measurement
  immediately.
- **IBI accuracy `ERROR`** = a loose watch or movement. If the heart rate shows
  "–" for a long time, that is almost always the reason — not the code. The app
  tells the user so.
- **BIA error codes:** 4 = electrode on the wrist, 7/8 = finger next to the
  upper/lower button, 9 = both, 10 = too loose. Wait instead of aborting.
- **SpO₂:** 2 = valid, −6 = signal lost, 0 = still calculating.
- **`AMBIENT` temperature** is the **case temperature** of the sensor chip, not
  the room air. Heat flux models for core body temperature fail on this. The
  honest way: the deviation from a personal baseline taken over several nights.
- **Blood pressure is not possible:** the sensor sits behind a signature
  permission, and Samsung Health Monitor demands a Samsung phone. What does
  work: the ECG data point carries the **green PPG channel** with it (100 Hz) —
  and out of that comes the **pulse arrival time**. That is a travel time, not a
  blood pressure, and it should be named that way.

---

## 9. Rhythm: respiratory arrhythmia is not a false alarm

A naive analysis ("varies a lot → irregular") raises false alarms on young,
healthy people: while breathing, the heart rate varies a lot, but
**rhythmically**.

The difference is in the **pattern**: predicting a beat from its neighbours
across several intervals, plus spectral band power. If the variation is
predictable, it is breathing. If it is chaotic **and** without a pattern, it is
irregular. In between belongs an honest "not sure".

Do not guess the thresholds — read them off many simulated series of
measurements and check them against real recordings (`herold/test/`).

---

## 10. Small stuff that eats time

- **The adb port changes** every time the watch restarts. Find it over mDNS.
- A forgotten **`uhr.sh --daemon`** reconnects endlessly, fights every other adb
  access and sets off a storm of notifications on the watch. With connection
  trouble, **check your own machine first**: `pgrep -fl uhr.sh`.
- **Screenshots** need a `KEYCODE_WAKEUP` first, otherwise they come out black.
  `svc power stayon` only works on the charging cable.
- **Notifications** open with a swipe **from the left**; from the bottom comes
  the app list.
- **`am force-stop`** kills the notification service. After that it only comes
  back by opening the app or by a restart — never leave it like that.
- **Multiple DEX files:** `zip … classes*.dex`, not `classes.dex`.
- **Classic watch faces** need `--target-sdk-version 29`.
- **System updates** put bloatware back, and reset factory reset protection and
  Wi-Fi debugging. Apps you installed yourself survive.
- **Careful with auto-tappers:** a script that confirms every installation
  dialog also confirms **leftover** dialogs from earlier runs — and then it
  looks as if the wrong app is being installed.
