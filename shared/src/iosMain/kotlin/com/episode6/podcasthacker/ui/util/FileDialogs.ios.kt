package com.episode6.podcasthacker.ui.util

import androidx.compose.runtime.Composable

@Composable
internal actual fun rememberFileImportLauncher(title: String, onImport: (String) -> Unit): (() -> Unit)? = null

@Composable
internal actual fun rememberFileExportLauncher(
    title: String,
    fileName: String,
    mimeType: String,
    content: () -> String,
): (() -> Unit)? = null
