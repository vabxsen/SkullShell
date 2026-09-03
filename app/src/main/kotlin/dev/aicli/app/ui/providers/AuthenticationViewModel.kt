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

    private var startedFor: String? = null

    fun controllerFor(id: String): TerminalSessionController? = sessionManager.controllerFor(id)

    fun startLogin(providerId: String) {
        if (startedFor == providerId) return
        startedFor = providerId
        val provider = providersById[providerId] ?: run {
            _error.value = "Unknown provider: $providerId"
            return
        }
        viewModelScope.launch {
            try {
                val process = provider.auth.startLogin()
                val controller = sessionManager.createSession(
                    "Sign in — ${provider.displayName}", providerId, null,
                    termuxEnvironment.homeDir.absolutePath, process, 100, 30,
                )
                _sessionId.value = controller.meta.id
                pollAuthState(provider)
            } catch (e: Exception) {
                _error.value = "Couldn't start sign-in: ${e.message}"
            }
        }
    }

    private fun pollAuthState(provider: AIProvider) {
        viewModelScope.launch {
            while (isActive) {
                val state = provider.auth.currentState()
                _authState.value = state
                if (state is AuthState.SignedIn || state is AuthState.Error) break
                delay(2_000)
            }
        }
    }
}
