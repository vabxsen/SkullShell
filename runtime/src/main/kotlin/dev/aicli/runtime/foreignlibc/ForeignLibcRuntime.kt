package dev.aicli.runtime.foreignlibc

import android.content.Context
import dev.aicli.core.filesystem.SafeFiles
import dev.aicli.runtime.archive.TarGzExtractor
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.terminal.runPtyCommand
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

enum class LibcFlavor { MUSL, GLIBC }
sealed class ForeignLibcState {
    data object NotInstalled : ForeignLibcState()
    data class Downloading(val bytesDownloaded: Long, val totalBytes: Long) : ForeignLibcState()
    data class Extracting(val filesExtracted: Int) : ForeignLibcState()
    data object Ready : ForeignLibcState()
    data class Failed(val reason: String) : ForeignLibcState()
}

/** Official Alpine/Ubuntu Base root filesystems, verified before being marked usable. */
class ForeignLibcRuntime(private val context: Context) {
    private val env = TermuxEnvironment(context)
    private fun rootfsDir(flavor: LibcFlavor) = File(context.filesDir, "foreign/${flavor.name.lowercase()}")
    fun isInstalled(flavor: LibcFlavor): Boolean {
        val root = rootfsDir(flavor)
        return File(root, ".skullshell-ready").isFile &&
            (flavor != LibcFlavor.MUSL || File(root, "usr/lib/libstdc++.so.6").isFile)
    }

    fun install(flavor: LibcFlavor): Flow<ForeignLibcState> = flow {
        mutex.withLock {
            if (isInstalled(flavor)) { emit(ForeignLibcState.Ready); return@flow }
            val root = rootfsDir(flavor)
            try {
                val (url, checksum) = resolveArchive(flavor)
                val archive = File(context.cacheDir, url.substringAfterLast('/'))
                download(url, archive) { bytes, total -> emit(ForeignLibcState.Downloading(bytes, total)) }
                val actual = archive.inputStream().use { input ->
                    val digest = MessageDigest.getInstance("SHA-256")
                    val buffer = ByteArray(65536)
                    while (true) { val count = input.read(buffer); if (count < 0) break; digest.update(buffer, 0, count) }
                    digest.digest().joinToString("") { "%02x".format(it) }
                }
                check(actual.equals(checksum, ignoreCase = true)) { "Root filesystem checksum mismatch" }
                check(SafeFiles.deleteTree(root))
                root.mkdirs()
                val files = TarGzExtractor.extract(archive, root, allowLinks = true)
                emit(ForeignLibcState.Extracting(files.size))
                archive.delete()
                val result = runPtyCommand(env.prootCommand(listOf("/bin/sh", "-c", "printf runtime-ok"), env.homeDir.absolutePath, root),
                    env.buildEnvironment(), env.homeDir.absolutePath)
                check(result.exitCode == 0 && result.output.contains("runtime-ok")) { "Compatibility layer failed: ${result.output}" }
                if (flavor == LibcFlavor.MUSL) {
                    // Fetch/extract through apk's signature checks without package scripts or
                    // database replacement, which require syscalls Android does not provide.
                    val dependencies = File(root, "tmp/skullshell-dependencies").apply { mkdirs() }
                    suspend fun apk(args: List<String>) = runPtyCommand(env.prootCommand(
                        listOf("/sbin/apk", "--no-progress") + args, env.homeDir.absolutePath, root),
                        env.buildEnvironment(), env.homeDir.absolutePath, timeoutMillis = 180_000).requireSuccess()
                    val fetchOutput = apk(listOf("fetch", "--no-cache", "--recursive", "--url", "--simulate", "libstdc++", "libgcc"))
                    val urls = Regex("https://[^\\s]+\\.apk").findAll(fetchOutput).map { it.value }.distinct().toList()
                    check(urls.isNotEmpty()) { "Could not resolve C++ runtime packages: $fetchOutput" }
                    // apk's atomic download uses linkat(AT_EMPTY_PATH), denied to Android apps.
                    // Stream through Java, then let apk verify the package signatures on extraction.
                    for (packageUrl in urls) dev.aicli.core.networking.downloadFile(packageUrl,
                        File(dependencies, packageUrl.substringAfterLast('/')))
                    val packages = dependencies.listFiles().orEmpty().filter { it.extension == "apk" }
                    check(packages.isNotEmpty()) { "No C++ runtime packages were downloaded: $fetchOutput" }
                    for (file in packages) {
                        apk(listOf("verify", "/tmp/skullshell-dependencies/${file.name}"))
                        apk(listOf("extract", "--force-overwrite", "--no-chown", "--destination", "/", "/tmp/skullshell-dependencies/${file.name}"))
                    }
                    check(File(root, "usr/lib/libstdc++.so.6").isFile) { "The C++ runtime is missing" }
                    SafeFiles.deleteTree(dependencies)
                }
                File(root, ".skullshell-ready").writeText(url)
                emit(ForeignLibcState.Ready)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) { emit(ForeignLibcState.Failed(e.message ?: "Compatibility layer setup failed")) }
        }
    }.flowOn(Dispatchers.IO)

