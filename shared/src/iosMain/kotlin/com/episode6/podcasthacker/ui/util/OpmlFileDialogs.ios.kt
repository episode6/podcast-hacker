package com.episode6.podcasthacker.ui.util

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberOpmlImportLauncher(onImport: (String) -> Unit): (() -> Unit)? = null

@Composable
internal actual fun rememberOpmlExportLauncher(content: () -> String): (() -> Unit)? = null
