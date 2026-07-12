package com.episode6.podcasthacker.ui.util

import androidx.compose.runtime.Composable

/**
 * A launcher that opens the platform's open-file dialog ([title] shown where the
 * platform supports one) and reads the chosen file's text into [onImport] (never
 * invoked on cancel), or null on platforms without file dialogs (ios, until the app
 * gets real device attention).
 */
@Composable
internal expect fun rememberFileImportLauncher(title: String, onImport: (String) -> Unit): (() -> Unit)?

/**
 * A launcher that opens the platform's save-file dialog (suggesting [fileName]) and
 * writes [content] — evaluated at save time — to the chosen location, or null on
 * platforms without file dialogs (ios). [mimeType] informs pickers that filter by type
 * (android's SAF); platforms without that concept ignore it.
 */
@Composable
internal expect fun rememberFileExportLauncher(
    title: String,
    fileName: String,
    mimeType: String,
    content: () -> String,
): (() -> Unit)?
