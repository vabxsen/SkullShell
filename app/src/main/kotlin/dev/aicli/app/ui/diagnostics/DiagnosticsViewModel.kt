package dev.aicli.app.ui.diagnostics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.ui.common.UiState
import dev.aicli.runtime.health.HealthCheckResult
import dev.aicli.runtime.health.RuntimeHealthChecker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class DiagnosticsViewModel(private val healthChecker: RuntimeHealthChecker) : ViewModel() {
    private val _state = MutableStateFlow<UiState<List<HealthCheckResult>>>(UiState.Loading)
    val state: StateFlow<UiState<List<HealthCheckResult>>> = _state.asStateFlow()

    init { runDiagnostics() }

    fun runDiagnostics() {
        viewModelScope.launch {
            _state.value = UiState.Loading
            try {
                _state.value = UiState.Success(healthChecker.runAll())
            } catch (e: Exception) {
                _state.value = UiState.Error(e.message ?: "Diagnostics failed", e)
            }
        }
    }

    fun exportText(results: List<HealthCheckResult>): String = buildString {
        appendLine("AI CLI Diagnostics")
        appendLine("Android ${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})")
        appendLine("ABIs: ${android.os.Build.SUPPORTED_ABIS.joinToString()}")
        appendLine()
        for (result in results) {
            appendLine("[${result.status}] ${result.label} — ${result.detail}")
        }
    }
}
