package dev.aicli.core.filesystem

import java.io.File
import java.io.IOException

class PathTraversalException(message: String) : SecurityException(message)

/**
 * Resolves a user- or CLI-supplied relative path against a fixed root and refuses anything that
 * canonicalizes outside it. Every filesystem call this app makes on behalf of a spawned process
 * (git, node, the CLIs themselves) — cwd, argv paths, file read/write from the UI's file browser
 * — goes through this first. This is the boundary that keeps "the CLI can do anything inside the
 * workspace" from becoming "the CLI can do anything on the device."
 */
object SafePath {
    /**
     * @throws PathTraversalException if [requested] resolves outside [root].
     */
    fun resolve(root: File, requested: String): File {
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

    fun isWithin(root: File, candidate: File): Boolean = try {
        val canonicalRoot = root.canonicalFile
        val canonicalCandidate = candidate.canonicalFile
        canonicalCandidate == canonicalRoot || canonicalCandidate.path.startsWith(canonicalRoot.path + File.separator)
    } catch (e: IOException) {
        false
    }
}
