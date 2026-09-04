package dev.aicli.provider.antigravity

import android.content.Context
import dev.aicli.core.filesystem.SafeFiles
import dev.aicli.provider.api.*
import dev.aicli.runtime.archive.TarGzExtractor
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.runtime.foreignlibc.*
import dev.aicli.terminal.runPtyCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import dev.aicli.core.networking.ReleaseVersion
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.serialization.json.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Uses the checksum-verified release manifest from Google's official CLI installer. */
class AntigravityInstaller(private val context: Context) : ProviderInstaller {
    private val env = TermuxEnvironment(context)
    private val libc = ForeignLibcRuntime(context)
    val binaryPath: File get() = File(env.homeDir, ".local/bin/agy")

    override fun install(): Flow<InstallEvent> = flow {
        try {
            val arch = when (env.termuxAbi) { "aarch64" -> "arm64"; "x86_64" -> "amd64"; else -> error("No Antigravity build for this architecture") }
            libc.install(LibcFlavor.GLIBC).collect { state ->
                if (state is ForeignLibcState.Failed) error(state.reason)
                if (state !is ForeignLibcState.Ready) emit(InstallEvent.Progress("Installing compatibility layer", null))
            }
            emit(InstallEvent.Progress("Resolving Antigravity release", 0.2f))
            val manifestUrl = "https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_$arch.json"
            val connection = connection(manifestUrl)
            val manifest = try { Json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonObject }
                finally { connection.disconnect() }
            val url = manifest.getValue("url").jsonPrimitive.content
            val checksum = manifest.getValue("sha512").jsonPrimitive.content
            check(URL(url).protocol == "https") { "Release URL must use HTTPS" }
            val archive = File(context.cacheDir, "antigravity-download.tar.gz")
            emit(InstallEvent.Progress("Downloading Antigravity", 0.35f))
            val download = connection(url)
            val digest = MessageDigest.getInstance("SHA-512")
            try { download.inputStream.use { input -> archive.outputStream().use { output ->
                val buffer = ByteArray(65536)
                while (true) { val n = input.read(buffer); if (n < 0) break; output.write(buffer, 0, n); digest.update(buffer, 0, n) }
            } } } finally { download.disconnect() }
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(checksum, ignoreCase = true)) { "Antigravity download checksum mismatch" }
            val staging = File(context.cacheDir, "antigravity-extract")
            check(SafeFiles.deleteTree(staging))
            staging.mkdirs()
            try {
                TarGzExtractor.extract(archive, staging)
                val binary = staging.resolve("antigravity")
                check(binary.isFile) { "Release did not contain the Antigravity executable" }
                binary.setExecutable(true, true)
                // Cache is bound explicitly: it is outside filesDir in a foreign root filesystem.
                val result = runPtyCommand(libc.wrapCommand(LibcFlavor.GLIBC, listOf(binary.absolutePath,"--version"), env.homeDir.absolutePath,
                    mapOf(context.cacheDir.absolutePath to context.cacheDir.absolutePath)), env.buildEnvironment(), env.homeDir.absolutePath)
                result.requireSuccess()
                check(Regex("\\d+\\.\\d+\\.\\d+").containsMatchIn(result.output)) { "Antigravity did not report a version" }
                binaryPath.parentFile?.mkdirs()
                val pending = File(binaryPath.parentFile, "agy.new")
                binary.copyTo(pending, overwrite = true)
                pending.setExecutable(true,true)
                Files.move(pending.toPath(), binaryPath.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } finally { SafeFiles.deleteTree(staging); archive.delete() }
            emit(InstallEvent.Completed)
        } catch (e: CancellationException) { throw e
        } catch (e: Exception) { emit(InstallEvent.Failed("install", e.message ?: "Antigravity install failed", e)) }
    }.flowOn(Dispatchers.IO)

    override fun uninstall(): Flow<InstallEvent> = flow {
        check(!binaryPath.exists() || binaryPath.delete()) { "Could not remove Antigravity" }
        emit(InstallEvent.Completed)
    }.flowOn(Dispatchers.IO)
    override suspend fun checkForUpdate(): ProviderState.UpdateAvailable? = withContext(Dispatchers.IO) {
        if (!binaryPath.exists() || !libc.isInstalled(LibcFlavor.GLIBC)) return@withContext null
        try {
            val current = runPtyCommand(libc.wrapCommand(LibcFlavor.GLIBC,
                listOf(binaryPath.absolutePath, "--version"), env.homeDir.absolutePath),
                env.buildEnvironment(), env.homeDir.absolutePath).requireSuccess()
            val version = Regex("\\d+\\.\\d+\\.\\d+").find(current)?.value ?: return@withContext null
            val arch = when (env.termuxAbi) { "aarch64" -> "arm64"; "x86_64" -> "amd64"; else -> return@withContext null }
            val connection = connection("https://antigravity-cli-auto-updater-974169037036.us-central1.run.app/manifests/linux_$arch.json")
            val latest = try { Json.parseToJsonElement(connection.inputStream.bufferedReader().use { it.readText() }).jsonObject.getValue("version").jsonPrimitive.content }
                finally { connection.disconnect() }
            if (ReleaseVersion.isNewer(latest, version)) ProviderState.UpdateAvailable(version, latest) else null
        } catch (e: CancellationException) { throw e
        } catch (_: Exception) { null }
    }
    private fun connection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply { connectTimeout=15_000; readTimeout=60_000 }
}
