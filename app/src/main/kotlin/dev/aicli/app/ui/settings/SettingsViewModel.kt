package dev.aicli.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.ui.common.InstallProgressUi
import dev.aicli.app.ui.install.toInstallEvent
import dev.aicli.core.settings.AdvancedSettings
import dev.aicli.core.settings.AppearanceSettings
import dev.aicli.core.settings.SettingsRepository
import dev.aicli.core.settings.TerminalSettings
import dev.aicli.core.settings.ThemeMode
import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.InstallEvent
import dev.aicli.runtime.bootstrap.BootstrapManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiData(val appearance: AppearanceSettings, val terminal: TerminalSettings, val advanced: AdvancedSettings)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val bootstrapManager: BootstrapManager,
    val providers: List<AIProvider>,
) : ViewModel() {

    val uiData: StateFlow<SettingsUiData?> = combine(
        settingsRepository.appearanceSettings,
        settingsRepository.terminalSettings,
        settingsRepository.advancedSettings,
    ) { appearance, terminal, advanced -> SettingsUiData(appearance, terminal, advanced) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _repairProgress = MutableStateFlow<InstallProgressUi?>(null)
    val repairProgress: StateFlow<InstallProgressUi?> = _repairProgress.asStateFlow()

    fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) {
        viewModelScope.launch { settingsRepository.updateAppearance(transform) }
    }

    fun setThemeMode(mode: ThemeMode) {
        updateAppearance { it.copy(themeMode = mode) }
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        updateAppearance { it.copy(dynamicColorEnabled = enabled) }
    }

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
            _repairProgress.value = InstallProgressUi("bootstrap", "Linux runtime", null, done = false)
            bootstrapManager.install(forceReinstall = true).collect { state ->
                val event = state.toInstallEvent()
                _repairProgress.value = InstallProgressUi(
                    "bootstrap", "Linux runtime", event,
                    done = event is InstallEvent.Completed || event is InstallEvent.Failed,
                )
            }
        }
    }

    fun dismissRepairProgress() {
        _repairProgress.value = null
    }
}
