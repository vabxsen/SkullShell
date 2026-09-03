package dev.aicli.app.data

import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.ProviderState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Shared aggregation of every provider's [ProviderState], observed by Providers and Diagnostics
 * so neither duplicates the other's `providers.map { it.detectState() }` logic. Stays pull-based
 * ([refreshAll] must be called explicitly) rather than a polling loop — `detectState()` performs
 * real shell/file IO per provider, so continuous polling would be a real perf/battery regression.
 */
class ProviderStateRepository(private val providers: List<AIProvider>) {
    private val _states = MutableStateFlow<Map<String, ProviderState>>(emptyMap())
    val states: StateFlow<Map<String, ProviderState>> = _states.asStateFlow()

    suspend fun refreshAll() {
        _states.value = providers.associate { it.id to it.detectState() }
    }
}
