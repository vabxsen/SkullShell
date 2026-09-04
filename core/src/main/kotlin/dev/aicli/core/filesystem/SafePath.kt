package dev.aicli.core.filesystem

import java.io.File
import java.io.IOException

class PathTraversalException(message: String) : SecurityException(message)

/** Confines the app's file API to a root. Arbitrary CLI processes do not use this API. */
object SafePath {
    /**
     * @throws PathTraversalException if [requested] resolves outside [root].
     */
    fun resolve(root: File, requested: String): File {
        requireRelative(requested)
        val canonicalRoot = root.canonicalFile
        val candidate = File(canonicalRoot, requested)
        val canonicalCandidate = try {
            candidate.canonicalFile
        } catch (e: IOException) {
            throw PathTraversalException("Could not resolve path: $requested")
        }
        if (canonicalCandidate != canonicalRoot && !canonicalCandidate.path.startsWith(canonicalRoot.path + File.separator)) {
            throw PathTraversalException("Path '$requested' escapes root '${canonicalRoot.path}'")
        }
        return canonicalCandidate
    }

    /** Delete/rename the directory entry itself, without following its final symlink. */
    fun entry(root: File, requested: String): File {
        requireRelative(requested)
        val base = root.canonicalFile.toPath()
        val target = base.resolve(requested).normalize()
        if (!target.startsWith(base) || (target != base && !target.parent.toFile().canonicalFile.toPath().startsWith(base))) {
            throw PathTraversalException("Path '$requested' escapes root")
        }
        return target.toFile()
    }

    private fun requireRelative(requested: String) {
        if (File(requested).isAbsolute || requested.startsWith('/') || requested.startsWith('\\') || Regex("^[A-Za-z]:").containsMatchIn(requested)) {
            throw PathTraversalException("Expected a relative workspace path")
        }
    }

    fun isWithin(root: File, candidate: File): Boolean = try {
        val canonicalRoot = root.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        canonicalCandidate == canonicalRoot || canonicalCandidate.path.startsWith(canonicalRoot.path + File.separator)
    } catch (e: IOException) {
        false
    }
}
