package dev.aicli.provider.antigravity

import dev.aicli.runtime.bootstrap.TermuxEnvironment

/**
 * The safe, reversible subset of the environment fixes documented in ARCHITECTURE.md §5 for
 * running a glibc-linked Go binary inside a Termux-shaped Bionic bootstrap:
 *  - `GODEBUG=netdns=go` forces Go's pure-Go DNS resolver instead of glibc's, which can't see
 *    the bootstrap's resolver config.
 *  - `SSL_CERT_FILE` points at the bootstrap's own CA bundle, since the glibc process has no
 *    other way to find it.
 *  - `LD_PRELOAD`/`LD_LIBRARY_PATH` are explicitly cleared: [TermuxEnvironment.buildEnvironment]
 *    sets `LD_PRELOAD` to termux-exec's *Bionic* shim for every other provider, but injecting a
 *    Bionic preload into a glibc process breaks its own dynamic linker's symbol resolution.
 *
 * What this does NOT and cannot fix — a blocked `faccessat2` syscall (kernel seccomp policy) or a
 * 39-bit virtual-address layout tripping up Go's allocator — is exactly why
 * [AntigravityProvider.detectState] pattern-matches for those failures separately instead of
 * assuming this environment alone guarantees a working launch.
 */
object AntigravityEnvironment {
    fun build(env: TermuxEnvironment): Map<String, String> {
        val base = env.buildEnvironment(
            extra = mapOf(
                "GODEBUG" to "netdns=go",
                "SSL_CERT_FILE" to "${env.prefixDir.absolutePath}/etc/tls/cert.pem",
            ),
        ).toMutableMap()
        base.remove("LD_PRELOAD")
        base.remove("LD_LIBRARY_PATH")
        return base
    }
}
