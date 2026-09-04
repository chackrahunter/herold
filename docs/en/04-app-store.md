[Deutsche Fassung](../04-markt.md)

# 04 — Markt: an app store without a Google account

Without a Google account there is no Play Store on the watch — and therefore no
apps. Markt ("market") closes that gap: it signs in to Google Play
**anonymously** and installs apps straight onto the watch.

## How the anonymous sign-in works

Markt uses **gplayapi**, the same library as Aurora Store:

1. Markt collects the watch's real properties (33 mandatory fields: model,
   display, ABIs, system features …) — see `Geraet.java`.
2. These go to Aurora's **token service** (`https://auroraoss.com/api/auth`),
   which returns an anonymous Play account.
3. `AuthHelper.build(...)` turns that into a complete sign-in (device check-in,
   uploading the configuration).
4. The sign-in is stored locally, so that a new device is not registered with
   Google on every start.

Because the watch's real properties get uploaded, Play serves the **watch
variants** of apps by itself (for example the Samsung Browser for Wear OS).

## Showing only Wear OS apps — measured, not guessed

The obvious approaches do **not** work:

- The **native Play search** returns **zero results** under a watch profile.
- `restriction`, `compatibility` and even a successful delivery test do **not**
  distinguish between a phone app and a watch app (Play will happily deliver
  WhatsApp to the watch).

What does work reliably is **Google's own Wear category**:

- `/store/apps/category/ANDROID_WEAR` — apps for the watch
- `/store/apps/category/WATCH_FACE` — watch faces

Markt pages deeply through these categories using the web helpers and builds a
**catalog of ~1000 watch apps** from them, which is cached on the watch
(refreshed daily). The browse lists are fed from that catalog — no phone app can
end up there.

**Search** additionally goes through the Play web search, so that apps which are
not listed in the categories can be found as well.

## Installing

Through a `PackageInstaller` session (`Installer.java`, `Ladedienst.java`):
download → open session → write the APK into it → confirm.

**Once per app the user confirms on the watch** ("Do you want to install this
app?"). Android requires this of every store except the Play Store.

> **Important:** Without turning off Factory Reset Protection, **every**
> installation fails. See [07-findings.md](07-findings.md).

## Updates

"My apps" queries all self-installed apps from Play in **one** call
(`bulkDetails`), compares the versions and offers individual updates or "Update
all".

## Installing a specific version

`market://details?id=<paket>&vc=<versionCode>` installs an older version on
purpose — useful when the latest one requires a newer Wear OS than you have.

## Limits

- **Paid apps** do not work: without an account you cannot buy anything.
- Some apps Play does **not** deliver to an account-less device at all
  (`AppNotSupported`). Markt then shows "Not available for this watch".
- Apps that need **Google push (FCM)** only work in a limited way: without a
  Google device registration, no push messages arrive.
