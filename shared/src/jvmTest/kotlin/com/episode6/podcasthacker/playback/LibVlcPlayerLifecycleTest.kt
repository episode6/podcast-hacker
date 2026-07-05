package com.episode6.podcasthacker.playback

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import okio.Path.Companion.toPath
import org.junit.Assume.assumeTrue
import kotlin.test.Test
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Drives the real libvlc engine end to end. Skipped unless PROBE_MP3 points at a local
 * audio file (CI runners don't have libvlc); run it manually after touching the binding:
 *
 * ```
 * ffmpeg -f lavfi -i "sine=frequency=440:duration=30" -q:a 9 /tmp/probe.mp3
 * PROBE_MP3=/tmp/probe.mp3 ./gradlew :shared:jvmTest --tests "*LibVlcPlayerLifecycleTest"
 * ```
 *
 * Uses the system VLC by default; to exercise a bundled layout instead, also set
 * `-Dcompose.application.resources.dir=<dir-containing-vlc/>` via PROBE_VLC_RESOURCES.
 */
class LibVlcPlayerLifecycleTest {

    @Test
    fun playPauseSeekSpeedEndRestartStop_onRealEngine(): Unit = runBlocking {
        val mp3Path = System.getenv("PROBE_MP3")
        assumeTrue("PROBE_MP3 not set; skipping real-engine lifecycle test", mp3Path != null)
        System.getenv("PROBE_VLC_RESOURCES")?.let {
            System.setProperty("compose.application.resources.dir", it)
        }

        val player = LibVlcPodcastPlayer()
        player.load(mp3Path!!.toPath(), PlaybackMetadata("probe-guid", "Probe Episode"))
        withTimeout(15.seconds) { player.state.first { it.status == PlayerStatus.Playing } }
        println("PROBE: loaded libvlc from ${com.sun.jna.NativeLibrary.getInstance("vlc").file}")
        println("PROBE: playing, duration=${player.state.value.duration}")

        delay(2500)
        val position = player.state.value.position
        println("PROBE: position after 2.5s = $position")
        check(position > Duration.ZERO) { "position should advance" }

        player.pause()
        withTimeout(5.seconds) { player.state.first { it.status == PlayerStatus.Paused } }
        println("PROBE: paused at ${player.state.value.position}")

        player.seekTo(20.seconds)
        withTimeout(5.seconds) { player.state.first { it.position >= 19.seconds } }
        println("PROBE: seeked to ${player.state.value.position}")

        player.setSpeed(2f)
        player.play()
        withTimeout(5.seconds) { player.state.first { it.status == PlayerStatus.Playing } }

        // ride out the rest of the 30s file at 2x from ~20s
        withTimeout(15.seconds) { player.state.first { it.status == PlayerStatus.Ended } }
        println("PROBE: ended at ${player.state.value.position}")

        player.play()
        withTimeout(10.seconds) {
            player.state.first { it.status == PlayerStatus.Playing && it.position < 10.seconds }
        }
        println("PROBE: restarted after end at ${player.state.value.position}")

        player.stop()
        withTimeout(5.seconds) { player.state.first { it.status == PlayerStatus.Idle } }
        check(player.state.value.episodeGuid == "probe-guid") { "guid retained after stop" }
        println("PROBE: stopped, guid retained")
    }
}
