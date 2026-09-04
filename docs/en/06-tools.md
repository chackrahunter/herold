[Deutsche Fassung](../06-werkzeuge.md)

# 06 — Tools

Three small scripts that make daily life with a watch and no phone bearable.

---

## `uhr.sh` — connecting to the watch

The watch's adb port **changes on every restart**. Do not guess it, let the
script find it.

```bash
./uhr.sh --id             # print the transport ID (connects if needed)
./uhr.sh shell <befehl>   # run a command on the watch
./uhr.sh --shot bild.png  # grab a screenshot
./uhr.sh --log            # show the app's messages
./uhr.sh --daemon         # start the watchdog (keeps the connection alive)
./uhr.sh --daemon-stop    # stop the watchdog
```

Why a **transport ID** instead of the device name? The watch's mDNS names
contain dots and spaces, and those break `adb -s`.

> **Warning from experience:** never leave a forgotten `--daemon` from an old
> session running. It reconnects endlessly, fights every other adb access, and
> the watch reports "adb connected over Wi-Fi" every second. If something
> behaves oddly, run `pgrep -fl uhr.sh` **first**.

---

## `entruempeln.sh` — removing bloatware reversibly

Removes Google and Samsung apps that are useless without a Google account and
without a Samsung phone. **Nothing is deleted** — the apps stay on the system
partition and can be brought back.

```bash
./entruempeln.sh --sichern          # back up the package list first
./entruempeln.sh --probe            # show what would happen
./entruempeln.sh --weg              # do it (logs everything)
./entruempeln.sh --zurueck <paket>  # bring one app back
./entruempeln.sh --zurueck alle     # bring everything back
```

Among other things it removes Play Store, Maps, Google Messages, Assistant,
Wallet, Samsung Wallet, SmartThings, Find My Phone/Watch, Smart Switch, Samsung
Cloud, Outlook. **Bixby is only disabled**, not removed — according to reports
from other people, uninstalling it disturbs GPS.

What stays: alarm, timer, stopwatch, world clock, calendar, contacts, phone,
music, gallery, Samsung Health, keyboard.

> After a **system update** much of it comes back. Just run the script again.

---

## `apps.sh` — installing and checking apps

```bash
./apps.sh datei.apk            # install
./apps.sh --start <paket>      # launch, wait, grab a screenshot
./apps.sh --installer <paket>  # allow the app to install packages itself
./apps.sh --weg <paket>        # remove
```

`--start` is handy for checking: it wakes the screen, launches the app, takes a
screenshot and counts crashes — so you can see whether something runs without
lifting a finger.

---

## Small moves you need often

```bash
# wake the screen (otherwise the screenshot is black)
adb -t <id> shell input keyevent KEYCODE_WAKEUP

# notifications: swipe in from the left. From the bottom you get the app list.
adb -t <id> shell input swipe 8 216 400 216

# what is in the foreground right now?
adb -t <id> shell dumpsys window | grep mCurrentFocus

# count crashes
adb -t <id> shell logcat -d -b crash | grep -c FATAL
```

`svc power stayon` only works **on the charging cable**.
