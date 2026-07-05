package com.episode6.podcasthacker.playback

import com.sun.jna.Native
import com.sun.jna.NativeLibrary
import java.io.File

/**
 * Locates and loads libvlc, preferring the copy bundled in the app image and falling
 * back to a system VLC installation.
 *
 * Bundled layout (populated by `scripts/fetch-libvlc.sh`, packaged via compose's
 * appResources): `<resources>/vlc/{libvlc,libvlccore}.<ext>` + `vlc/plugins/`. Two
 * loader tricks make a relocated copy work:
 *  - libvlccore is loaded explicitly first, so libvlc's `libvlccore.so.9`-style
 *    DT_NEEDED resolves by soname to the already-loaded library instead of searching
 *    system paths;
 *  - `VLC_PLUGIN_PATH` is set (via setenv, before `libvlc_new` scans plugins) because
 *    linux distro builds bake an absolute plugin path that won't exist on end-user
 *    machines. This also keeps the LGPL replaceability story honest: users can point
 *    the app at their own libvlc build.
 */
internal object LibVlcDiscovery {

    val instance: LibVlc? by lazy { load() }

    private fun load(): LibVlc? {
        val bundled = bundledVlcDir()
        if (bundled != null && runCatching { loadFrom(bundled) }.isSuccess) {
            return Native.load("vlc", LibVlc::class.java)
        }
        return runCatching {
            systemVlcDirs().forEach { NativeLibrary.addSearchPath("vlc", it.absolutePath) }
            systemVlcDirs().forEach { NativeLibrary.addSearchPath("vlccore", it.absolutePath) }
            runCatching { NativeLibrary.getInstance("vlccore") }
            Native.load("vlc", LibVlc::class.java)
        }.getOrNull()
    }

    private fun loadFrom(vlcDir: File) {
        val plugins = File(vlcDir, "plugins")
        if (plugins.isDirectory) setProcessEnv("VLC_PLUGIN_PATH", plugins.absolutePath)
        // libs sit in vlcDir on linux/windows, vlcDir/lib in the macos VLC.app layout
        listOf(vlcDir, File(vlcDir, "lib")).filter { it.isDirectory }.forEach { dir ->
            NativeLibrary.addSearchPath("vlccore", dir.absolutePath)
            NativeLibrary.addSearchPath("vlc", dir.absolutePath)
        }
        NativeLibrary.getInstance("vlccore")
        NativeLibrary.getInstance("vlc")
    }

    /** compose desktop packages `desktopApp/resources/<platform>/` here. */
    private fun bundledVlcDir(): File? =
        System.getProperty("compose.application.resources.dir")
            ?.let { File(it, "vlc") }
            ?.takeIf { it.isDirectory }

    private fun systemVlcDirs(): List<File> {
        val os = System.getProperty("os.name").lowercase()
        return when {
            os.contains("mac") -> listOf(
                File("/Applications/VLC.app/Contents/MacOS/lib"),
            )
            os.contains("win") -> listOfNotNull(
                System.getenv("ProgramFiles")?.let { File(it, "VideoLAN/VLC") },
                System.getenv("ProgramFiles(x86)")?.let { File(it, "VideoLAN/VLC") },
            )
            else -> emptyList() // linux system installs are on the default loader path
        }.filter { it.isDirectory }
    }
}
