package dev.aicli.provider.opencode

import android.content.Context
import android.os.Build
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.core.networking.GitHubReleaseResolver
import dev.aicli.provider.api.InstallEvent
import dev.aicli.provider.api.ProviderInstaller
import dev.aicli.provider.api.ProviderState
import dev.aicli.runtime.archive.TarGzExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

/**
 * Fetches OpenCode's real linux-arm64/linux-x64 binary straight from its GitHub Release rather
 * than going through its npm installer, which has a known, open Android/Termux bug (see
 * OpencodeCompatibility). GitHub org/repo per the upstream bug tracker link this project's
 * research turned up (anomalyco/opencode#12515) — OpenCode has moved between orgs before
 * (sst/opencode, opencode-ai/opencode), so re-verify this if resolution starts 404ing.
 */
class OpencodeInstaller(private val context: Context) : ProviderInstaller {
    private val installDir = File(context.filesDir, "providers/opencode")
    val binaryPath: File get() = File(installDir, "opencode")

    private val archKeyword: String?
        get() = when {
            "arm64-v8a" in Build.SUPPORTED_ABIS -> "arm64"
            "x86_64" in Build.SUPPORTED_ABIS -> "x64"
            else -> null
        }

    override fun install(): Flow<InstallEvent> = flow {
        val arch = archKeyword
        if (arch == null) {
            emit(InstallEvent.Failed("compatibility", "No supported CPU architecture for OpenCode's Linux builds"))
            return@flow
        }

        emit(InstallEvent.Progress("resolve", 0.1f, "Looking up the latest OpenCode release…"))
        val release = GitHubReleaseResolver.latestRelease("anomalyco", "opencode")
            .getOrElse {
                emit(InstallEvent.Failed("resolve", "Could not reach GitHub to resolve the OpenCode release: ${it.message}"))
                return@flow
            }

        val asset = release.assets.firstOrNull {
            it.name.contains("linux", ignoreCase = true) &&
                it.name.contains(arch, ignoreCase = true) &&
                (it.name.endsWith(".tar.gz") || it.name.endsWith(".zip"))
        } ?: run {
            emit(InstallEvent.Failed(
                "resolve",
                "Release ${release.tag_name} has no linux-$arch asset. Available: " +
                    release.assets.joinToString { it.name }.ifEmpty { "(none)" },
            ))
            return@flow
        }

        emit(InstallEvent.Progress("download", 0.3f, "Downloading ${asset.name}…"))
        installDir.mkdirs()
        val archiveFile = File(context.cacheDir, asset.name)
        try {
            downloadTo(asset.browser_download_url, archiveFile)
        } catch (e: Exception) {
            emit(InstallEvent.Failed("download", "Download failed: ${e.message}", e))
            return@flow
        }

        emit(InstallEvent.Progress("extract", 0.7f, "Extracting…"))
        val extractedNames = try {
            if (asset.name.endsWith(".zip")) extractZip(archiveFile, installDir) else TarGzExtractor.extract(archiveFile, installDir)
        } catch (e: Exception) {
            emit(InstallEvent.Failed("extract", "Failed to extract ${asset.name}: ${e.message}", e))
            return@flow
        } finally {
            archiveFile.delete()
        }

        val binary = extractedNames.map { File(installDir, it) }.firstOrNull { it.name == "opencode" && it.isFile }
        if (binary == null) {
            emit(InstallEvent.Failed("verify", "Extracted archive did not contain an 'opencode' binary (found: ${extractedNames.joinToString()})"))
            return@flow
        }
        if (binary.absolutePath != binaryPath.absolutePath) {
            binary.copyTo(binaryPath, overwrite = true)
        }
        binaryPath.setExecutable(true, false)

        AppLog.i(LogCategory.INSTALLER, "OpenCode ${release.tag_name} installed at ${binaryPath.absolutePath}")
        emit(InstallEvent.Completed)
    }.flowOn(Dispatchers.IO)

    private fun extractZip(zipFile: File, destinationDir: File): List<String> {
        val names = mutableListOf<String>()
        ZipInputStream(zipFile.inputStream().buffered()).use { zis ->
            var entry = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val outFile = File(destinationDir, entry.name)
                    outFile.parentFile?.mkdirs()
                    outFile.outputStream().use { out -> zis.copyTo(out) }
                    names += entry.name
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return names
    }

    private fun downloadTo(url: String, destination: File) {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = true
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("HTTP $code downloading $url")
            connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
        } finally {
            connection.disconnect()
        }
    }

    override fun uninstall(): Flow<InstallEvent> = flow {
        val ok = installDir.deleteRecursively()
        emit(if (ok) InstallEvent.Completed else InstallEvent.Failed("uninstall", "Could not remove ${installDir.absolutePath}"))
    }

    override suspend fun checkForUpdate(): ProviderState.UpdateAvailable? = null
}
