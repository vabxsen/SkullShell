// JNI glue around POSIX pty(7)/forkpty(3). This is the entire native surface of the app: no
// proot, no privilege escalation, nothing Termux-specific. Bionic has implemented openpty/forkpty
// since API 23 (see NDK sysroot usr/include/pty.h) and this app's minSdk is 31, so we call them
// directly rather than hand-rolling openpty+fork+setsid+TIOCSCTTY ourselves.
//
// Design choice: this file only owns process lifecycle (fork/exec/resize/signal/wait). Reading
// and writing the PTY's master fd happens on the Kotlin side via android.os.ParcelFileDescriptor
// .adoptFd(int) (a public, non-hidden SDK API) wrapping java.io.FileInputStream/FileOutputStream —
// that keeps the JNI surface small and avoids re-implementing buffered I/O in C, and it avoids
// the private java.io.FileDescriptor(int) constructor that would require reflection.
//
// Caller contract: argv[0] must already be an absolute path. We deliberately do not implement
// PATH search (execvp/execvpe) here — the Kotlin layer always resolves the executable against a
// known PREFIX/bin (see TermuxEnvironment), so there is no ambiguity about which binary runs.

#include <jni.h>
#include <pty.h>
#include <unistd.h>
#include <errno.h>
#include <string.h>
#include <stdlib.h>
#include <signal.h>
#include <sys/wait.h>
#include <sys/ioctl.h>
#include <termios.h>
#include <android/log.h>

#define LOG_TAG "AICLI-PTY"
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static char *dupString(const char *src) {
    if (!src) return NULL;
    size_t len = strlen(src) + 1;
    char *out = malloc(len);
    if (out) memcpy(out, src, len);
    return out;
}

// Converts a jobjectArray of java.lang.String into a NULL-terminated char** suitable for
// execve()'s argv/envp. Caller owns the result and must pass it to freeCStrings().
static char **toCStringArray(JNIEnv *env, jobjectArray arr) {
    if (arr == NULL) {
        char **out = calloc(1, sizeof(char *));
        return out;
    }
    jsize len = (*env)->GetArrayLength(env, arr);
    char **out = calloc((size_t) len + 1, sizeof(char *));
    if (!out) return NULL;
    for (jsize i = 0; i < len; i++) {
        jstring js = (jstring) (*env)->GetObjectArrayElement(env, arr, i);
        const char *chars = (*env)->GetStringUTFChars(env, js, NULL);
        out[i] = dupString(chars);
        (*env)->ReleaseStringUTFChars(env, js, chars);
        (*env)->DeleteLocalRef(env, js);
    }
    out[len] = NULL;
    return out;
}

static void freeCStringArray(char **arr) {
    if (!arr) return;
    for (char **p = arr; *p; p++) free(*p);
    free(arr);
}

