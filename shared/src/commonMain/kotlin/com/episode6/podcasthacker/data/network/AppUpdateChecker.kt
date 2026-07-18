package com.episode6.podcasthacker.data.network

import com.episode6.podcasthacker.BuildInfo
import dev.zacsweers.metro.AppScope
import dev.zacsweers.metro.Inject
import dev.zacsweers.metro.SingleIn
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Checks github for a newer build of the app. Snapshot builds compare their embedded git
 * sha against the latest commit on main; when they differ we link to that commit's first
 * comment (CI posts an APK download link as a commit comment on every main build), falling
 * back to the commit itself if it has no comments. Release builds compare their version
 * name against the latest github release's tag and link to the release page.
 *
 * Uses unauthenticated github API calls (rate-limited to 60/hour per IP — plenty for a
 * user-initiated menu action). The response is decoded from text because the shared
 * [HttpClient] has no content negotiation installed.
 */
@Inject
@SingleIn(AppScope::class)
class AppUpdateChecker(private val httpClient: HttpClient) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun checkForUpdate(
        isSnapshot: Boolean = BuildInfo.IS_SNAPSHOT,
        currentSha: String = BuildInfo.GIT_SHA,
        currentVersionName: String = BuildInfo.VERSION_NAME,
    ): UpdateCheckResult = if (isSnapshot) {
        val latest = json.decodeFromString<GithubCommit>(
            httpClient.get("$API_BASE/commits/main").bodyAsText()
        )
        if (latest.sha == currentSha) {
            UpdateCheckResult.UpToDate(currentSha.take(SHORT_SHA_LENGTH))
        } else {
            val comments = json.decodeFromString<List<GithubCommitComment>>(
                httpClient.get("$API_BASE/commits/${latest.sha}/comments").bodyAsText()
            )
            UpdateCheckResult.UpdateAvailable(comments.firstOrNull()?.htmlUrl ?: latest.htmlUrl)
        }
    } else {
        val release = json.decodeFromString<GithubRelease>(
            httpClient.get("$API_BASE/releases/latest").bodyAsText()
        )
        if (release.tagName.removePrefix("v") == currentVersionName) {
            UpdateCheckResult.UpToDate(currentVersionName)
        } else {
            UpdateCheckResult.UpdateAvailable(release.htmlUrl)
        }
    }

    private companion object {
        const val API_BASE = "https://api.github.com/repos/episode6/podcast-hacker"
        const val SHORT_SHA_LENGTH = 7
    }
}

sealed interface UpdateCheckResult {
    /** [versionLabel] is the release version name, or the short git sha for snapshots. */
    data class UpToDate(val versionLabel: String) : UpdateCheckResult

    /** [url] is what to open: a commit comment, a bare commit, or a release page. */
    data class UpdateAvailable(val url: String) : UpdateCheckResult
}

@Serializable
private data class GithubCommit(
    val sha: String,
    @SerialName("html_url") val htmlUrl: String,
)

@Serializable
private data class GithubCommitComment(
    @SerialName("html_url") val htmlUrl: String,
)

@Serializable
private data class GithubRelease(
    @SerialName("tag_name") val tagName: String,
    @SerialName("html_url") val htmlUrl: String,
)
