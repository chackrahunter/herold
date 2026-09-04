<div align="center">

# ⌚ Herold

### Run a Galaxy Watch with an iPhone — no Google account, no Samsung phone

Notifications, health measurements, an app store of its own and custom watch
faces — all hand-built, all without Gradle.

<br>

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)
[![Wear OS](https://img.shields.io/badge/Wear%20OS-4%20to%206-4285F4?style=for-the-badge&logo=wearos&logoColor=white)](https://wearos.google.com/)
[![No Gradle](https://img.shields.io/badge/Build-without%20Gradle-success?style=for-the-badge)](docs/en/01-building-without-gradle.md)
[![No Google account](https://img.shields.io/badge/Google%20account-not%20needed-critical?style=for-the-badge)](docs/en/04-app-store.md)

[![Java](https://img.shields.io/badge/JDK-21-orange?style=flat-square&logo=openjdk&logoColor=white)](#requirements)
[![Tested on](https://img.shields.io/badge/tested-Galaxy%20Watch%206%20Classic-black?style=flat-square&logo=samsung&logoColor=white)](#requirements)
[![iPhone](https://img.shields.io/badge/paired%20with-iPhone-lightgrey?style=flat-square&logo=apple&logoColor=white)](docs/en/03-herold-app.md)
[![Docs](https://img.shields.io/badge/docs-thorough-informational?style=flat-square)](#-documentation)
[![Release](https://img.shields.io/github/v/release/chackrahunter/herold?style=flat-square&label=Release&color=success)](https://github.com/chackrahunter/herold/releases/latest)
[![Downloads](https://img.shields.io/github/downloads/chackrahunter/herold/total?style=flat-square&label=Downloads)](https://github.com/chackrahunter/herold/releases)

[![Ko-fi](https://img.shields.io/badge/Support_on-Ko--fi-FF5E5B?style=for-the-badge&logo=kofi&logoColor=white)](https://ko-fi.com/chackrahunter)
[![PayPal](https://img.shields.io/badge/Donate_with-PayPal-00457C?style=for-the-badge&logo=paypal&logoColor=white)](https://www.paypal.me/Donsko2007)

<br>

🇬🇧 **English** · [🇩🇪 Deutsch](README.md)

[**⬇ Download APKs**](https://github.com/chackrahunter/herold/releases/latest) ·
[**Set up the watch**](docs/en/00-watch-setup.md) ·
[**Docs**](#-documentation) ·
[**Hard-won findings**](docs/en/07-findings.md) ·
[**Support**](#-support-this-project)

</div>

> [!NOTE]
> Every documentation page is available in English under `docs/en/`. The German
> originals stay in `docs/` and each English page links back to its source.

---

## ⬇ Download ready-made APKs

**You do not have to build anything.** Since
[**v1.1.0**](https://github.com/chackrahunter/herold/releases/latest) the release
carries signed APKs:

| File | What it is |
|---|---|
| [`herold-1.1.0.apk`](https://github.com/chackrahunter/herold/releases/download/v1.1.0/herold-1.1.0.apk) | iPhone notifications, ECG, heart rate, SpO₂, skin temperature, respiratory rate |
| [`zifferblatt-1.1.0.apk`](https://github.com/chackrahunter/herold/releases/download/v1.1.0/zifferblatt-1.1.0.apk) | Example watch face |
| `markt` — **build it yourself** | The app store bundles `gplayapi`, which is GPL-3.0, so a prebuilt APK cannot be shipped under this repository's MIT licence. See [DRITTANBIETER.md](DRITTANBIETER.md). |

```bash
adb connect <watch-ip>:<port>
adb install herold-1.1.0.apk
```

> [!WARNING]
> If the install fails with `Can't install packages while in secure FRP`, the
> watch is in factory reset protection. That blocks **every** install on an
> account-less watch. The one command that fixes it is in
> [docs/en/07-findings.md](docs/en/07-findings.md).

---

## What this is

Samsung never meant this combination to work. A Galaxy Watch 4 or newer can
**officially only be set up and managed from an Android phone**. Pair one with
an **iPhone** and you are left with an expensive watch: no notifications, no app
store, no access to its own sensors.

This project makes it fully usable anyway — **without a Google account**,
**without a Samsung phone** and **without the Play Store app**.

It exists because my mother has a Galaxy Watch 6 Classic and an iPhone.
Everything here runs on that exact watch, every day.

<div align="center">

| Store: home | Store: search | Store: app page | Example watch face |
|:---:|:---:|:---:|:---:|
| <img src="bilder/markt-start.png" width="180"> | <img src="bilder/markt-suche.png" width="180"> | <img src="bilder/markt-app.png" width="180"> | <img src="bilder/zifferblatt.png" width="180"> |

</div>

---

## ✨ What is in the box

<table>
<tr>
<td width="50%" valign="top">

### 📲 `herold/` — the bridge to the iPhone

Pulls notifications straight off the iPhone over **ANCS** (Apple Notification
Center Service) — no relay server, no third-party app, no subscription.

Plus the sensors Samsung otherwise keeps locked behind its own app:

- **ECG** (30 s at 500 Hz) with waveform and analysis
- **Heart rate and rhythm**, with an honest rhythm assessment
- **Blood oxygen (SpO₂)**, **skin temperature**, **body composition**
- **Respiratory rate**, derived from the intervals between beats
- **Pulse arrival time** from ECG + PPG
- Tiles, a measurement history with a detail view, background measurements

</td>
<td width="50%" valign="top">

### 🛒 `markt/` — an app store without an account

Signs in to Google Play **anonymously** and installs apps straight onto the
watch. No Google account anywhere.

- Deliberately lists **only Wear OS apps** and watch faces
- Catalogue built from Google's own Wear OS categories (~1000 entries)
- Search across all of Google Play
- Update checks for everything you installed through it
- Installing a specific older version on purpose

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🎨 `zifferblatt/` — your own watch face

A complete, working example with original artwork: background, time, date and
a frugal ambient mode — plus the trick for **making a watch face active over
adb**.

</td>
<td width="50%" valign="top">

### 🔧 `werkzeuge/` — everyday tooling

- `uhr.sh` — find the watch and keep the connection alive
- `entruempeln.sh` — remove bloatware **reversibly**
- `apps.sh` — sideload, launch and check apps

</td>
</tr>
</table>

---

## 🚀 Quick start

> [!IMPORTANT]
> **Watch not set up yet?** A Galaxy Watch 4 or newer can officially only be set
> up from an Android phone. The way around that — documented by Samsung
> themselves — is in **[docs/en/00-watch-setup.md](docs/en/00-watch-setup.md)**.
> **Start there.**

```bash
git clone https://github.com/chackrahunter/herold.git
cd herold
```

**1. Fetch the libraries.** They are deliberately **not** in this repo — they
belong to other people. [docs/en/02-libraries.md](docs/en/02-libraries.md)
explains where each one comes from. They go into `herold/libs/`, `markt/libs/`
and `zifferblatt/libs/`.

**2. Check the paths.** Each `build.sh` sets `SDK=` and `JAVA_HOME` at the top.

**3. Connect the watch** (switch wireless debugging on at the watch first):

```bash
./werkzeuge/uhr.sh --id      # finds the watch over mDNS
```

**4. Build and sideload it onto the watch:**

```bash
cd herold && ./build.sh --install
```

> [!TIP]
> The first build generates a signing key for you. It stays local and is **not**
> in the repo — otherwise anyone could sign updates for your app. Back it up
> anyway: lose it and you can never update your own app again.

---

## 🧰 Requirements

**Hardware**

| | |
|---|---|
| Watch | Samsung Galaxy Watch 4 or newer — developed on and used daily with a **Galaxy Watch 6 Classic (SM-R955F)**, Wear OS 4 through 6 |
| Phone | An **iPhone** for the notification bridge — or no phone at all |
| Computer | macOS or Linux to build on |

**Software**

| | |
|---|---|
| Android SDK | **Command line tools** (`aapt2`, `d8`, `zipalign`, `apksigner`) plus `platforms/android-33` |
| Java | **JDK 21** (sources compile to Java 17 bytecode) |
| adb | with **wireless debugging** to the watch |
| optional | `rsvg-convert` for the watch face artwork |

---

## 📚 Documentation

The pages below are in German; the summaries say what each one covers.

| | What it covers |
|---|---|
| [**00 — Setting up the watch**](docs/en/00-watch-setup.md) | Getting through setup without a Samsung phone. **Start here.** |
| [01 — Building without Gradle](docs/en/01-building-without-gradle.md) | The `aapt2 → javac → d8 → apksigner` chain and its pitfalls |
| [02 — Libraries](docs/en/02-libraries.md) | Which JARs you need and where to get them |
| [03 — Herold](docs/en/03-herold-app.md) | The ANCS bridge, sensors, rhythm analysis, background measurements |
| [04 — Markt (the app store)](docs/en/04-app-store.md) | Anonymous Play sign-in, the Wear OS catalogue, installing, updates |
| [05 — Watch face](docs/en/05-watch-face.md) | Building a watch face and making it active |
| [06 — Tools](docs/en/06-tools.md) | `uhr.sh`, `entruempeln.sh`, `apps.sh` |
| [**07 — Hard-won findings**](docs/en/07-findings.md) | **The valuable one:** things documented nowhere else that each cost hours |

> [!NOTE]
> If you read only one page, make it [**07 — Hard-won findings**](docs/en/07-findings.md).
> Among other things, it explains why **every single** app install fails until
> you flip one hidden flag, and why someone else's iPhone cannot see the watch at
> all.

---

## ⚠️ Important notes

> [!CAUTION]
> **The health measurements are not a medical device.**
> Herold reads the sensors and computes values from them. It makes **no
> diagnosis**, names **no conditions** and gives **no all-clear**. The rhythm
> analysis deliberately only says whether the heartbeat was regular — never
> whether anything is "fine". If something feels wrong, see a doctor, not a
> watch.

**Debloating is reversible.** `entruempeln.sh` removes apps for the current user
only (`pm uninstall --user 0`). Nothing is deleted from the system partition.
Order: `--sichern` (back up) → `--probe` (dry run) → `--weg` (remove).

**System updates undo all of it.** After a Wear OS update the removed bloatware
is back, wireless debugging is off and factory reset protection is on again.
Just run the scripts again.

**Legal.** The code is MIT. It does use other people's services and libraries
(Google Play, the Samsung Health Sensor SDK, gplayapi) — check for yourself
whether your use matches their terms. Watch faces of protected characters belong
to their rights holders; there are none in here.

---

## 🧩 More from me

Herold is not the only thing I build. If you are curious, here are my other
public projects. Have a look — I am glad about anyone who stops by.

| Project | What it is |
|---|---|
| [**SiliconFlow**](https://github.com/chackrahunter/siliconflow) | A Fabric mod that makes Minecraft run more evenly on Apple Silicon Macs: mixins that cap the drawing work for entities, particles and the HUD, memory-aware budgets, optional diagnostic overlays. Beta — and so far it builds for exactly one version, Minecraft 1.21.4. |
| [**Stellium**](https://github.com/chackrahunter/stellium-chat) | A team chat where everyone writes and reads in their own language — a language model translates in between, 22 languages. Plus an AI assistant, a task board, a calendar and shared files. Desktop app for macOS, Windows and Linux. |

Both are MIT-licensed, and both have ready-made files in their releases.

---

## 💛 Support this project

This is built in my spare time and is **funded entirely by donations**. There is
no company behind it, no ads, no subscription and no data collection — only the
time I put in.

**I can only keep going if enough comes in**: new features, keeping up with
future Wear OS versions, and helping everyone who rebuilds this. If it saved you
time or is worth something to you, anything is appreciated — however small.

<div align="center">
<br>

<a href="https://ko-fi.com/chackrahunter"><img src="https://img.shields.io/badge/Support_on-Ko--fi-FF5E5B?style=for-the-badge&logo=kofi&logoColor=white" alt="Support on Ko-fi"></a>
&nbsp;
<a href="https://www.paypal.me/Donsko2007"><img src="https://img.shields.io/badge/Donate_with-PayPal-00457C?style=for-the-badge&logo=paypal&logoColor=white" alt="Donate with PayPal"></a>

<br>
</div>

- **Ko-fi:** [ko-fi.com/chackrahunter](https://ko-fi.com/chackrahunter)
- **PayPal:** [paypal.me/Donsko2007](https://www.paypal.me/Donsko2007)

It helps without money too: ⭐ leave a star, report a bug, or tell someone.

---

## 🤝 Contributing

Bug reports and improvements are welcome — especially experience with **other
watch models**. If something behaves differently for you, open an issue with the
model, the Wear OS version and what happened.

---

<div align="center">

**MIT License** · see [LICENSE](LICENSE)

<sub>Built for a Galaxy Watch 6 Classic paired to an iPhone 14.</sub>

</div>
