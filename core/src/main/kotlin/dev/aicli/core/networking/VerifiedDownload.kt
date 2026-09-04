package dev.aicli.core.networking

import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Streams a download, checks its length and published digest, and discards partial files. */
suspend fun downloadFile(
    url: String,
    destination: File,
    expectedSize: Long = 0,
    expectedDigest: String? = null,
    onProgress: suspend (Long, Long) -> Unit = { _, _ -> },
) {
    require(URL(url).protocol == "https") { "Downloads require HTTPS" }
    val digestParts = expectedDigest?.split(':', limit = 2)
    val digest = digestParts?.let {
        require(it.size == 2) { "Invalid download checksum" }
        MessageDigest.getInstance(when (it[0].lowercase()) {
            "sha1" -> "SHA-1"; "sha256" -> "SHA-256"; "sha512" -> "SHA-512"
            else -> error("Unsupported download checksum")
        })
    }
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 15_000
        readTimeout = 30_000
        instanceFollowRedirects = true
    }
    var complete = false
    try {
        check(connection.responseCode in 200..299) { "Download failed (HTTP ${connection.responseCode})" }
        val reportedSize = connection.contentLengthLong
        val total = expectedSize.takeIf { it > 0 } ?: reportedSize
        var count = 0L
        var last = 0L
        connection.inputStream.use { input -> destination.outputStream().use { output ->
            val buffer = ByteArray(65536)
            while (true) {
                currentCoroutineContext().ensureActive()
                val n = input.read(buffer)
                if (n < 0) break
                output.write(buffer, 0, n)
                digest?.update(buffer, 0, n)
                count += n
                if (count - last >= 512 * 1024 || count == total) {
                    onProgress(count, total)
                    last = count
                }
            }
        } }
        check(count > 0 && (total <= 0 || count == total) && (reportedSize <= 0 || count == reportedSize)) { "Download was incomplete" }
        if (digest != null) {
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            check(actual.equals(digestParts!![1], ignoreCase = true)) { "Download checksum mismatch" }
        }
        complete = true
    } finally {
        connection.disconnect()
        if (!complete) destination.delete()
    }
}
