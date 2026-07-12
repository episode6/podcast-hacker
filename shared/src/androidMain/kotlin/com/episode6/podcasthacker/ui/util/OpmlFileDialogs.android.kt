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
// the location. Stream io hops to the io dispatcher; callbacks land back on Main.

@Composable
internal actual fun rememberOpmlImportLauncher(onImport: (String) -> Unit): (() -> Unit)? {
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
    // opml files rarely carry a registered mime type, so no useful filter exists
    return { launcher.launch(arrayOf("*/*")) }
}

@Composable
internal actual fun rememberOpmlExportLauncher(content: () -> String): (() -> Unit)? {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val currentContent by rememberUpdatedState(content)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/xml")) { uri ->
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
    return { launcher.launch(OPML_EXPORT_FILE_NAME) }
}
