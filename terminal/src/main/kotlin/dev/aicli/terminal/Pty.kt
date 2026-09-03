package dev.aicli.terminal

/**
 * Raw JNI binding to `terminal/src/main/cpp/pty_native.c` (built as `libaicli_pty.so`). Not for
 * direct use outside this file — [PtyProcess] is the public API. Method names/signatures here
 * must exactly match the `Java_dev_aicli_terminal_PtyNative_*` symbols in the native source.
 */
internal object PtyNative {
    init {
        System.loadLibrary("aicli_pty")
    }

    /** Returns `[masterFd, pid]` on success, or `[-1, -errno]` on failure. */
    external fun forkExec(argv: Array<String>, envp: Array<String>, cwd: String?, cols: Int, rows: Int): LongArray

    /** Returns 0 on success, `-errno` on failure. */
    external fun resize(fd: Int, cols: Int, rows: Int): Int

    /** Signals the child's whole process group. Returns 0 on success (including already-exited), `-errno` otherwise. */
    external fun killProcessGroup(pid: Int, signal: Int): Int

    /** Blocks the calling thread until [pid] exits. Must only be called once per pid — see [PtyProcess]'s single waiter coroutine. */
    external fun waitFor(pid: Int): Int

    external fun closeFd(fd: Int): Int
}

// PtySignal and the spawn-failure exception live in PtyProcess.kt, next to the public API that
// actually uses them — keeping a single definition avoids the redeclaration this file used to
// have when both files were written concurrently.
