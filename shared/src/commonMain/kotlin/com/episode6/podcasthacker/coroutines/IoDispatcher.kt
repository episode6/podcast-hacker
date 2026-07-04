package com.episode6.podcasthacker.coroutines

import kotlinx.coroutines.CoroutineDispatcher

/**
 * Dispatchers.IO isn't referencable from common code (the native variant is a
 * platform-only extension), so each platform supplies it here.
 */
internal expect val ioDispatcher: CoroutineDispatcher
