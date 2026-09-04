<div align="center">

# ⌚ Herold

### Run a Galaxy Watch with an iPhone — no Google account, no Samsung phone

Notifications, health measurements, an app store of its own and custom watch
faces. All hand-built, all without Gradle.

<br>

[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg?style=for-the-badge)](LICENSE)
[![Wear OS](https://img.shields.io/badge/Wear%20OS-4%20to%206-4285F4?style=for-the-badge&logo=wearos&logoColor=white)](https://wearos.google.com/)
[![No Gradle](https://img.shields.io/badge/Build-without%20Gradle-success?style=for-the-badge)](docs/01-bauen-ohne-gradle.md)
[![No Google account](https://img.shields.io/badge/Google%20account-not%20needed-critical?style=for-the-badge)](docs/04-markt.md)

[![Java](https://img.shields.io/badge/JDK-21-orange?style=flat-square&logo=openjdk&logoColor=white)](#requirements)
[![Tested on](https://img.shields.io/badge/tested-Galaxy%20Watch%206%20Classic-black?style=flat-square&logo=samsung&logoColor=white)](#requirements)
[![iPhone](https://img.shields.io/badge/paired%20with-iPhone-lightgrey?style=flat-square&logo=apple&logoColor=white)](docs/03-herold.md)
[![Docs](https://img.shields.io/badge/docs-thorough-informational?style=flat-square)](#-documentation)

[![Ko-fi](https://img.shields.io/badge/Support_on-Ko--fi-FF5E5B?style=for-the-badge&logo=kofi&logoColor=white)](https://ko-fi.com/chackrahunter)
[![PayPal](https://img.shields.io/badge/Donate_with-PayPal-00457C?style=for-the-badge&logo=paypal&logoColor=white)](https://www.paypal.me/Donsko2007)

<br>

🇬🇧 **English** · [🇩🇪 Deutsch](README.md)

[**Set up the watch**](docs/00-uhr-einrichten.md) ·
[**Docs**](#-documentation) ·
[**Hard-won findings**](docs/07-erkenntnisse.md) ·
[**Support**](#-support-this-project)

</div>

> [!NOTE]
> The detailed documentation in `docs/` is written in German. The code, the
> build scripts and this overview are in English. If you get stuck on a German
> page, open an issue — I am happy to translate the parts people actually need.

---

## What this is

Samsung does not intend this combination to exist. A Galaxy Watch 4 or newer can
**officially only be set up and managed from an Android phone**. Paired with an
**iPhone** it is an expensive watch with no notifications, no app store and no
access to its own sensors.

This project makes it fully usable anyway — with **no Google account**, **no
Samsung phone** and **no Play Store app**.

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
Center Service) — no relay, no third-party app, no subscription.

Plus the sensors Samsung otherwise keeps behind its own app:

- **ECG** (30 s at 500 Hz) with waveform and analysis
- **Heart rate & rhythm**, with an honest rhythm assessment
- **Blood oxygen (SpO₂)**, **skin temperature**, **body composition**
- **Respiratory rate** derived from beat intervals
- **Pulse arrival time** from ECG + PPG
- Tiles, a history with a detail view, background measurements

</td>
<td width="50%" valign="top">

### 🛒 `markt/` — an app store without an account

Signs in to Google Play **anonymously** and installs apps directly on the watch.
No Google account anywhere.

- Deliberately lists **only Wear OS apps** and watch faces
- Catalogue built from Google's own Wear categories (~1000 entries)
- Search across all of Play
- Update check for everything you installed through it
- Installing a specific older version on purpose

</td>
</tr>
<tr>
<td width="50%" valign="top">

### 🎨 `zifferblatt/` — your own watch face

A complete, working example with original artwork: background, time, date, a
frugal ambient mode — and the trick for **making a face active over adb**.

</td>
<td width="50%" valign="top">

### 🔧 `werkzeuge/` — everyday tooling

- `uhr.sh` — find the watch and keep the connection alive
- `entruempeln.sh` — remove bloatware **reversibly**
- `apps.sh` — push, launch and check apps

</td>
</tr>
</table>

---

## 🚀 Quick start

> [!IMPORTANT]
> **No set-up watch yet?** A Galaxy Watch 4+ can officially only be set up from
> an Android phone. The way around it — documented by Samsung themselves — is in
> **[docs/00-uhr-einrichten.md](docs/00-uhr-einrichten.md)**. **Start there.**

```bash
git clone https://github.com/chackrahunter/herold.git
cd herold
```

**1. Fetch the libraries.** They are deliberately **not** in this repo (they
belong to other people). [docs/02-bibliotheken.md](docs/02-bibliotheken.md)
explains where to get each one. They go into `herold/libs/`, `markt/libs/` and
`zifferblatt/libs/`.

**2. Check the paths.** Every `build.sh` sets `SDK=` and `JAVA_HOME` at the top.

**3. Connect the watch** (turn on wireless debugging on the watch first):

```bash
./werkzeuge/uhr.sh --id      # finds the watch over mDNS
```

**4. Build and install:**

```bash
cd herold && ./build.sh --install
```

> [!TIP]
> The first build generates a signing key for you. It stays local and is **not**
> in the repo — otherwise anyone could sign updates for your app. Do back it up
> anyway: without it you can never update your own app again.

---

## 🧰 Requirements

**Hardware**

| | |
|---|---|
| Watch | Samsung Galaxy Watch 4 or newer — developed and used daily on a **Galaxy Watch 6 Classic (SM-R955F)**, Wear OS 4 through 6 |
| Phone | An **iPhone** for the notification bridge — or no phone at all |
| Computer | macOS or Linux to build on |

**Software**

| | |
|---|---|
| Android SDK | **Command line tools** (`aapt2`, `d8`, `zipalign`, `apksigner`) plus `platforms/android-33` |
| Java | **JDK 21** (sources are compiled to Java 17 bytecode) |
| adb | with **wireless debugging** to the watch |
| optional | `rsvg-convert` for the watch face artwork |

---

## 📚 Documentation

The pages below are in German; the headings tell you what each one covers.

| | What it covers |
|---|---|
| [**00 — Uhr einrichten**](docs/00-uhr-einrichten.md) | Getting past set-up without a Samsung phone. **Start here.** |
| [01 — Bauen ohne Gradle](docs/01-bauen-ohne-gradle.md) | The `aapt2 → javac → d8 → apksigner` chain and its traps |
| [02 — Bibliotheken](docs/02-bibliotheken.md) | Which JARs you need and where to get them |
| [03 — Herold](docs/03-herold.md) | The ANCS bridge, sensors, rhythm analysis, background measurements |
| [04 — Markt](docs/04-markt.md) | Anonymous Play sign-in, the Wear catalogue, installing, updates |
| [05 — Zifferblatt](docs/05-zifferblatt.md) | Building a watch face and making it active |
| [06 — Werkzeuge](docs/06-werkzeuge.md) | `uhr.sh`, `entruempeln.sh`, `apps.sh` |
| [**07 — Erkenntnisse**](docs/07-erkenntnisse.md) | **The valuable one:** things written down nowhere else that cost hours |

> [!NOTE]
> If you read only one page, read [**07 — Erkenntnisse**](docs/07-erkenntnisse.md).
> Among other things it explains why **every single** app install fails until you
> flip one hidden flag, and why someone else's iPhone cannot see the watch at all.

---

## ⚠️ Important notes

> [!CAUTION]
> **The health measurements are not a medical device.**
> Herold reads the sensors and computes values. It makes **no diagnosis**, names
> **no conditions** and gives **no all-clear**. The rhythm analysis deliberately
> only says whether the heartbeat was regular — never whether anything is
> "fine". If something feels wrong, see a doctor, not a watch.

**Debloating is reversible.** `entruempeln.sh` removes apps for the current user
only (`pm uninstall --user 0`). Nothing is deleted from the system partition.
Order: `--sichern` (back up) → `--probe` (dry run) → `--weg` (remove).

**System updates undo everything.** After a Wear OS update the removed bloatware
is back, wireless debugging is off and factory reset protection is re-enabled.
Just run the scripts again.

**Legal.** The code is MIT. It does use other people's services and libraries
(Google Play, the Samsung Health Sensor SDK, gplayapi) — check for yourself
whether your use matches their terms. Watch faces of protected characters belong
to their rights holders; there is none in here.

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