// Returns a 2-element long[]: [masterFd, pid] on success, or [-1, -errno] on failure.
JNIEXPORT jlongArray JNICALL
Java_dev_aicli_terminal_PtyNative_forkExec(JNIEnv *env, jclass clazz,
                                            jobjectArray jArgv, jobjectArray jEnvp,
                                            jstring jCwd, jint cols, jint rows) {
    (void) clazz;

    char **argv = toCStringArray(env, jArgv);
    char **envp = toCStringArray(env, jEnvp);
    const char *cwd = jCwd ? (*env)->GetStringUTFChars(env, jCwd, NULL) : NULL;

    if (!argv || !argv[0]) {
        LOGE("forkExec called with empty argv");
        freeCStringArray(argv);
        freeCStringArray(envp);
        if (cwd) (*env)->ReleaseStringUTFChars(env, jCwd, cwd);
        jlongArray result = (*env)->NewLongArray(env, 2);
        jlong vals[2] = {-1, -EINVAL};
        (*env)->SetLongArrayRegion(env, result, 0, 2, vals);
        return result;
    }

    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_col = (unsigned short) cols;
    ws.ws_row = (unsigned short) rows;

    int masterFd = -1;
    // forkpty() allocates the pty pair, and in the child calls login_tty(): setsid(), TIOCSCTTY,
    // and dup2 of the slave onto fd 0/1/2. That's exactly what a real terminal needs, and it means
    // the child is its own session+process-group leader — important for killProcessGroup below.
    pid_t pid = forkpty(&masterFd, NULL, NULL, &ws);

    if (pid < 0) {
        int err = errno;
        LOGE("forkpty failed: %s", strerror(err));
        freeCStringArray(argv);
        freeCStringArray(envp);
        if (cwd) (*env)->ReleaseStringUTFChars(env, jCwd, cwd);
        jlongArray result = (*env)->NewLongArray(env, 2);
        jlong vals[2] = {-1, -err};
        (*env)->SetLongArrayRegion(env, result, 0, 2, vals);
        return result;
    }

    if (pid == 0) {
        // Child process. Standard fds are already the pty slave; nothing else has been touched
        // by the JVM yet other than the fork itself, so it's safe to call async-signal-unsafe-ish
        // libc here in practice (no JVM/JNI calls past this point, which IS required).
        if (cwd && chdir(cwd) != 0) {
            // Leave failure diagnosis to the exec'd program itself; we still attempt exec so the
            // user sees a real error from the CLI/shell rather than a silent app-side abort.
        }
        execve(argv[0], argv, envp);
        // Only reached if execve failed. 127 is the conventional "command not found/not
        // executable" shell exit code; the parent's waitFor() will surface it as such.
        _exit(127);
    }

    // Parent process.
    freeCStringArray(argv);
    freeCStringArray(envp);
    if (cwd) (*env)->ReleaseStringUTFChars(env, jCwd, cwd);

    jlongArray result = (*env)->NewLongArray(env, 2);
    jlong vals[2] = {masterFd, pid};
    (*env)->SetLongArrayRegion(env, result, 0, 2, vals);
    return result;
}

// Returns 0 on success, -errno on failure.
JNIEXPORT jint JNICALL
Java_dev_aicli_terminal_PtyNative_resize(JNIEnv *env, jclass clazz, jint fd, jint cols, jint rows) {
    (void) env;
    (void) clazz;
    struct winsize ws;
    memset(&ws, 0, sizeof(ws));
    ws.ws_col = (unsigned short) cols;
    ws.ws_row = (unsigned short) rows;
    if (ioctl(fd, TIOCSWINSZ, &ws) < 0) {
        int err = errno;
        LOGE("TIOCSWINSZ failed on fd %d: %s", fd, strerror(err));
        return -err;
    }
    return 0;
}

// Signals the whole process group rooted at pid (see the login_tty() note above), not just the
// immediate child, so Ctrl+C reaches a CLI that a spawned shell forked. Returns 0 on success
// (including "already exited"), -errno otherwise.
JNIEXPORT jint JNICALL
Java_dev_aicli_terminal_PtyNative_killProcessGroup(JNIEnv *env, jclass clazz, jint pid, jint signal) {
    (void) env;
    (void) clazz;
    if (kill(-pid, signal) < 0) {
        if (errno == ESRCH) {
            return 0;
        }
        int err = errno;
        LOGE("kill(-%d, %d) failed: %s", pid, signal, strerror(err));
        return -err;
    }
    return 0;
}

// Blocks the calling thread until pid exits. Callers MUST invoke this off the main thread
// (the Kotlin PtyProcess wrapper does so via a dedicated Dispatchers.IO coroutine). Returns the
// process's exit code (0-255), 128+signal if it died from a signal, or -1 on a waitpid error.
JNIEXPORT jint JNICALL
Java_dev_aicli_terminal_PtyNative_waitFor(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    int status = 0;
    pid_t result;
    do {
        result = waitpid((pid_t) pid, &status, 0);
    } while (result < 0 && errno == EINTR);

    if (result < 0) {
        LOGE("waitpid(%d) failed: %s", pid, strerror(errno));
        return -1;
    }
    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    }
    if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return -1;
}

// Best-effort fd close for error-recovery paths where a ParcelFileDescriptor was never adopted
// on the Kotlin side. Returns 0 on success, -errno otherwise.
JNIEXPORT jint JNICALL
Java_dev_aicli_terminal_PtyNative_closeFd(JNIEnv *env, jclass clazz, jint fd) {
    (void) env;
    (void) clazz;
    if (close(fd) < 0) {
        return -errno;
    }
    return 0;
}
