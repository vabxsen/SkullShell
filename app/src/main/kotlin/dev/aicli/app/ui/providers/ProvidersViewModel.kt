package dev.aicli.app.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.data.ProviderStateRepository
import dev.aicli.app.data.SessionManager
import dev.aicli.app.ui.common.InstallProgressUi
import dev.aicli.app.ui.common.ProviderCard
import dev.aicli.app.ui.common.UiState
import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.InstallEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import dev.aicli.runtime.bootstrap.BootstrapManager
import dev.aicli.runtime.bootstrap.BootstrapState
import dev.aicli.app.ui.install.toInstallEvent
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException

/**
 * Backs the Providers screen. Reuses [ProviderStateRepository] (also used by
 * [dev.aicli.app.ui.diagnostics.DiagnosticsViewModel]) for state *observation* so every screen
 * agrees on what "Ready"/"Error"/etc. actually means, but keeps its own install-trigger logic —
 * two call sites isn't enough to justify a shared "InstallCoordinator" abstraction.
 */
class ProvidersViewModel(
    private val providers: List<AIProvider>,
    private val providerStateRepository: ProviderStateRepository,
    private val bootstrapManager: BootstrapManager,
    private val sessionManager: SessionManager,
) : ViewModel() {
    private var installJob: Job? = null
    private var refreshJob: Job? = null

    private val _uiState = MutableStateFlow<UiState<List<ProviderCard>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ProviderCard>>> = _uiState.asStateFlow()

    private val _installProgress = MutableStateFlow<InstallProgressUi?>(null)
    val installProgress: StateFlow<InstallProgressUi?> = _installProgress.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (refreshJob?.isActive == true) return
        refreshJob = viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                providerStateRepository.refreshAll()
                val states = providerStateRepository.states.value
                _uiState.value = UiState.Success(providers.map { ProviderCard(it, states.getValue(it.id)) })
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load providers", e)
            }
        }
    }

    fun installOrUpdate(providerId: String) {
        val provider = providers.firstOrNull { it.id == providerId } ?: return
        if (installJob?.isActive == true) return
        if (hasRunningSessions(provider)) return
        installJob = viewModelScope.launch {
            _installProgress.value = InstallProgressUi(providerId, provider.displayName, null, done = false)
            try {
            bootstrapManager.install().collect { state ->
                if (state is BootstrapState.Failed) error(state.reason)
                if (state !is BootstrapState.Ready) _installProgress.value = InstallProgressUi(providerId, provider.displayName, state.toInstallEvent(), done = false)
            }
            provider.installer.install().collect { event ->
                _installProgress.value = InstallProgressUi(providerId, provider.displayName, event, done = event is InstallEvent.Completed || event is InstallEvent.Failed)
            }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _installProgress.value = InstallProgressUi(providerId, provider.displayName, InstallEvent.Failed("install", e.message ?: "Installation failed", e), done = true)
            }
            refresh()
        }
    }

    fun uninstall(providerId: String) {
        val provider = providers.firstOrNull { it.id == providerId } ?: return
        if (installJob?.isActive == true) return
        if (hasRunningSessions(provider)) return
        installJob = viewModelScope.launch {
            _installProgress.value = InstallProgressUi(providerId, provider.displayName, null, done = false)
            try {
            provider.installer.uninstall().collect { event ->
                _installProgress.value = InstallProgressUi(providerId, provider.displayName, event, done = event is InstallEvent.Completed || event is InstallEvent.Failed)
            }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _installProgress.value = InstallProgressUi(providerId, provider.displayName, InstallEvent.Failed("uninstall", e.message ?: "Uninstall failed", e), done = true)
            }
            refresh()
        }
    }

    private fun hasRunningSessions(provider: AIProvider): Boolean {
        if (sessionManager.runningCount.value == 0) return false
        _installProgress.value = InstallProgressUi(provider.id, provider.displayName,
            InstallEvent.Failed("install", "Close your running terminal sessions before changing agent installations."), done = true)
        return true
    }

    /** Reinstall without removing the current installation first. */
    fun repair(providerId: String) {
        installOrUpdate(providerId)
    }

    fun dismissInstallProgress() {
        _installProgress.value = null
    }
}
