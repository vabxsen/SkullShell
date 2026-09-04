# Architecture and verification

Updated 2026-09-04. See docs/FEATURE_AUDIT.md for the test matrix and remaining
account/device coverage. The audit uses the dedicated SkullShell_UI_Review x86_64
Android 15 emulator; other emulators and projects are not used.

## 1. Android application

The app uses Kotlin, Jetpack Compose and Material3. Home provides the primary
terminal action; the toolbar gear opens a Settings navigation hub for Home,
Projects, Terminal, Agents and Diagnostics. There is no app bottom navigation bar.
DataStore persists preferences and Room stores project/session metadata.

## 2. Termux runtime

Termux packages are compiled for /data/data/com.termux/files/usr. Changing PREFIX
alone cannot relocate those embedded paths. SkullShell stores the downloaded
bootstrap in its own filesDir/usr and uses a separately launched, bundled Termux
PRoot executable to present the expected prefix within the app's Android sandbox.
The app's data directory is bound to /data/data/com.termux inside that process,
including its apt cache. It does not write to another app's actual data directory.

The bootstrap is extracted into a clean staging directory. Archive paths are
confined, symlink paths are handled without following their existing leaf, and ELF
executables/scripts receive executable permissions even outside bin/. Setup checks
real subprocess execution, installs Node.js LTS, npm, Git and ripgrep, and writes a
ready marker only after success. A failed replacement restores the previous usr
directory. Home directories and project contents are outside that replacement.
Repair replaces packages and agent binaries stored under usr; reinstall agents
after a full runtime repair. Settings blocks repair while terminal sessions run.

### 2a. Process execution

TermuxEnvironment starts the bundled PRoot through Android's system linker. Its
native-library dependencies and unbundled executable loader live in the extracted
APK native-library directory. PRoot handles execution of downloaded executables;
using a Bionic linker directly on a static Linux binary is not sufficient. The
host preload is removed before a foreign-libc guest program starts. System.getProperty
("os.arch") selects the actual process ABI rather than an optional translated ABI.

Native components, original source archives, build recipes, hashes and licenses
are recorded in THIRD_PARTY_NOTICES and included in APK assets. No native binaries
are modified to change Android's kernel policy or SELinux restrictions.

## 3. PTY

The app-owned C/JNI module opens a PTY, forks, changes the working directory, and
executes the requested command. A failed chdir exits 126 and a failed exec exits
127. File descriptors use close-on-exec. Kotlin owns one waitpid task per child;
output is drained before bounded command probes inspect the exit status. Timeout
or cancellation closes the PTY and kills its process group. Resize ignores closed
PTYs, so an ended session can remain visible without crashing during layout.

## 4. Terminal

The synchronized terminal buffer has bounded scrollback and snapshots rows for
rendering. The ANSI parser supports cursor movement, colors, erase, line/character
editing, scroll regions, alternate screen and device-status responses. The Compose
Canvas renders visible rows; a text field supplies the Android input connection.
Hardware control/navigation keys and shortcut buttons send terminal sequences.
Long-press selection either copies automatically or opens the native Copy/Paste
menu. Bracketed paste is honored when requested by the running application.

This remains a terminal-emulation subset. Existing lines do not reflow after a
width change, and complex grapheme/emoji shaping is incomplete. An interactive TUI
can use sequences beyond the tested subset; no claim of complete xterm conformance
is made.

## 5. Providers

AIProvider defines install, uninstall, update detection, compatibility, launch and
authentication. Provider state checks execute the real binary and check exit
status. A nonempty config file is not sufficient to label an agent signed in.
Downloads are streamed and verified against published checksums where available;
agent replacement occurs only after the candidate executable reports a version.

- Claude Code: the official npm Linux musl platform binary, launched in an Alpine
  compatibility root. Authentication uses claude auth login/status/logout.
- Codex: the official GitHub Linux musl binary, launched through the base runtime.
  Device-code sign-in uses codex login --device-auth. Android cannot provide its
  usual Linux sandbox; the launch keeps the existing --sandbox danger-full-access
  compatibility option unless the caller supplies another sandbox option.
- OpenCode: the official GitHub musl release, using the shared Alpine environment
  and its C++ runtime dependencies. The provider reads the CLI's credential store
  conservatively and delegates interactive sign-in to opencode auth login.
- Antigravity: the official release manifest used by Google's CLI installer;
  SHA-512 is checked before execution in an Ubuntu Base glibc environment. Its
  authentication state remains Unknown where the CLI supplies no checked status.
  Installed therefore means the executable was verified, not that an account has
  authenticated. Device-specific syscall restrictions can still prevent execution.

Alpine and Ubuntu root filesystems are checksum verified. Linux runtimes and
provider releases remain upstream software; endpoint changes and new release
requirements must be tested when updating. ARM64 runtime artifacts are packaged,
but a physical ARM64 phone is required to verify its kernel and CPU behavior.

## 6. App composition

AppContainer owns repositories, runtime services and providers. ViewModels use
those shared objects. Provider failures are isolated so one broken agent cannot
prevent the remaining agent cards from loading. In-progress UI operations reject
duplicate taps, and errors remain visible with retry paths.

## 7. Project storage and isolation

App projects live in filesDir/workspaces/<id>. External folders use Android's
Storage Access Framework and persistent URI grants. Their actual files are copied
to filesDir/staging/<id> before CLI access. Save changes to folder explicitly
exports new/edited files and deletions; SHA-256 baselines detect concurrent external
edits before any writes. Symbolic links are not followed during export. Empty new
directories are created; existing empty external directories are retained.

SafePath protects the app's filesystem API against path traversal. It does not
sandbox arbitrary shell commands or isolate projects from each other. Commands and
agents share the app's Android UID, private home, installed tools and accessible
project files. The Android application sandbox is the process-level boundary.
CLI-managed credential files are private app files, not SecretStore-encrypted data.

## 8. Sessions and background behavior

SessionManager owns live controllers in the app process. TerminalSessionService
uses Android's specialUse foreground-service type while commands run. A short
command that exits before service startup also stops the service correctly.
Notification permission is requested when the first terminal starts, if needed.
Denied notification permission does not prevent a foreground terminal session.
Activity recreation retains controllers; process death cannot retain PTYs. Startup
reconciles stale running database rows, and no session is claimed to have survived
a force-stop. Metadata is retained; terminal output/history is not restored after
process death. Battery restrictions and low-memory termination remain OS behavior.

## 9. App updates

Release checks compare numeric versions and prerelease identifiers. Downloads check
length and available published digests. Before Android's installer is opened, the
APK must parse, match this app's package name/signing history and have a higher
version code. Debug and release packages have different application IDs. Actual
installation still uses Android's permission and confirmation UI.

## Primary implementation references

- Termux package paths: https://github.com/termux/termux-packages/wiki/Termux-file-system-layout
- Native build recipes: https://github.com/termux/termux-packages/wiki/Building-packages
- PRoot: https://github.com/termux/proot
- Alpine package verification: https://github.com/alpinelinux/apk-tools
- Ubuntu Base: https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/
- Claude authentication: https://code.claude.com/docs/en/cli-reference
- Foreground service type: https://developer.android.com/develop/background-work/services/fgs/service-types#special-use
