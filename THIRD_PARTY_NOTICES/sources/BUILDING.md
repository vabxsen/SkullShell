# Rebuilding the bundled native runtime

The original unmodified sources and their expected SHA-256 values are beside this
file. `termux-build-recipes/COMMIT` pins the Termux build repository used as the
reference for these package versions. The copied package directories include the
build recipes and any package patches. No SkullShell source patches are applied.

On a supported Linux host, clone https://github.com/termux/termux-packages, check
out the commit recorded in COMMIT, and use that repository's documented build
container (scripts/run-docker.sh) and build-package.sh workflow. Build proot,
libtalloc and libandroid-shmem for each of aarch64, arm and x86_64, using the source
versions/checksums in these recipes. The Termux build system downloads the Android
NDK and other toolchain dependencies separately.

PRoot is built with PROOT_WITH_LIBANDROID_SHMEM=true and its loader is unbundled
into $PREFIX/libexec/proot. Package proot's executable as libskullshell_proot.so,
its loader as libskullshell_loader.so, libtalloc's shared object as libtalloc.so,
and libandroid-shmem.so under the corresponding Android jniLibs ABI directory.
No stripping or binary modification is required. Rebuild the APK with Gradle.
These components can be replaced and the app rebuilt with modified versions.

The exact published binary artifacts (rather than a promise of a byte-identical
rebuild with a different toolchain) are pinned in binary-manifest.json. Upstream
build instructions: https://github.com/termux/termux-packages/wiki/Building-packages
