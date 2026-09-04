package dev.aicli.app.ui.terminal

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.data.ProjectRepository
import dev.aicli.app.data.SessionManager
import dev.aicli.app.data.TerminalSessionController
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.ProviderLaunchRequest
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.terminal.PtyProcess
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import dev.aicli.core.settings.SettingsRepository
import dev.aicli.core.settings.TerminalSettings
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn

class TerminalViewModel(
    private val sessionManager: SessionManager,
    val providersById: Map<String, AIProvider>,
    private val termuxEnvironment: TermuxEnvironment,
    private val projectRepository: ProjectRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    val terminalSettings = settingsRepository.terminalSettings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TerminalSettings())
    val sessions = sessionManager.sessions
    private var resolvedFor: String? = null

    /**
     * Parses a `terminal/{sessionId}` nav arg of the form `provider:<id>` or `project:<id>`
     * (see [dev.aicli.app.ui.nav.Destinations]) into a real working directory and starts the
     * first session for it. Idempotent per [tag] so `LaunchedEffect(tag)` can call this safely
     * on every recomposition without spawning duplicate sessions.
     */
    fun resolveAndOpen(tag: String) {
        if (resolvedFor == tag) return
        resolvedFor = tag
        viewModelScope.launch {
            val (kind, id) = tag.split(":", limit = 2).let { it.getOrElse(0) { "" } to it.getOrElse(1) { "" } }
            when (kind) {
                "resume" -> if (sessions.value.any { it.id == id }) selectSession(id) else openShell(defaultWorkspaceDir())
                "project" -> {
                    val project = projectRepository.get(id)
                    if (project != null) projectRepository.markOpened(project.id)
                    val workingDirectory = project?.root?.rootDirectory?.absolutePath ?: defaultWorkspaceDir()
                    openShell(workingDirectory, projectId = id)
                }
                "provider" -> launchProvider(id, defaultWorkspaceDir())

                "session" -> if (sessions.value.isNotEmpty()) selectSession(sessions.value.last().id) else openShell(defaultWorkspaceDir())
                else -> openShell(defaultWorkspaceDir())
            }
        }
    }

    private fun defaultWorkspaceDir(): String =
        File(termuxEnvironment.homeDir.parentFile, "workspaces/default").apply { mkdirs() }.absolutePath

    /** Opens another shell using the same working directory as the current active session
     *  (or the default workspace if there is none yet) — backs the terminal screen's "+" tab. */
    fun newShellInCurrentContext() {
        val current = sessions.value.firstOrNull { it.id == activeSessionId.value }
        openShell(current?.workingDirectory ?: defaultWorkspaceDir(), projectId = current?.projectId)
    }

    private val _activeSessionId = MutableStateFlow<String?>(null)
    val activeSessionId: StateFlow<String?> = _activeSessionId.asStateFlow()

    private val _launchError = MutableStateFlow<String?>(null)
    val launchError: StateFlow<String?> = _launchError.asStateFlow()

    fun controllerFor(sessionId: String): TerminalSessionController? = sessionManager.controllerFor(sessionId)

    fun selectSession(sessionId: String) {
        _activeSessionId.value = sessionId
    }

    /** Opens a plain shell session (no AI provider) rooted at [workingDirectory]. */
    fun openShell(workingDirectory: String, projectId: String? = null) {
        viewModelScope.launch {
            if (!termuxEnvironment.isBootstrapInstalled) {
                AppLog.w(LogCategory.TERMINAL, "Refusing to open shell: bootstrap not installed at ${termuxEnvironment.prefixDir}")
                _launchError.value = "Install the Linux environment in Settings under Set up or repair runtime."
                return@launch
            }
            try {
                val shellPath = File(termuxEnvironment.prefixDir, "bin/bash").takeIf { it.exists() }?.absolutePath
                    ?: File(termuxEnvironment.prefixDir, "bin/sh").absolutePath
                val process = PtyProcess.spawn(
                    command = termuxEnvironment.wrapForExec(listOf(shellPath), workingDirectory),
                    environment = termuxEnvironment.buildEnvironment(),
                    workingDirectory = workingDirectory,
                    initialCols = 100,
                    initialRows = 30,
                )
                val controller = sessionManager.createSession("Shell", null, projectId, workingDirectory, process, 100, 30)
                _launchError.value = null
                _activeSessionId.value = controller.meta.id
            } catch (e: Exception) {
                AppLog.e(LogCategory.TERMINAL, "Failed to open shell: ${e.stackTraceToString()}")
                _launchError.value = "Couldn't start a shell: ${e.message}"
            }
        }
    }

    /** Launches [providerId]'s CLI rooted at [workingDirectory] — the provider owns compatibility/wrapping. */
    fun launchProvider(providerId: String, workingDirectory: String, projectId: String? = null) {
        viewModelScope.launch {
            val provider = providersById[providerId] ?: run {
                _launchError.value = "Unknown provider: $providerId"
                return@launch
            }
            try {
                val process = provider.launch(ProviderLaunchRequest(workingDirectory = workingDirectory, initialCols = 100, initialRows = 30))
                val controller = sessionManager.createSession(provider.displayName, providerId, projectId, workingDirectory, process, 100, 30)
                _launchError.value = null
                _activeSessionId.value = controller.meta.id
            } catch (e: Exception) {
                AppLog.e(LogCategory.TERMINAL, "Failed to launch ${provider.id}: ${e.stackTraceToString()}")
                _launchError.value = "Couldn't launch ${provider.displayName}: ${e.message}"
            }
        }
    }

    fun closeSession(sessionId: String) {
        viewModelScope.launch {
            sessionManager.closeSession(sessionId)
            if (_activeSessionId.value == sessionId) {
                _activeSessionId.value = sessions.value.firstOrNull { it.id != sessionId }?.id
            }
        }
    }

    fun clearLaunchError() { _launchError.value = null }
}
