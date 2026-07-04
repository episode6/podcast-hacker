package com.episode6.podcasthacker

import okio.Path

/**
 * Standard directories the app can write to on this platform.
 */
data class AppDirs(
    val dataDir: Path,
    val cacheDir: Path,
)

expect fun PlatformContext.appDirs(): AppDirs
