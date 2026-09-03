# Architecture & Technical Decisions

This document is the record of the research and decisions behind this app, per the
project brief's Phase 1 requirement. It is written to be re-read, not just filed away —
every provider-specific compatibility shim in the code links back to a section here.

Environment this was built against: Windows dev host, JDK 21 (Temurin), Android SDK
with platforms 35/36, build-tools 34-36, NDK r27c, an **x86_64-only** local emulator
(`Scoop_Test`, `android-35 google_apis x86_64`), no physical Android device, no NDK/ARM64
emulator image pre-installed (NDK r27c + CMake 3.22.1 were installed as part of this build).
This matters: everything below claiming "works" has been verified by building and running
on the x86_64 emulator (which exercises the real code paths — PTY, bootstrap extraction,
process spawning) but **not on real ARM64 hardware**. Section "Known Limitations" at the
bottom is explicit about what that means.

## 1. Why not "just run it like Linux"

Android 12+ blocks exactly what these CLIs assume is free: no arbitrary `exec()` of
downloaded binaries outside the app's own storage without `W^X` violations on newer
API levels, scoped storage removes free filesystem roaming, background execution is
throttled, and the OS ships **Bionic**, not glibc — so prebuilt Linux binaries (Node,
git, the CLIs' own native modules) don't run as-is. None of this is solved by CPU
translation (FEX/Box64/Box86) — those fix instruction-set mismatches, not libc, PTY
semantics, filesystem layout, or npm's platform-detection logic. We don't use them:
every CLI here has a real ARM64 (or ARM64-capable) build, so there is nothing to
translate.

## 2. Linux userland: consume Termux's bootstrap, don't re-invent it

**Decision:** the app downloads Termux's own official bootstrap archive
(`bootstrap-<abi>.zip`, published at `github.com/termux/termux-packages/releases`,
tag prefix `bootstrap-`) into app-private storage (`filesDir/usr`), exactly as the
`termux-app` APK itself does on first launch. This gives us `bash`, `dpkg`/`apt`
pointed at Termux's own package repository, and a `PREFIX` layout
(`$PREFIX/bin`, `$PREFIX/lib`, `$PREFIX/etc`) that thousands of existing Termux
packages (`nodejs`, `git`, `python`, `openssh`, `ripgrep`, ...) already target with
real Android/Bionic builds. This is the actively-maintained, security-patched
solution to "native binaries for Android ARM64" — writing our own package repo would
be reinventing years of Termux's cross-compilation work for no benefit.

**Licensing boundary (see `THIRD_PARTY_NOTICES/`):** we consume Termux's *binary
package distribution* the way `apt`/Debian users consume Debian's — that's
Termux's supported distribution channel, not a redistribution of their app. We do
**not** vendor any of Termux's own GPLv3 application source (its terminal-emulator,
its PTY JNI glue, its `TermuxOpenReceiver`, etc.). Every line of PTY/JNI/terminal
code in this repo (`terminal/src/main/cpp`, `terminal/src/main/kotlin`) is our own,
written against POSIX (`openpty`/`forkpty`, both present in Bionic), so this app's
own license is independent of GPL. If a future contributor is tempted to copy code
from `termux-app` to save time: don't — it re-licenses this repo.

Practical consequence for local dev: the x86_64 emulator gets `bootstrap-x86_64.zip`
(also an official Termux artifact) so the whole pipeline — download, checksum,
extract, `apt install nodejs`, spawn PTY, run a CLI — is exercised end-to-end on
the only hardware available in this session. Real devices resolve to `aarch64` (or
`arm` on 32-bit-only devices) via `Build.SUPPORTED_ABIS`.

### 2a. The exec restriction that almost got missed

Android 10+ (API 29+) enforces W^X: an app targeting API 29+ cannot `execve()` an
arbitrary file it wrote to its own private storage the way a normal Linux process
could — this is real, documented Android platform behavior, not a Termux quirk. It's
the reason Termux ships **`termux-exec`**, a small `LD_PRELOAD` shim
(`libtermux-exec-ld-preload.so`) that rewrites `execve()` calls to route through the
system's own trusted dynamic linker instead of a raw kernel `execve()` of an
app-private file. Verified directly against the current bootstrap archive
(`bootstrap-2026.08.30-r1+apt.android-7`, both `aarch64` and `x86_64` variants):
`termux-exec` ships inside the base bootstrap zip itself
(`lib/libtermux-exec-ld-preload.so`), not as a separate package — no extra `apt
install` step is needed before it's usable — and every process this app spawns
inside the bootstrap gets
`LD_PRELOAD=$PREFIX/lib/libtermux-exec-ld-preload.so` set by `TermuxEnvironment`
(`runtime/.../bootstrap/TermuxEnvironment.kt`). Skipping this would mean every CLI
launch fails with a permission error on real devices while appearing to work in ways
that are easy to misread — worth calling out explicitly since it's the one part of
this architecture that is *not* optional or a nice-to-have.

