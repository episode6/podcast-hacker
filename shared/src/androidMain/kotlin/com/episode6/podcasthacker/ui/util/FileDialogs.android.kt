package com.episode6.podcasthacker.ui.util

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.episode6.podcasthacker.coroutines.ioDispatcher
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Storage Access Framework pickers: no storage permissions needed, and the user chooses
// the location. SAF dialogs are system ui with no title of ours, so [title] is unused.
// Stream io hops to the io dispatcher; callbacks land back on Main.

@Composable
internal actual fun rememberFileImportLauncher(title: String, onImport: (String) -> Unit): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentOnImport by rememberUpdatedState(onImport)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // user cancelled
        scope.launch {
            val text = withContext(ioDispatcher) {
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                }.getOrNull()
            }
            text?.let { currentOnImport(it) }
        }
    }
    // no type filter: exported files (.opml especially) rarely carry a mime type the
    // picker could match on
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
internal actual fun rememberFileExportLauncher(
    title: String,
    fileName: String,
    mimeType: String,
    content: () -> String,
): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentContent by rememberUpdatedState(content)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(mimeType)) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult // user cancelled
        val text = currentContent()
        scope.launch {
            withContext(ioDispatcher) {
                runCatching {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.encodeToByteArray()) }
                }
            }
        }
    }
    return { launcher.launch(fileName) }
}
