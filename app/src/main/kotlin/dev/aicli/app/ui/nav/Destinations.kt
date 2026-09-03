package dev.aicli.app.ui.nav

/** Top-level navigation graph. Kept as plain route strings (no Navigation Compose type-safety
 *  library in this project's dependency catalog) — small enough graph that this stays readable. */
object Destinations {
    const val HOME = "home"
    const val PROJECTS = "projects"
    const val TERMINAL = "terminal/{sessionId}"
    const val SETTINGS = "settings"
    const val DIAGNOSTICS = "diagnostics"

    fun terminal(sessionId: String) = "terminal/$sessionId"
}
