package dev.aicli.provider.codex

import android.os.Build
import dev.aicli.provider.api.ProviderCompatibilityReport

/**
 * See ARCHITECTURE.md §5 "Codex CLI". The binary itself (Rust, musl-static) is not the
 * compatibility problem — it needs no glibc, no Bionic shim. The problem is Codex's *default*
 * Linux sandbox (Landlock LSM + a bubblewrap fallback requiring `unshare(CLONE_NEWUSER)`), which
 * stock Android kernels restrict for unprivileged apps. [CodexInstaller] writes
 * `sandbox_mode = "danger-full-access"` into `~/.codex/config.toml` on install specifically to
 * avoid Codex silently failing every command with a namespace-creation error; our own
 * [dev.aicli.core.filesystem] workspace boundary is the isolation layer that actually applies
 * here instead.
 */
object CodexCompatibility {
    fun check(): ProviderCompatibilityReport {
        val supported = Build.SUPPORTED_ABIS.toList()
        val hasArm64OrX64 = "arm64-v8a" in supported || "x86_64" in supported
        if (!hasArm64OrX64) {
            return ProviderCompatibilityReport(
                compatible = false,
                summary = "No arm64-v8a or x86_64 ABI available (device reports: ${supported.joinToString()}). " +
                    "Codex CLI publishes musl builds for aarch64/x86_64 Linux only.",
            )
        }
        return ProviderCompatibilityReport(
            compatible = true,
            summary = "Codex CLI's statically-linked musl binary runs directly; no glibc or Bionic shim needed.",
            caveats = listOf(
                "Codex's built-in Landlock/bubblewrap sandbox cannot create unprivileged user " +
                    "namespaces on Android, so this app runs Codex with sandboxing disabled " +
                    "(sandbox_mode = danger-full-access) and relies on the app's own workspace " +
                    "boundary instead. Codex will have the same filesystem access as any other " +
                    "process this app spawns — nothing beyond the selected project directory.",
            ),
        )
    }
}
