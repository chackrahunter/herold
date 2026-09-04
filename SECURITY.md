# Security Policy

> **Auf Deutsch:** Diese Seite ist auf Englisch, damit sie möglichst viele erreicht.
> Sicherheitsmeldungen **auf Deutsch sind genauso willkommen**.

## Report privately, please

Do not open a public issue for a security problem.

1. Go to the **Security** tab of this repository and choose
   **Report a vulnerability**. That opens a private advisory that only you and I
   can read.
2. If that button is not there for you, open a normal issue that says *only*
   that you have a security report and how I can reach you privately — no
   details in the public thread.

Helpful in a report: which part it concerns (`herold`, `markt`, `zifferblatt`,
`werkzeuge`), the watch model and system version, what an attacker would need
(physical access? the same Wi-Fi? a paired phone? a nearby BLE device?), and the
steps to reproduce it. Test only on hardware you own.

---

## What you are dealing with

Read this before you spend real time on it: **this is a spare-time project by
one person**, built for one watch on one wrist. There is no company behind it,
no security team, no bounty and no CVE process.

I read what comes in and fix what I can, but **I do not promise a response
time** — not a short one and not a long one. Pretending otherwise would be worse
than saying it plainly. If a fix matters to you on a deadline, this is not a
dependency you should be relying on.

Fixes land on `master` and go out with the next release. Only the newest release
and current `master` get them; older tags are not patched.

---

## Deliberate, documented — not a vulnerability

The whole point of this project is getting past restrictions on a watch you own,
so a few things look alarming out of context. These are known and intended:

- **`secure_frp_mode` is turned off.** Without it *every* app installation on an
  account-less watch fails; see
  [docs/07-erkenntnisse.md](docs/07-erkenntnisse.md), item 1. It does weaken
  factory reset protection on your own watch. That is the trade, and it is
  written down.
- **`WRITE_SECURE_SETTINGS` and `REQUEST_INSTALL_PACKAGES`.** `markt` needs the
  second one to install at all, and the first one to clear `secure_frp_mode`
  itself at start-up instead of making you do it by hand. Both are granted over
  adb.
- **Anonymous Google Play sign-in.** `markt` signs in with no account, through
  gplayapi and a token service run by the Aurora project
  (`markt/src/de/doncalvin/markt/Play.java`). Whether that matches Play's terms
  is on whoever runs it — the README says the same.
- **The keystore passwords sit in plain text in each `build.sh`.** They guard a
  self-signed key that is generated on your own machine, for your own installs.
  The keystore is gitignored and never leaves your computer.
- **`werkzeuge/entruempeln.sh` removes system apps** for user 0 (reversibly),
  and **wireless debugging leaves adb open** on your network for as long as you
  leave it on. Both are the feature, not the bug.
- **A deliberately relaxed certificate date check.**
  `herold/src/de/doncalvin/herold/IconHolen.java` accepts a certificate that
  fails *only* on `notBefore`/`notAfter`, because a watch running without a
  phone often has the wrong clock. Issuer, signature and trust anchor are still
  verified normally, and this path is used only to fetch public app icons.
  If you can show that this exception reaches further than that, **that is a
  report I want.**

---

## Worth reporting

- Anything that lets another device on the network — or a BLE device in range —
  make the watch do something it should not. The ANCS/AMS parser in
  `AncsService.java` processes data that comes straight off the phone.
- Anything in `markt` that would let a manipulated download reach
  `PackageInstaller` as if it were genuine.
- **Anything that leaks personal data off the watch.** For reference on what
  leaves the device today: `herold`'s only outbound requests are an iTunes
  lookup by app bundle id and the icon image download that follows it
  (`IconHolen.java`); measurements are written to the app's own storage and are
  not uploaded anywhere.
- Shell scripts under `werkzeuge/` that could be made to run something you did
  not intend.
- A pinned library that is known-vulnerable. Versions and SHA-256 sums live in
  `markt/BIBLIOTHEKEN.txt` and [docs/02-bibliotheken.md](docs/02-bibliotheken.md).
  The real fix belongs upstream, but tell me so I can move the pin.

## Not in scope

- Vulnerabilities in Wear OS, One UI Watch, Google Play or the Samsung Health
  Sensor SDK — those belong to their owners, not here.
- Anything that already assumes an attacker with an adb shell on the watch, or a
  shell on your computer. At that point it is over regardless.
- "Wireless debugging is on", "FRP is off", "the app can install packages" — see
  the section above.

---

## After you report

I will confirm that it arrived. If a fix ships, the advisory and the release
note will say what it was, and you get credit under whatever name you like — or
none, if you prefer.

## One thing that is not a security report

The health measurements are **not a medical device**: no diagnosis, no condition
names, no all-clear. A value that comes out wrong is a bug and belongs in a
normal issue, not in a private advisory.
