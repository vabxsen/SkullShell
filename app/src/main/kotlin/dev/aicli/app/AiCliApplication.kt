package dev.aicli.app

import android.app.Application
import dev.aicli.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import dev.aicli.core.logging.AppLog

class AiCliApplication : Application() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        scope.launch { container.sessionManager.reconcileAfterRestart() }
        scope.launch { container.settingsRepository.advancedSettings.collect { AppLog.debugEnabled = it.debugLogging } }
    }
}
