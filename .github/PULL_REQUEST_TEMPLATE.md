<!--
Danke fürs Mitmachen. Deutsch oder Englisch — beides ist willkommen.
German or English, whichever you prefer.
-->

## What this changes

<!-- One or two sentences. If it fixes an issue: "Fixes #123". -->

## Which part

- [ ] `herold/` — notifications and sensors
- [ ] `markt/` — the app store
- [ ] `zifferblatt/` — the watch face
- [ ] `werkzeuge/` — the shell helpers
- [ ] `docs/` — documentation only

## Built and tested

This project builds **without Gradle**: `aapt2 → javac → d8 → zipalign → apksigner`,
driven by the `build.sh` inside each part. There is no build system that will
catch a mistake for you — a new library has to be added to **both** the `javac`
classpath and the `d8` call, or the app compiles fine and crashes on the watch.
See [docs/01-bauen-ohne-gradle.md](https://github.com/chackrahunter/herold/blob/master/docs/01-bauen-ohne-gradle.md).

- [ ] `./build.sh` runs through in every part I touched
- [ ] `./build.sh --selftest` passes (`herold/` and `markt/` — `zifferblatt/` only builds)
- [ ] I ran it on a real watch

Model and Wear OS version you tested on:

<!-- e.g. SM-R955F, Wear OS 5 — or "did not test on hardware", which is fine, just say so -->

## Checks

- [ ] No libraries, `.jar`/`.aar` files, keystores or build output committed — they are gitignored for a reason
- [ ] No personal data: no Bluetooth or MAC addresses, no IP addresses, no serial numbers, no health readings from a real person
- [ ] Anything I claim in the docs, I actually ran

<!--
Not sure whether the change is wanted? Open it anyway, or ask first in
Discussions. A pull request that needs a rewrite is still better than one
that never gets written.
-->
