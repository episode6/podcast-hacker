package com.episode6.podcasthacker.ui.util

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import com.episode6.podcasthacker.coroutines.ioDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

// AWT FileDialogs (native look, no new dependency). Launchers run on the AWT event
// thread (compose desktop's Main), where a modal dialog pumps events in a nested loop,
// so showing it inline is safe; only the file io hops off-thread.

@Composable
internal actual fun rememberOpmlImportLauncher(onImport: (String) -> Unit): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val currentOnImport by rememberUpdatedState(onImport)
    return {
        val dialog = FileDialog(null as Frame?, "Import OPML", FileDialog.LOAD)
        dialog.isVisible = true
        dialog.files.firstOrNull()?.let { file ->
            scope.launch {
                withContext(ioDispatcher) { runCatching { file.readText() }.getOrNull() }
                    ?.let { currentOnImport(it) }
            }
        }
    }
}

@Composable
internal actual fun rememberOpmlExportLauncher(content: () -> String): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val currentContent by rememberUpdatedState(content)
    return {
        val dialog = FileDialog(null as Frame?, "Export OPML", FileDialog.SAVE)
        dialog.file = OPML_EXPORT_FILE_NAME
        dialog.isVisible = true
        val directory = dialog.directory
        val fileName = dialog.file // null when cancelled
        if (directory != null && fileName != null) {
            val text = currentContent()
            scope.launch {
                withContext(ioDispatcher) { runCatching { File(directory, fileName).writeText(text) } }
            }
        }
    }
}
