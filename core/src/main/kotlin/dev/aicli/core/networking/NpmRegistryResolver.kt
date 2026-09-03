package dev.aicli.core.networking

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Resolves real npm registry metadata for a single package version — used instead of hardcoding
 * a version number or guessing a tarball URL shape, for providers whose actual binary we fetch
 * directly from the npm tarball rather than through `npm install` (see ClaudeProvider: npm's own
 * platform-detection breaks under Termux's Node, which reports `process.platform === "android"`).
 */
object NpmRegistryResolver {
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Dist(val tarball: String, val shasum: String? = null)

    @Serializable
    data class PackageVersion(val name: String, val version: String, val dist: Dist)

    suspend fun latestVersion(packageName: String): Result<PackageVersion> = withContext(Dispatchers.IO) {
        runCatching {
            val encoded = packageName.replace("/", "%2F")
            val text = HttpGet.text("https://registry.npmjs.org/$encoded/latest")
            json.decodeFromString(PackageVersion.serializer(), text)
        }
    }
}

/** Tiny shared GET helper so [NpmRegistryResolver] and [GitHubReleaseResolver] don't duplicate connection setup. */
internal object HttpGet {
    fun text(url: String): String {
        val connection = (java.net.URL(url).openConnection() as java.net.HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", "aicli-android")
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) {
                val err = connection.errorStream?.bufferedReader()?.readText().orEmpty()
                throw java.io.IOException("GET $url returned HTTP $code: $err")
            }
            return connection.inputStream.bufferedReader().readText()
        } finally {
            connection.disconnect()
        }
    }
}