## 3. PTY: our own JNI, not Termux's

`terminal/src/main/cpp/pty_native.c` is ~150 lines wrapping `openpty(3)` /
`forkpty(3)`, which Bionic has implemented since API 21. No `proot`, no privilege
escalation — this is the same mechanism every terminal app on Linux uses, it works
identically on Android because the kernel is Linux. It hands back a master fd for
read/write and the child PID for signaling (`SIGINT`/`SIGTERM`/`SIGWINCH` for
resize). All I/O happens on a dedicated thread pool via `Dispatchers.IO`, never the
main thread.

## 4. Terminal emulation: hand-rolled VT100/ANSI subset, not a vendored lib

`terminal/src/main/kotlin/.../AnsiParser.kt` and `TerminalBuffer.kt` implement CSI
parsing (cursor movement, SGR/color, erase, scroll regions), alternate-screen
buffer, and a **bounded** scrollback ring buffer (default 5000 lines, configurable) —
sized because the brief explicitly forbids unbounded output buffering. Rendering is
a custom Compose `Canvas` composable, not one `Text` per line, so redraws don't
recompute layout for the whole scrollback on every byte received.

## 5. Provider compatibility research (dated 2026-09-03; re-verify before trusting)

### Claude Code — best case, with one corrected assumption
Anthropic publishes a `linux-arm64-musl` build specifically — a musl-linked binary
has no runtime dependency on the host's libc being glibc *or* Bionic, which is
exactly the property we need. Initial research treated the shell-script native
installer (`curl -fsSL https://claude.ai/install.sh | bash`) as the primary path,
but further digging into `anthropics/claude-code` issues turned up recurring
platform-autodetection bugs (wrong arch selected, musl-vs-glibc picked
incorrectly) that trace back to the same root cause as the OpenCode and
(pre-shutdown) Gemini CLI bugs in §"OpenCode" below: `process.platform` inside
Termux's own Node build reports `"android"`, which nothing upstream expects.
**Decision:** don't trust the installer script's or npm's own platform detection.
`ClaudeInstaller` resolves and installs the `@anthropic-ai/claude-code-linux-<abi>-musl`
optional-dependency package directly (same shape of fix as OpenCode's), via
Termux's own Node/npm, rather than running the upstream shell installer as-is.

### Codex CLI — good, with one deliberate compatibility override
`@openai/codex` ships a real `codex-aarch64-unknown-linux-musl` binary — Rust,
statically linked, no glibc dependency, which sidesteps the Bionic-vs-glibc problem
entirely. **However**, Codex's default sandboxing on Linux layers Landlock (LSM,
kernel 5.13+) with a bubblewrap (`bwrap`) fallback that needs **unprivileged user
namespace creation** (`unshare(CLONE_NEWUSER)`). Stock Android kernels/SELinux
policy restrict that for regular apps. Rather than pretend this works, `CodexCompatibility`
sets `sandbox_mode = danger-full-access` by default and documents *why* in the UI
(Settings → Providers → Codex → "Sandboxing"): our own app already constrains the
CLI to the user's chosen workspace directory via the filesystem layer (§7), so we
are not removing an isolation boundary that existed, we're substituting our
coarser one for Codex's finer one. This is surfaced to the user, not hidden.

### OpenCode — known, open, fixable bug
`opencode-ai`'s postinstall script keys off `process.platform`, which Termux's own
Node.js build reports as **`"android"`**, not `"linux"` — so its optionalDependencies
resolution looks for a package (`opencode-android-arm64`) that has never been
published (tracked upstream: `anomalyco/opencode#12515`). Real ARM64 Linux binaries
*do* exist (`opencode-linux-arm64`). `OpencodeInstaller` bypasses npm's postinstall
entirely: it resolves and downloads the matching `opencode-linux-<abi>` binary
directly from its GitHub Release and places it on `PATH` itself. This is more
reliable than trying to trick npm's platform check.

