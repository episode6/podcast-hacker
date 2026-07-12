# Podcast Hacker Changelog

### v1.0.0 - Unreleased

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
- Android snapshot builds now install side-by-side with the released app instead of
  overwriting it: they use the applicationId `com.episode6.snapshots.podcasthacker`
  and are labeled "PodcastHacker (SNAPSHOT)".
