#!/usr/bin/env bash
# Syncs MARKETING_VERSION / CURRENT_PROJECT_VERSION in the iOS xcconfig from
# self.versions.toml (the version source of truth for all platforms).
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSIONS_FILE="$REPO_ROOT/self.versions.toml"
XCCONFIG="$REPO_ROOT/iosApp/Configuration/Config.xcconfig"

NAME="$(sed -n 's/^name = "\(.*\)"$/\1/p' "$VERSIONS_FILE")"
CODE="$(sed -n 's/^code = "\(.*\)"$/\1/p' "$VERSIONS_FILE")"

if [[ -z "$NAME" || -z "$CODE" ]]; then
  echo "error: could not parse name/code from $VERSIONS_FILE" >&2
  exit 1
fi

sed -i.bak \
  -e "s/^MARKETING_VERSION=.*/MARKETING_VERSION=$NAME/" \
  -e "s/^CURRENT_PROJECT_VERSION=.*/CURRENT_PROJECT_VERSION=$CODE/" \
  "$XCCONFIG"
rm -f "$XCCONFIG.bak"

echo "synced $XCCONFIG to MARKETING_VERSION=$NAME CURRENT_PROJECT_VERSION=$CODE"
