package dev.aicli.terminal

import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

enum class PtySignal(val number: Int) {
    INT(2), QUIT(3), KILL(9), TERM(15), TSTP(20),
}

class PtySpawnException(message: String) : Exception(message)

/** Converts an environment map to `KEY=VALUE` envp entries. Pure/testable — see PtyProcessTest. */
internal fun toEnvpArray(environment: Map<String, String>): Array<String> =
    environment.map { (k, v) -> "$k=$v" }.toTypedArray()

/**
 * A running child process attached to a real PTY (see `pty_native.c`). All I/O happens off the
 * main thread. [outputFlow] emits raw bytes as they arrive; this class does no ANSI parsing —
 * that's [AnsiParser]'s job, one layer up.
 *
 * PTY read/write does NOT go through a custom native read()/write() pair: the master fd is
 * adopted into a [ParcelFileDescriptor] (a public SDK API, no reflection) and wrapped in plain
 * `java.io.FileInputStream`/`FileOutputStream`, which keeps the JNI surface to lifecycle calls
 * only (fork/exec/resize/signal/wait) — see the design-choice comment at the top of pty_native.c.
 */
class PtyProcess private constructor(
    val pid: Int,
    private val masterPfd: ParcelFileDescriptor,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val input = FileInputStream(masterPfd.fileDescriptor)
    private val output = FileOutputStream(masterPfd.fileDescriptor)
    private val destroyed = AtomicBoolean(false)
    private val exitDeferred = CompletableDeferred<Int>()

    init {
        // waitpid() can only be meaningfully awaited once per pid (a second call after the child
        // has been reaped returns ECHILD), so exactly one coroutine owns the blocking wait; every
        // caller of waitForExit() just awaits its result instead of each polling independently.
        scope.launch { exitDeferred.complete(PtyNative.waitFor(pid)) }
    }

    val outputFlow: Flow<ByteArray> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            val buffer = ByteArray(8192)
            try {
                while (isActive) {
                    val n = try {
                        input.read(buffer)
                    } catch (e: IOException) {
                        -1
                    }
                    if (n <= 0) break // EOF: child exited and closed its end of the pty.
                    send(buffer.copyOf(n))
                }
            } finally {
                close()
            }
        }
        awaitClose { job.cancel() }
    }

    suspend fun write(data: ByteArray) = withContext(Dispatchers.IO) {
        try {
            output.write(data)
            output.flush()
        } catch (e: IOException) {
            // A write racing the child's exit is expected during teardown; outputFlow's EOF and
            // waitForExit() are the sources of truth for "did the process end," not this.
        }
    }

    fun resize(cols: Int, rows: Int) {
        PtyNative.resize(masterPfd.fd, cols, rows)
    }

    fun sendSignal(signal: PtySignal) {
        PtyNative.killProcessGroup(pid, signal.number)
    }

    /** Suspends until the child exits. Exit code convention: 0-255 normal exit, 128+signal if killed by a signal. */
    suspend fun waitForExit(): Int = exitDeferred.await()

    /** Idempotent, safe from any thread: force-kills the child (if still alive) and tears down I/O. */
    fun destroy() {
        if (!destroyed.compareAndSet(false, true)) return
        PtyNative.killProcessGroup(pid, PtySignal.KILL.number)
        scope.cancel()
        runCatching { masterPfd.close() }
    }

    companion object {
        suspend fun spawn(
            command: List<String>,
            environment: Map<String, String>,
            workingDirectory: String,
            initialCols: Int,
            initialRows: Int,
        ): PtyProcess = withContext(Dispatchers.IO) {
            require(command.isNotEmpty()) { "command must have at least argv[0]" }
            val result = PtyNative.forkExec(
                command.toTypedArray(),
                toEnvpArray(environment),
                workingDirectory,
                initialCols,
                initialRows,
            )
            val masterFd = result[0].toInt()
            val pidOrNegErrno = result[1].toInt()
            if (masterFd < 0) {
                throw PtySpawnException("forkpty failed (errno ${-pidOrNegErrno}) launching ${command.first()}")
            }
            PtyProcess(pidOrNegErrno, ParcelFileDescriptor.adoptFd(masterFd))
        }
    }
}
