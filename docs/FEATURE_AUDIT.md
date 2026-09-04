# Functional audit — 2026-09-04

## Scope and environment

Real builds and on-device tests were performed on Windows with the dedicated
SkullShell_UI_Review Android 15 (API 35), x86_64 emulator. The Material3 UI and
Settings-based navigation were retained. No provider account was authenticated,
no paid model request was made, and no physical ARM64 device was available.
This is a verified coverage report, not a guarantee that every possible device,
upstream CLI release, network condition or terminal sequence is free of defects.

## Verified workflows

| Area | Executed checks | Result |
| --- | --- | --- |
| Runtime installation | Live bootstrap download/extraction, apt package setup, Node/npm/Git/ripgrep | Passed |
| Runtime recovery | Cancel a real repair after the directory swap; restore the working runtime and preserve home files | Passed |
| Diagnostics | All nine checks; real executable versions, PTY, filesystem, network; Copy report UI | Passed |
| Claude Code | Live official install, candidate verification, installed-state detection, launch/help, login process startup and cancellation | Passed |
| Codex CLI | Live official install, launch/help, uninstall and reinstall, login process startup and cancellation | Passed |
| OpenCode | Live official install including C++ dependencies, launch/help, login process startup and cancellation | Passed |
| Antigravity | Official manifest/checksum, Ubuntu Base setup, launch/help, login process startup and cancellation | Passed |
| Agent development tools | Both compatibility environments: Node writes a file, Git initializes/adds/commits it, ripgrep reads it | Passed |
| Terminal engine | ANSI parsing/editing/alternate-screen regressions, cursor responses, bounded operations, Unicode | Passed |
| Terminal process | Repeated PTY output/exit-code checks, missing working-directory rejection, real subprocesses, timeout cleanup | Passed |
| Terminal UI | Typed command execution, backspace, activity recreation preserving the same session, exit status and session/service cleanup | Passed |
| Keyboard input | Exact-byte checks for repeated commits, hardware and soft-keyboard deletion, deletion beyond local history, and a 6,000-character paste followed by more typing | Passed |
| Projects | Create, persist, open timestamp, remove metadata, nested file operations, root/traversal protection, safe symlink deletion | Passed |
| External folders | Actual Android document picker and persisted grant; import existing files; save edits/additions/deletions; reject concurrent remote edits before writing; resolve and retry | Passed |
| Preferences | Appearance and terminal values persist; reset restores defaults; original test-device preferences restored afterward | Passed |
| SecretStore | Keystore-backed value round-trip and removal | Passed |
| Update checking | Live release check; invalid APK rejection; numeric/prerelease version comparison | Passed |
| Logging | API-key/token/Bearer redaction and ordinary-message preservation | Passed |
| Sign-in links | Complete HTTPS links extracted from ANSI output; duplicate/incomplete link handling; tapped Codex's browser button and confirmed Chrome opens | Passed through browser handoff |

Native builds package x86_64, ARM64 and ARMv7 support. Providers correctly reject
architectures for which their publisher supplies no binary. Runtime and provider
readiness now depend on actual successful execution; a file's existence or a
configuration file alone is insufficient.

The browser handoff reached Chrome's first-run screen. Browser terms and provider
sign-in were not completed, and no account credentials were entered.

## Significant fixes

- Replaced broken prefix relocation and direct static-binary execution with a
  bundled PRoot/loader process. The original runtime could open a shell while child
  commands and package installation still failed.
- Added real runtime readiness gates, native executable permissions, safe staged
  extraction, checksum/length checks, and rollback after interrupted replacement.
- Installed missing musl C++ dependencies. Android-compatible shell/tool wrappers
  keep Bionic library settings separate from musl/glibc loaders. Both canonical
  Android app-data paths are mapped for verified staged executables.
- Mapped DNS/CA configuration for static Linux binaries, fixing Codex sign-in's
  network startup failure.
- Replaced the incomplete external-folder registration with actual SAF import and
  conflict-checked explicit export. Empty new directories are included in export;
  existing empty external directories are retained.
