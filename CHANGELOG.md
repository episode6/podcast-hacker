# Podcast Hacker Changelog

### v1.0.20 - Unreleased

- Opening the Now Playing screen from the mini player bar now slides the screen up
  from the bottom (and back down when leaving) while the bar's content fades out in
  place, instead of the default cross-fade between screens. The Now Playing screen's
  background is now the same grey as the bar (so it reads as the bar expanded), and
  screens carry an opaque background during transitions instead of sliding up
  see-through and flashing the root background in at the end.
- Now Playing cleanup: the transport controls collapse into a single row (skip to
  previous ad boundary, back 15s, play/pause, forward 30s, skip to next ad boundary)
  with the ad-boundary countdowns shown under the outer buttons, and the text glyphs
  (↺/▶/❚❚/⇤/⇥) are replaced with proper Material icons here and on the mini player
  bar.
- Snapshot app identity now follows the headache-tracker suffix pattern: the android
  applicationId / macOS + iOS bundle id is `com.episode6.podcasthacker.snapshot`
  (previously `com.episode6.snapshots.podcasthacker`), and android debug builds append
  a further `.debug` applicationIdSuffix so local/PR debug installs coexist with
  main's release-type snapshot APKs instead of clobbering them (or being blocked by a
  signature mismatch). Note: already-installed snapshot builds live under the old id
  and won't be updated by new builds — uninstall them once.
- CI: after building the APK, the Build Installers workflow now comments on the
  triggering PR (or commit, for pushes) with a download link for the APK artifact
  plus a QR code for installing it on a device. QR images are committed to the
  `episode6/qrcodes` repo and hot-linked via raw URLs (requires a
  `QRCODES_GITHUB_TOKEN` secret; without it the comment posts link-only).

### v1.0.10 - 2026-07-14

- Committed a shared debug keystore (`androidApp/debug.keystore`, standard debug
  credentials) and pointed the android debug signing config at it, so CI-built and
  local debug APKs share a signature and can overwrite each other on-device.
- Snapshot builds now derive their versionCode from the git commit count (at HEAD's
  merge-base with main) instead of the hardcoded 25,600,000: snapshots installed from
  main can never be downgraded by an older main build, and branch/PR builds carry their
  closest main ancestor's code so a later main build installs right over them. iOS
  snapshots are pinned to build number 1 (the committed xcconfig can't track a
  per-commit value). Note: because the new codes are far lower, an already-installed
  android snapshot must be uninstalled once before a new snapshot build will install
  over it.
- New verify-versions CI workflow fails any PR where a committed copy of the app
  version drifts from self.versions.toml: the iOS xcconfig (via a new
  `sync-ios-version.sh --verify` mode, which also catches an accidentally committed
  `--release` swap) and the required `### v<VERSION>` CHANGELOG section. Previously
  this was only checked at ship time.
- Change the app's accent color from Pocket-Casts-ish red to episode6 orange (`#FF6600`),
  matching the new release app icon; the red-tinted container/secondary theme roles are
  retinted to orange equivalents.

### v1.0.0 - 2026-07-12

- Initial release: subscribe, download (ads cut) and play podcasts on android + desktop
  (deb/msi/dmg installers), with a best-effort iOS app.
- Note: the desktop installers are still unsigned — only the android app (signed release
  APK) is prod-ready.
- Add refresh to the main podcast grid screen: pull-to-refresh on android, a toolbar
  button on desktop; while syncing, touch platforms show the pull indicator instead of
  the top loading bar.
- Order the podcast grid by most recently released episode (Recently Played and Add
  Podcast tiles stay at the bottom).
- Replace the "← Back" text button with an icon button, and fix a stale (dead) back
  button lingering on the podcast grid after returning from another screen.
- Snapshot builds now install side-by-side with the released app on every platform
  instead of overwriting it: they're labeled "PodcastHacker (SNAPSHOT)" and use a
  distinct app id (android applicationId / iOS + macOS bundle id
  `com.episode6.snapshots.podcasthacker`); desktop installers and jvm data dirs use
  `PodcastHacker-SNAPSHOT`.
- New release app icon on every platform: the podcast-waves glyph in white on an
  episode6-orange background. Snapshot builds keep the previous dark icon, so
  side-by-side installs are distinguishable at a glance.
- Add a "Third-party license notices" option to the podcast grid's overflow menu; it
  opens a screen rendering THIRD_PARTY_LICENSES.md (embedded at build time), which now
  covers all shipped libraries, not just the desktop-bundled libvlc/JNA.
- Recently Played: when an episode's file isn't downloaded, the row's play button
  becomes a live (re-)download button (with a spinner while the download runs) instead
  of greying out alongside the trash button.
- Android media notification: always show skip-back/skip-forward buttons that skip to
  the nearest ad boundary (falling back to the fixed 15s/30s seeks). Previously there
  was no forward button, and the back button restarted the episode from the beginning.
