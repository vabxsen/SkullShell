package dev.aicli.runtime.archive

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.GZIPOutputStream

class TarGzExtractorTest {
    @get:Rule val temp = TemporaryFolder()

    private fun archive(name: String, content: ByteArray, declaredSize: Int = content.size): java.io.File {
        val file = temp.newFile()
        GZIPOutputStream(file.outputStream()).use { out ->
            val header = ByteArray(512)
            fun text(offset: Int, value: String) { value.toByteArray().copyInto(header, offset) }
            text(0, name)
            text(100, "0000755")
            text(124, declaredSize.toString(8).padStart(11, '0'))
            header[156] = '0'.code.toByte()
            for (i in 148..155) header[i] = 32
            text(148, header.sumOf { it.toInt() and 255 }.toString(8).padStart(6, '0') + "\u0000 ")
            out.write(header)
            out.write(content)
            if (declaredSize == content.size) {
                out.write(ByteArray((512 - content.size % 512) % 512))
                out.write(ByteArray(1024))
            }
        }
        return file
    }

    @Test fun extractsFilesAndEmptyFiles() {
        val destination = temp.newFolder()
        TarGzExtractor.extract(archive("nested/hello.txt", "hello".toByteArray()), destination)
        assertEquals("hello", destination.resolve("nested/hello.txt").readText())
        TarGzExtractor.extract(archive("empty", byteArrayOf()), destination)
        assertTrue(destination.resolve("empty").isFile)
    }

    @Test fun rejectsTraversal() {
        assertThrows(IOException::class.java) {
            TarGzExtractor.extract(archive("../escaped", byteArrayOf(1)), temp.newFolder())
        }
    }

    @Test fun rejectsAbsolutePaths() {
        assertThrows(IOException::class.java) {
            TarGzExtractor.extract(archive("/escaped", byteArrayOf(1)), temp.newFolder())
        }
    }

    @Test fun rejectsTruncatedDataInsteadOfCreatingPaddedBinary() {
        assertThrows(IOException::class.java) {
            TarGzExtractor.extract(archive("binary", byteArrayOf(1), 1024), temp.newFolder())
        }
    }

    @Test fun refusesExistingSymlinkEscape() {
        val destination = temp.newFolder()
        val outside = temp.newFolder()
        try {
            java.nio.file.Files.createSymbolicLink(destination.resolve("link").toPath(), outside.toPath())
        } catch (e: java.nio.file.FileSystemException) {
            org.junit.Assume.assumeNoException(e)
        }
        assertThrows(IOException::class.java) {
            TarGzExtractor.extract(archive("link/escaped", byteArrayOf(1)), destination)
        }
        assertFalse(outside.resolve("escaped").exists())
    }

    @Test fun failedExecutableVerificationPreservesPreviousInstall() = kotlinx.coroutines.runBlocking {
        val destination = temp.newFolder().resolve("agent").apply { writeText("working-old-agent") }
        val candidate = archive("package/agent", "broken-new-agent".toByteArray())
        try {
            installExecutable(candidate, destination, setOf("agent")) { error("Version probe failed") }
            fail("Expected failed verification")
        } catch (e: IllegalStateException) { assertEquals("Version probe failed", e.message) }
        assertEquals("working-old-agent", destination.readText())
        assertFalse(destination.parentFile.resolve(".agent-install").exists())
        assertFalse(candidate.exists())
    }
}
