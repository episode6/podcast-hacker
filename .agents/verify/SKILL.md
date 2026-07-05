---
name: verify
description: How to verify podcast-hacker changes end-to-end by driving the real Android app on a device/emulator (local feed server via adb reverse, subscribe/download/play flow, screenshots). Use when verifying UI or playback changes in this repo, or when asked to run/screenshot the Android app.
---

# Verifying podcast-hacker on Android

Shared Compose UI lives in `shared/`; the quickest real-runtime surfaces are the
Android app (device/emulator) and `:shared:jvmTest` (desktop compose UI integration
test — CI-grade, not a substitute for driving the app).

## Build + install

```bash
./gradlew :androidApp:installDebug          # android compile task is :shared:compileAndroidMain
adb shell am start -n com.episode6.podcasthacker/.MainActivity
adb shell pm clear com.episode6.podcasthacker   # reset to empty state
```

## Local feed the app can reach

No real network needed — serve a fixture feed from the host and reverse-forward:

```bash
mkdir feedsrv && cd feedsrv
cp <repo>/androidApp/src/androidTest/assets/episode.mp3 ep1.mp3   # real 30s mp3
# write feed.xml: rss channel w/ one item, enclosure url http://localhost:8888/ep1.mp3
# (mirror the FEED_XML fixture in androidApp/src/androidTest/.../AppDeviceIntegrationTest.kt)
python3 -m http.server 8888 &
adb reverse tcp:8888 tcp:8888
```

Then in the app: Add Podcast → type `http://localhost:8888/feed.xml` → "Subscribe to
RSS url" → tile appears → podcast → episode → Download (runs real tacita pipeline,
finishes in seconds) → Play (real MediaController/ExoPlayer). Back from Now Playing
shows the mini player bar.

## Driving + screenshots

- `adb shell input tap X Y`, `adb shell input text '...'`, screenshots via
  `adb exec-out screencap -p > shot.png`.
- Foldables (multiple displays) prepend a warning that corrupts the PNG; list ids with
  `adb shell dumpsys SurfaceFlinger --display-id` and use `screencap -p -d <id>`.
- Emulator gotcha: first text input can trigger a "Try out your stylus" sheet that
  swallows keystrokes — Cancel it, refocus the field, retype.
- Gesture nav check: `adb shell settings get secure navigation_mode` (2 = gestures).
- Landscape probe: `settings put system user_rotation 1` (and back to 0).

## Gotchas

- The feed's artwork URLs 404 by design — podcast tiles render as blank dark squares.
- The fixture episode is 30s; playback ends quickly, after which the mini bar shows a
  paused-at-end state.
- Physical devices on adb-over-wifi can drop mid-session; `adb reverse` must be re-run
  after any reconnect.
