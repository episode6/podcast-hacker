package com.episode6.podcasthacker

import okio.Path.Companion.toPath

// mirrors the jpackage packageName split in desktopApp/build.gradle.kts, so a
// side-by-side snapshot install keeps its data separate from the released app's
private val APP_DIR_NAME = if (BuildInfo.IS_SNAPSHOT) "PodcastHacker-SNAPSHOT" else "PodcastHacker"

actual fun PlatformContext.appDirs(): AppDirs {
    val os = System.getProperty("os.name").lowercase()
    val home = System.getProperty("user.home")
    return when {
        "mac" in os -> AppDirs(
            dataDir = "$home/Library/Application Support/$APP_DIR_NAME".toPath(),
            cacheDir = "$home/Library/Caches/$APP_DIR_NAME".toPath(),
        )
        "win" in os -> AppDirs(
            dataDir = (System.getenv("APPDATA") ?: "$home\\AppData\\Roaming").toPath() / APP_DIR_NAME,
            cacheDir = (System.getenv("LOCALAPPDATA") ?: "$home\\AppData\\Local").toPath() / APP_DIR_NAME / "cache",
        )
        else -> AppDirs(
            dataDir = (System.getenv("XDG_DATA_HOME")?.toPath() ?: "$home/.local/share".toPath()) / APP_DIR_NAME,
            cacheDir = (System.getenv("XDG_CACHE_HOME")?.toPath() ?: "$home/.cache".toPath()) / APP_DIR_NAME,
        )
    }
}
