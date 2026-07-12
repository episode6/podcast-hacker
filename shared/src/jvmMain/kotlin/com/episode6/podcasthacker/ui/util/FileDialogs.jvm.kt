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
// so showing it inline is safe; only the file io hops off-thread. [mimeType] has no AWT
// equivalent and is ignored.

@Composable
internal actual fun rememberFileImportLauncher(title: String, onImport: (String) -> Unit): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val currentOnImport by rememberUpdatedState(onImport)
    return {
        val dialog = FileDialog(null as Frame?, title, FileDialog.LOAD)
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
internal actual fun rememberFileExportLauncher(
    title: String,
    fileName: String,
    mimeType: String,
    content: () -> String,
): (() -> Unit)? {
    val scope = rememberCoroutineScope()
    val currentContent by rememberUpdatedState(content)
    return {
        val dialog = FileDialog(null as Frame?, title, FileDialog.SAVE)
        dialog.file = fileName
        dialog.isVisible = true
        val directory = dialog.directory
        val chosenName = dialog.file // null when cancelled
        if (directory != null && chosenName != null) {
            val text = currentContent()
            scope.launch {
                withContext(ioDispatcher) { runCatching { File(directory, chosenName).writeText(text) } }
            }
        }
    }
}
