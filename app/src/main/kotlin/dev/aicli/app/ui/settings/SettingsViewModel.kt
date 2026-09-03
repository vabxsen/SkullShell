package dev.aicli.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.core.settings.AdvancedSettings
import dev.aicli.core.settings.SettingsRepository
import dev.aicli.core.settings.TerminalSettings
import dev.aicli.provider.api.AIProvider
import dev.aicli.runtime.bootstrap.BootstrapManager
import dev.aicli.runtime.bootstrap.BootstrapState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiData(val terminal: TerminalSettings, val advanced: AdvancedSettings)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val bootstrapManager: BootstrapManager,
    val providers: List<AIProvider>,
) : ViewModel() {

    val uiData: StateFlow<SettingsUiData?> = combine(
        settingsRepository.terminalSettings,
        settingsRepository.advancedSettings,
    ) { terminal, advanced -> SettingsUiData(terminal, advanced) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _repairState = MutableStateFlow<BootstrapState?>(null)
    val repairState: StateFlow<BootstrapState?> = _repairState.asStateFlow()

    fun updateTerminal(transform: (TerminalSettings) -> TerminalSettings) {
        viewModelScope.launch { settingsRepository.updateTerminalSettings(transform) }
    }

    fun setDebugLogging(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDebugLogging(enabled) }
    }

    fun resetAllSettings() {
        viewModelScope.launch { settingsRepository.resetAll() }
    }

    fun repairRuntime() {
        viewModelScope.launch {
            bootstrapManager.install(forceReinstall = true).collect { _repairState.value = it }
        }
    }
}
