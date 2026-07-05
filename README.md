# Podcast Hacker

A podcast app that cuts the injected ads out of your episodes.

Podcast Hacker is a Kotlin Multiplatform podcast player (Android + Desktop first-class,
iOS best-effort) built around [tacita](https://github.com/episode6/tacita), which
downloads each episode twice and diffs the copies to find and remove dynamically-inserted
ads. Because tacita needs complete files to diff, playback is **download-first** — there
is no streaming.

## What it does

- Subscribe to podcasts via iTunes search or a pasted RSS url
- Download episodes through tacita's ad-cutting pipeline
- Play them with the usual comforts: seek, ±15/30s skips, playback speed, lock-screen /
  media-key controls, resume-from-position across restarts
- Background-safe downloads on Android (user-initiated data transfer jobs on api 34+,
  a foreground service on older versions)

## Platform notes

| Platform | Status | Playback engine |
|---|---|---|
| Android | first-class | Media3 ExoPlayer in a MediaSessionService |
| Desktop (Linux/macOS/Windows) | first-class | libvlc (bundled) via an in-repo JNA binding |
| iOS | kept compiling, best-effort | AVPlayer |

Desktop installers bundle [libvlc](https://www.videolan.org/vlc/libvlc.html) (LGPL
2.1+, dynamically loaded — see [THIRD_PARTY_LICENSES.md](./THIRD_PARTY_LICENSES.md)),
so end users need nothing extra installed. Dev builds without the staged bundle
(`scripts/fetch-libvlc.sh`) fall back to a system VLC installation. (JavaFX Media was
the original plan but its Linux backend can't decode against current ffmpeg, and vlcj
was rejected for being GPL v3 — see TODO.md for the archaeology.)

## Building & running

- Android app: `./gradlew :androidApp:assembleDebug`
- Desktop app: `./start` (builds + launches the distributable), or
  `./gradlew :desktopApp:run`
- iOS app: `scripts/sync-ios-version.sh`, then open [/iosApp](./iosApp) in Xcode
- Tests: `./gradlew check` (or per-target: `:shared:jvmTest`,
  `:shared:testAndroidHostTest`, `:shared:iosSimulatorArm64Test`)

Installers (deb/msi/dmg) and a debug APK are built by CI and attached to GitHub releases
on `v*` tags.

## How it's built

Shared Compose Multiplatform UI and business logic in [/shared](./shared/src), driven by
[redux-store-flow](https://github.com/episode6/redux-store-flow) (an app-scoped store
with side effects for all IO), Metro for DI, Room KMP for persistence, and ktor +
RSSParser for feeds. Per-platform entry points live in
[/androidApp](./androidApp), [/desktopApp](./desktopApp), and [/iosApp](./iosApp).

The full build plan, per-stage implementation notes, and accumulated gotchas live in
[TODO.md](./TODO.md).

## Credit

The whole point of this app is [tacita](https://github.com/episode6/tacita)'s
ad-cutting download pipeline; this is mostly a comfortable player wrapped around it.
