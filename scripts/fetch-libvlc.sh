#!/usr/bin/env bash
# Fetches libvlc (LGPL 2.1+) for the current platform into desktopApp/resources/<platform>/vlc,
# where compose desktop's appResources packaging picks it up. Run before packaging installers
# (CI does this; local `./start` builds fall back to a system VLC if this hasn't run).
#
# Layout produced:
#   linux/windows: vlc/{libvlc,libvlccore}.<ext> + vlc/plugins/ + license text
#   macos:         vlc/lib/*.dylib + vlc/plugins/ + license text  (mirrors VLC.app)
set -euo pipefail
cd "$(dirname "$0")/.."

VLC_VERSION="3.0.21"

case "$(uname -s)" in
    Linux)  PLATFORM_DIR="linux-x64" ;;
    Darwin) [ "$(uname -m)" = "arm64" ] && PLATFORM_DIR="macos-arm64" || PLATFORM_DIR="macos-x64" ;;
    MINGW*|MSYS*|CYGWIN*) PLATFORM_DIR="windows-x64" ;;
    *) echo "Unsupported platform: $(uname -s)" >&2; exit 1 ;;
esac

DEST="desktopApp/resources/$PLATFORM_DIR/vlc"
if [ -d "$DEST/plugins" ]; then
    echo "libvlc already present at $DEST"
    exit 0
fi
rm -rf "$DEST"
mkdir -p "$DEST"
WORK="$(mktemp -d)"
trap 'rm -rf "$WORK"' EXIT

case "$PLATFORM_DIR" in
    linux-x64)
        # distro build matches the runner's system libs; apt-get download needs no root
        (cd "$WORK" && apt-get download libvlc5 libvlccore9 vlc-plugin-base vlc-data > /dev/null)
        for deb in "$WORK"/*.deb; do dpkg-deb -x "$deb" "$WORK/root"; done
        LIBDIR="$WORK/root/usr/lib/x86_64-linux-gnu"
        # unversioned names so JNA finds them; sonames inside stay intact and resolve
        # because the discovery preloads libvlccore
        cp "$LIBDIR"/libvlc.so.5.* "$DEST/libvlc.so"
        cp "$LIBDIR"/libvlccore.so.9.* "$DEST/libvlccore.so"
        cp -r "$LIBDIR/vlc/plugins" "$DEST/plugins"
        cp "$WORK/root/usr/share/doc/libvlc5/copyright" "$DEST/COPYRIGHT.txt"
        # the debian copyright file references the LGPL rather than embedding it
        cp /usr/share/common-licenses/LGPL-2.1 "$DEST/COPYING.txt" 2>/dev/null \
            || curl -fsSL -o "$DEST/COPYING.txt" "https://code.videolan.org/videolan/vlc/-/raw/$VLC_VERSION/COPYING.LIB"
        ;;
    macos-arm64|macos-x64)
        curl -fsSL -o "$WORK/vlc.dmg" \
            "https://get.videolan.org/vlc/$VLC_VERSION/macosx/vlc-$VLC_VERSION-universal.dmg"
        # volume name contains spaces ("/Volumes/VLC media player") — take the full tail
        MOUNT="$(hdiutil attach "$WORK/vlc.dmg" -nobrowse -readonly | sed -n 's|.*\(/Volumes/.*\)|\1|p' | head -1)"
        # -L dereferences symlinks: plugins reference versioned dylib names via @loader_path
        cp -RL "$MOUNT/VLC.app/Contents/MacOS/lib" "$DEST/lib"
        cp -RL "$MOUNT/VLC.app/Contents/MacOS/plugins" "$DEST/plugins"
        cp "$MOUNT/VLC.app/Contents/Resources/English.lproj/../../COPYING.txt" "$DEST/COPYING.txt" 2>/dev/null \
            || cp "$MOUNT/VLC.app/COPYING.txt" "$DEST/COPYING.txt" 2>/dev/null \
            || curl -fsSL -o "$DEST/COPYING.txt" "https://code.videolan.org/videolan/vlc/-/raw/3.0.21/COPYING.LIB"
        hdiutil detach "$MOUNT" > /dev/null
        ;;
    windows-x64)
        curl -fsSL -o "$WORK/vlc.zip" \
            "https://get.videolan.org/vlc/$VLC_VERSION/win64/vlc-$VLC_VERSION-win64.zip"
        unzip -q "$WORK/vlc.zip" -d "$WORK"
        SRC="$WORK/vlc-$VLC_VERSION"
        cp "$SRC/libvlc.dll" "$SRC/libvlccore.dll" "$DEST/"
        cp -r "$SRC/plugins" "$DEST/plugins"
        cp "$SRC/COPYING.txt" "$DEST/COPYING.txt"
        ;;
esac

echo "libvlc $VLC_VERSION staged at $DEST"
