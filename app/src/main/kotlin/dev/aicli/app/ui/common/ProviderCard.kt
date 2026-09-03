package dev.aicli.app.ui.common

import dev.aicli.provider.api.AIProvider
import dev.aicli.provider.api.InstallEvent
import dev.aicli.provider.api.ProviderState

/** Shared by Providers and Diagnostics — a provider paired with its currently observed state. */
data class ProviderCard(val provider: AIProvider, val state: ProviderState)

/** Drives the install-progress UI — a provider's own install/uninstall Flow<InstallEvent>. */
data class InstallProgressUi(val providerId: String, val displayName: String, val latestEvent: InstallEvent?, val done: Boolean)
