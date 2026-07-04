package com.episode6.podcasthacker

import okio.Path.Companion.toOkioPath

actual fun PlatformContext.appDirs(): AppDirs = AppDirs(
    dataDir = context.filesDir.toOkioPath(),
    cacheDir = context.cacheDir.toOkioPath(),
)
