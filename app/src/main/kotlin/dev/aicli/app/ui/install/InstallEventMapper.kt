package dev.aicli.app.ui.install

import dev.aicli.app.update.UpdateDownloadState
import dev.aicli.provider.api.InstallEvent
import dev.aicli.runtime.bootstrap.BootstrapState

/**
 * [InstallEvent] (provider-api) is this app's one canonical "installing something" shape —
 * step + optional progress fraction + optional log line, terminated by Completed/Failed. Rather
 * than invent a parallel `InstallStep`/`InstallPhase` type, runtime flows that aren't already
 * shaped this way (like [BootstrapState], the one-time Linux userland bootstrap) get a small
 * pure mapper into it, so [dev.aicli.app.ui.components.InstallProgressSheet] is the single
 * install-progress UI for the whole app, not one of several.
 */
fun BootstrapState.toInstallEvent(): InstallEvent = when (this) {
    is BootstrapState.NotInstalled -> InstallEvent.Progress("Not installed", null)
    is BootstrapState.Resolving -> InstallEvent.Progress(message, null)
    is BootstrapState.Downloading -> InstallEvent.Progress(
        step = "Downloading (${bytesDownloaded / (1024 * 1024)}MB / ${totalBytes / (1024 * 1024)}MB)",
        fraction = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else null,
    )
    is BootstrapState.Extracting -> InstallEvent.Progress(
        step = "Extracting ($filesExtracted/$totalFiles)",
        fraction = if (totalFiles > 0) filesExtracted.toFloat() / totalFiles else null,
    )
    is BootstrapState.Ready -> InstallEvent.Completed
    is BootstrapState.Failed -> InstallEvent.Failed("bootstrap", reason, throwable)
}

/** Same fold for [dev.aicli.app.update.AppUpdateManager]'s download flow — see the doc above. */
fun UpdateDownloadState.toInstallEvent(): InstallEvent = when (this) {
    is UpdateDownloadState.Downloading -> InstallEvent.Progress(
        step = "Downloading (${bytesDownloaded / (1024 * 1024)}MB / ${totalBytes / (1024 * 1024)}MB)",
        fraction = if (totalBytes > 0) bytesDownloaded.toFloat() / totalBytes else null,
    )
    is UpdateDownloadState.ReadyToInstall -> InstallEvent.Completed
    is UpdateDownloadState.Failed -> InstallEvent.Failed("update", reason, throwable)
}
