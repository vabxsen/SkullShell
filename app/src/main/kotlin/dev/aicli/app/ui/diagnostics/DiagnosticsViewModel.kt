package dev.aicli.app.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.data.ProviderStateRepository
import dev.aicli.app.ui.common.ProviderCard
import dev.aicli.app.ui.common.UiState
import dev.aicli.provider.api.AIProvider
import dev.aicli.runtime.health.HealthCheckResult
import dev.aicli.runtime.health.RuntimeHealthChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DiagnosticsUiData(val healthChecks: List<HealthCheckResult>, val providerStates: List<ProviderCard>)

class DiagnosticsViewModel(
    private val healthChecker: RuntimeHealthChecker,
    private val providers: List<AIProvider>,
    private val providerStateRepository: ProviderStateRepository,
) : ViewModel() {
    private val _state = MutableStateFlow<UiState<DiagnosticsUiData>>(UiState.Loading)
    val state: StateFlow<UiState<DiagnosticsUiData>> = _state.asStateFlow()

    init { runDiagnostics() }

    fun runDiagnostics() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                val health = healthChecker.runAll()
                providerStateRepository.refreshAll()
                val states = providerStateRepository.states.value
                val providerCards = providers.map { ProviderCard(it, states.getValue(it.id)) }
                _state.value = UiState.Success(DiagnosticsUiData(health, providerCards))
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Diagnostics failed", e)
            }
        }
    }

    fun exportText(data: DiagnosticsUiData): String = buildString {
        appendLine("AI CLI Diagnostics")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        appendLine("Runtime health:")
        for (result in data.healthChecks) {
            appendLine("[${result.status}] ${result.label} — ${result.detail}")
        }
        appendLine()
        appendLine("AI providers:")
        for (card in data.providerStates) {
            appendLine("[${card.state::class.simpleName}] ${card.provider.displayName}")
        }
    }
}