### Antigravity CLI — Gemini CLI's replacement (see decision below), partial support
Google shut down "Login with Google" OAuth for Gemini CLI's individual/free/Pro/Ultra
tier on **2026-06-18**, redirecting individual users to **Antigravity CLI** (`agy`), a
closed-source Go rewrite. The user for this project chose to replace the Gemini CLI
provider slot with Antigravity CLI rather than keep a mostly-dead integration.
Antigravity ships a real Linux ARM64 build, but independent Termux compatibility
work (see `AntigravityCompatibility.kt` doc comment) documents genuine, non-trivial
breakage:
- Its binary needs **glibc**, not Bionic (it dynamic-links against `libc.so.6`) —
  handled by installing Termux's community `glibc` package as a second, smaller
  runtime layer alongside the Bionic bootstrap, not by patching Bionic.
- Go's `faccessat2` syscall (439) is blocked by Android's seccomp filter on some
  kernels → `SIGSYS`. No safe app-level fix exists for this (it's a kernel policy,
  not an env var); we detect the exact signal/exit pattern and surface *"blocked by
  this device's kernel seccomp policy"* rather than a generic crash.
- TCMalloc (Go's allocator on this build) assumes a 48-bit virtual address space;
  many Android devices run a 39-bit user VA layout → allocation failure at startup.
  Same treatment: detected, explained, not silently retried forever.
- DNS/TLS: fixed safely via `GODEBUG=netdns=go`, `SSL_CERT_FILE` pointed at the
  bootstrap's CA bundle, and unsetting `LD_PRELOAD`/`LD_LIBRARY_PATH` before exec
  (Termux injects a Bionic preload that breaks a glibc process).

We apply every fix that's a safe, reversible environment change. We do **not**
byte-patch Google's proprietary binary (also legally distinct from patching our own
JNI code) — where the failure is structural, `AntigravityProvider` reports
`INCOMPATIBLE` with the specific reason rather than pretending success. This is the
one provider where "Ready" cannot be promised for every device; see Known Limitations.

## 6. Provider abstraction

`provider-api` defines `AIProvider` (id, display name, `Installer`, `LaunchStrategy`,
`CompatibilityCheck`, `AuthStrategy`, version/health checks) exactly per the brief.
Each of `provider-claude/codex/opencode/antigravity` is an independent Gradle module
implementing that interface — the UI and session manager depend only on
`provider-api`, never a concrete provider, so adding a 5th CLI later is "add a
module," not "touch the app module."

## 7. Filesystem boundary

Two distinct roots, never conflated in the UI or the code:
- **App workspace** — `filesDir/workspaces/<id>`, fully app-managed, no SAF needed.
- **User-selected external project** — opened via Storage Access Framework
  (`ACTION_OPEN_DOCUMENT_TREE`), accessed only through the returned
  `content://` URI / `DocumentFile`, copied into a workspace-scoped staging dir
  before a CLI (which needs real POSIX paths, not `content://`) touches it, with
  changes synced back explicitly. Path-traversal is prevented in `core`'s
  `SafePath` by canonicalizing and rejecting any resolved path outside the
  declared root before it's ever handed to a spawned process's argv/cwd.

## 8. Process lifecycle

A foreground service (`TerminalSessionService`, type `specialUse`... actually
`dataSync`-adjacent per Android 14+ FGS type rules, see the service's manifest entry
for the exact declared type and justification string) hosts the PTY sessions so
they survive Activity recreation. If Android kills the process anyway (low memory,
user force-stop), the session is *not* shown as still running on next launch —
`SessionRepository` marks it "ended: runtime terminated by the OS" on restart if the
PID it remembers is no longer alive. We do not claim a session survived when we
can't prove it did.

## Verified on-device (x86_64 emulator, Android 15 / API 35), 2026-09-03

Actually built, installed, and exercised on the `Scoop_Test` emulator (`android-35
google_apis x86_64`) via `adb install` + `adb shell input` — not just "it compiles":

- Clean install of the debug APK on a fresh Android 15 image; app launches with no crash.
- `libaicli_pty.so` (the native PTY module) loads successfully via `nativeloader`
  (confirmed in logcat) on-device, for all three built ABIs (arm64-v8a, armeabi-v7a, x86_64).
  Plain 32-bit `x86` is deliberately not built — real x86 (non-x86_64) Android hardware
  predates API 31 entirely, so no minSdk-31-compliant device could hit this gap; verified
  by checking `terminal/build.gradle.kts`'s `abiFilters` and the actual release APK's
  `lib/x86/` contents (present for third-party prebuilts, absent for our own `.so` — as
  expected, not an oversight).
