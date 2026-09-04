package dev.aicli.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.data.ProjectRepository
import dev.aicli.app.data.SessionManager
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    projectRepository: ProjectRepository,
    private val sessionManager: SessionManager,
    private val environment: TermuxEnvironment,
) : ViewModel() {
    val projects = projectRepository.projects.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val sessions = sessionManager.sessions
    private val _runtimeInstalled = MutableStateFlow<Boolean?>(null)
    val runtimeInstalled = _runtimeInstalled.asStateFlow()
    fun controllerFor(id: String) = sessionManager.controllerFor(id)
    fun refreshRuntime() {
        viewModelScope.launch(Dispatchers.IO) { _runtimeInstalled.value = environment.isBootstrapInstalled }
    }
}
