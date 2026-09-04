package dev.aicli.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.aicli.app.BuildConfig
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.core.networking.GitHubReleaseResolver
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Result of asking GitHub whether a newer release than the running build exists. */
sealed class UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Available(val currentVersion: String, val latestVersion: String, val downloadUrl: String, val assetSize: Long) : UpdateCheckResult()
    data class Failed(val reason: String, val throwable: Throwable? = null) : UpdateCheckResult()
}

/** Progress of downloading a release APK that [UpdateCheckResult.Available] pointed at. */
sealed class UpdateDownloadState {
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : UpdateDownloadState()
    data object ReadyToInstall : UpdateDownloadState()
    data class Failed(val reason: String, val throwable: Throwable? = null) : UpdateDownloadState()
}

/**
 * Checks this app's own GitHub Releases for a newer build than [BuildConfig.VERSION_NAME], and
 * downloads + hands off to the system package installer when one exists. Same download-with-
 * progress shape as [dev.aicli.runtime.bootstrap.BootstrapManager] (streamed via
 * `HttpURLConnection` into `context.cacheDir`), reusing [GitHubReleaseResolver] rather than a
 * second GitHub client.
 */
class AppUpdateManager(private val context: Context) {
    private val repoOwner = "vabxsen"
    private val repoName = "SkullShell"

    suspend fun checkForUpdate(): UpdateCheckResult {
        val currentVersion = BuildConfig.VERSION_NAME
        val release = GitHubReleaseResolver.latestRelease(repoOwner, repoName).getOrElse {
            AppLog.w(LogCategory.NETWORK, "Update check failed: ${it.message}")
            return UpdateCheckResult.Failed(it.message ?: "Could not reach GitHub", it)
        }
        if (!isNewer(release.tag_name, currentVersion)) {
            return UpdateCheckResult.UpToDate(currentVersion)
        }
        val asset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: return UpdateCheckResult.Failed("Release ${release.tag_name} has no APK asset attached")
        return UpdateCheckResult.Available(currentVersion, release.tag_name, asset.browser_download_url, asset.size)
    }

    /** Downloads [downloadUrl] into the cache dir, then launches the system install prompt. */
    fun downloadAndInstall(downloadUrl: String, expectedSize: Long): Flow<UpdateDownloadState> = flow {
        val apkFile = File(context.cacheDir, "skullshell-update.apk")
        val connection = (URL(downloadUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 30_000
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw java.io.IOException("Download failed with HTTP $code for $downloadUrl")
            val total = connection.contentLengthLong.takeIf { it > 0 } ?: expectedSize

            connection.inputStream.use { input ->
                FileOutputStream(apkFile).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var downloaded = 0L
                    var lastEmit = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break
                        output.write(buffer, 0, read)
                        downloaded += read
                        if (downloaded - lastEmit > 512 * 1024 || downloaded == total) {
                            emit(UpdateDownloadState.Downloading(downloaded, total))
                            lastEmit = downloaded
                        }
                    }
                }
            }
            AppLog.i(LogCategory.NETWORK, "Downloaded update APK from $downloadUrl (${apkFile.length()} bytes)")
            promptInstall(apkFile)
            emit(UpdateDownloadState.ReadyToInstall)
        } catch (e: Exception) {
            AppLog.e(LogCategory.NETWORK, "Update download failed: ${e.stackTraceToString()}")
            emit(UpdateDownloadState.Failed(e.message ?: "Download failed", e))
        } finally {
            connection.disconnect()
        }
    }.flowOn(Dispatchers.IO)

    private fun promptInstall(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

    /** Loose semver comparison: strips a leading "v", compares dot-separated numeric parts. */
    private fun isNewer(latestTag: String, currentVersion: String): Boolean {
        val latest = latestTag.removePrefix("v").removePrefix("V")
        if (latest == currentVersion) return false
        val latestParts = latest.split(".").mapNotNull { it.toIntOrNull() }
        val currentParts = currentVersion.split(".").mapNotNull { it.toIntOrNull() }
        if (latestParts.isEmpty() || currentParts.isEmpty()) return latest != currentVersion
        val length = maxOf(latestParts.size, currentParts.size)
        for (i in 0 until length) {
            val l = latestParts.getOrElse(i) { 0 }
            val c = currentParts.getOrElse(i) { 0 }
            if (l != c) return l > c
        }
        return false
    }
}
