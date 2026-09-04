package dev.aicli.app

import androidx.documentfile.provider.DocumentFile
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aicli.core.filesystem.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ProjectStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val repository = (context.applicationContext as AiCliApplication).container.projectRepository

    @Test fun projectCrudAndFilesystemBoundaries() = runBlocking {
        val project = repository.createAppWorkspace("Audit ${UUID.randomUUID()}")
        val fs = FileSystemManager(project.root)
        try {
            assertEquals(project, repository.get(project.id))
            assertTrue(fs.writeText("nested/hello.txt", "world") is FileOpResult.Success)
            assertEquals(FileOpResult.Success("world"), fs.readText("nested/hello.txt"))
            assertTrue(fs.rename("nested/hello.txt", "renamed.txt") is FileOpResult.Success)
            assertTrue(fs.rename("", "moved-root") is FileOpResult.Failure)
            assertTrue(fs.delete("") is FileOpResult.Failure)
            assertTrue(fs.writeText("../escaped", "no") is FileOpResult.Failure)
            assertTrue(fs.delete("renamed.txt") is FileOpResult.Success)
            val original = project.root.rootDirectory.resolve("keep.txt").apply { writeText("keep") }
            java.nio.file.Files.createSymbolicLink(project.root.rootDirectory.resolve("link.txt").toPath(), original.toPath())
            assertTrue(fs.delete("link.txt") is FileOpResult.Success)
            assertEquals("keep", original.readText())
            val linkedDirectory = project.root.rootDirectory.resolve("links").apply { mkdirs() }
            java.nio.file.Files.createSymbolicLink(linkedDirectory.resolve("outside").toPath(), original.toPath())
            assertTrue(SafeFiles.deleteTree(linkedDirectory))
            assertEquals("keep", original.readText())
            repository.markOpened(project.id)
            assertNotNull(repository.get(project.id)?.lastOpenedAtEpochMillis)
            repository.remove(project.id)
            assertNull(repository.get(project.id))
        } finally {
            repository.remove(project.id)
            SafeFiles.deleteTree(project.root.rootDirectory)
        }
        Unit
    }

    /** Select only the throwaway /Documents/SkullShell-Audit folder through the real picker first. */
    @Test fun externalFolderImportsExportsAndProtectsConcurrentEdits() = runBlocking {
        assumeTrue(InstrumentationRegistry.getArguments().getString("safFixture") == "true")
        val project = repository.projects.first().single { it.name == "SkullShell-Audit" }
        val root = project.root as WorkspaceRoot.ExternalProject
        check(root.treeUri.toString().contains("SkullShell-Audit"))
        assertTrue(context.contentResolver.persistedUriPermissions.any { it.uri == root.treeUri && it.isWritePermission })
        val local = root.stagingDirectory
        assertEquals("original project content", local.resolve("readme.txt").readText())
        assertTrue(local.resolve("subfolder/delete-me.txt").isFile)
        local.resolve("readme.txt").writeText("edited in SkullShell")
        local.resolve("subfolder/delete-me.txt").delete()
        local.resolve("created.txt").writeText("new file")
        assertEquals(3, repository.saveToFolder(project.id))
        val remote = DocumentFile.fromTreeUri(context, root.treeUri)!!
        fun read(name: String) = context.contentResolver.openInputStream(remote.findFile(name)!!.uri)!!.bufferedReader().use { it.readText() }
        assertEquals("edited in SkullShell", read("readme.txt"))
        assertEquals("new file", read("created.txt"))
        assertNull(remote.findFile("subfolder")!!.findFile("delete-me.txt"))
        assertEquals(0, repository.saveToFolder(project.id))
        context.contentResolver.openOutputStream(remote.findFile("readme.txt")!!.uri, "wt")!!.use { it.write("edited outside".toByteArray()) }
        local.resolve("readme.txt").writeText("conflicting local edit")
        local.resolve("created.txt").writeText("must not be partially saved")
        try {
            repository.saveToFolder(project.id)
            fail("Concurrent edits should stop export")
        } catch (e: IllegalStateException) { assertTrue(e.message.orEmpty().contains("changed outside")) }
        assertEquals("edited outside", read("readme.txt"))
        assertEquals("new file", read("created.txt"))
        // Resolve the conflict explicitly and prove a subsequent save can succeed.
        local.resolve("readme.txt").writeText("edited outside")
        assertEquals(2, repository.saveToFolder(project.id))
    }
}
