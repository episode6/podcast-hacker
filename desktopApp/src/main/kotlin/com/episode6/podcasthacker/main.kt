package com.episode6.podcasthacker

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPlacement
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.WindowState
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.episode6.podcasthacker.inject.createAppGraph
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce
import okio.FileSystem
import okio.Path
import okio.SYSTEM
import okio.buffer
import java.util.Properties

@OptIn(FlowPreview::class)
fun main() {
    val appGraph = createAppGraph(PlatformContext())
    val windowStateFile = appGraph.appDirs.dataDir / "window-state.properties"
    application {
        val windowState = rememberWindowState(
            placement = WindowPlacement.Floating,
            position = WindowPosition.PlatformDefault,
            size = DpSize(1024.dp, 768.dp),
        ).also { restoreWindowState(windowStateFile, it) }
        // persist on change (debounced) so a crash/kill doesn't lose the layout
        LaunchedEffect(windowState) {
            snapshotFlow { windowStateSnapshot(windowState) }
                .debounce(1_000)
                .collect { FileSystem.SYSTEM.writeWindowState(windowStateFile, it) }
        }
        Window(
            onCloseRequest = {
                FileSystem.SYSTEM.writeWindowState(windowStateFile, windowStateSnapshot(windowState))
                exitApplication()
            },
            state = windowState,
            title = "PodcastHacker",
        ) {
            App(appGraph)
        }
    }
}

private data class WindowStateSnapshot(
    val maximized: Boolean,
    val x: Float?,
    val y: Float?,
    val width: Float,
    val height: Float,
)

private fun windowStateSnapshot(state: WindowState) = WindowStateSnapshot(
    maximized = state.placement == WindowPlacement.Maximized,
    x = state.position.takeIf { it.isSpecified }?.x?.value,
    y = state.position.takeIf { it.isSpecified }?.y?.value,
    width = state.size.width.value,
    height = state.size.height.value,
)

private fun restoreWindowState(file: Path, state: WindowState) {
    val fs = FileSystem.SYSTEM
    if (!fs.exists(file)) return
    runCatching {
        val props = Properties()
        fs.source(file).buffer().inputStream().use { props.load(it) }
        val width = props.getProperty("width")?.toFloatOrNull()
        val height = props.getProperty("height")?.toFloatOrNull()
        if (width != null && height != null && width > 200f && height > 200f) {
            state.size = DpSize(width.dp, height.dp)
        }
        val x = props.getProperty("x")?.toFloatOrNull()
        val y = props.getProperty("y")?.toFloatOrNull()
        if (x != null && y != null) state.position = WindowPosition(x.dp, y.dp)
        if (props.getProperty("maximized")?.toBoolean() == true) {
            state.placement = WindowPlacement.Maximized
        }
    }
}

private fun FileSystem.writeWindowState(file: Path, snapshot: WindowStateSnapshot) {
    runCatching {
        file.parent?.let { createDirectories(it) }
        val props = Properties()
        props.setProperty("maximized", snapshot.maximized.toString())
        snapshot.x?.let { props.setProperty("x", it.toString()) }
        snapshot.y?.let { props.setProperty("y", it.toString()) }
        props.setProperty("width", snapshot.width.toString())
        props.setProperty("height", snapshot.height.toString())
        sink(file).buffer().outputStream().use { props.store(it, "PodcastHacker window state") }
    }
}
