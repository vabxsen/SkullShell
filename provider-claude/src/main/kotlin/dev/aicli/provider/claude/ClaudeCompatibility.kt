package dev.aicli.provider.claude

import android.content.Context
import dev.aicli.provider.api.ProviderCompatibilityReport
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.runtime.foreignlibc.ForeignLibcRuntime
import dev.aicli.runtime.foreignlibc.LibcFlavor

/**
 * Claude Code compatibility findings, verified 2026-09-03 by downloading and inspecting the
 * actual npm package (see ARCHITECTURE.md's Claude Code section for the corrected story):
 *
 * - `@anthropic-ai/claude-code` publishes a real `linux-arm64-musl` build
 *   (`@anthropic-ai/claude-code-linux-arm64-musl`, and `-linux-x64-musl` for the x86_64 emulator
 *   used to develop this app). `file` on the extracted binary reports:
 *   `ELF 64-bit LSB executable, ARM aarch64, dynamically linked, interpreter /lib/ld-musl-aarch64.so.1`
 *   — a real, absolute, hardcoded loader path that doesn't exist in our Bionic bootstrap.
 * - The upstream native installer script and npm's own platform auto-detection have documented,
 *   recurring bugs picking the wrong arch/libc variant (see `anthropics/claude-code` issues
 *   referenced in ARCHITECTURE.md) — Termux's own Node build reports `process.platform ===
 *   "android"`, which nothing upstream expects. We don't run the upstream installer at all; we
 *   resolve and download the exact `-linux-<arch>-musl` package ourselves (see
 *   [ClaudeInstaller]).
 * - The musl-loader problem is solved generically by [ForeignLibcRuntime] with [LibcFlavor.MUSL]
 *   — an Alpine Linux minirootfs (which genuinely has `/lib/ld-musl-*.so.1`) run via `proot`.
 *   This path was verified end-to-end for URL resolution and archive extraction in this session;
 *   it was NOT verified running the real 200MB `claude` binary against real hardware (no ARM64
 *   device or emulator was available — see ARCHITECTURE.md's Known Limitations). Treat this as
 *   "should work, mechanism is sound and each piece independently verified" rather than
 *   "confirmed working end-to-end."
 */
object ClaudeCompatibility {
    suspend fun check(context: Context): ProviderCompatibilityReport {
        val env = TermuxEnvironment(context)
        if (!env.isBootstrapInstalled) {
            return ProviderCompatibilityReport(
                compatible = false,
                summary = "Linux userland isn't installed yet — set up the runtime from Home first.",
            )
        }
        val musl = ForeignLibcRuntime(context)
        return ProviderCompatibilityReport(
            compatible = true,
            summary = if (musl.isInstalled(LibcFlavor.MUSL)) {
                "Compatible — musl runtime layer is installed."
            } else {
                "Compatible, but requires a one-time ~3MB musl runtime download on first install."
            },
            caveats = listOf(
                "Claude Code's Linux binary links against musl libc, not this device's Bionic libc. " +
                    "This app runs it inside a small Alpine Linux (musl) environment via proot " +
                    "(no root required) rather than skip the incompatibility.",
                "Not yet verified on real ARM64 hardware in this build — see ARCHITECTURE.md.",
            ),
        )
    }
}
