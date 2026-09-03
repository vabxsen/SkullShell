# Native code in terminal/src/main/cpp/pty_native.c calls into this object by exact JNI symbol
# name (Java_dev_aicli_terminal_PtyNative_forkExec etc.) — R8 must never rename or strip it,
# or every native PTY spawn breaks silently at runtime in a release build.
-keep class dev.aicli.terminal.PtyNative { *; }

# androidx.security-crypto pulls in com.google.crypto.tink, which references compile-time-only
# annotations (error-prone, JSR-305) that aren't on the runtime classpath. They're safe to ignore:
# these are pure source-level annotations with no runtime behavior.
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**

# Room's own consumer rules (bundled in its AAR) already cover its generated code; kotlinx
# serialization / coroutines likewise ship consumer rules. Nothing else in this app relies on
# reflection over its own classes, so no further keep rules should be needed — if R8 strips
# something real, that's a signal to add a narrowly-scoped rule here, not to disable R8.
