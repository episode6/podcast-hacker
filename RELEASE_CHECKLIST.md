## Podcast Hacker Release Checklist

This mirrors the episode6 library repos' release process, except we deploy installers to
a GitHub release via CI (`build-installers.yml`) instead of publishing to sonatype.
Agent skills in [.agents/](./.agents) automate most of it (`release-branch-skill`,
`ship-release-skill`, `update-docs-skill`).

**No `-SNAPSHOT` versions in this repo:** the version name feeds jpackage directly and
installers build on every push to `main`, so `main` always carries the *next* release's
plain numeric version. The release branch therefore inherits the correct version when cut.

### Cut new Release Branch

1. Ensure main branch is green
2. `<VERSION>` = the current `name` in `self.versions.toml`
3. `git checkout -b release/v<VERSION>`
4. Push/track empty branch

### Version bump PRs

- Create 2 PRs
    - `[VERSION] Snapshot v<NEXT_VERSION>` points at `main`
        - Bump `name` AND `code` together in `self.versions.toml` (VITAL — `code` is the
          android versionCode / iOS build number and must move with every `name` bump).
          Only patch-increment `name`; major/minor bumps are an explicit human decision.
        - Run `scripts/sync-ios-version.sh` and commit the updated
          `iosApp/Configuration/Config.xcconfig`
        - Update `CHANGELOG.md`: add a new `### v<NEXT_VERSION> - Unreleased` section and
          stamp the outgoing `v<VERSION>` section with its release date
    - `[VERSION] Release v<VERSION>` points at new release branch
        - Stamp the release date on the `v<VERSION>` section of `CHANGELOG.md` and ensure
          all changes since the last release are documented
        - Verify `name`/`code` in `self.versions.toml` and `Config.xcconfig` are already
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
   (`adb install`). Remember the dmg/msi versions are mapped `0.x.y → 1.x.y` (jpackage
   rejects MAJOR==0 there) — the deb and APK carry the real version.

### Hotfixes

- We do not cut new release branches for hotfixes, instead we append to the affected
  release branch and add a new release tag
- All fixes (including hotfixes) should be applied to the `main` branch first whenever
  possible and cherry-picked onto the appropriate release-branch for a hotfix
- A hotfix needs its own version bump PR on the release branch (`name` + `code` +
  xcconfig + `CHANGELOG.md`). The hotfix claims the next unreleased version (typically
  the one `main` currently carries) — bump `main` past it first via a normal
  `[VERSION] Snapshot` PR so `main`'s `name`/`code` always stay ahead of every shipped
  release
