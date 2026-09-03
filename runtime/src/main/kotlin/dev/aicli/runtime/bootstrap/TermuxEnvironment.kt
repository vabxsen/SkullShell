package dev.aicli.runtime.bootstrap

import android.content.Context
import android.os.Build
import java.io.File

/**
 * Resolves the on-device layout of the Linux userland this app manages, and builds the
 * environment every spawned process (package-manager commands, and ultimately the CLIs
 * themselves) must run with.
 *
 * The layout intentionally mirrors Termux's own `$PREFIX` convention
 * (`/data/data/com.termux/files/usr` for the real Termux app) so that Termux's bootstrap
 * archive and its package repository's binaries — which hardcode this shape into RPATHs,
 * shebangs, and `dpkg` metadata — work unmodified once extracted into *our* app's private
 * storage instead of theirs. See ARCHITECTURE.md §2.
 */
class TermuxEnvironment(context: Context) {
    private val filesDir = context.filesDir

    /** Root of the extracted bootstrap, analogous to Termux's `$PREFIX`. */
    val prefixDir: File = File(filesDir, "usr")

    /** Analogous to Termux's `$HOME`; where CLI config/state/dotfiles live, not project code. */
    val homeDir: File = File(filesDir, "home")

    val tmpDir: File = File(prefixDir, "tmp")

    /**
     * The device ABI we install a bootstrap for. Termux publishes bootstrap-{aarch64,arm,i686,x86_64}.zip.
     *
     * Verified on a real device (x86_64 emulator, `ro.product.cpu.abilist=x86_64,arm64-v8a`,
     * `ro.enable.native.bridge.exec=1`): [Build.SUPPORTED_ABIS] listing `arm64-v8a` here reflects
     * this device's ability to run *APK-bundled native libraries* through Android's own
     * PackageManager-managed native-bridge translation (ndk_translation) — it does **not** mean a
     * standalone ELF binary handed to a raw `execve()` gets transparently translated. Trusting
     * SUPPORTED_ABIS here (as an earlier version of this code did) resolves `bootstrap-aarch64.zip`
     * on this x86_64 device, and the *system's own* `/system/bin/linker64` then refuses to load it
     * ("is for EM_AARCH64 instead of EM_X86_64") — confirmed by direct testing. `System.getProperty
     * ("os.arch")` reflects the ART runtime's actual native architecture — the one anything we
     * directly exec() actually runs as — and is what a plain process spawn needs, not what an APK's
     * bundled .so can be translated from.
     */
    val termuxAbi: String by lazy {
        val osArch = System.getProperty("os.arch")
        when (osArch) {
            "aarch64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            "armv7l", "arm" -> "arm"
            "x86", "i686" -> "i686"
            else -> {
                // Unrecognized os.arch string: fall back to SUPPORTED_ABIS, best-effort.
                val supported = Build.SUPPORTED_ABIS.toList()
                when {
                    "x86_64" in supported -> "x86_64"
                    "arm64-v8a" in supported -> "aarch64"
                    "armeabi-v7a" in supported -> "arm"
                    "x86" in supported -> "i686"
                    else -> error("No supported ABI matches a known Termux bootstrap: os.arch='$osArch', SUPPORTED_ABIS=$supported")
                }
            }
        }
    }

    fun ensureDirectoriesExist() {
        prefixDir.mkdirs()
        homeDir.mkdirs()
        tmpDir.mkdirs()
    }

    val isBootstrapInstalled: Boolean
        get() = File(prefixDir, "bin/bash").exists() || File(prefixDir, "bin/sh").exists()

    val termuxExecPreloadLib: File
        get() = File(prefixDir, "lib/libtermux-exec-ld-preload.so")

    val hasTermuxExec: Boolean
        get() = termuxExecPreloadLib.exists()

    /**
     * Full replacement environment for a process running inside the bootstrap. This is never
     * merged with the Zygote/app process's own environment — CLIs must see a clean, Linux-shaped
     * environment, not Android's app-process env vars.
     *
     * [LD_PRELOAD] is set to termux-exec's shim whenever it's installed (see ARCHITECTURE.md
     * §2a) — without it, exec of any bootstrap binary fails on API 29+ with EACCES. It is
     * intentionally *not* set before termux-exec itself is installed (first-run bootstrap
     * extraction has to happen through a different path — see [BootstrapManager]).
     */
    fun buildEnvironment(extra: Map<String, String> = emptyMap()): Map<String, String> {
        val path = listOf(
            "${prefixDir.absolutePath}/bin",
            "${prefixDir.absolutePath}/bin/applets",
        ).joinToString(":")

        val base = mutableMapOf(
            "PREFIX" to prefixDir.absolutePath,
            "HOME" to homeDir.absolutePath,
            "TMPDIR" to tmpDir.absolutePath,
            "PATH" to path,
            "LD_LIBRARY_PATH" to "${prefixDir.absolutePath}/lib",
            "LANG" to "en_US.UTF-8",
            "TERM" to "xterm-256color",
            "COLORTERM" to "truecolor",
            "SSL_CERT_FILE" to "${prefixDir.absolutePath}/etc/tls/cert.pem",
            "npm_config_cache" to "${homeDir.absolutePath}/.npm-cache",
        )
        if (hasTermuxExec) {
            base["LD_PRELOAD"] = termuxExecPreloadLib.absolutePath
        }
        base.putAll(extra)
        return base
    }

    /**
     * Wraps [command] so its *first* exec actually succeeds on API 29+, then relies on
     * [buildEnvironment]'s `LD_PRELOAD` to carry termux-exec's interception into every process
     * that binary itself subsequently forks — see ARCHITECTURE.md §2a for the full story.
     *
     * Confirmed by direct on-device testing (`adb shell run-as ... `, permissive=0): Android's
     * SELinux policy denies `execute_no_trans` for any `app_data_file`-labeled file execed
     * directly by an `untrusted_app` process — this is not a config quirk, it applies to *every*
     * binary in the bootstrap, every time, with no per-app opt-out. `LD_PRELOAD` alone cannot
     * prevent this: it only affects a process's own libc symbol resolution *after* it starts, and
     * this denial happens at the kernel during the `execve()` syscall itself, before any new
     * process image (or its LD_PRELOAD) exists. termux-exec's actual fix (confirmed against its
     * own `system_linker_exec` naming) is to route the very first exec of a process tree through
     * `/system/bin/linker64` (a `system_file`-labeled, SELinux-trusted binary) instead of the
     * kernel execve()-ing the target file directly — the linker then manually loads and maps the
     * real target's ELF segments itself. This still respects `LD_PRELOAD` in the target's own
     * envp during that manual load, so once this first process is running, its *own* subsequent
     * execve() calls (e.g. bash forking `ls`) are caught by the now-loaded termux-exec shim
     * without this app needing to wrap every single spawn — only the outermost one per session.
     */
    fun wrapForExec(command: List<String>): List<String> {
        require(command.isNotEmpty()) { "command must have at least the target binary" }
        val linker = systemLinkerPath()
        return listOf(linker) + command
    }

    private fun systemLinkerPath(): String = when (termuxAbi) {
        "aarch64", "x86_64" -> "/system/bin/linker64"
        else -> "/system/bin/linker"
    }
}
