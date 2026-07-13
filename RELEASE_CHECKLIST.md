## Podcast Hacker Release Checklist

This mirrors the episode6 library repos' release process, except we deploy installers to
a GitHub release via CI (`build-installers.yml`) instead of publishing to sonatype.
Agent skills in [.agents/](./.agents) automate most of it (`release-branch-skill`,
`ship-release-skill`, `update-docs-skill`).

### Versioning

- `name` in `self.versions.toml` is the single source of truth: `MAJOR.MINOR.PATCH`.
  **Cutting a release branch bumps the patch by 10**, so regular releases land on
  multiples of 10 and the 9 values in between are reserved for hotfixing that release
  (`1.2.30` → hotfixes `1.2.31`–`1.2.39`, next release `1.2.40`). This keeps hotfixes
  visible in the name (distinct tags and artifact filenames) at the cost of one patch
  digit — 1000 releases per minor, 9 hotfixes per release.
- The android versionCode / iOS build number is **derived** from the name — never set it
  manually. Formula (mixed radix): `(MAJOR × 256 + MINOR) × 10000 + PATCH`, e.g.
  `1.2.30` → `2580030`, hotfix `1.2.31` → `2580031`, next release `1.2.40` → `2580040`.
  Newer versions always produce bigger codes, so older builds can never override newer
  ones. (The code isn't eyeball-decodable back into major/minor — the last 4 digits are
  the patch, the rest is `major × 256 + minor`.)
- Limits: major and minor max out at 255 (deliberately matching Windows MSI's
  ProductVersion caps; MSI's third field allows 65535 so patch is never a concern
  there), patch at 9999, and major must stay >= 1 (jpackage rejects MAJOR==0 for
  dmg/msi). The highest possible code — `255.255.9999` → 655,359,999 — sits well under
  Google Play's 2,100,000,000 versionCode cap.
- The formula lives in the root `build.gradle.kts` — the single source of truth. Release
  tooling queries it via `./gradlew -q printReleaseVersionCode` /
  `printSnapshotVersionCode` instead of reimplementing it.
- **No `-SNAPSHOT` suffixes** (jpackage requires plain numeric versions). Instead every
  build sets `BuildInfo.IS_SNAPSHOT = true` (generated into `shared` commonMain) except
  CI builds off a release tag (`GITHUB_REF=refs/tags/v*`). `main` always carries the
  *next* release's version, so the release branch inherits the correct version when cut.
- Snapshot builds hardcode their versionCode to `25,600,000` (v10.0.0's derived code)
  instead of using the formula: high enough that a snapshot installs over every prod
  build below v10 for the foreseeable future (`9.255.9999` derives to 25,599,999), low
  enough to leave schema wiggle room if a build with that code ever shipped by accident. (Consequence: installing a prod
  build over a snapshot requires an uninstall, and the hardcode must be revisited
  before v10.0.0 ships.) The committed iOS xcconfig also carries the snapshot build
  number; CI swaps in the release-derived code on tag builds
  (`scripts/sync-ios-version.sh --release`) — that swap is workspace-only and must
  never be committed.
- **Snapshot builds carry their own app identity** so they install side-by-side with
  release builds instead of overwriting them: the display name gains a ` (SNAPSHOT)`
  suffix and the android applicationId / macOS bundle id becomes
  `com.episode6.snapshots.podcasthacker` (see `selfAppName` / `selfAppId` in the root
  `build.gradle.kts`; the desktop jpackage packageName and jvm data dirs use
  `PodcastHacker-SNAPSHOT`). Snapshot builds also swap in their own app icon (the
  dark variant; releases carry the white-on-episode6-orange `#FF6600` one): android
  via `manifestPlaceholders` + `*_snapshot` mipmaps, desktop via the
  `PodcastHacker-SNAPSHOT.*` files in `desktopApp/icons`, iOS via the
  `AppIcon-Snapshot` asset catalog. The committed iOS xcconfig likewise carries the
  snapshot bundle id + display name + icon name, swapped to the release identity by
  the same `sync-ios-version.sh --release` workspace-only step on tag builds.

### Cut new Release Branch

1. Ensure main branch is green
2. `<VERSION>` = the current `name` in `self.versions.toml`
3. `git checkout -b release/v<VERSION>`
4. Push/track empty branch

### Version bump PRs

- Create 2 PRs
    - `[VERSION] Snapshot v<NEXT_VERSION>` points at `main`
        - Bump `name` in `self.versions.toml` (VITAL). Bump the **patch by 10** (e.g.
          `1.2.30` → `1.2.40`); major/minor bumps are an explicit human decision (and
          reset the lower segments to 0). Never hand out the 9 values between release
          patches — they're reserved for hotfixing the release below them. The
          versionCode derives automatically.
        - Run `scripts/sync-ios-version.sh` and commit the updated
          `iosApp/Configuration/Config.xcconfig`
        - Update `CHANGELOG.md`: add a new `### v<NEXT_VERSION> - Unreleased` section and
          stamp the outgoing `v<VERSION>` section with its release date
    - `[VERSION] Release v<VERSION>` points at new release branch
        - Stamp the release date on the `v<VERSION>` section of `CHANGELOG.md` and ensure
          all changes since the last release are documented
        - Verify `name` in `self.versions.toml` and `Config.xcconfig` are already
          consistent (no version change expected — main carried the right version at cut
          time)

### Harden Release Branch

- Sanity pass on at least android + desktop: subscribe, download (ads cut),
  play/pause/seek/speed, position resumes after restart. For the desktop pass use a
  packaged build (`./scripts/fetch-libvlc.sh` + `./start`) so the bundled libvlc path
  is what's exercised.
- Licenses: `THIRD_PARTY_LICENSES.md` must match what's actually bundled (libvlc LGPL
  text ships inside the installers via `fetch-libvlc.sh`).
- Fix any bugs on the `main` branch first then cherry-pick (via PR) into release branch

### Release

1. From the release branch: `./scripts/ship-release.py --output /tmp/release-result.json`
   — creates the GitHub release + tag `v<VERSION>` pointing at the release branch, with
   notes extracted from `CHANGELOG.md`
2. The tag push triggers `build-installers.yml`, which builds deb/msi/dmg + a signed
   release APK and attaches them to the release (the iOS shard is best-effort and doesn't
   gate the release)
3. Verify the release: artifacts attached and carry the right version;
   `sudo apt install ./podcasthacker_<name>_amd64.deb` locally; sideload the APK
   (`adb install`).

### Hotfixes

- We do not cut new release branches for hotfixes, instead we append to the affected
  release branch and add a new release tag
- All fixes (including hotfixes) should be applied to the `main` branch first whenever
  possible and cherry-picked onto the appropriate release-branch for a hotfix
- A hotfix needs its own version bump PR on the release branch: bump the patch by 1
  within the release's reserved range (e.g. `1.2.30` → `1.2.31`, up to `1.2.39` — 9
  hotfixes per release), run `scripts/sync-ios-version.sh`, and update `CHANGELOG.md`.
  No coordination with `main` is needed — the derived versionCodes keep ordering
  (main's `1.2.40` always outranks any `1.2.3x` hotfix)
