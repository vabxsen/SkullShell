package dev.aicli.core.filesystem

import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * This is the one boundary in the app that keeps "the CLI can do anything inside the workspace"
 * from becoming "the CLI can do anything on the device" — see SafePath.kt's doc comment. It gets
 * a dedicated, deliberately adversarial test suite rather than incidental coverage from other
 * tests.
 */
class SafePathTest {
    private lateinit var root: File
    private lateinit var outsideDir: File

    @Before
    fun setUp() {
        root = Files.createTempDirectory("safepath_root").toFile()
        File(root, "sub").mkdirs()
        File(root, "sub/file.txt").writeText("inside")
        outsideDir = Files.createTempDirectory("safepath_outside").toFile()
        File(outsideDir, "secret.txt").writeText("outside")
    }

    @After
    fun tearDown() {
        root.deleteRecursively()
        outsideDir.deleteRecursively()
    }

    @Test
    fun `a plain relative path inside root resolves normally`() {
        val resolved = SafePath.resolve(root, "sub/file.txt")
        assertThat(resolved.readText()).isEqualTo("inside")
    }

    @Test
    fun `an empty relative path resolves to the root itself`() {
        val resolved = SafePath.resolve(root, "")
        assertThat(resolved).isEqualTo(root.canonicalFile)
    }

    @Test
    fun `a single dot-dot traversal above root is rejected`() {
        assertThrows(PathTraversalException::class.java) {
            SafePath.resolve(root, "../outside.txt")
        }
    }

    @Test
    fun `a deeply nested dot-dot traversal is rejected`() {
        assertThrows(PathTraversalException::class.java) {
            SafePath.resolve(root, "sub/../../../../../../etc/passwd")
        }
    }

    @Test
    fun `a dot-dot traversal that stays within root by coincidence is allowed`() {
        // sub/../sub/file.txt canonicalizes back inside root — traversal syntax alone isn't
        // the violation, escaping the root is.
        val resolved = SafePath.resolve(root, "sub/../sub/file.txt")
        assertThat(resolved.readText()).isEqualTo("inside")
    }

    @Test
    fun `an absolute path pointing outside root is rejected`() {
        assertThrows(PathTraversalException::class.java) {
            SafePath.resolve(root, outsideDir.absolutePath + "/secret.txt")
        }
    }

    @Test
    fun `a symlink inside root that points outside it is rejected`() {
        val link = File(root, "escape_link")
        try {
            Files.createSymbolicLink(link.toPath(), File(outsideDir, "secret.txt").toPath())
        } catch (e: Exception) {
            // Symlink creation can require elevated privileges on some Windows configurations
            // (no Developer Mode / no admin) — this environment-dependent case is skipped rather
            // than falsely failed; the other traversal tests still cover the core boundary.
            return
        }
        assertThrows(PathTraversalException::class.java) {
            SafePath.resolve(root, "escape_link")
        }
    }

    @Test
    fun `isWithin agrees with resolve for a path inside root`() {
        assertThat(SafePath.isWithin(root, File(root, "sub/file.txt"))).isTrue()
    }

    @Test
    fun `isWithin agrees with resolve for a path outside root`() {
        assertThat(SafePath.isWithin(root, File(outsideDir, "secret.txt"))).isFalse()
    }

    @Test
    fun `isWithin returns true for the root directory itself`() {
        assertThat(SafePath.isWithin(root, root)).isTrue()
    }

    private fun assertThrows(expected: Class<out Throwable>, block: () -> Unit) {
        try {
            block()
        } catch (t: Throwable) {
            assertThat(expected.isInstance(t)).isTrue()
            return
        }
        throw AssertionError("Expected ${expected.simpleName} but nothing was thrown")
    }
}
