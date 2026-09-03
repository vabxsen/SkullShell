package dev.aicli.provider.api

import dev.aicli.terminal.PtyProcess
import kotlinx.coroutines.flow.Flow

/**
 * Real, observed provider state. Every state here must correspond to something actually
 * checked at the time it's reported — never a hardcoded "Ready". See ARCHITECTURE.md §6 and
 * the project brief's "No Fake Functionality" section.
 */
sealed class ProviderState {
    data object NotInstalled : ProviderState()
    data class Installing(val stepDescription: String, val progressFraction: Float?) : ProviderState()
    data class Installed(val version: String) : ProviderState()
    data class UpdateAvailable(val currentVersion: String, val latestVersion: String) : ProviderState()
    data object AuthRequired : ProviderState()
    data class Ready(val version: String) : ProviderState()
    data class Error(val reason: String, val throwable: Throwable? = null) : ProviderState()
    data class Incompatible(val reason: String) : ProviderState()
}

data class ProviderCompatibilityReport(
    val compatible: Boolean,
    val summary: String,
    /** Specific, named caveats — surfaced to the user, never silently swallowed. */
    val caveats: List<String> = emptyList(),
)

sealed class InstallEvent {
    data class Progress(val step: String, val fraction: Float?, val logLine: String? = null) : InstallEvent()
    data object Completed : InstallEvent()
    data class Failed(val step: String, val reason: String, val throwable: Throwable? = null) : InstallEvent()
}

interface ProviderInstaller {
    fun install(): Flow<InstallEvent>
    fun uninstall(): Flow<InstallEvent>
    suspend fun checkForUpdate(): ProviderState.UpdateAvailable?
}

sealed class AuthState {
    data object Unknown : AuthState()
    data object SignedOut : AuthState()
    data object SignedIn : AuthState()
    data class Error(val reason: String) : AuthState()
}

/**
 * A provider's own supported auth mechanism, driven, never faked. See project brief §17: we
 * launch the CLI's own `login` flow (which itself opens a browser via Android's normal intent
 * mechanism when the CLI needs OAuth) and observe the *real* resulting state — we never embed a
 * third-party login page in a WebView, never intercept credentials, never simulate success.
 */
interface ProviderAuth {
    suspend fun currentState(): AuthState
    /** Starts the provider's own login flow inside a real PTY session; caller owns rendering it. */
    suspend fun startLogin(): PtyProcess
    suspend fun logout()
}

data class ProviderLaunchRequest(
    val workingDirectory: String,
    val extraArgs: List<String> = emptyList(),
    val initialCols: Int = 80,
    val initialRows: Int = 24,
)

/**
 * The one interface the UI and session manager depend on. Concrete providers (claude, codex,
 * opencode, antigravity) each live in their own Gradle module and implement this — adding a 5th
 * CLI later means adding a module, not touching feature/terminal or feature/home.
 */
interface AIProvider {
    val id: String
    val displayName: String
    val installer: ProviderInstaller
    val auth: ProviderAuth

    /** Runs real compatibility checks for the current device (ABI, kernel features if relevant). */
    suspend fun checkCompatibility(): ProviderCompatibilityReport

    /** Runs the actual `--version` (or equivalent) check against the installed binary, if any. */
    suspend fun detectState(): ProviderState

    /** Spawns the CLI in a real PTY. Throws if [detectState] is not [ProviderState.Ready]. */
    suspend fun launch(request: ProviderLaunchRequest): PtyProcess
}
