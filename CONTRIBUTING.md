# Contributing

> **Auf Deutsch:** Diese Seite ist auf Englisch, damit sie möglichst viele erreicht.
> Issues und Pull Requests auf **Deutsch sind genauso willkommen** — schreib in der
> Sprache, in der du dich wohler fühlst.

Thanks for looking. Everything below has one goal: making this work on watches
that are not mine.

---

## What helps most: a report from another watch

This project was developed on, and is used daily on, exactly one device — a
**Galaxy Watch 6 Classic (SM-R955F)** paired to an iPhone 14, across Wear OS 4
to 6. That is the entire test lab.

So the single most useful thing you can send is **"here is what happened on my
watch"** — and both outcomes are worth writing down. A plain *"markt installs
fine on an SM-R900, Wear OS 5"* is not noise; it is the only way the list of
watches this is known to work on ever gets longer than one entry.

Especially wanted:

- **Other Galaxy Watch models** — 4, 5, 7, 8, Ultra, FE, and the non-Classic
  variants.
- **Other Wear OS / One UI Watch versions**, above all right after a system
  update. Updates undo the changes this project makes, and they occasionally
  change behaviour outright.
- **Non-Samsung Wear OS watches.** The health features need the Samsung Health
  Sensor SDK (see [docs/02-bibliotheken.md](docs/02-bibliotheken.md)), so expect
  at most partial success there — partial success is still a result worth
  having.
- **A step from [docs/07-erkenntnisse.md](docs/07-erkenntnisse.md) that did not
  apply to you.** That page is the most valuable thing in this repo and it was
  written from a sample size of one.

---

## What a good report contains

1. **The exact model number** — the `SM-…` string, not "Galaxy Watch 6".
2. **The system version** — Wear OS / One UI Watch version and the build id.
3. **Which part** — `herold`, `markt`, `zifferblatt` or `werkzeuge` — and
   whether you used the APK from a release or built it yourself (then name the
   commit).
4. **What you did, what you expected, what actually happened.** In that order,
   in plain sentences.
5. **The adb output** — the command you ran and its *complete* output, not a
   summary of it. The line you skipped is usually the one that explains it.
6. **A screenshot**, if a screen is involved.

Copy-paste to collect it:

```bash
# 1 + 2 — straight from the watch
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.build.version.sdk
adb shell getprop ro.build.display.id

# 5 — what the app said, and whether anything crashed
adb shell logcat -d -s Herold:I | tail -40     # or Markt:I
adb shell logcat -d -b crash | tail -60

# 6 — a picture of the screen
./werkzeuge/uhr.sh --shot bild.png
```

> [!IMPORTANT]
> `werkzeuge/uhr.sh` finds the watch by matching a **hard-coded model string**
> (`MODELL=SM_R955F`, near the top of the file). On any other watch it will find
> nothing until you change that line to the value your watch reports in
> `adb devices -l` as `model:…` — note the underscores there, not hyphens.
> If you had to change it, please say so in your issue. That is exactly the kind
> of thing that should stop being hard-coded.

---

## Before you post: take the personal bits out

Logs from this project can contain things that should not sit in a public issue.
Two that are in the code, not hypothetical:

- `AncsService` logs the **Bluetooth address** of the phone when it unpairs.
- It logs the **artist and title** of whatever the phone is currently playing.

So before you paste: replace Bluetooth addresses, IP addresses and adb ports,
serial numbers, notification text, and any measured health values. `.gitignore`
already keeps measurement CSVs and the watchdog log out of commits — the same
care belongs in an issue body. Nobody reading the tracker should be able to work
out whose watch it is.

---

## Building without Gradle, in three sentences

Every app folder has a `build.sh` that drives the Android SDK tools directly —
`aapt2 compile` → `aapt2 link` → `javac` → `d8` → `zip` + `zipalign` →
`apksigner` — and drops a signed APK into that folder's `build/`. There is no
dependency resolver, so every third-party JAR has to be fetched once by hand
into the project's `libs/`: [docs/02-bibliotheken.md](docs/02-bibliotheken.md)
says which ones and where from, and `markt/BIBLIOTHEKEN.txt` records the exact
versions with their SHA-256 sums. Before your first build, adjust `SDK=` and
`JAVA_HOME` at the top of the `build.sh` to your machine — JDK 21, sources
compiled to Java 17 bytecode, against `platforms/android-33`.

The traps that cost the most time are collected in
[docs/01-bauen-ohne-gradle.md](docs/01-bauen-ohne-gradle.md). Two worth knowing
before you start:

- **The keystore is generated once and then reused forever.** Delete it and the
  watch refuses your next update with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`. It
  is gitignored — never commit it, and do back it up.
- **Judge a build by whether the APK exists**, not by whether the output looked
  fine.

---

## Checking a change before you send it

- `herold/test/pruefen.sh` — runs the rhythm analysis against synthetic heart
  rhythms **on your computer, no watch required**. Run it whenever you touch
  `RhythmusAnalyse.java`.
- `herold/build.sh --selftest` and `markt/build.sh --selftest` — build, install,
  launch, take a screenshot and read the log back. If the script cannot reach
  your watch, `./build.sh` plus a manual `adb install -r -g build/<name>.apk`
  does the same job.
- `herold/test/oberflaeche.sh` — opens every screen of the app on the watch and
  saves a picture of each, so you can see that none of them fall over.

---

## Pull requests

- **Match the language around your change.** Code, comments and documentation in
  this repo are German, identifiers included (`Kachel`, `Verlauf`, `Sonde`).
  Don't switch a file to English halfway through.
- **Keep it small**, and say what you tested it on: model, system version, built
  yourself or release APK.
- **Never commit** `libs/*.jar`, `*.apk`, `*.keystore`, build output or
  measurement CSVs. `.gitignore` covers all of them — glance at `git status`
  anyway.
- **No new build system, and dependencies only when they earn it.** The point of
  the build here is that you can read it top to bottom in a minute.
- **No health claims.** The rule from the README stands: this makes no
  diagnosis, names no condition and gives no all-clear. A change that adds one
  will not be merged, however well meant.

---

## Translation

Not everything here exists in English — `README.en.md` is the English overview,
the detailed pages under `docs/` are German. If a German page is what stopped
you, say which one. Translating the pages people actually reach for is worth
more than translating all eight.

---

## Not sure it is worth an issue?

It is. *"Doesn't work on my SM-R870"* plus three lines of log beats silence
every time.
