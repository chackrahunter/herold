# Third-party libraries / Fremde Bibliotheken

None of these are in this repository — see
[docs/02-bibliotheken.md](docs/02-bibliotheken.md) for where to get each one.
This page records **which licence each one carries**, because that decides what
you may do with a built APK.

Keine dieser Bibliotheken liegt im Repo. Diese Seite hält fest, **unter welcher
Lizenz jede steht** — davon hängt ab, was mit einer gebauten APK erlaubt ist.

## herold/

| Library | Licence |
|---|---|
| **Samsung Health Sensor SDK** (`samsung-health-sensor.jar`, `.aar`) | Samsung proprietary — **may not be redistributed** ⚠ obtain it yourself from the Samsung Developer Portal |
| `androidx.annotation` | Apache-2.0 |
| `androidx.concurrent:concurrent-futures` | Apache-2.0 |
| `androidx.wear.protolayout` (+ `-expression`, `-proto`, `-external-protobuf`) | Apache-2.0 |
| `androidx.wear.tiles` (+ `-proto`) | Apache-2.0 |
| `com.google.guava:listenablefuture` | Apache-2.0 |

> [!WARNING]
> **The Samsung Health Sensor SDK may not be passed on, and apps using it need
> Samsung's approval before release.** The SDK licence says the licensee "must
> not, directly or indirectly, sell, redistribute, rent, lease, lend or
> sublicense all or any part of the SDK" (Appendix 1, 3.4), and that before any
> public release of an app built with it, "Licensee shall obtain prior key
> signing for the Service from SAMSUNG" (Appendix 1, 4.4(i)). This project has
> no such agreement. That is why **no prebuilt `herold` APK is offered** — build
> it yourself with your own copy of the SDK, under your own licence with Samsung.
>
> **Das Samsung Health Sensor SDK darf nicht weitergegeben werden, und Apps, die
> es nutzen, brauchen vor der Veröffentlichung Samsungs Zustimmung.** Deshalb
> liegt **keine fertige `herold`-APK** im Release — selbst bauen, mit der eigenen
> Kopie des SDK.

## markt/

| Library | Licence |
|---|---|
| **`com.auroraoss:gplayapi`** | **GPL-3.0** ⚠ |
| `org.jetbrains:annotations` | Apache-2.0 |
| `com.google.errorprone:error_prone_annotations` | Apache-2.0 |
| `com.google.code.gson:gson` | Apache-2.0 |
| `org.jetbrains.kotlin:kotlin-stdlib`, `kotlin-parcelize-runtime` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-coroutines-*` | Apache-2.0 |
| `org.jetbrains.kotlinx:kotlinx-serialization-*` | Apache-2.0 |
| `com.squareup.okhttp3:okhttp`, `com.squareup.okio:okio` | Apache-2.0 |
| `com.google.protobuf:protobuf-javalite` | BSD-3-Clause |

> [!WARNING]
> **`gplayapi` is GPL-3.0.** An APK that bundles it is a combined work and can
> only be distributed under the GPL-3.0 — not under this repository's MIT
> licence. That is why **no prebuilt `markt` APK is offered**: build it yourself
> from source, where the question does not arise for you as the end user.
>
> **`gplayapi` steht unter GPL-3.0.** Eine APK, die sie einbindet, ist ein
> verbundenes Werk und darf nur unter GPL-3.0 weitergegeben werden — nicht unter
> der MIT-Lizenz dieses Repos. Deshalb liegt **keine fertige `markt`-APK** im
> Release: selbst bauen, dann stellt sich die Frage für dich als Nutzer nicht.

## zifferblatt/

| Library | Licence |
|---|---|
| `com.google.android.support:wearable` | Apache-2.0 |

---

The source code in this repository is MIT (see [LICENSE](LICENSE)). The
libraries above keep their own licences; MIT covers only the code written here.