- Home screen renders real, non-hardcoded state: provider cards show `NotInstalled` from
  an actual `detectState()` call per provider, and "Runtime health: 4/9 checks passing"
  came from `RuntimeHealthChecker.runAll()` actually executing each check (the 5 failures
  on a fresh install are correctly the bootstrap/node/npm/git/PTY-binary checks, since no
  bootstrap has been installed yet in this environment — not a bug, the expected state).
- Project creation (Room-backed) works and survives an app restart (force-stop + relaunch
  showed the created project still listed).
- Opening a project's terminal actually spawns a real process through the full pipeline:
  Kotlin → JNI → `forkpty()` → exec. Since no bootstrap was installed, the exec target
  didn't exist, and the child correctly exited with **127** ("command not found") — this
  was caught by the native `waitFor`, propagated through `PtyProcess.waitForExit()`, into
  `SessionRunState.EXITED`, and would have left a **blank screen with no explanation**
  before a fix — a real bug this testing pass caught, not a hypothetical. Fixed: a
  `SessionEndedBanner` in `TerminalScreen.kt` now turns every non-running state (`EXITED`
  with exit code, `KILLED_BY_OS`, `ERROR`) into an actionable message. Re-verified after
  the fix: the banner renders correctly ("Command not found (exit 127)" plus a "Start new
  session" action).
- A second real bug this pass caught: `TerminalSessionService` initially tore itself down
  (`stopForeground`/`stopSelf`) on the very first, necessarily-empty session-list emission
  right after launch — meaning the foreground service that's supposed to keep sessions
  alive across Activity recreation never actually stayed alive. Fixed by (a) only starting
  the service once `SessionManager.createSession` actually has a session to host, instead
  of eagerly in `MainActivity.onCreate`, and (b) only auto-stopping on a *transition* from
  having sessions to having none, not on the initial state.
- A third bug this pass caught: `TerminalSessionService` was constructing its own private
  `SessionManager(applicationContext)` instead of sharing the one instance in `AppContainer`
  — meaning it could never actually see sessions the UI created. Fixed to use the shared
  instance from `AiCliApplication.container`.
- A fourth, more fundamental bug found via a *second*, deeper testing pass (after the
  above fixes were already verified working): every single process spawn inside the
  bootstrap was actually failing. `adb logcat` during a real shell-open attempt showed the
  literal kernel decision:
  ```
  avc: granted { execute } for name="dash" ... scontext=u:r:untrusted_app ... tcontext=u:object_r:app_data_file
  avc: denied { execute_no_trans } for path=".../files/usr/bin/dash" ... permissive=0
  ```
  This is a real SELinux type-enforcement rule, not a config quirk: `untrusted_app`-domain
  processes are denied `execute_no_trans` on `app_data_file`-labeled files, full stop,
  regardless of the file's static/dynamic linkage. Setting `LD_PRELOAD` (already done, for
  termux-exec) cannot prevent this by itself — that only changes a process's *own* libc
  symbol resolution *after* it starts, and this denial fires during the `execve()` **syscall
  itself**, before any new process (or its environment) exists yet. The fix, confirmed by
  directly testing the mechanism on-device before touching any code: route the very first
  exec of a process tree through `/system/bin/linker64` (a `system_file`-labeled, trusted
  binary) instead of letting the kernel exec the target file directly — the linker then
  manually loads and jumps into the real target's ELF segments itself, and *that* is
  permitted. `TermuxEnvironment.wrapForExec()` implements this and is now applied at every
  `PtyProcess.spawn()` call site across the app (11 call sites, including inside
  `ForeignLibcRuntime.wrapCommand()` for proot itself). Once a process is running this way,
  `LD_PRELOAD`'s termux-exec shim (loaded into *that* process at its own startup) correctly
  carries the fix forward to everything *it* forks — see the doc comment on `wrapForExec()`
  for the full mechanism. Re-verified after the fix: `avc: granted` for both `dash` and
  `libtermux-exec-ld-preload.so`, no denial; the PTY health check went from "PTY opened,
  child exited with 127" to "PTY opened, shell exited cleanly"; and a real interactive
  `bash-5.3$` prompt rendered in the terminal screen, running real typed commands.
  **Caveat**: this trick is a documented property of *dynamically-linked* ELF binaries
  (the kernel's own exec of such a binary hands off to its `PT_INTERP` loader the same
  way); Codex CLI's Rust `*-unknown-linux-musl` build is *statically* linked (no
  interpreter at all), and it's unverified in this session whether `linker64` can bootstrap
  a static binary the same way — `wrapForExec()` is applied to Codex's spawns for
  consistency and because it's a harmless no-op if it doesn't help, but this specific case
  needs real-device verification before trusting it.
