# Ternix — Native Android AI Coding Workspace

A native Android 12+ (API 31+) app that runs Claude Code, Codex CLI, OpenCode, and
Antigravity CLI in a real terminal on-device — real PTY, real Linux userland, real
process execution. No simulation, no mocked output.

**Start with [`ARCHITECTURE.md`](ARCHITECTURE.md)** — it's the record of the research
behind every non-obvious decision here (why a Termux bootstrap instead of a homegrown
userland, why Claude Code and Antigravity need a musl/glibc compatibility layer, why
Codex disables its own sandbox, what's actually been verified vs. still needs testing).
This README is the map; that document is the territory.

## Current status

Builds clean end-to-end: `./gradlew assembleDebug` produces an installable APK across
all 9 modules including the native JNI PTY layer (3 ABIs). Installed and exercised on
an x86_64 Android 15 emulator this session — see ARCHITECTURE.md's "Verified on-device"
section for exactly what was and wasn't confirmed working, including two real bugs found
and fixed via that testing (a foreground-service lifecycle bug, and a blank-screen-on-
process-exit bug). **Not yet verified on real ARM64 hardware, and no provider's install
flow has been run start-to-finish against the live network in one session** — both are
explicit, tracked gaps, not silent ones. Read ARCHITECTURE.md's "Known Limitations"
before treating any provider as production-ready.

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

This project's own code is MIT-licensed (see [`LICENSE`](LICENSE)). It downloads and
executes (never vendors) third-party binary distributions at runtime — Termux's
bootstrap, Alpine Linux's minirootfs, npm/GitHub-hosted CLI binaries. See
[`THIRD_PARTY_NOTICES/`](THIRD_PARTY_NOTICES/) for what's consumed and how, and
ARCHITECTURE.md §2 for why that consumption model keeps this app's own MIT license
independent of the GPL-licensed components it interacts with at runtime.
