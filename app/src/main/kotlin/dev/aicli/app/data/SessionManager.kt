package dev.aicli.app.data

import android.content.Context
import dev.aicli.core.db.AppDatabase
import dev.aicli.core.db.SessionEntity
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import dev.aicli.terminal.AnsiParser
import dev.aicli.terminal.PtyProcess
import dev.aicli.terminal.TerminalBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

data class SessionMeta(
    val id: String,
    val title: String,
    val providerId: String?,
    val projectId: String?,
    val workingDirectory: String,
    val createdAtEpochMillis: Long,
)

enum class SessionRunState { RUNNING, EXITED, KILLED_BY_OS, ERROR }

/** One live PTY session: process I/O, ANSI parsing into a [TerminalBuffer], and its own lifecycle. */
class TerminalSessionController(
    val meta: SessionMeta,
    val process: PtyProcess,
    val buffer: TerminalBuffer,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val parser = AnsiParser(buffer)

    private val _runState = MutableStateFlow(SessionRunState.RUNNING)
    val runState: StateFlow<SessionRunState> = _runState.asStateFlow()

    private val _exitCode = MutableStateFlow<Int?>(null)
    val exitCode: StateFlow<Int?> = _exitCode.asStateFlow()

    var onTitleChange: ((String) -> Unit)?
        get() = parser.onTitleChange
        set(value) { parser.onTitleChange = value }

    init {
        scope.launch {
            process.outputFlow.collect { bytes -> parser.feed(bytes) }
        }
        scope.launch {
            val code = process.waitForExit()
            _exitCode.value = code
            _runState.value = SessionRunState.EXITED
            AppLog.i(LogCategory.PROCESS, "Session ${meta.id} (${meta.title}) exited with $code")
        }
    }

    fun sendInput(bytes: ByteArray) {
        scope.launch { process.write(bytes) }
    }

    fun resize(cols: Int, rows: Int) {
        buffer.resize(cols, rows)
        process.resize(cols, rows)
    }

    fun destroy() {
        process.destroy()
        scope.cancel()
    }
}

/**
 * Owns every live [TerminalSessionController] for the process lifetime of
 * [dev.aicli.app.service.TerminalSessionService]. Session *metadata* (title, provider, working
 * directory, last-known state) is persisted via Room so the Projects/Sessions UI can show history
 * even for sessions no longer running; the live [TerminalSessionController] objects themselves
 * are not persisted (they can't be — a PTY doesn't survive process death) and are reconciled
 * against reality on startup: see [reconcileAfterRestart].
 */
class SessionManager(private val context: Context) {
    private val dao = AppDatabase.get(context).sessionDao()
    private val controllers = LinkedHashMap<String, TerminalSessionController>()

    private val _sessions = MutableStateFlow<List<SessionMeta>>(emptyList())
    val sessions: StateFlow<List<SessionMeta>> = _sessions.asStateFlow()

    fun controllerFor(sessionId: String): TerminalSessionController? = controllers[sessionId]

    suspend fun createSession(
        title: String,
        providerId: String?,
        projectId: String?,
        workingDirectory: String,
        process: PtyProcess,
        initialCols: Int,
        initialRows: Int,
    ): TerminalSessionController {
        val id = UUID.randomUUID().toString()
        val actualMeta = SessionMeta(
            id = id,
            title = title,
            providerId = providerId,
            projectId = projectId,
            workingDirectory = workingDirectory,
            createdAtEpochMillis = System.currentTimeMillis(),
        )
        val buffer = TerminalBuffer(initialCols, initialRows)
        val controller = TerminalSessionController(actualMeta, process, buffer)
        controllers[id] = controller
        // Start (or confirm) the foreground service now that there's an actual session to host —
        // not eagerly at app launch, which would show a misleading "0 active sessions" notification.
        androidx.core.content.ContextCompat.startForegroundService(
            context,
            android.content.Intent(context, dev.aicli.app.session.TerminalSessionService::class.java),
        )
        dao.upsert(
            SessionEntity(
                id = id, title = title, providerId = providerId, projectId = projectId,
                workingDirectory = actualMeta.workingDirectory, createdAtEpochMillis = actualMeta.createdAtEpochMillis,
                lastKnownPid = process.pid, state = "running", exitCode = null,
            )
        )
        refresh()
        return controller
    }

    suspend fun closeSession(sessionId: String) {
        controllers.remove(sessionId)?.destroy()
        dao.updateState(sessionId, "exited", null)
        refresh()
    }

    private fun refresh() {
        _sessions.value = controllers.values.map { it.meta }
    }

    /**
     * Called once at app/service startup: any session row still marked "running" in the database
     * did not survive if there's no live [TerminalSessionController] for it in this fresh process
     * — Android killed the process, the PTY is gone, and there is no way to reattach to it. Marked
     * honestly rather than shown as still running. See ARCHITECTURE.md §8.
     */
    suspend fun reconcileAfterRestart() {
        val running = dao.getRunning()
        for (row in running) {
            if (!controllers.containsKey(row.id)) {
                dao.updateState(row.id, "killed_by_os", null)
                AppLog.w(LogCategory.PROCESS, "Session ${row.id} (${row.title}) marked killed_by_os on restart — no live PTY found")
            }
        }
    }
}
