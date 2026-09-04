package dev.aicli.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.ui.common.InstallProgressUi
import dev.aicli.app.ui.install.toInstallEvent
import dev.aicli.app.update.AppUpdateManager
import dev.aicli.app.update.UpdateCheckResult
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
    private val appUpdateManager: AppUpdateManager,
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

    /** Transient status line under "Check for update" — checking/up-to-date/failed messages that
     *  don't need a full progress sheet (only an actual download does, see [updateProgress]). */
    private val _updateStatus = MutableStateFlow<String?>(null)
    val updateStatus: StateFlow<String?> = _updateStatus.asStateFlow()

    private val _updateProgress = MutableStateFlow<InstallProgressUi?>(null)
    val updateProgress: StateFlow<InstallProgressUi?> = _updateProgress.asStateFlow()

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

    /**
     * Checking and "you're up to date" are quick, non-blocking results shown as a status line
     * (see [updateStatus]) — only an actual update turns into the shared download-progress sheet
     * ([updateProgress], same [InstallProgressUi] shape [repairProgress] uses), which ends by
     * handing off to Android's own package-installer UI, not a "tap to install" step of our own.
     */
    fun checkForUpdate() {
        viewModelScope.launch {
            _updateStatus.value = "Checking for updates…"
            when (val result = appUpdateManager.checkForUpdate()) {
                is UpdateCheckResult.UpToDate ->
                    _updateStatus.value = "You're on the latest version (${result.currentVersion})"
                is UpdateCheckResult.Failed ->
                    _updateStatus.value = "Update check failed: ${result.reason}"
                is UpdateCheckResult.Available -> {
                    _updateStatus.value = null
                    _updateProgress.value = InstallProgressUi("update", "SkullShell ${result.latestVersion}", null, done = false)
                    appUpdateManager.downloadAndInstall(result.downloadUrl, result.assetSize).collect { state ->
                        val event = state.toInstallEvent()
                        if (event is InstallEvent.Completed) {
                            // The system installer prompt now owns the screen — dismiss our sheet
                            // instead of showing a redundant "ready" state behind it.
                            _updateProgress.value = null
                        } else {
                            _updateProgress.value = InstallProgressUi(
                                "update", "SkullShell ${result.latestVersion}", event,
                                done = event is InstallEvent.Failed,
                            )
                        }
                    }
                }
            }
        }
    }

    fun dismissUpdateStatus() {
        _updateStatus.value = null
    }

    fun dismissUpdateProgress() {
        _updateProgress.value = null
    }
}
