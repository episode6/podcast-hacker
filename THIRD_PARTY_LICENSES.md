# Third-party license notices

Podcast Hacker itself is MIT-licensed
(<https://github.com/episode6/podcast-hacker/blob/main/LICENSE>). It is built with the
third-party components below. This document is also compiled into the app and shown on
its license-notices screen.

## Apache License 2.0

The app ships with the following libraries, used under the **Apache License 2.0**
(<https://www.apache.org/licenses/LICENSE-2.0>):

- **Kotlin** standard library and the **kotlinx** libraries (coroutines, serialization,
  datetime) — © JetBrains s.r.o. and Kotlin contributors — <https://kotlinlang.org>
- **Compose Multiplatform** — © JetBrains s.r.o. —
  <https://www.jetbrains.com/compose-multiplatform/>
- **AndroidX / Jetpack** (Compose UI, Activity, AppCompat, Core, Lifecycle, Navigation,
  Room, SQLite, Media3) — © The Android Open Source Project —
  <https://developer.android.com/jetpack>
- **Ktor** — © JetBrains s.r.o. — <https://ktor.io>
- **OkHttp** (Ktor's client engine on Android and desktop) — © Square, Inc. —
  <https://square.github.io/okhttp/>
- **Okio** — © Square, Inc. — <https://square.github.io/okio/>
- **Coil** — © Coil Contributors — <https://github.com/coil-kt/coil>
- **RSS Parser** — © Marco Gomiero — <https://github.com/prof18/RSS-Parser>
- **Metro** runtime — © Zac Sweers — <https://github.com/ZacSweers/metro>

## MIT License

The app ships with the following libraries, used under the **MIT License**
(<https://opensource.org/license/mit>):

- **redux-store-flow** — © episode6, Inc. —
  <https://github.com/episode6/redux-store-flow>
- **tacita** — © episode6, Inc. — <https://github.com/episode6/tacita>

## libvlc (VLC media engine) — desktop only

The desktop app bundles **libvlc** and its plugins from the
[VideoLAN VLC project](https://www.videolan.org/vlc/), used for audio playback.

- **License**: GNU Lesser General Public License v2.1 or later (LGPL 2.1+). The full
  license text ships alongside the libraries inside the app image
  (`lib/app/resources/vlc/COPYING.txt` / `COPYRIGHT.txt`).
- **Source code**: <https://code.videolan.org/videolan/vlc>
- **Dynamic linking / replaceability**: libvlc is loaded dynamically at runtime and is
  not statically linked into the application. You can substitute your own build of
  libvlc: delete or replace the bundled `vlc/` directory in the app image, or leave the
  app to fall back to a system VLC installation (it also honors the `VLC_PLUGIN_PATH`
  environment variable).
- libvlc is © the VideoLAN team and contributors; Podcast Hacker makes no modifications
  to it.

## JNA (Java Native Access) — desktop only

The bindings that let the app talk to libvlc use
[JNA](https://github.com/java-native-access/jna), which is dual-licensed under
Apache-2.0 / LGPL 2.1; Podcast Hacker uses it under the **Apache License 2.0**
(<https://www.apache.org/licenses/LICENSE-2.0>).
