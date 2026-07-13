#!/usr/bin/env bash
# Verifies every committed copy of the app version stays in sync with
# self.versions.toml (the single source of truth). The android versionName /
# versionCode, desktop jpackage packageVersion, and generated BuildInfo all read
# the toml (or git) at build time and can't drift; the only committed duplicates
# that can are:
#   1. iosApp/Configuration/Config.xcconfig — checked via sync-ios-version.sh
#      --verify (MARKETING_VERSION, pinned snapshot CURRENT_PROJECT_VERSION, and
#      the snapshot app identity — also catches an accidentally committed
#      --release swap)
#   2. CHANGELOG.md — must contain a section heading for the current version
#      (ship-release.py extracts the release notes from it at ship time; catch
#      its absence early)
# Enforced in CI by .github/workflows/verify-versions.yml.
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

NAME="$(sed -n 's/^name = "\(.*\)"$/\1/p' "$REPO_ROOT/self.versions.toml")"
if [[ -z "$NAME" ]]; then
  echo "error: could not parse name from self.versions.toml" >&2
  exit 1
fi

"$REPO_ROOT/scripts/sync-ios-version.sh" --verify

# headings look like "### v1.0.10 - Unreleased"
if ! grep -q "^### v$NAME " "$REPO_ROOT/CHANGELOG.md"; then
  echo "error: CHANGELOG.md has no '### v$NAME' section for the current version" >&2
  echo "in self.versions.toml — add one (ship-release.py needs it for release notes)" >&2
  exit 1
fi
echo "verified CHANGELOG.md has a section for v$NAME"
