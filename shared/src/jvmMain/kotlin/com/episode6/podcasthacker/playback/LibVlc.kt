package com.episode6.podcasthacker.playback

import com.sun.jna.Callback
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * Minimal JNA binding to libvlc 3.x — just the surface [LibVlcPodcastPlayer] needs.
 * In-repo (rather than vlcj) because vlcj is GPL v3, which would make our distributed
 * binaries GPL; libvlc itself is LGPL 2.1+ and JNA is Apache-2.0 dual-licensed, so this
 * keeps the shipped app MIT-compatible.
 */
@Suppress("FunctionName")
internal interface LibVlc : Library {
    fun libvlc_new(argc: Int, argv: Array<String>?): Pointer?
    fun libvlc_errmsg(): String?

    fun libvlc_media_new_path(instance: Pointer, path: String): Pointer?
    fun libvlc_media_add_option(media: Pointer, option: String)
    fun libvlc_media_release(media: Pointer)

    fun libvlc_media_player_new(instance: Pointer): Pointer?
    fun libvlc_media_player_set_media(player: Pointer, media: Pointer?)
    fun libvlc_media_player_play(player: Pointer): Int
    fun libvlc_media_player_set_pause(player: Pointer, doPause: Int)
    fun libvlc_media_player_stop(player: Pointer)
    fun libvlc_media_player_set_time(player: Pointer, timeMs: Long)
    fun libvlc_media_player_set_rate(player: Pointer, rate: Float): Int
    fun libvlc_media_player_event_manager(player: Pointer): Pointer

    fun libvlc_event_attach(eventManager: Pointer, eventType: Int, callback: LibVlcEventCallback, userData: Pointer?): Int
}

/**
 * `libvlc_callback_t`. Runs on libvlc's event threads: implementations must not call
 * back into libvlc (documented deadlock hazard) — read the event struct + update app
 * state only.
 */
internal fun interface LibVlcEventCallback : Callback {
    fun invoke(event: Pointer, userData: Pointer?)
}

/**
 * `libvlc_event_t` layout on 64-bit platforms: `int type` (offset 0, 4 bytes padding),
 * `void* p_obj` (offset 8), event union (offset 16). TimeChanged/LengthChanged carry an
 * int64 as the union's first member. Reading the union directly avoids calling
 * `get_time`/`get_length` from the callback thread.
 */
internal fun Pointer.eventType(): Int = getInt(0)
internal fun Pointer.eventInt64Payload(): Long = getLong(16)

// libvlc_event_e values (stable libvlc 3.x ABI)
internal object LibVlcEvent {
    const val MediaPlayerPlaying = 260
    const val MediaPlayerPaused = 261
    const val MediaPlayerStopped = 262
    const val MediaPlayerEndReached = 265
    const val MediaPlayerEncounteredError = 266
    const val MediaPlayerTimeChanged = 267
    const val MediaPlayerLengthChanged = 273
}

/** posix setenv / windows _putenv_s, needed to point libvlc at a bundled plugin dir. */
@Suppress("FunctionName")
internal interface PosixC : Library {
    fun setenv(name: String, value: String, overwrite: Int): Int
}

@Suppress("FunctionName")
internal interface WindowsCrt : Library {
    fun _putenv_s(name: String, value: String): Int
}

internal fun setProcessEnv(name: String, value: String) {
    runCatching {
        if (System.getProperty("os.name").lowercase().contains("win")) {
            Native.load("msvcrt", WindowsCrt::class.java)._putenv_s(name, value)
        } else {
            Native.load("c", PosixC::class.java).setenv(name, value, 1)
        }
    }
}
