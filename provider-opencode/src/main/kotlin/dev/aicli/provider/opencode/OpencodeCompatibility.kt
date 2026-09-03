package dev.aicli.provider.opencode

import android.os.Build
import dev.aicli.provider.api.ProviderCompatibilityReport

/**
 * See ARCHITECTURE.md §5 "OpenCode". Its npm postinstall keys off `process.platform`, which
 * Termux's own Node.js build reports as `"android"` rather than `"linux"`, so its
 * optionalDependencies resolution looks for a package that was never published
 * (upstream: anomalyco/opencode#12515). [OpencodeInstaller] sidesteps this entirely by fetching
 * the real linux-arm64/linux-x64 binary directly from GitHub Releases instead of going through npm.
 */
object OpencodeCompatibility {
    fun check(): ProviderCompatibilityReport {
        val supported = Build.SUPPORTED_ABIS.toList()
        val hasArm64OrX64 = "arm64-v8a" in supported || "x86_64" in supported
        if (!hasArm64OrX64) {
            return ProviderCompatibilityReport(
                compatible = false,
                summary = "No arm64-v8a or x86_64 ABI available (device reports: ${supported.joinToString()}).",
            )
        }
        return ProviderCompatibilityReport(
            compatible = true,
            summary = "OpenCode's real linux-arm64/x64 binary is fetched directly from GitHub Releases, " +
                "bypassing its npm installer's known Android platform-detection bug.",
            caveats = listOf(
                "OpenCode's own npm/curl installer does not work unmodified on Android/Termux " +
                    "(process.platform reports 'android', not 'linux') — this app works around it " +
                    "by downloading the upstream Linux binary release directly rather than patching " +
                    "OpenCode's installer.",
            ),
        )
    }
}
