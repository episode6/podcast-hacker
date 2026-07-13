# Podcast Hacker Changelog

### v1.0.10 - Unreleased

- Snapshot builds now derive their versionCode from the git commit count (at HEAD's
  merge-base with main) instead of the hardcoded 25,600,000: snapshots installed from
  main can never be downgraded by an older main build, and branch/PR builds carry their
  closest main ancestor's code so a later main build installs right over them. iOS
  snapshots are pinned to build number 1 (the committed xcconfig can't track a
  per-commit value). Note: because the new codes are far lower, an already-installed
  android snapshot must be uninstalled once before a new snapshot build will install
  over it.

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
