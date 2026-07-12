#!/usr/bin/env bash
# Syncs MARKETING_VERSION / CURRENT_PROJECT_VERSION in the iOS xcconfig from
# self.versions.toml (the version source of truth for all platforms).
# CURRENT_PROJECT_VERSION is derived from the name via scripts/version-code.py;
# MARKETING_VERSION is truncated to 3 segments (CFBundleShortVersionString
# doesn't allow a 4th/hotfix segment — the build number carries that ordering).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSIONS_FILE="$REPO_ROOT/self.versions.toml"
XCCONFIG="$REPO_ROOT/iosApp/Configuration/Config.xcconfig"

NAME="$(sed -n 's/^name = "\(.*\)"$/\1/p' "$VERSIONS_FILE")"

if [[ -z "$NAME" ]]; then
  echo "error: could not parse name from $VERSIONS_FILE" >&2
  exit 1
fi

CODE="$("$REPO_ROOT/scripts/version-code.py" "$NAME")"
MARKETING="$(cut -d. -f1-3 <<<"$NAME")"

sed -i.bak \
  -e "s/^MARKETING_VERSION=.*/MARKETING_VERSION=$MARKETING/" \
  -e "s/^CURRENT_PROJECT_VERSION=.*/CURRENT_PROJECT_VERSION=$CODE/" \
  "$XCCONFIG"
rm -f "$XCCONFIG.bak"

echo "synced $XCCONFIG to MARKETING_VERSION=$MARKETING CURRENT_PROJECT_VERSION=$CODE"