- A fifth bug, found in the same pass: the ABI selected for the bootstrap download itself
  was wrong on this very device. `Build.SUPPORTED_ABIS` listed `arm64-v8a` first (this
  emulator has `ro.enable.native.bridge.exec=1`, Android's own ARM-on-x86 translation for
  *APK-bundled native libraries*), and `TermuxEnvironment.termuxAbi` trusted that, resolving
  `bootstrap-aarch64.zip` on an x86_64 device. The bug surfaced immediately and unambiguously:
  `/system/bin/linker64` (the *real*, x86_64 system linker) refused to load it —
  `"...dash" is for EM_AARCH64 (183) instead of EM_X86_64 (62)`, confirmed by direct testing.
  `Build.SUPPORTED_ABIS` answers "what can this device translate for an installed APK's
  bundled `.so`," not "what can a raw `execve()` of an arbitrary standalone binary actually
  run as" — those are different Android subsystems. Fixed: `termuxAbi` now keys off
  `System.getProperty("os.arch")`, which reports the ART runtime's actual native
  architecture, falling back to `SUPPORTED_ABIS` only if that's unrecognized. Re-verified:
  a fresh bootstrap install after this fix correctly resolved `bootstrap-x86_64.zip`.
- A sixth bug, found once the shell was finally interactive enough to type into: `TerminalView`
  had **no keyboard input path at all** — it was pure `Canvas` drawing plus drag gestures for
  scroll/selection; `onInput` was accepted as a parameter but never invoked, and there was no
  Android input connection (no focusable/text-capable element) for a soft keyboard or IME to
  attach to. Typing was completely non-functional; only `TerminalKeyboardBar`'s dedicated
  buttons (Ctrl+C, arrows, etc.) could send anything. Fixed with the standard technique for
  custom-rendered terminal UIs: an invisible (`size(1.dp).alpha(0f)`) `BasicTextField` overlaid
  on the `Canvas`, focused on tap, whose value is diffed against its previous value on every
  change (added text → forwarded as UTF-8 bytes, shortened text → backspace bytes) and then
  drained back to empty — the field's own displayed text is never what's on screen; the Canvas
  above renders the real terminal grid. `onPreviewKeyEvent` separately handles Enter/Backspace/
  Delete for hardware keyboards. Re-verified: typed `echo HELLO_TYPED` character-by-character
  and pressed Enter through the real on-screen keyboard; the shell echoed and executed it correctly.
  **Caveat, found during the same verification**: sending a whole string through `adb shell
  input text "..."` in one call reliably dropped characters (only a prefix arrived); sending
  the same characters with ~150ms gaps between them (roughly human typing speed, if anything
  slower) delivered every character correctly, in order, executed by the real shell. This
  points to the reset-to-empty-per-keystroke pattern racing against Android's input-connection
  batching under very fast/bulk synthetic input (rapid IME commits — e.g. swipe-to-type
  inserting a whole word at once — may be a real-world analog) rather than a fundamental design
  flaw; realistic human typing speed was verified working correctly. Treat sustained
  very-fast/bulk input as an open, documented risk rather than a confirmed-fine case.
- `./gradlew assembleDebug` (all 9 modules, including the CMake native build for 3 ABIs)
  and `./gradlew test` both succeed clean after all six fixes above.
- `./gradlew :app:lintDebug` run for the first time this session: 0 errors, 94 warnings,
  all standard pre-release polish items (missing data-extraction/backup rules, string
  literals not extracted to `strings.xml`, no monochrome/themed launcher icon variant,
  a couple of libraries with newer versions available) — none are correctness bugs.
  `./gradlew :app:assembleRelease` (R8/ProGuard minification) failed on the first attempt
  for two real reasons, both fixed: `app/build.gradle.kts` pointed at a `proguard-rules.pro`
  that didn't exist, and R8 refused to proceed over missing compile-time-only annotation
  classes (`com.google.errorprone.annotations.*`, `javax.annotation.*`) pulled in
  transitively by `androidx.security-crypto`'s Tink dependency — a well-known, documented
  issue with that library, fixed with the standard `-dontwarn` rules. A real proguard file
  was also added keeping `dev.aicli.terminal.PtyNative` unobfuscated — R8 renaming that
  class would silently break every native PTY spawn in a release build, since the native
  side calls into it by exact JNI symbol name, not through the Kotlin call graph R8 can see.
  The release build succeeds after both fixes; it's deliberately left **unsigned** (no
  keystore/signing config exists yet — see §33 in the original brief and the maintainer
  note in `THIRD_PARTY_NOTICES/README.md`), so it was not installed/run — packaging and
  minification succeeding is what's confirmed, not a signed, installed release build.

