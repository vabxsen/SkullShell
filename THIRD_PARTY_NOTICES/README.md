# Third-party notices and source

SkullShell's application source is MIT licensed (see the repository's LICENSE).
The APK also contains the following unmodified native components, invoked as a
separate PRoot process. Their own licenses continue to apply to those components.

| Component | Version | License | Upstream source |
| --- | --- | --- | --- |
| PRoot and its executable loader | 5.1.107.92 | GPL-2.0-or-later | https://github.com/termux/proot/tree/v5.1.107.92 |
| libtalloc shared library | 2.4.3 | LGPL-3.0-or-later | https://www.samba.org/ftp/talloc/talloc-2.4.3.tar.gz |
| libandroid-shmem | 0.7 | BSD-3-Clause | https://github.com/termux/libandroid-shmem/tree/v0.7 |

The original source archives, SHA-256 hashes, Termux package recipes and patches,
and build-reference commit are in `sources/`. License texts are in `licenses/`.
This entire directory is included in the APK's assets, so source and notices travel
with the binaries. PRoot/loader files are renamed for Android native-library
packaging; their bytes are unchanged. The library libtalloc is LGPL licensed per
its own LICENSE and talloc.h (the broader Termux package metadata says GPL-3.0).
The GPLv3 text incorporated by LGPLv3 is included as well.

`runtime/src/main/jniLibs/manifest.json` and `sources/binary-manifest.json` record
the exact official Termux binary packages and per-file hashes for x86_64, ARM64,
and ARMv7. `tools/fetch-native-runtime.ps1` restores those pinned packages. See
`sources/BUILDING.md` for source rebuilding and replacing the native components.

## Downloads made on the device

These distributions are fetched directly from their publishers when selected;
they are not included in the APK:

- Termux bootstrap and apt packages: https://github.com/termux/termux-packages .
  Each package retains its upstream license and installed notices. The package set
  includes GPL and LGPL software, among other licenses.
- Alpine minirootfs and C++ runtime packages: https://alpinelinux.org . Components
  include musl (MIT), BusyBox (GPL-2.0), apk-tools (GPL-2.0), and GCC runtime libraries
  (GPL with the GCC runtime library exception). Rootfs hashes and apk repository
  signatures are checked during installation.
- Ubuntu Base: https://cdimage.ubuntu.com/ubuntu-base/releases/24.04/release/ .
  Licenses vary by package; the root filesystem retains its copyright notices.
- Claude Code: Anthropic's official platform package in the npm registry.
- Codex CLI: https://github.com/openai/codex/releases .
- OpenCode: https://github.com/anomalyco/opencode/releases .
- Antigravity CLI: the release manifest used by https://antigravity.google/cli/install.sh .
  The respective publisher's license and service terms govern each CLI.

SkullShell does not reimplement provider authentication. The CLIs manage their
credentials in the app's private home directory; those files are not encrypted by
SkullShell's separate SecretStore. Android app backups are disabled.

## JVM and Android dependencies

AndroidX (including Compose, Material3, Navigation, DataStore, Room and Security),
Kotlin, kotlinx.coroutines and kotlinx.serialization are Apache-2.0 projects.
Version declarations are in `gradle/libs.versions.toml` and module build files.
The app's own code does not link to libtalloc or PRoot through JNI: its JNI library
only supplies PTY process primitives, and starts the separate PRoot executable.
