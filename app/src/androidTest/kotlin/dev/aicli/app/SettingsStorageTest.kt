package dev.aicli.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.aicli.core.settings.*
import dev.aicli.core.security.SecretStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SettingsStorageTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun preferencesPersistAndResetToDefaults() = runBlocking {
        val repository = SettingsRepository(context)
        val appearance = repository.appearanceSettings.first()
        val terminal = repository.terminalSettings.first()
        val advanced = repository.advancedSettings.first()
        try {
            repository.updateAppearance { AppearanceSettings(ThemeMode.DARK, false) }
            repository.updateTerminalSettings { it.copy(fontSize=18f, scrollbackLines=250, cursorBlink=false, copyOnSelect=true) }
            repository.setDebugLogging(true)
            val fresh = SettingsRepository(context)
            assertEquals(AppearanceSettings(ThemeMode.DARK, false), fresh.appearanceSettings.first())
            assertEquals(250, fresh.terminalSettings.first().scrollbackLines)
            assertEquals(18f, fresh.terminalSettings.first().fontSize)
            assertTrue(fresh.advancedSettings.first().debugLogging)
            repository.resetAll()
            assertEquals(AppearanceSettings(), fresh.appearanceSettings.first())
            assertEquals(TerminalSettings(), fresh.terminalSettings.first())
            assertEquals(AdvancedSettings(), fresh.advancedSettings.first())
        } finally {
            repository.updateAppearance { appearance }
            repository.updateTerminalSettings { terminal }
            repository.setDebugLogging(advanced.debugLogging)
        }
    }
    @Test fun keystoreBackedSecretsRoundTripAndDelete() {
        val key = "audit-fixture-key"
        val store = SecretStore(context)
        try {
            store.put(key, "audit-value")
            assertEquals("audit-value", SecretStore(context).get(key))
            assertTrue(store.has(key))
            store.remove(key)
            assertFalse(store.has(key))
        } finally { store.remove(key) }
    }
}
