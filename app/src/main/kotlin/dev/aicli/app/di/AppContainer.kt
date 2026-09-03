package dev.aicli.app.di

import android.content.Context
import dev.aicli.app.data.ProjectRepository
import dev.aicli.app.data.SessionManager
import dev.aicli.core.networking.NetworkMonitor
import dev.aicli.core.security.SecretStore
import dev.aicli.core.settings.SettingsRepository
import dev.aicli.provider.antigravity.AntigravityProvider
import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.claude.ClaudeProvider
import dev.aicli.provider.codex.CodexProvider
import dev.aicli.provider.opencode.OpenCodeProvider
import dev.aicli.runtime.bootstrap.BootstrapManager
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.runtime.health.RuntimeHealthChecker
import dev.aicli.runtime.pkg.PackageManager

/**
 * Manual composition root — this project has no Hilt/Dagger in its dependency catalog by design
 * (kept simple and explicit for a project this size). One instance, built in
 * [dev.aicli.app.AiCliApplication.onCreate] and handed to ViewModels via
 * [dev.aicli.app.di.ViewModelFactory].
 */
class AppContainer(private val context: Context) {
    val settingsRepository = SettingsRepository(context)
    val secretStore = SecretStore(context)
    val termuxEnvironment = TermuxEnvironment(context)
    val bootstrapManager = BootstrapManager(context)
    val packageManager = PackageManager(context)
    val healthChecker = RuntimeHealthChecker(context)
    val networkMonitor = NetworkMonitor(context)
    val projectRepository = ProjectRepository(context)
    val sessionManager = SessionManager(context)

    val providers: List<AIProvider> by lazy {
        listOf(
            ClaudeProvider(context),
            CodexProvider(context),
            OpenCodeProvider(context),
            AntigravityProvider(context),
        )
    }

    val providersById: Map<String, AIProvider> by lazy { providers.associateBy { it.id } }
}
