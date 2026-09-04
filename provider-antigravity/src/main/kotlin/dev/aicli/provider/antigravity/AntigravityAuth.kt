package dev.aicli.provider.antigravity

import android.content.Context
import dev.aicli.provider.api.AuthState
import dev.aicli.provider.api.ProviderAuth
import dev.aicli.runtime.bootstrap.TermuxEnvironment
import dev.aicli.terminal.PtyProcess
import java.io.File
import dev.aicli.runtime.foreignlibc.ForeignLibcRuntime
import dev.aicli.runtime.foreignlibc.LibcFlavor

/**
 * Antigravity authenticates via the system secret-service keyring, falling back to a Google
 * Sign-In browser flow — there's no simple on-disk credential file to check for presence the way
 * the other three providers' auth files work, and this app doesn't run a keyring daemon. Rather
 * than fabricate a check, this always reports [AuthState.Unknown]; the real signal is the CLI's
 * own prompt when launched (it detects SSH/headless environments and prints an authorization URL,
 * which this app opens the same way as any other CLI's OAuth link — see project brief §17).
 */
class AntigravityAuth(private val context: Context, private val binaryPath: File) : ProviderAuth {
    private val env = TermuxEnvironment(context)

    override suspend fun currentState(): AuthState = AuthState.Unknown

    override suspend fun startLogin(): PtyProcess = PtyProcess.spawn(
        command = ForeignLibcRuntime(context).wrapCommand(LibcFlavor.GLIBC, listOf(binaryPath.absolutePath), env.homeDir.absolutePath),
        environment = env.buildEnvironment(),
        workingDirectory = env.homeDir.absolutePath,
        initialCols = 100,
        initialRows = 32,
    )

    override suspend fun logout() {
        error("Use Antigravity's account controls inside the CLI to sign out")
    }
}
