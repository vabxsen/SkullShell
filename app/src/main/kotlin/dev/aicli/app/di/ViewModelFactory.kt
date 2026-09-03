package dev.aicli.app.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import dev.aicli.app.ui.diagnostics.DiagnosticsViewModel
import dev.aicli.app.ui.projects.ProjectsViewModel
import dev.aicli.app.ui.providers.AuthenticationViewModel
import dev.aicli.app.ui.providers.ProvidersViewModel
import dev.aicli.app.ui.settings.SettingsViewModel
import dev.aicli.app.ui.terminal.TerminalViewModel

/** Small manual ViewModel factory — see [AppContainer]'s doc comment for why this isn't Hilt. */
class ViewModelFactory(private val container: AppContainer) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        return when (modelClass) {
            ProjectsViewModel::class.java -> ProjectsViewModel(container.projectRepository) as T
            TerminalViewModel::class.java -> TerminalViewModel(container.sessionManager, container.providersById, container.termuxEnvironment, container.projectRepository) as T
            SettingsViewModel::class.java -> SettingsViewModel(container.settingsRepository, container.bootstrapManager, container.providers) as T
            DiagnosticsViewModel::class.java -> DiagnosticsViewModel(container.healthChecker, container.providers, container.providerStateRepository) as T
            ProvidersViewModel::class.java -> ProvidersViewModel(container.providers, container.providerStateRepository) as T
            AuthenticationViewModel::class.java -> AuthenticationViewModel(container.providersById, container.sessionManager, container.termuxEnvironment) as T
            else -> error("Unknown ViewModel class: $modelClass")
        }
    }
}
