# SkullShell — Native Android AI Coding Workspace

A native Android 12+ (API 31+) app that runs Claude Code, Codex CLI, OpenCode, and
Antigravity CLI in a real terminal on-device — real PTY, real Linux userland, real
process execution. No simulation, no mocked output.

**Start with [`ARCHITECTURE.md`](ARCHITECTURE.md)** — it's the record of the research
behind every non-obvious decision here (why a Termux bootstrap instead of a homegrown
userland, why Claude Code and Antigravity need a musl/glibc compatibility layer, why
Codex disables its own sandbox, what's actually been verified vs. still needs testing).
This README is the map; that document is the territory.

## Current status

A functional audit was completed with real builds, JVM tests and on-device
instrumentation on the dedicated SkullShell_UI_Review Android 15 x86_64 emulator.
See [docs/FEATURE_AUDIT.md](docs/FEATURE_AUDIT.md) for verified workflows and coverage
limits. Provider account sign-in, paid/API requests and physical ARM64 execution
require separate testing; install/launch checks do not prove authenticated usage.

## Building

Requirements: JDK 17+, Android SDK (platforms 35/36, build-tools 34+), NDK 27.2.12479018,
CMake 3.22.1 (for the native PTY module).

```
./gradlew assembleDebug      # debug APK, all modules
./gradlew test               # unit tests
./gradlew :app:installDebug  # install to a connected device/emulator
```

minSdk 31 (Android 12), targetSdk/compileSdk 36 (Android 16). No root required, no
hidden/non-SDK APIs, no device-specific vendor code.

## Module map

```
app/                  Compose UI, navigation, ViewModels, DI composition root, the
                       foreground service that hosts live terminal sessions.
core/                 Logging, Keystore-backed secrets, filesystem/workspace safety
                       (path-traversal prevention, SAF vs. app-private storage),
                       DataStore settings, Room persistence, GitHub/npm registry
                       resolvers, network monitoring.
terminal/             The terminal engine: a native (C/JNI) PTY module — our own code,
                       not vendored from Termux — plus a hand-written VT100/ANSI parser,
                       a bounded-scrollback terminal buffer, and the Compose renderer
                       and on-screen keyboard bar. Framework-free where possible, so
                       the parser/buffer are plain-JVM unit testable.
runtime/              The Linux userland: downloads and extracts Termux's official
                       bootstrap archive into app-private storage, wraps apt/dpkg for
                       package installs, runs real health checks, and (for providers
                       whose binaries need a libc this Bionic bootstrap doesn't have)
                       a proot-based foreign-libc layer.
provider-api/         The AIProvider interface every CLI implements — install, auth,
                       compatibility check, launch, version detection. The UI depends
                       only on this, never a concrete provider.
provider-claude/      Claude Code.
provider-codex/       OpenAI's Codex CLI.
provider-opencode/    OpenCode.
provider-antigravity/ Antigravity CLI (Google's Gemini CLI replacement).
```

Adding a fifth CLI means adding a `provider-*` module implementing `AIProvider` — the
app module and terminal engine don't need to change.

## Licensing

The application's own source is MIT licensed. The APK bundles unmodified PRoot,
its loader, libtalloc and libandroid-shmem with their respective licenses, source
archives and build recipes. Other runtime packages and agents are downloaded from
their publishers on the device. See [THIRD_PARTY_NOTICES](THIRD_PARTY_NOTICES/README.md).
