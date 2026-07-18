package com.episode6.podcasthacker.data.network

import assertk.assertThat
import assertk.assertions.containsExactly
import assertk.assertions.isEqualTo
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test

private const val OLD_SHA = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
private const val NEW_SHA = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"
private const val COMMIT_URL = "https://github.com/episode6/podcast-hacker/commit/$NEW_SHA"
private const val COMMENT_URL = "$COMMIT_URL#commitcomment-111"
private const val RELEASE_URL = "https://github.com/episode6/podcast-hacker/releases/tag/v1.0.30"

private val LATEST_COMMIT_JSON = """
{
  "sha": "$NEW_SHA",
  "html_url": "$COMMIT_URL",
  "commit": { "message": "some change" }
}
""".trimIndent()

private val COMMENTS_JSON = """
[
  { "id": 111, "html_url": "$COMMENT_URL", "body": "apk link" },
  { "id": 222, "html_url": "$COMMIT_URL#commitcomment-222", "body": "second" }
]
""".trimIndent()

private val LATEST_RELEASE_JSON = """
{
  "tag_name": "v1.0.30",
  "html_url": "$RELEASE_URL",
  "name": "1.0.30"
}
""".trimIndent()

class AppUpdateCheckerTest {

    private fun checkerRespondingWith(vararg bodiesByPath: Pair<String, String>): Pair<AppUpdateChecker, MockEngine> {
        val engine = MockEngine { request ->
            val body = bodiesByPath.toMap().getValue(request.url.encodedPath)
            respond(content = body, headers = headersOf(HttpHeaders.ContentType, "application/json"))
        }
        return AppUpdateChecker(HttpClient(engine)) to engine
    }

    @Test
    fun snapshot_behindMain_linksToFirstCommitComment() = runTest {
        val (checker, engine) = checkerRespondingWith(
            "/repos/episode6/podcast-hacker/commits/main" to LATEST_COMMIT_JSON,
            "/repos/episode6/podcast-hacker/commits/$NEW_SHA/comments" to COMMENTS_JSON,
        )

        val result = checker.checkForUpdate(isSnapshot = true, currentSha = OLD_SHA)

        assertThat(result).isEqualTo(UpdateCheckResult.UpdateAvailable(COMMENT_URL))
        assertThat(engine.requestHistory.map { it.url.host }.distinct()).containsExactly("api.github.com")
    }

    @Test
    fun snapshot_behindMain_noComments_linksToCommit() = runTest {
        val (checker, _) = checkerRespondingWith(
            "/repos/episode6/podcast-hacker/commits/main" to LATEST_COMMIT_JSON,
            "/repos/episode6/podcast-hacker/commits/$NEW_SHA/comments" to "[]",
        )

        val result = checker.checkForUpdate(isSnapshot = true, currentSha = OLD_SHA)

        assertThat(result).isEqualTo(UpdateCheckResult.UpdateAvailable(COMMIT_URL))
    }

    @Test
    fun snapshot_atHeadOfMain_reportsUpToDateWithShortSha() = runTest {
        val (checker, engine) = checkerRespondingWith(
            "/repos/episode6/podcast-hacker/commits/main" to LATEST_COMMIT_JSON,
        )

        val result = checker.checkForUpdate(isSnapshot = true, currentSha = NEW_SHA)

        assertThat(result).isEqualTo(UpdateCheckResult.UpToDate(NEW_SHA.take(7)))
        // no comment lookup when there's nothing to link to
        assertThat(engine.requestHistory.map { it.url.encodedPath })
            .containsExactly("/repos/episode6/podcast-hacker/commits/main")
    }

    @Test
    fun release_behindLatestRelease_linksToRelease() = runTest {
        val (checker, _) = checkerRespondingWith(
            "/repos/episode6/podcast-hacker/releases/latest" to LATEST_RELEASE_JSON,
        )

        val result = checker.checkForUpdate(isSnapshot = false, currentVersionName = "1.0.20")

        assertThat(result).isEqualTo(UpdateCheckResult.UpdateAvailable(RELEASE_URL))
    }

    @Test
    fun release_matchingLatestTag_reportsUpToDateWithVersion() = runTest {
        val (checker, _) = checkerRespondingWith(
            "/repos/episode6/podcast-hacker/releases/latest" to LATEST_RELEASE_JSON,
        )

        val result = checker.checkForUpdate(isSnapshot = false, currentVersionName = "1.0.30")

        assertThat(result).isEqualTo(UpdateCheckResult.UpToDate("1.0.30"))
    }
}
