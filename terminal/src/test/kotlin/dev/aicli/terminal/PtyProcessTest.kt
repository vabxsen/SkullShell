package dev.aicli.terminal

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The native fork/exec/wait path (pty_native.c, PtyNative) cannot be exercised in a plain JVM
 * unit test — there's no .so to load and no real fork() available. Only the pure-Kotlin pieces
 * factored out specifically for testability are covered here; the native path is exercised
 * manually/instrumented on an emulator instead (see ARCHITECTURE.md).
 */
class PtyProcessTest {

    @Test
    fun `toEnvpArray formats KEY=VALUE pairs`() {
        val result = toEnvpArray(mapOf("PATH" to "/usr/bin", "HOME" to "/home/user"))
        assertThat(result.toList()).containsExactly("PATH=/usr/bin", "HOME=/home/user")
    }

    @Test
    fun `toEnvpArray handles empty map`() {
        assertThat(toEnvpArray(emptyMap()).toList()).isEmpty()
    }

    @Test
    fun `toEnvpArray preserves values containing equals signs`() {
        val result = toEnvpArray(mapOf("FOO" to "a=b=c"))
        assertThat(result.toList()).containsExactly("FOO=a=b=c")
    }

    @Test
    fun `PtySignal numbers match POSIX signal numbers`() {
        assertThat(PtySignal.INT.number).isEqualTo(2)
        assertThat(PtySignal.QUIT.number).isEqualTo(3)
        assertThat(PtySignal.KILL.number).isEqualTo(9)
        assertThat(PtySignal.TERM.number).isEqualTo(15)
        assertThat(PtySignal.TSTP.number).isEqualTo(20)
    }
}
