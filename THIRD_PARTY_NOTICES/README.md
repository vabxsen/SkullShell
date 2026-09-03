# Third-Party Notices

This app's own source is original work, MIT-licensed (see [`LICENSE`](../LICENSE) at
the repository root). It **downloads and executes, at runtime, on the user's device**,
several third-party distributions it does not vendor or redistribute in the APK. This
file records what those are, where they come from, and why consuming them this way
doesn't extend their license to this app's own code.

## Termux bootstrap (`runtime/.../bootstrap/BootstrapManager.kt`)

- **What**: `bootstrap-<abi>.zip` from `github.com/termux/termux-packages` releases —
  a prebuilt Bionic-targeted Linux userland (bash, coreutils, dpkg/apt, and, once
  packages are installed, Node.js, git, etc.), plus `termux-exec`'s LD_PRELOAD shim.
- **License**: individual packages vary (mostly GPL/LGPL/MIT/BSD upstream projects
  cross-compiled by the termux-packages project); `termux-exec` itself is
  Apache-2.0/MIT per its own `share/doc/termux-exec/licenses/` (verified in the
  extracted archive).
- **How consumed**: downloaded and extracted into this app's own private storage at
  first run, exactly as the official `termux-app` APK does on a user's device — this
  app does not vendor Termux's application source (which is GPLv3), only consumes its
  public binary package distribution, the same way an `apt` user on Debian consumes
  Debian's compiled packages without inheriting GPL onto their own unrelated software.

## Alpine Linux minirootfs (`runtime/.../foreignlibc/ForeignLibcRuntime.kt`)

- **What**: `alpine-minirootfs-<version>-<arch>.tar.gz` from
  `dl-cdn.alpinelinux.org`, used to provide a real musl libc + dynamic loader for
  CLI binaries (Claude Code) that link against musl rather than Bionic.
- **License**: Alpine's base system is a mix of MIT/BSD-licensed components (musl libc
  itself is MIT).
- **How consumed**: downloaded and extracted at runtime on the user's device, run via
  `proot` (not vendored, not modified, not redistributed by this app).

## proot

- **What**: a ptrace-based `chroot`-alike, installed via `apt install proot` from
  Termux's own package repository, used to give musl/glibc-linked binaries a
  consistent filesystem view without root.
- **License**: GPLv2 (upstream `proot-ng`/`proot` project).
- **How consumed**: installed as an unmodified binary package via the same apt
  mechanism as every other bootstrap tool; invoked as a separate process, not linked
  into this app's own code.

## npm-distributed CLI binaries (Claude Code, OpenCode) and GitHub-released binaries
## (Codex CLI, Antigravity CLI)

- **What**: each provider's own official binary, fetched directly from its publisher's
  npm registry package or GitHub Releases at install time, on the user's device, using
  the user's own account/credentials for any auth the CLI itself requires.
- **License / distribution terms**: each publisher's own (Anthropic, OpenAI, the
  OpenCode project, Google) — this app does not redistribute these binaries; it
  automates the same download a user would otherwise run by hand, per each project's
  own published installation instructions.

## AndroidX / Jetpack / Kotlin libraries

Compose, Material3, Navigation, DataStore, Room, WorkManager, Security-Crypto,
kotlinx.coroutines, kotlinx.serialization — all Apache-2.0, declared in
`gradle/libs.versions.toml` and pulled from Google's/JetBrains' Maven repositories at
build time. Standard Android dependency licensing; no special handling needed beyond
what a standard Android app already does (their licenses are bundled into the app's
`/META-INF/` NOTICE files by AGP automatically).

## Maintainer note

Before a public release, replace this file with a generated NOTICE (e.g. via the
Gradle License plugin) enumerating exact versions and license texts of every
third-party build dependency — that level of detail wasn't finalized in this session.
The project's own license (MIT, `LICENSE` at the repository root) is set.
