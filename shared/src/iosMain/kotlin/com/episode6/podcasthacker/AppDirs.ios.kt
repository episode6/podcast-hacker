package com.episode6.podcasthacker

import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSSearchPathDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual fun PlatformContext.appDirs(): AppDirs = AppDirs(
    dataDir = directoryPath(NSDocumentDirectory),
    cacheDir = directoryPath(NSCachesDirectory),
)

private fun directoryPath(directory: NSSearchPathDirectory): Path =
    (NSSearchPathForDirectoriesInDomains(directory, NSUserDomainMask, true).first() as String).toPath()
