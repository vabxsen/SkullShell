package dev.aicli.core.networking

/** Numeric releases sort numerically; a prerelease sorts before the corresponding stable build. */
object ReleaseVersion {
    fun isNewer(latest: String, current: String): Boolean {
        val pattern = Regex("^[vV]?(\\d+(?:\\.\\d+){0,3})(?:-([0-9A-Za-z.-]+))?(?:\\+[0-9A-Za-z.-]+)?$")
        val a = pattern.matchEntire(latest) ?: return false
        val b = pattern.matchEntire(current) ?: return false
        val av = a.groupValues[1].split('.').map { it.toLongOrNull() ?: return false }
        val bv = b.groupValues[1].split('.').map { it.toLongOrNull() ?: return false }
        for (i in 0 until maxOf(av.size, bv.size)) {
            val comparison = av.getOrElse(i) { 0L }.compareTo(bv.getOrElse(i) { 0L })
            if (comparison != 0) return comparison > 0
        }
        val ap = a.groupValues[2]
        val bp = b.groupValues[2]
        if (ap == bp) return false
        if (ap.isEmpty() || bp.isEmpty()) return ap.isEmpty()
        val ai = ap.split('.')
        val bi = bp.split('.')
        for (i in 0 until minOf(ai.size, bi.size)) {
            val an = ai[i].toLongOrNull()
            val bn = bi[i].toLongOrNull()
            val comparison = when {
                an != null && bn != null -> an.compareTo(bn)
                an != null -> -1
                bn != null -> 1
                else -> ai[i].compareTo(bi[i])
            }
            if (comparison != 0) return comparison > 0
        }
        return ai.size > bi.size
    }
}
