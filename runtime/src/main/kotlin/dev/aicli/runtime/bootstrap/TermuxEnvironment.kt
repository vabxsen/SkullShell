package dev.aicli.runtime.bootstrap

import android.content.Context
import android.os.Build
import java.io.File

/** Termux packages retain their compiled prefix inside a PRoot process owned by this app. */
class TermuxEnvironment(context: Context) {
    private val connectivity = context.getSystemService(android.net.ConnectivityManager::class.java)
    private val filesDir = context.filesDir
    private val nativeDir = File(context.applicationInfo.nativeLibraryDir)
    private val cacheDir = context.cacheDir
    val prefixDir = File(filesDir, "usr")
    val homeDir = File(filesDir, "home")
    val tmpDir = File(prefixDir, "tmp")
    val prootBinary = File(nativeDir, "libskullshell_proot.so")
    val prootLoader = File(nativeDir, "libskullshell_loader.so")

    val termuxAbi: String by lazy {
        when (System.getProperty("os.arch")) {
            "aarch64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            "armv7l", "arm" -> "arm"
            "x86", "i686" -> "i686"
            else -> error("Unsupported native architecture: ${System.getProperty("os.arch")} (${Build.SUPPORTED_ABIS.joinToString()})")
        }
    }

    fun ensureDirectoriesExist() {
        check(prefixDir.isDirectory || prefixDir.mkdirs()) { "Could not create runtime directory" }
        check(homeDir.isDirectory || homeDir.mkdirs()) { "Could not create home directory" }
        check(tmpDir.isDirectory || tmpDir.mkdirs()) { "Could not create temporary directory" }
        File(cacheDir, "apt/archives/partial").mkdirs()
    }

    val isBootstrapInstalled: Boolean
        get() = File(prefixDir, ".skullshell-ready").exists() && File(prefixDir, "bin/bash").exists() && prootLoader.exists()
    val termuxExecPreloadLib: File get() = File(prefixDir, "lib/libtermux-exec-ld-preload.so")
    val hasTermuxExec: Boolean get() = prootBinary.exists() && prootLoader.exists()

    /** Host-side dependencies for PRoot; the guest's env command removes the host preload. */
    fun buildEnvironment(extra: Map<String, String> = emptyMap()): Map<String, String> {
      refreshDns()
      return mapOf(
        "HOME" to homeDir.absolutePath,
        "PREFIX" to GUEST_PREFIX,
        "TMPDIR" to tmpDir.absolutePath,
        "PATH" to "$GUEST_PREFIX/bin:/system/bin",
        "LD_LIBRARY_PATH" to "${nativeDir.absolutePath}:${prefixDir.absolutePath}/lib",
        "LD_PRELOAD" to File(nativeDir, "libtalloc.so").absolutePath,
        "PROOT_LOADER" to prootLoader.absolutePath,
        "PROOT_TMP_DIR" to tmpDir.absolutePath,
        "LANG" to "en_US.UTF-8",
        "TERM" to "xterm-256color",
        "COLORTERM" to "truecolor",
        "SSL_CERT_FILE" to "$GUEST_PREFIX/etc/tls/cert.pem",
        "npm_config_cache" to "${homeDir.absolutePath}/.npm-cache",
      ) + extra
    }

    fun refreshDns(rootfs: File? = null) {
        val servers = connectivity.getLinkProperties(connectivity.activeNetwork)?.dnsServers.orEmpty()
        if (servers.isEmpty()) return
        val config = servers.joinToString("\n", postfix = "\n") { "nameserver ${it.hostAddress}" }
        val destination = if (rootfs == null) File(prefixDir, "etc/resolv.conf") else File(rootfs, "etc/resolv.conf")
        destination.parentFile?.mkdirs()
        if (!destination.exists() || destination.readText() != config) destination.writeText(config)
    }

    fun wrapForExec(command: List<String>, workingDirectory: String = homeDir.absolutePath): List<String> =
        prootCommand(command, workingDirectory)

    fun prootCommand(
        command: List<String>,
        workingDirectory: String,
        rootfs: File? = null,
        extraBindings: Map<String, String> = emptyMap(),
        emulateRoot: Boolean = false,
    ): List<String> {
        require(command.isNotEmpty())
        check(prootBinary.exists() && prootLoader.exists()) { "Runtime support is missing for this device architecture" }
        val args = mutableListOf(systemLinkerPath(), prootBinary.absolutePath, "--kill-on-exit")
        if (emulateRoot) args += "-0"
        if (rootfs != null) {
            refreshDns(rootfs)
            args += listOf("-r", rootfs.absolutePath)
            for (path in listOf("/system", "/apex", "/dev", "/proc", nativeDir.absolutePath)) args += listOf("-b", path)
            val data = filesDir.parentFile!!
            for (alias in setOf(data.absolutePath, data.canonicalPath)) args += listOf("-b", "${data.absolutePath}:$alias")
            val wrappers = nativeToolWrappers(rootfs)
            for (shell in listOf("sh", "bash")) {
                val wrapper = File(wrappers, shell)
                if (wrapper.isFile) args += listOf("-b", "${wrapper.absolutePath}:/bin/$shell!")
            }
        }
        args += listOf("-b", "${filesDir.parentFile!!.absolutePath}:/data/data/com.termux")
        if (rootfs == null) {
            // Static Linux binaries use the conventional resolver/CA locations, not Bionic's
            // network APIs or Termux's compiled configuration paths.
            for ((source, target) in listOf("etc/resolv.conf" to "/etc/resolv.conf", "etc/tls/cert.pem" to "/etc/ssl/cert.pem")) {
                val file = File(prefixDir, source)
                if (file.isFile) args += listOf("-b", "${file.absolutePath}:$target")
            }
        }
        for ((host, guest) in extraBindings) args += listOf("-b", "$host:$guest")
        args += listOf("-w", workingDirectory, "/system/bin/env", "-u", "LD_PRELOAD", "-u", "LD_LIBRARY_PATH")
        if (rootfs != null) args += listOf("PATH=/usr/local/bin:/usr/bin:/bin:$GUEST_PREFIX/bin:/system/bin", "SHELL=/bin/bash")
        else args += "LD_LIBRARY_PATH=$GUEST_PREFIX/lib"
        args += command
        return args
    }

    private fun nativeToolWrappers(rootfs: File): File {
        val directory = File(rootfs, "usr/local/bin").apply { mkdirs() }
        // Keep Bionic tool dependencies out of the musl/glibc loader's search path.
        // The Android-built shells also use process syscalls supported by the host kernel.
        for (name in listOf("sh", "bash", "git", "node", "npm", "npx", "rg", "ssh", "python", "python3")) {
            if (!File(prefixDir, "bin/$name").isFile) continue
            val script = "#!/system/bin/sh\nexec /system/bin/env LD_LIBRARY_PATH=$GUEST_PREFIX/lib $GUEST_PREFIX/bin/$name \"\$@\"\n"
            val wrapper = File(directory, name)
            if (!wrapper.exists() || wrapper.readText() != script) {
                wrapper.writeText(script)
                check(wrapper.setExecutable(true, true))
            }
        }
        return directory
    }

    private fun systemLinkerPath() = if (termuxAbi in listOf("aarch64", "x86_64")) "/system/bin/linker64" else "/system/bin/linker"

    companion object { const val GUEST_PREFIX = "/data/data/com.termux/files/usr" }
}
