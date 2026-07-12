package com.episode6.podcasthacker.ui.util

import androidx.compose.runtime.Composable

/** Suggested to the save dialog by [rememberOpmlExportLauncher]. */
internal const val OPML_EXPORT_FILE_NAME = "subscriptions.opml"

/**
 * A launcher that opens the platform's open-file dialog and reads the chosen file's
 * text into [onImport] (never invoked on cancel), or null on platforms without file
 * dialogs (ios, until the app gets real device attention).
 */
@Composable
internal expect fun rememberOpmlImportLauncher(onImport: (String) -> Unit): (() -> Unit)?

/**
 * A launcher that opens the platform's save-file dialog (suggesting
 * [OPML_EXPORT_FILE_NAME]) and writes [content] — evaluated at save time — to the
 * chosen location, or null on platforms without file dialogs (ios).
 */
@Composable
internal expect fun rememberOpmlExportLauncher(content: () -> String): (() -> Unit)?
