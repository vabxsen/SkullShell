package dev.aicli.app.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.data.SessionManager
import dev.aicli.app.data.TerminalSessionController
import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.AuthState
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import dev.aicli.app.data.SessionRunState

/**
 * Drives a real sign-in flow: spawns the provider's own `startLogin()` PTY (never a WebView,
 * never a simulated success — see [dev.aicli.provider.api.ProviderAuth]'s own doc comment) and
 * wires it through the existing [SessionManager] so it gets the same lifecycle/PTY-monitoring/
 * Room bookkeeping as any other session, rather than a parallel lightweight session type.
 */
class AuthenticationViewModel(
    val providersById: Map<String, AIProvider>,
    private val sessionManager: SessionManager,
    private val termuxEnvironment: TermuxEnvironment,
) : ViewModel() {

    private val _sessionId = MutableStateFlow<String?>(null)
    val sessionId: StateFlow<String?> = _sessionId.asStateFlow()

    private val _authState = MutableStateFlow<AuthState>(AuthState.Unknown)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()
    private val _links = MutableStateFlow<List<String>>(emptyList())
    val links: StateFlow<List<String>> = _links.asStateFlow()

    private var startedFor: String? = null
    private var loginJob: Job? = null
    private var pollJob: Job? = null
    private var linkJob: Job? = null

    fun controllerFor(id: String): TerminalSessionController? = sessionManager.controllerFor(id)

    fun startLogin(providerId: String) {
        if (startedFor == providerId && _error.value == null && _authState.value !is AuthState.Error) return
        loginJob?.cancel()
        pollJob?.cancel()
        linkJob?.cancel()
        _links.value = emptyList()
        _error.value = null
        _authState.value = AuthState.Unknown
        startedFor = providerId
        val provider = providersById[providerId] ?: run {
            _error.value = "Unknown provider: $providerId"
            return
        }
        loginJob = viewModelScope.launch {
            try {
                _sessionId.value?.let { sessionManager.closeSession(it) }
                _sessionId.value = null
                val process = provider.auth.startLogin()
                val controller = sessionManager.createSession(
                    "Sign in — ${provider.displayName}", providerId, null,
                    termuxEnvironment.homeDir.absolutePath, process, 100, 30,
                )
                _sessionId.value = controller.meta.id
                linkJob = viewModelScope.launch {
                    controller.outputTail.collect { output -> _links.value = terminalLinks(output) }
                }
                pollAuthState(provider)
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _error.value = "Couldn't start sign-in: ${e.message}"
            }
        }
    }

    private fun pollAuthState(provider: AIProvider) {
        pollJob = viewModelScope.launch {
            while (isActive) {
                val state = try { provider.auth.currentState() }
                catch (e: CancellationException) { throw e }
                catch (e: Exception) { AuthState.Error(e.message ?: "Authentication check failed") }
                _authState.value = state
                if (state is AuthState.SignedIn || state is AuthState.Error) break
                val controller = _sessionId.value?.let(sessionManager::controllerFor)
                if (controller?.runState?.value == SessionRunState.EXITED) {
                    _error.value = "Sign-in ended without completing (exit ${controller.exitCode.value}). Try signing in again."
                    break
                }
                delay(2_000)
            }
        }
    }
}

internal fun terminalLinks(output: String): List<String> {
    val plain = output.replace(Regex("\u001B\\[[0-?]*[ -/]*[@-~]"), "")
    // Wait for a delimiter so an incomplete URL arriving across PTY chunks isn't offered.
    return Regex("https://[^\\s\\u001B]+(?=\\s)").findAll(plain).map { it.value.trimEnd(')', ']', '.', ',', ';') }
        .filter { it.length <= 8192 }.distinct().toList().takeLast(3)
}
