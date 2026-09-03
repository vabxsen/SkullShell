package dev.aicli.core.settings

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "aicli_settings")

enum class CursorStyle { BLOCK, BAR, UNDERLINE }
enum class TerminalTheme { DARK_DEFAULT, HIGH_CONTRAST, SOLARIZED_DARK }

/** App chrome light/dark preference — distinct from [TerminalTheme], which is the ANSI palette. */
enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class AppearanceSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColorEnabled: Boolean = false,
)

data class TerminalSettings(
    val fontSize: Float = 13f,
    val lineSpacing: Float = 1.0f,
    val scrollbackLines: Int = 5000,
    val cursorStyle: CursorStyle = CursorStyle.BLOCK,
    val cursorBlink: Boolean = true,
    val theme: TerminalTheme = TerminalTheme.DARK_DEFAULT,
    val copyOnSelect: Boolean = false,
)

data class AdvancedSettings(
    val debugLogging: Boolean = false,
)

/** Thin typed wrapper over DataStore; this is the *only* place raw preference keys are named. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val FONT_SIZE = floatPreferencesKey("terminal_font_size")
        val LINE_SPACING = floatPreferencesKey("terminal_line_spacing")
        val SCROLLBACK = intPreferencesKey("terminal_scrollback")
        val CURSOR_STYLE = stringPreferencesKey("terminal_cursor_style")
        val CURSOR_BLINK = booleanPreferencesKey("terminal_cursor_blink")
        val THEME = stringPreferencesKey("terminal_theme")
        val COPY_ON_SELECT = booleanPreferencesKey("terminal_copy_on_select")
        val DEBUG_LOGGING = booleanPreferencesKey("advanced_debug_logging")
        val APP_THEME_MODE = stringPreferencesKey("app_theme_mode")
        val DYNAMIC_COLOR_ENABLED = booleanPreferencesKey("dynamic_color_enabled")
    }

    val appearanceSettings: Flow<AppearanceSettings> = context.dataStore.data.map { prefs ->
        AppearanceSettings(
            themeMode = prefs[Keys.APP_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: false,
        )
    }

    suspend fun updateAppearance(update: (AppearanceSettings) -> AppearanceSettings) {
        context.dataStore.edit { prefs ->
            val current = AppearanceSettings(
                themeMode = prefs[Keys.APP_THEME_MODE]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
                dynamicColorEnabled = prefs[Keys.DYNAMIC_COLOR_ENABLED] ?: false,
            )
            val next = update(current)
            prefs[Keys.APP_THEME_MODE] = next.themeMode.name
            prefs[Keys.DYNAMIC_COLOR_ENABLED] = next.dynamicColorEnabled
        }
    }

    val terminalSettings: Flow<TerminalSettings> = context.dataStore.data.map { prefs ->
        TerminalSettings(
            fontSize = prefs[Keys.FONT_SIZE] ?: 13f,
            lineSpacing = prefs[Keys.LINE_SPACING] ?: 1.0f,
            scrollbackLines = prefs[Keys.SCROLLBACK] ?: 5000,
            cursorStyle = prefs[Keys.CURSOR_STYLE]?.let { runCatching { CursorStyle.valueOf(it) }.getOrNull() } ?: CursorStyle.BLOCK,
            cursorBlink = prefs[Keys.CURSOR_BLINK] ?: true,
            theme = prefs[Keys.THEME]?.let { runCatching { TerminalTheme.valueOf(it) }.getOrNull() } ?: TerminalTheme.DARK_DEFAULT,
            copyOnSelect = prefs[Keys.COPY_ON_SELECT] ?: false,
        )
    }

    val advancedSettings: Flow<AdvancedSettings> = context.dataStore.data.map { prefs ->
        AdvancedSettings(debugLogging = prefs[Keys.DEBUG_LOGGING] ?: false)
    }

    suspend fun updateTerminalSettings(update: (TerminalSettings) -> TerminalSettings) {
        context.dataStore.edit { prefs ->
            val current = TerminalSettings(
                fontSize = prefs[Keys.FONT_SIZE] ?: 13f,
                lineSpacing = prefs[Keys.LINE_SPACING] ?: 1.0f,
                scrollbackLines = prefs[Keys.SCROLLBACK] ?: 5000,
                cursorStyle = prefs[Keys.CURSOR_STYLE]?.let { runCatching { CursorStyle.valueOf(it) }.getOrNull() } ?: CursorStyle.BLOCK,
                cursorBlink = prefs[Keys.CURSOR_BLINK] ?: true,
                theme = prefs[Keys.THEME]?.let { runCatching { TerminalTheme.valueOf(it) }.getOrNull() } ?: TerminalTheme.DARK_DEFAULT,
                copyOnSelect = prefs[Keys.COPY_ON_SELECT] ?: false,
            )
            val next = update(current)
            prefs[Keys.FONT_SIZE] = next.fontSize
            prefs[Keys.LINE_SPACING] = next.lineSpacing
            prefs[Keys.SCROLLBACK] = next.scrollbackLines
            prefs[Keys.CURSOR_STYLE] = next.cursorStyle.name
            prefs[Keys.CURSOR_BLINK] = next.cursorBlink
            prefs[Keys.THEME] = next.theme.name
            prefs[Keys.COPY_ON_SELECT] = next.copyOnSelect
        }
    }

    suspend fun setDebugLogging(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DEBUG_LOGGING] = enabled }
    }

    suspend fun resetAll() {
        context.dataStore.edit { it.clear() }
    }
}
