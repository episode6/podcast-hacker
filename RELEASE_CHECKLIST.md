# Release checklist

1. **Versions** — bump `name` AND `code` together in `self.versions.toml`
   (`name` is the human version, `code` is the android versionCode; both must move):

   ```toml
   [versions]
   name = "0.0.1"
   code = "1"
   ```

   Then run `scripts/sync-ios-version.sh` and commit the result (keeps
   `iosApp/Configuration/Config.xcconfig` MARKETING_VERSION in sync).

2. **Green main** — all PRs for the release merged; CI green on main.

3. **Sanity pass** — on at least android + desktop: subscribe, download (ads cut),
   play/pause/seek/speed, position resumes after restart. For the desktop pass use a
   packaged build (`./scripts/fetch-libvlc.sh` + `./start`) so the bundled libvlc path
   is what's exercised.

4. **Licenses** — THIRD_PARTY_LICENSES.md must match what's actually bundled
   (libvlc LGPL text ships inside the installers via fetch-libvlc.sh).

5. **Tag** — `git tag v<name> && git push origin v<name>`. CI builds deb/msi/dmg + debug
   APK and attaches them to an auto-created GitHub release (the iOS shard is best-effort
   and doesn't gate the release).

6. **Verify the release** — artifacts attached and carry the right version;
   `sudo apt install ./podcasthacker_<name>_amd64.deb` locally; sideload the APK
   (`adb install`). Remember the dmg/msi versions are mapped `0.x.y → 1.x.y`
   (jpackage rejects MAJOR==0 there) — the deb and APK carry the real version.