What this does **not** cover: no provider CLI install (Claude Code, Codex, OpenCode,
Antigravity) was run all the way to a working `--version`/`Ready` state — OpenCode's
install was exercised far enough to hit and correctly report a real, expected error
(bootstrap not yet installed in that particular run), which at least confirms the
install-trigger wiring and error surfacing are real, but no provider's full binary
download + extraction + first-run flow completed in this session. Real ARM64 hardware
was not available — every finding above was on an x86_64 emulator; the SELinux exec
policy is expected to be identical on real hardware (it's a platform-wide AOSP policy,
not device-specific), but this has not been confirmed on a physical ARM64 device. See
Known Limitations below.

## Known Limitations (as of this build)

1. **No physical-device or ARM64-emulator verification was performed** — only the
   x86_64 emulator was available in this environment. ARM64-specific failure modes
   (the Antigravity VA-width / seccomp issues above being the clearest example) are
   implemented defensively based on documented upstream reports, not confirmed
   first-hand on that hardware. Treat Antigravity support as "best-effort, verify on
   your device" until real-device testing happens.
2. Antigravity CLI is closed-source and newly released (2026); its install script
   and binary layout may change without notice, unlike the other three providers
   which have stable, documented distribution mechanisms.
3. This document and the compatibility notes above are dated. Re-run the research
   (installer URLs, npm package names, sandbox behavior) before relying on them —
   all four upstream projects ship frequently.
4. **No provider's install flow has been run end-to-end against the real network**
   (downloading a real bootstrap, a real npm tarball, a real GitHub release asset, and
   confirming the resulting binary actually reaches `Ready`). Each piece (URL resolution,
   download-with-progress, tar/zip extraction, symlink handling) was verified individually
   against real remote data while researching this build (see the dated citations
   throughout this document), and the code compiles and the app runs — but the full
   chain for any one provider, start to a working `claude --version`/`codex --version`/
   etc., was not observed in one continuous run. Budget time for this before calling any
   provider "done."
5. Antigravity's GLIBC compatibility layer is a documented, unimplemented gap (see
   `ForeignLibcRuntime.kt`) — `AntigravityProvider` will report `Incompatible` until a
   maintainer wires up a verified glibc rootfs source.
6. **Codex CLI's exec path is unverified.** `wrapForExec()`'s exec-via-system-linker fix
   (§"Verified on-device") is confirmed working for dynamically-linked bootstrap binaries
   (bash, dash, proot, and everything running inside a proot'd rootfs — i.e. Claude Code
   and OpenCode's actual execution path). Codex's own binary is statically linked, a case
   this session did not get to install and test live; whether `/system/bin/linker64` can
   bootstrap a static ELF the same way is unconfirmed. If it can't, Codex needs a different
   fix (candidates: a small custom static-ELF loader stub, or checking whether Rust's musl
   target can be told to always keep a runtime under `dlopen`-based dynamic linking
   instead) — don't assume Codex works until this is verified on a device.
7. **Terminal keyboard input has only been verified at realistic human typing speed.**
   `TerminalView`'s invisible-`BasicTextField` input path (added and verified this session
   — see §"Verified on-device") correctly delivered a real typed command end-to-end, but
   sending a whole string through `adb shell input text` in a single call reliably dropped
   characters, while the same characters sent ~150ms apart did not. This looks like the
   per-keystroke reset-to-empty pattern racing Android's input-connection batching under
   very fast/bulk input — a real concern for swipe-to-type or predictive-text word
   insertion, which commit multiple characters at once the same way. Not fixed in this
   session; needs either a debounced/frame-deferred clear or a non-empty-anchor text field
   design, then re-verification specifically with swipe-typing and autocomplete, not just
   manual key-by-key typing.
8. IME composition for non-ASCII input (accented characters, CJK, emoji picker) is not
   specifically handled — the field is drained to empty after every change, which is
   incompatible with how composing text (e.g. holding a key for an accent, or a CJK input
   method's candidate selection) normally works. Plain ASCII command-line usage (the
   overwhelming majority of terminal/CLI interaction) is unaffected and verified working;
   don't assume anything beyond that without further work.
