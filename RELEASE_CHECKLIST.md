## Podcast Hacker Release Checklist

This mirrors the episode6 library repos' release process, except we deploy installers to
a GitHub release via CI (`build-installers.yml`) instead of publishing to sonatype.
Agent skills in [.agents/](./.agents) automate most of it (`release-branch-skill`,
`ship-release-skill`, `update-docs-skill`).

### Versioning

- `name` in `self.versions.toml` is the single source of truth: `MAJOR.MINOR.PATCH`,
  where the **patch segment is reserved for hotfixing shipped releases** — normal
  releases always ship `X.Y.0`, and cutting a release branch bumps the minor.
- The android versionCode / iOS build number is **derived** from the name — never set it
  manually. Formula: concat `MAJOR | MINOR (4 digits) | PATCH (2 digits)`, e.g. `1.2.0`
  → `1000200`, hotfix `1.2.3` → `1000203`. Newer versions always produce bigger codes
  and hotfixes slot between minors, so older builds can never override newer ones.
- Limits: minor maxes out at 9999, patch at 99, and major must stay >= 1 (jpackage
  rejects MAJOR==0 for dmg/msi). The major has no fixed max — the derived code just has
  to stay within Google Play's versionCode cap of 2,100,000,000, which allows majors up
  to 2100 (`2100.0.0` is the exact ceiling). (Heads-up if we near it: Windows MSI caps
  its ProductVersion minor at 255, so bump the major before minor 256 while we still
  ship an msi.)
- The formula lives in the root `build.gradle.kts` — the single source of truth. Release
  tooling queries it via `./gradlew -q printReleaseVersionCode` /
  `printSnapshotVersionCode` instead of reimplementing it.
- **No `-SNAPSHOT` suffixes** (jpackage requires plain numeric versions). Instead every
  build sets `BuildInfo.IS_SNAPSHOT = true` (generated into `shared` commonMain) except
  CI builds off a release tag (`GITHUB_REF=refs/tags/v*`). `main` always carries the
  *next* release's version, so the release branch inherits the correct version when cut.
- Snapshot builds hardcode their versionCode to `10,000,000` (v10.0.0's derived code)
  instead of using the formula: high enough that a snapshot installs over every prod
  build below v10 for the foreseeable future, low enough to leave schema wiggle room if
  a build with that code ever shipped by accident. (Consequence: installing a prod
  build over a snapshot requires an uninstall, and the hardcode must be revisited
  before v10.0.0 ships.) The committed iOS xcconfig also carries the snapshot build
  number; CI swaps in the release-derived code on tag builds
  (`scripts/sync-ios-version.sh --release`) — that swap is workspace-only and must
  never be committed.

### Cut new Release Branch

1. Ensure main branch is green
2. `<VERSION>` = the current `name` in `self.versions.toml`
3. `git checkout -b release/v<VERSION>`
4. Push/track empty branch

### Version bump PRs

- Create 2 PRs
    - `[VERSION] Snapshot v<NEXT_VERSION>` points at `main`
        - Bump `name` in `self.versions.toml` (VITAL). Only minor-increment, with patch
          reset to 0 (e.g. `1.2.0` → `1.3.0`); major bumps are an explicit human
          decision and the patch is reserved for hotfixes. The versionCode derives
          automatically.
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
- A hotfix needs its own version bump PR on the release branch: bump the patch segment
  of `name` (e.g. `1.2.0` → `1.2.1`, max `99`), run `scripts/sync-ios-version.sh`, and
  update `CHANGELOG.md`. No coordination with `main` is needed — the derived versionCodes
  keep ordering (main's `1.3.0` always outranks any `1.2.x` hotfix)
