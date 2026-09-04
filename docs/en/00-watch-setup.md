[Deutsche Fassung](../00-uhr-einrichten.md)

# 00 — Setting up the watch without a Samsung phone

**This is the very first step.** Everything else in this repo assumes a watch
that is already set up.

For the Galaxy Watch 4 and newer, Samsung officially requires setup through the
**Galaxy Wearable app on an Android phone**. With an iPhone — or with no phone
at all — there seems to be no way past the welcome screen.

But there is a way around that hurdle, and Samsung documents it itself.

---

## The procedure (Galaxy Watch 4 and newer, Wear OS)

1. Turn the watch on with the **power button**.
2. On the **welcome screen, swipe up**.
3. **Tap the "Wear" icon at the top repeatedly** — keep going until this message
   appears:

   > *"Please long-tap watch icon for more than 3 seconds"*

   The message disappears again after a few seconds. If nothing shows up, keep
   tapping; it can easily take two dozen taps.
4. Now **press and hold** the same icon for longer than **3 seconds**, until
   **"Wird gestartet"** ("starting") appears.
5. The watch runs the rest of the setup on its own (language, time zone,
   Wi-Fi). Done — the watch now works standalone.

Source: [Samsung Germany — setting up a Galaxy Watch without a smartphone](https://www.samsung.com/de/support/mobile-devices/galaxy-watch-ohne-smartphone-einrichten/)

---

## What works afterwards — and what does not

**Out of the box**, a watch set up this way stays bare: time, fitness tracking,
music controls. No notifications, no Play Store, no apps.

**That is exactly where this repo comes in:**

| Gap | Solution here |
|---|---|
| No notifications from the iPhone | [`herold/`](../../herold) — pulls them straight off the iPhone over ANCS |
| No way to install apps without a Google account | [`markt/`](../../markt) — anonymous Play sign-in |
| No custom watch faces | [`zifferblatt/`](../../zifferblatt) — an example to build your own from |
| Full of useless bloatware | [`werkzeuge/entruempeln.sh`](../../werkzeuge) |

---

## Do these right afterwards

**1. Unlock the developer options** (needed for everything that follows):
Settings → *About watch* (*Info zur Uhr*) → *Software information*
(*Softwareinformationen*) → tap the **software version** repeatedly until
"Entwicklermodus aktiviert" ("developer mode enabled") appears.

**2. Turn on debugging over Wi-Fi:**
Settings → *Developer options* (*Entwickleroptionen*) → *ADB debugging* and
*Debugging over Wi-Fi*. That screen also shows the address (`IP:Port`) you need
in order to connect.

> The **port changes every time the watch restarts**. Do not guess it —
> `werkzeuge/uhr.sh --id` finds it over mDNS.

**3. Turn off factory reset protection** — otherwise **every** app install
fails (see [07-findings.md](07-findings.md)):

```bash
adb shell settings put global secure_frp_mode 0
adb shell settings put secure secure_frp_mode 0
```

---

## Pitfalls

- **A factory reset undoes everything.** After one, the procedure above starts
  from the top again — including developer options and factory reset
  protection.
- **A system update** (to a newer Wear OS, for example) turns debugging over
  Wi-Fi off, brings removed bloatware back and can reset factory reset
  protection. Just walk through the three points above again.
- **Bluetooth to the iPhone** is a different thing from the Samsung setup: the
  watch pairs later in the completely normal way, through the iPhone's
  Bluetooth settings — Herold takes care of that. Details in
  [03-herold-app.md](03-herold-app.md).
