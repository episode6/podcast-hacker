#!/usr/bin/env bash
# Syncs MARKETING_VERSION / CURRENT_PROJECT_VERSION in the iOS xcconfig from
# self.versions.toml (the version source of truth for all platforms).
#
# By default CURRENT_PROJECT_VERSION is pinned to the snapshot versionCode —
# the committed xcconfig represents snapshot builds, matching what android and
# desktop snapshot builds carry. CI passes --release when building from a tag
# to swap in the formula-derived code (that change is never committed).
#
# The versionCode formula lives in the root build.gradle.kts (the single source
# of truth); this script queries it via gradle.
#
# Usage: sync-ios-version.sh [--release]
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSIONS_FILE="$REPO_ROOT/self.versions.toml"
XCCONFIG="$REPO_ROOT/iosApp/Configuration/Config.xcconfig"

NAME="$(sed -n 's/^name = "\(.*\)"$/\1/p' "$VERSIONS_FILE")"

if [[ -z "$NAME" ]]; then
  echo "error: could not parse name from $VERSIONS_FILE" >&2
  exit 1
fi

if [[ "${1:-}" == "--release" ]]; then
  CODE_TASK="printReleaseVersionCode"
else
  CODE_TASK="printSnapshotVersionCode"
fi
CODE="$(cd "$REPO_ROOT" && ./gradlew -q "$CODE_TASK" | tail -n1)"
if ! [[ "$CODE" =~ ^[0-9]+$ ]]; then
  echo "error: unexpected output from ./gradlew $CODE_TASK: '$CODE'" >&2
  exit 1
fi
sed -i.bak \
  -e "s/^MARKETING_VERSION=.*/MARKETING_VERSION=$NAME/" \
  -e "s/^CURRENT_PROJECT_VERSION=.*/CURRENT_PROJECT_VERSION=$CODE/" \
  "$XCCONFIG"
rm -f "$XCCONFIG.bak"

echo "synced $XCCONFIG to MARKETING_VERSION=$NAME CURRENT_PROJECT_VERSION=$CODE"
