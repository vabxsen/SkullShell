package dev.aicli.core.networking

import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Resolves real GitHub Releases metadata (asset names + download URLs) so installers never
 * hardcode a version number that will silently rot — every provider installer and the
 * bootstrap manager ask GitHub what the current release actually contains, at install time.
 */
object GitHubReleaseResolver {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Asset(val name: String, val browser_download_url: String, val size: Long = 0, val digest: String? = null)

    @Serializable
    data class Release(val tag_name: String, val name: String? = null, val assets: List<Asset> = emptyList(), val prerelease: Boolean = false)

    /** The single latest release of [owner]/[repo], via GitHub's `/releases/latest` endpoint. */
    suspend fun latestRelease(owner: String, repo: String): Result<Release> = withContext(Dispatchers.IO) {
        fetchJson("https://api.github.com/repos/$owner/$repo/releases/latest")
    }

    /**
     * The most recent release whose tag matches [tagPrefix] (GitHub's release list is already
     * newest-first). Used for repos like termux-packages where bootstrap releases are
     * interleaved with unrelated package releases, so "/releases/latest" isn't reliable.
     */
    suspend fun latestReleaseWithTagPrefix(
        owner: String,
        repo: String,
        tagPrefix: String,
        perPage: Int = 30,
        excludePrerelease: Boolean = true,
    ): Result<Release> =
        withContext(Dispatchers.IO) {
            fetchJsonList("https://api.github.com/repos/$owner/$repo/releases?per_page=$perPage")
                .mapCatching { releases ->
                    val candidates = releases.filter { it.tag_name.startsWith(tagPrefix) }
                    (if (excludePrerelease) candidates.firstOrNull { !it.prerelease } else null)
                        ?: candidates.firstOrNull()
                        ?: error("No release with tag prefix '$tagPrefix' found in the last $perPage releases of $owner/$repo")
                }
        }

    private fun fetchJson(url: String): Result<Release> = runCatching {
        val text = get(url)
        json.decodeFromString(Release.serializer(), text)
    }

    private fun fetchJsonList(url: String): Result<List<Release>> = runCatching {
        val text = get(url)
        json.decodeFromString(kotlinx.serialization.builtins.ListSerializer(Release.serializer()), text)
    }

    private fun get(url: String): String {
        AppLog.d(LogCategory.NETWORK, "GET $url")
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", "aicli-android")
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                throw java.io.IOException("GitHub API $url returned HTTP $code: $err")
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
