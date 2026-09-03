package dev.aicli.provider.antigravity

import android.os.Build
import dev.aicli.provider.api.ProviderCompatibilityReport

/**
 * The genuinely hard provider — see ARCHITECTURE.md §5 "Antigravity CLI". Unlike the other three,
 * Antigravity's official Linux build dynamic-links against real glibc (not musl, not Bionic), and
 * independent Termux compatibility work has documented real, device-dependent breakage this app
 * cannot always fix:
 *
 *  - Needs an actual glibc runtime layer alongside the Bionic bootstrap (Termux's community
 *    `glibc` package provides one) — not installed automatically by this app; see the caveat below.
 *  - Go's `faccessat2` syscall is blocked by some devices' seccomp policy → SIGSYS crash. This is
 *    a kernel policy, not something an app-level env var can fix.
 *  - Go's TCMalloc allocator assumes a 48-bit user virtual address space; many Android devices run
 *    a 39-bit layout → allocation failure at startup. Also not fixable at the app level.
 *  - DNS/TLS *are* fixable safely (GODEBUG=netdns=go, SSL_CERT_FILE, clearing the LD_PRELOAD/
 *    LD_LIBRARY_PATH Termux injects for Bionic processes) — [AntigravityProvider] applies these.
 *
 * There is no reliable, public Android API to query a device's syscall seccomp filter or its
 * userspace VA width ahead of time, so this reports "compatible, with caveats" rather than trying
 * to predict success — [AntigravityProvider.detectState] detects the specific real failure
 * signatures (exit code, stderr pattern) if they occur and reports [dev.aicli.provider.api.ProviderState.Incompatible]
 * with the precise reason, instead of a generic error or a silent retry loop.
 */
object AntigravityCompatibility {
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
            summary = "Antigravity CLI's official binary can run here, but it needs a real glibc " +
                "layer (not just Bionic) and has known, device-dependent failure modes on Android " +
                "that this app cannot guarantee around. Best-effort support — verify on your device.",
            caveats = listOf(
                "Requires a glibc runtime alongside the Bionic bootstrap. This app does not " +
                    "install one automatically — see Settings → Providers → Antigravity for manual setup.",
                "Some devices' kernels block the faccessat2 syscall Go's runtime uses, causing an " +
                    "immediate crash (SIGSYS) that no app-level setting can work around.",
                "Some devices use a 39-bit virtual address layout that Go's allocator (TCMalloc) " +
                    "doesn't expect, also causing a startup crash outside this app's control.",
                "If Antigravity fails to start, this app reports the specific detected cause " +
                    "(kernel seccomp policy, address-space layout, missing glibc, ...) rather than " +
                    "a generic error — see Diagnostics.",
            ),
        )
    }
}
