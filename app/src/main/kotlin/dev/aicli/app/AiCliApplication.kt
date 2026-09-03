package dev.aicli.app

import android.app.Application
import dev.aicli.app.di.AppContainer

class AiCliApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