- Fixed PTY output/exit races, timeout teardown and the closed-descriptor resize
  crash. Session counts reflect running processes, and foreground service startup
  handles commands that have already exited.
- Fixed terminal editing, alternate-screen cursor restoration, Unicode validation,
  bounded escape handling and IME backspace behavior. Added a normal Copy/Paste
  selection menu and complete sign-in links that open in the browser.
- Replaced the terminal's per-commit text-field reset with synchronous Compose
  text state and bounded input history. The previous input connection could replay
  earlier commits; regression tests inspect the exact bytes sent to the PTY.
- Prevented duplicate install/update actions and protected running sessions from
  runtime repair/agent replacement. Authentication errors no longer produce a
  false Ready state; retry remains available.
- Protected app updates with package identity, version-code and signing-certificate
  checks before opening Android's installer. Replaced loose version comparison.
- Included native dependency source archives, build recipes and licenses in APK
  assets and corrected misleading documentation about project isolation.

## Automated evidence

At the final JVM run: **64 tests, 63 passed, 1 skipped, 0 failed**. The skipped test
requires Windows symlink privileges; on-device tests separately exercise symlink
handling on Android. Android lint reported **0 errors, 94 warnings**; these are primarily
newer dependency/plugin version suggestions, plus small style/configuration items.
Those version suggestions were not addressed by an unrelated mass upgrade.

The final combined emulator regression completed with **16 tests passed, 0 failed**
after the input-state fix. It covers runtime/subprocesses, timeout cleanup, both
compatibility environments' development tools, projects, settings/Keystore, terminal
input and activity recreation, live update checking, and all four login startups.
Earlier dedicated runs additionally verified installation, runtime rollback and SAF.

On-device tests are in app/src/androidTest. Opt-in network tests download actual
upstream software. The external-folder test requires a disposable fixture selected
through the real document picker; it must not be pointed at personal project data.

Useful local evidence under app/build/ui-review (build outputs are gitignored):

- final-clean-regression.txt — final combined 16-test run, all passed.
- terminal-state-fix.txt — focused exact-byte and real-shell input regression passed.
- agents-final-check.txt — all four agent installs/launches plus compatibility tool checks passed.
- final-regression.txt — recovery, update, reinstall, settings, projects and terminal suite;
  its Codex sign-in failure was subsequently fixed by the DNS mapping.
- auth-development-tools.txt — the previously failing sign-in checks and the complete
  Node/Git/ripgrep workflow both pass after that fix.
- functional-round1.txt — external-folder import/export/conflict tests and settings pass;
  it also records the terminal resize crash that was fixed afterward.
- functional-round4.txt — the terminal UI workflow passes after the resize fix.
- diagnostics-final.png — the app's visible nine-pass diagnostics result.

Build/test commands:

```text
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest testDebugUnitTest :app:lintDebug
adb -s emulator-5580 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5580 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5580 shell am instrument -w -e class dev.aicli.app.RuntimeSmokeTest dev.aicli.app.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Add `-e network true` only for the explicitly opt-in network tests. Runtime tests
require setup first. The SAF fixture test additionally requires `-e safFixture true`
and the disposable selected folder described in its source.

## Remaining coverage and limits

- Completing each provider's account login, running authenticated model requests,
  quotas/billing and actual AI-directed editing require the user's own accounts.
  Startup/cancellation and honest signed-out states were tested; account success
  has not been claimed.
- Physical ARM64 hardware, Android 12/16 devices, vendor battery policies, long
  multi-hour sessions and low-memory stress need device coverage beyond this emulator.
- The terminal implements a tested ANSI subset, not complete xterm behavior. Width
  changes do not reflow existing text; complex grapheme shaping is incomplete.
- External-folder export is explicit and detects conflicts before writing. It is
  not a continuous synchronizer or a transactional multi-file backup system. A
  provider/storage failure partway through writing must be resolved/retried.
- A real newer, correctly signed release APK was not installed over this debug
  build. The current release lookup and invalid-package checks were tested.
- CLI credential files remain managed by the CLIs in private app storage. SafePath
  protects app file operations; arbitrary shell commands can access other files in
  the same Android app sandbox.
