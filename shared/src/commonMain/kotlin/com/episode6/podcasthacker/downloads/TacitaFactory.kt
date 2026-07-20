package com.episode6.podcasthacker.downloads

import com.episode6.tacita.Tacita

/**
 * Builds [Tacita] instances with a caller-chosen log callback. Tacita's log lambda is
 * instance-wide (not per-download), so a download that wants its own log capture — the
 * snapshot-build download log shown on Now Playing — needs its own instance. Instances
 * are cheap: they all share the app's single [io.ktor.client.HttpClient].
 */
fun interface TacitaFactory {
    fun create(log: (String) -> Unit): Tacita
}
