package dev.aicli.app.ui.providers

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.data.ProviderStateRepository
import dev.aicli.app.ui.common.InstallProgressUi
import dev.aicli.app.ui.common.ProviderCard
import dev.aicli.app.ui.common.UiState
import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.InstallEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Providers screen. Reuses [ProviderStateRepository] (also used by
 * [dev.aicli.app.ui.home.HomeViewModel]) for state *observation* so both screens agree on what
 * "Ready"/"Error"/etc. actually means, but keeps its own install-trigger logic — two call sites
 * isn't enough to justify a shared "InstallCoordinator" abstraction.
 */
class ProvidersViewModel(
    private val providers: List<AIProvider>,
    private val providerStateRepository: ProviderStateRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<List<ProviderCard>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<ProviderCard>>> = _uiState.asStateFlow()

    private val _installProgress = MutableStateFlow<InstallProgressUi?>(null)
    val installProgress: StateFlow<InstallProgressUi?> = _installProgress.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                providerStateRepository.refreshAll()
                val states = providerStateRepository.states.value
                _uiState.value = UiState.Success(providers.map { ProviderCard(it, states.getValue(it.id)) })
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load providers", e)
            }
        }
    }

    fun installOrUpdate(providerId: String) {
        val provider = providers.firstOrNull { it.id == providerId } ?: return
        viewModelScope.launch {
            _installProgress.value = InstallProgressUi(providerId, provider.displayName, null, done = false)
            provider.installer.install().collect { event ->
                _installProgress.value = InstallProgressUi(providerId, provider.displayName, event, done = event is InstallEvent.Completed || event is InstallEvent.Failed)
            }
            refresh()
        }
    }

    fun uninstall(providerId: String) {
        val provider = providers.firstOrNull { it.id == providerId } ?: return
        viewModelScope.launch {
            _installProgress.value = InstallProgressUi(providerId, provider.displayName, null, done = false)
            provider.installer.uninstall().collect { event ->
                _installProgress.value = InstallProgressUi(providerId, provider.displayName, event, done = event is InstallEvent.Completed || event is InstallEvent.Failed)
            }
            refresh()
        }
    }

    /** Per-provider repair: uninstall then reinstall. Distinct from Settings' "repair runtime",
     *  which repairs the shared Linux userland bootstrap, not a single provider's binary. */
    fun repair(providerId: String) {
        val provider = providers.firstOrNull { it.id == providerId } ?: return
        viewModelScope.launch {
            _installProgress.value = InstallProgressUi(providerId, provider.displayName, null, done = false)
            provider.installer.uninstall().collect { event ->
                _installProgress.value = InstallProgressUi(providerId, provider.displayName, event, done = false)
            }
            provider.installer.install().collect { event ->
                _installProgress.value = InstallProgressUi(providerId, provider.displayName, event, done = event is InstallEvent.Completed || event is InstallEvent.Failed)
            }
            refresh()
        }
    }

    fun dismissInstallProgress() {
        _installProgress.value = null
    }
}
