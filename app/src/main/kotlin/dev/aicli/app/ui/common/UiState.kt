package dev.aicli.app.ui.common

/** Every screen's data is one of these — no screen is allowed to render a blank/uninitialized view. */
sealed class UiState<out T> {
    data object Loading : UiState<Nothing>()
    data class Success<T>(val data: T) : UiState<T>()
    data class Error(val message: String, val throwable: Throwable? = null) : UiState<Nothing>()
    data object Offline : UiState<Nothing>()
}
