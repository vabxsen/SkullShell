package dev.aicli.app.ui.projects

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.aicli.app.data.ProjectRepository
import dev.aicli.app.ui.common.UiState
import dev.aicli.core.filesystem.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProjectsViewModel(private val repository: ProjectRepository) : ViewModel() {

    val uiState: StateFlow<UiState<List<Project>>> = repository.projects
        .map<List<Project>, UiState<List<Project>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(it.message ?: "Failed to load projects", it)) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UiState.Loading)

    private val _events = MutableStateFlow<String?>(null)
    val events: StateFlow<String?> = _events

    fun createAppWorkspace(name: String) {
        viewModelScope.launch {
            runCatching { repository.createAppWorkspace(name) }
                .onFailure { _events.value = "Couldn't create workspace: ${it.message}" }
        }
    }

    fun registerExternalProject(name: String, treeUri: Uri) {
        viewModelScope.launch {
            runCatching { repository.registerExternalProject(name, treeUri) }
                .onFailure { _events.value = "Couldn't open project: ${it.message}" }
        }
    }

    fun removeProject(projectId: String) {
        viewModelScope.launch { repository.remove(projectId) }
    }

    fun clearEvent() { _events.value = null }
}
