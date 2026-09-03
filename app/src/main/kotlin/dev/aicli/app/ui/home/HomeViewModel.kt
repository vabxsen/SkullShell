package dev.aicli.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.data.ProjectRepository
import dev.aicli.app.data.SessionManager
import dev.aicli.app.ui.common.UiState
import dev.aicli.core.filesystem.Project
import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.InstallEvent
import dev.aicli.provider.api.ProviderState
import dev.aicli.runtime.health.CheckStatus
import dev.aicli.runtime.health.RuntimeHealthChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class ProviderCard(val provider: AIProvider, val state: ProviderState)

/** Drives the install-progress dialog on Home — a provider's own install/uninstall Flow<InstallEvent>. */
data class InstallProgressUi(val providerId: String, val displayName: String, val latestEvent: InstallEvent?, val done: Boolean)

data class HomeUiData(
    val providerCards: List<ProviderCard>,
    val recentProjects: List<Project>,
    val healthPassCount: Int,
    val healthTotalCount: Int,
)

class HomeViewModel(
    private val providers: List<AIProvider>,
    private val healthChecker: RuntimeHealthChecker,
    private val projectRepository: ProjectRepository,
    private val sessionManager: SessionManager,
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<HomeUiData>>(UiState.Loading)
    val uiState: StateFlow<UiState<HomeUiData>> = _uiState.asStateFlow()

    private val _installProgress = MutableStateFlow<InstallProgressUi?>(null)
    val installProgress: StateFlow<InstallProgressUi?> = _installProgress.asStateFlow()

    init {
        refresh()
    }

    /**
     * Real install/update trigger — before this, tapping a provider card's "INSTALL"/"UPDATE"
     * label did nothing but navigate to a terminal that would then fail to launch a
     * not-yet-installed binary. Streams the provider's own `Flow<InstallEvent>` into a progress
     * UI and refreshes provider state on completion either way (success or failure both need the
     * dashboard to reflect what's actually true now).
     */
    fun installOrUpdateProvider(providerId: String) {
        val provider = providers.firstOrNull { it.id == providerId } ?: return
        viewModelScope.launch {
            _installProgress.value = InstallProgressUi(providerId, provider.displayName, null, done = false)
            provider.installer.install().collect { event ->
                _installProgress.value = InstallProgressUi(providerId, provider.displayName, event, done = event is InstallEvent.Completed || event is InstallEvent.Failed)
            }
            refresh()
        }
    }

    fun dismissInstallProgress() {
        _installProgress.value = null
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val cards = providers.map { provider -> ProviderCard(provider, provider.detectState()) }
                val projects = projectRepository.projects.first()
                val health = healthChecker.runAll()
                val passCount = health.count { it.status == CheckStatus.PASS }
                _uiState.value = UiState.Success(
                    HomeUiData(
                        providerCards = cards,
                        recentProjects = projects.sortedByDescending { it.lastOpenedAtEpochMillis ?: it.createdAtEpochMillis }.take(5),
                        healthPassCount = passCount,
                        healthTotalCount = health.size,
                    )
                )
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message ?: "Failed to load dashboard", e)
            }
        }
    }
}
