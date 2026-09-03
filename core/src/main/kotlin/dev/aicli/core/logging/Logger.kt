package dev.aicli.core.logging

import android.util.Log
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentLinkedDeque

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

enum class LogCategory { APP, RUNTIME, TERMINAL, PROCESS, AUTH, NETWORK, INSTALLER, PROVIDER }

data class LogEntry(
    val timestampMs: Long,
    val level: LogLevel,
    val category: LogCategory,
    val message: String,
)

/**
 * Structured, in-memory (bounded) + logcat-backed logger. Never accepts secret-shaped values —
 * see [redact]. Diagnostics/export screens read [entries] or [events]; nothing else in the app
 * should call android.util.Log directly, so a security or privacy review has one place to look.
 */
object AppLog {
    private const val MAX_ENTRIES = 4000
    private val buffer = ConcurrentLinkedDeque<LogEntry>()
    private val _events = MutableSharedFlow<LogEntry>(extraBufferCapacity = 64)
    val events: SharedFlow<LogEntry> = _events.asSharedFlow()

    private val redactPatterns = listOf(
        Regex("(?i)(api[_-]?key|token|secret|password|authorization)\\s*[:=]\\s*\\S+"),
        Regex("sk-[A-Za-z0-9]{10,}"),
        Regex("ghp_[A-Za-z0-9]{20,}"),
        Regex("Bearer\\s+[A-Za-z0-9._-]+"),
    )

    fun redact(message: String): String {
        var result = message
        for (pattern in redactPatterns) {
            result = pattern.replace(result) { m -> m.value.substringBefore(':').substringBefore('=') + "=<redacted>" }
        }
        return result
    }

    fun log(level: LogLevel, category: LogCategory, message: String) {
        val safe = redact(message)
        val entry = LogEntry(System.currentTimeMillis(), level, category, safe)
        buffer.addLast(entry)
        while (buffer.size > MAX_ENTRIES) buffer.pollFirst()
        _events.tryEmit(entry)
        val tag = "aicli/${category.name}"
        when (level) {
            LogLevel.DEBUG -> Log.d(tag, safe)
            LogLevel.INFO -> Log.i(tag, safe)
            LogLevel.WARN -> Log.w(tag, safe)
            LogLevel.ERROR -> Log.e(tag, safe)
        }
    }

    fun d(category: LogCategory, message: String) = log(LogLevel.DEBUG, category, message)
    fun i(category: LogCategory, message: String) = log(LogLevel.INFO, category, message)
    fun w(category: LogCategory, message: String) = log(LogLevel.WARN, category, message)
    fun e(category: LogCategory, message: String) = log(LogLevel.ERROR, category, message)

    fun entries(): List<LogEntry> = buffer.toList()

    fun exportText(): String {
        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        return buffer.joinToString("\n") { e ->
            "${fmt.format(e.timestampMs)} [${e.level}] ${e.category}: ${e.message}"
        }
    }

    fun clear() = buffer.clear()
}
