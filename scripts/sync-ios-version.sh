#!/usr/bin/env bash
# Syncs MARKETING_VERSION / CURRENT_PROJECT_VERSION in the iOS xcconfig from
# self.versions.toml (the version source of truth for all platforms), plus the
# snapshot/release app identity (bundle id + display name + app icon).
#
# By default the xcconfig is pinned to the snapshot versionCode and the snapshot
# identity (com.episode6.snapshots.* / "... (SNAPSHOT)") — the committed xcconfig
# represents snapshot builds, matching what android and desktop snapshot builds
# carry, so snapshot installs sit side-by-side with the released app. CI passes
# --release when building from a tag to swap in the formula-derived code and the
# release identity (that change is never committed).
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
  BUNDLE_ID_PREFIX="com.episode6.podcasthacker"
  DISPLAY_NAME="PodcastHacker"
  APPICON_NAME="AppIcon"
else
  CODE_TASK="printSnapshotVersionCode"
  BUNDLE_ID_PREFIX="com.episode6.snapshots.podcasthacker"
  DISPLAY_NAME="PodcastHacker (SNAPSHOT)"
  APPICON_NAME="AppIcon-Snapshot"
fi
CODE="$(cd "$REPO_ROOT" && ./gradlew -q "$CODE_TASK" | tail -n1)"
if ! [[ "$CODE" =~ ^[0-9]+$ ]]; then
  echo "error: unexpected output from ./gradlew $CODE_TASK: '$CODE'" >&2
  exit 1
fi
# $(TEAM_ID) is a literal xcconfig variable reference, not shell substitution
sed -i.bak \
  -e "s/^MARKETING_VERSION=.*/MARKETING_VERSION=$NAME/" \
  -e "s/^CURRENT_PROJECT_VERSION=.*/CURRENT_PROJECT_VERSION=$CODE/" \
  -e "s/^PRODUCT_BUNDLE_IDENTIFIER=.*/PRODUCT_BUNDLE_IDENTIFIER=$BUNDLE_ID_PREFIX.PodcastHacker\$(TEAM_ID)/" \
  -e "s/^INFOPLIST_KEY_CFBundleDisplayName=.*/INFOPLIST_KEY_CFBundleDisplayName=$DISPLAY_NAME/" \
  -e "s/^ASSETCATALOG_COMPILER_APPICON_NAME=.*/ASSETCATALOG_COMPILER_APPICON_NAME=$APPICON_NAME/" \
  "$XCCONFIG"
rm -f "$XCCONFIG.bak"

echo "synced $XCCONFIG to MARKETING_VERSION=$NAME CURRENT_PROJECT_VERSION=$CODE"
echo "  PRODUCT_BUNDLE_IDENTIFIER=$BUNDLE_ID_PREFIX.PodcastHacker\$(TEAM_ID) DISPLAY_NAME=$DISPLAY_NAME APPICON=$APPICON_NAME"
