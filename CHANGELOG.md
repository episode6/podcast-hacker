# Podcast Hacker Changelog

### v1.1.10 - Unreleased

- Fixed a flicker at the end of every episode download where the progress indicator
  briefly reverted to a download button before settling on the play button. The bug
  lived in the store (the in-flight download entry was cleared before Room's persisted
  downloaded flag reached the UI), so the same fix covers every surface that showed it:
  the podcast-detail episode list, the episode detail screen, recently played rows, and
  the now-playing/mini-player button. Downloads now show a terminal "finishing" progress
  state until the downloaded flag lands, then switch straight to play.
- **Ad-creative fingerprints** (tacita 0.0.4): every download now maintains a per-feed
  fingerprint store — ads the ad-diff proves and cuts are fingerprinted automatically,
  and when a known ad survives a later download (the sticky-fill blind spot) its
  boundaries come back as high-confidence skippable markers. Nothing is ever auto-cut
  or auto-skipped on a fingerprint match.
- **Confirm-ad button** on the Now Playing sheet (flag icon beside the skips filter):
  while the playhead sits between two ad-boundary markers, tapping it records the
  listener's confirmation that the bracketed range is an ad. Confirmed creatives are
  fingerprinted into the feed's store at tacita's strongest evidence tier, so future
  episodes carrying the same ad flag it for skipping. Once a confirmation is recorded,
  the flag icon tints the theme's primary color whenever the playhead is inside a
  confirmed range, and the button becomes a toggle: tapping again while inside the
  range reverses the ear-check, deleting the fingerprint from the store and clearing
  the mark. Confirmation failures (e.g. a range shorter than tacita's 5s floor) are
  logged and never disturb playback.
- **Ad fingerprints screen** (podcast grid's overflow menu): lists every subscribed
  feed's stored ad-creative fingerprints — provenance (ear-confirmed / diff-proven),
  the creative's runtime, encoded size, and content id — with a per-fingerprint delete
  button to revoke one (e.g. an ad confirmed in error). Deleting a fingerprint that is
  currently marked confirmed on the playing episode clears that mark too.
- Internal: `AdBoundary.Source` gains `Fingerprint`; per-feed store files live under
  `dataDir/fingerprints/` keyed by hashed feed url; `mavenLocal()` + the Sonatype
  snapshots repo are now scoped to all of `com.episode6.*` (snapshots only), so any
  locally-built or snapshot episode6 library can be developed against.
- While something is playing, every scrollable screen (podcast grid, podcast detail,
  recently played, add podcast search results, episode detail, license notices) now adds
  a trailing spacer the height of the mini player, so the end of the content can be
  scrolled clear of the Now Playing bar instead of being hidden beneath it. The spacer
  disappears when nothing is playing.
- Internal: raised the UI-test wait floor from 10s to 30s (and applied it to every wait,
  including the end-to-end test's). CI runs these tests concurrently with installer
  packaging on 2-core runners, and the resulting cpu starvation blew through a 10s wait
  on macos-latest (2026-07-19) after previously blowing through a 5s wait on
  windows-latest (2026-07-11). Waits return as soon as their condition holds, so the
  padding costs nothing on healthy runs.

### v1.1.0 - 2026-07-18

- The overflow (3-dots) menu gains a "Check for updates" option. Snapshot builds compare
  their embedded git sha against the latest commit on main and open that commit's first
  comment in the browser (where CI posts the APK download link; the bare commit page if
  it has no comments); release builds compare their version against the latest GitHub
  release and open its release page. While the lookup is in flight an alert dialog shows
  a cancelable progress spinner; when already current, the dialog says so and shows the
  running version (or short sha for snapshots).
- The Now Playing screen's skip-confidence slider now starts hidden behind its "skips"
  label, which shows the range of skip counts the filter can reach (e.g. `skips: 2-8`,
  or a single number when filtering can't change anything). Tapping the label reveals
  the slider (when there's a range to scrub through) and tapping it again hides it;
  while the slider is visible the label shows the current count as before.
- Android debug builds now use a yellow launcher-icon background (overriding both the
  orange release background and the dark snapshot background) so debug installs are
  distinguishable at a glance. Only affects the adaptive icon (API 26+); the legacy
  API 24/25 fallback PNGs are unchanged.
- Recently Played rows now show the same download detail as the episode-list rows:
  a queued icon while an episode waits for a download slot, then a determinate
  progress ring with byte progress (indeterminate while starting and while ads are
  being cut) instead of a single indefinite spinner for every in-flight state.
- Internal: the Now Playing sheet now derives its drag anchors from a layout modifier
  (mirroring material3's internal draggableAnchors) instead of mutating anchor state
  from a BoxWithConstraints subcomposition during the measure pass — Compose hygiene
  aimed at the flaky `performMeasureAndLayout called during measure layout` crash that
  hit the device-test suite on main. CI also retries the device tests once per run as
  a backstop against that known-flaky compose-test race.
- On a cold start, if an episode was played in a previous run, the now-playing bar now
  shows that episode by default (paused at its saved position) instead of starting
  hidden. Tapping play resumes the episode from where it left off.
- When the now-playing episode's file isn't downloaded (its download was deleted, or
  the play history came from a library import), the play buttons on the mini player
  bar and the Now Playing screen become download buttons — with the same live download
  detail as the episode-list rows (queued icon, then a progress ring) — instead of a
  play button that can't play anything.
- Importing a library backup that carries played episodes now pops up the now-playing
  bar with the most recently played of them (paused, offering a download) once the
  import lands, matching what a cold start would show — instead of the bar staying
  hidden until the next app restart. Imports without play history (e.g. OPML) don't
  touch the bar, and an actively playing episode is never interrupted.
- The mini player bar and Now Playing screen are now two faces of a single draggable
  sheet with a grab-handle pill at the top: drag the bar up to expand it into the full
  Now Playing UI and drag the screen back down to collapse it (tapping the bar, the new
  collapse chevron, and android's back gesture still work too, and the sheet tracks the
  finger with a cross-fade between the two layouts). Now Playing is no longer a
  navigation destination, so it never participates in the back stack; playing an
  episode expands the sheet, and Stop hides it in place instead of popping a screen.
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