    fun wrapCommand(flavor: LibcFlavor, command: List<String>, workingDirectory: String,
                    extraBindMounts: Map<String, String> = emptyMap()): List<String> {
        check(isInstalled(flavor)) { "Compatibility layer is not installed" }
        return env.prootCommand(command, workingDirectory, rootfsDir(flavor), extraBindMounts)
    }

    private fun resolveArchive(flavor: LibcFlavor): Pair<String, String> {
        val arch = when (env.termuxAbi) {
            "aarch64" -> if (flavor == LibcFlavor.MUSL) "aarch64" else "arm64"
            "x86_64" -> if (flavor == LibcFlavor.MUSL) "x86_64" else "amd64"
            "arm" -> "armhf"
            else -> error("Unsupported architecture")
        }
        val directory = if (flavor == LibcFlavor.MUSL) "https://dl-cdn.alpinelinux.org/alpine/latest-stable/releases/$arch/"
            else "https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/"
        val pattern = if (flavor == LibcFlavor.MUSL) Regex("alpine-minirootfs-([0-9.]+)-$arch\\.tar\\.gz")
            else Regex("ubuntu-base-([0-9.]+)-base-$arch\\.tar\\.gz")
        val name = pattern.findAll(getText(directory)).map { it.value }.distinct().maxWithOrNull(Comparator { a, b ->
            val av = pattern.matchEntire(a)!!.groupValues[1].split('.').map(String::toInt)
            val bv = pattern.matchEntire(b)!!.groupValues[1].split('.').map(String::toInt)
            var result = 0
            for (i in 0 until maxOf(av.size,bv.size)) { result = av.getOrElse(i){0}.compareTo(bv.getOrElse(i){0}); if (result != 0) break }
            result
        }) ?: error("No official root filesystem available for $arch")
        val sums = getText(directory + if (flavor == LibcFlavor.MUSL) "$name.sha256" else "SHA256SUMS")
        val sha = sums.lineSequence().firstOrNull { it.trim().endsWith(name) }?.trim()?.substringBefore(' ')
            ?: error("No checksum published for $name")
        check(Regex("[a-fA-F0-9]{64}").matches(sha))
        return "$directory$name" to sha
    }

    private fun connection(url: String) = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000; readTimeout = 30_000
    }
    private fun getText(url: String): String {
        val connection = connection(url)
        return try { connection.inputStream.bufferedReader().use { it.readText() } } finally { connection.disconnect() }
    }
    private suspend fun download(url: String, file: File, progress: suspend (Long, Long) -> Unit) {
        val connection = connection(url)
        try {
            check(connection.responseCode in 200..299) { "Download failed: HTTP ${connection.responseCode}" }
            val total = connection.contentLengthLong
            connection.inputStream.use { input -> file.outputStream().use { output ->
                val buffer = ByteArray(65536)
                var count = 0L
                var last = 0L
                while (true) {
                    val n = input.read(buffer); if (n < 0) break
                    output.write(buffer,0,n); count += n
                    if (count - last >= 512 * 1024 || count == total) { progress(count,total); last=count }
                }
                check(total <= 0 || count == total) { "Download was truncated" }
            } }
        } finally { connection.disconnect() }
    }
    companion object { private val mutex = Mutex() }
}
