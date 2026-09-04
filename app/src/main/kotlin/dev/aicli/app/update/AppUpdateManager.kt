package dev.aicli.app.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import dev.aicli.app.BuildConfig
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.core.networking.GitHubReleaseResolver
import dev.aicli.core.networking.downloadFile
import dev.aicli.core.networking.ReleaseVersion
import kotlinx.coroutines.CancellationException
import android.content.pm.PackageManager
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/** Result of asking GitHub whether a newer release than the running build exists. */
sealed class UpdateCheckResult {
    data class UpToDate(val currentVersion: String) : UpdateCheckResult()
    data class Available(val currentVersion: String, val latestVersion: String, val downloadUrl: String, val assetSize: Long, val digest: String? = null) : UpdateCheckResult()
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
        if (!ReleaseVersion.isNewer(release.tag_name, currentVersion)) {
            return UpdateCheckResult.UpToDate(currentVersion)
        }
        val asset = release.assets.firstOrNull { it.name.endsWith(".apk") }
            ?: return UpdateCheckResult.Failed("Release ${release.tag_name} has no APK asset attached")
        return UpdateCheckResult.Available(currentVersion, release.tag_name, asset.browser_download_url, asset.size, asset.digest)
    }

    /** Downloads [downloadUrl] into the cache dir, then launches the system install prompt. */
    fun downloadAndInstall(downloadUrl: String, expectedSize: Long, expectedDigest: String? = null): Flow<UpdateDownloadState> = flow {
        val apkFile = File(context.cacheDir, "skullshell-update.apk")
        try {
            downloadFile(downloadUrl, apkFile, expectedSize, expectedDigest) { count, total ->
                emit(UpdateDownloadState.Downloading(count, total))
            }
            validateUpdate(apkFile)
            promptInstall(apkFile)
            emit(UpdateDownloadState.ReadyToInstall)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) {
            apkFile.delete()
            AppLog.e(LogCategory.NETWORK, "Update download failed: ${e.message}")
            emit(UpdateDownloadState.Failed(e.message ?: "Download failed", e))
        }
    }.flowOn(Dispatchers.IO)

    @Suppress("DEPRECATION")
    internal fun validateUpdate(apkFile: File) {
        val manager = context.packageManager
        val incoming = checkNotNull(manager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)) {
            "The downloaded file is not a valid Android package"
        }
        check(incoming.packageName == context.packageName) {
            "This release is for a different app variant. Install a matching release build to receive updates."
        }
        val current = manager.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        check(incoming.longVersionCode > current.longVersionCode) { "This package is not newer than the installed app" }
        val signers = current.signingInfo?.apkContentsSigners.orEmpty()
        val incomingInfo = checkNotNull(incoming.signingInfo) { "The update has no signing certificate" }
        val history = if (incomingInfo.hasMultipleSigners()) incomingInfo.apkContentsSigners else incomingInfo.signingCertificateHistory
        check(signers.isNotEmpty() && signers.all { signer -> history.any { it == signer } }) {
            "The update's signing certificate does not match this app"
        }
    }

    private fun promptInstall(apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }

}
