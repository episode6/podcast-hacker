# Third-party license notices

Podcast Hacker itself is MIT-licensed (see [LICENSE](./LICENSE)). The desktop
installers additionally bundle the following third-party components:

## libvlc (VLC media engine)

The desktop app bundles **libvlc** and its plugins from the
[VideoLAN VLC project](https://www.videolan.org/vlc/), used for audio playback.

- **License**: GNU Lesser General Public License v2.1 or later (LGPL 2.1+). The full
  license text ships alongside the libraries inside the app image
  (`lib/app/resources/vlc/COPYING.txt` / `COPYRIGHT.txt`).
- **Source code**: <https://code.videolan.org/videolan/vlc>
- **Dynamic linking / replaceability**: libvlc is loaded dynamically at runtime and is
  not statically linked into the application. You can substitute your own build of
  libvlc: delete or replace the bundled `vlc/` directory in the app image, or leave the
  app to fall back to a system VLC installation (it also honors the `VLC_PLUGIN_PATH`
  environment variable).
- libvlc is © the VideoLAN team and contributors; Podcast Hacker makes no modifications
  to it.

## JNA (Java Native Access)

The bindings that let the app talk to libvlc use
[JNA](https://github.com/java-native-access/jna), which is dual-licensed under
Apache-2.0 / LGPL 2.1; Podcast Hacker uses it under the **Apache License 2.0**
(<https://www.apache.org/licenses/LICENSE-2.0>).
