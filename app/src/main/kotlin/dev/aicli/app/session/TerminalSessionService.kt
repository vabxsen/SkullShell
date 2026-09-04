package dev.aicli.app.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import dev.aicli.app.AiCliApplication
import dev.aicli.app.MainActivity
import dev.aicli.app.R
import dev.aicli.app.data.SessionManager
import dev.aicli.core.logging.AppLog
import dev.aicli.core.logging.LogCategory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Hosts every live [dev.aicli.app.data.TerminalSessionController] as a foreground service so PTY
 * sessions survive Activity recreation (rotation, backgrounding) rather than dying with the
 * Activity. It does NOT survive the whole app process being killed by the OS (low memory, user
 * force-stop) — nothing can, a PTY's file descriptors and child process die with the process that
 * held them. [SessionManager.reconcileAfterRestart] is what turns that into an honest "session
 * ended: runtime terminated by the OS" instead of a UI that lies about a session still running.
 * See ARCHITECTURE.md §8.
 */
class TerminalSessionService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var sessionManager: SessionManager

    private var started = false

    inner class LocalBinder : Binder() {
        fun service(): TerminalSessionService = this@TerminalSessionService
    }

    private val binder = LocalBinder()

    override fun onCreate() {
        super.onCreate()
        // Shared with the rest of the app via AppContainer — a second, independent SessionManager
        // instance here would track its own sessions and never see what the UI actually launches.
        sessionManager = (application as AiCliApplication).container.sessionManager
        createNotificationChannel()
        scope.launch {
            sessionManager.runningCount.collect { running ->
                if (running > 0) {
                    startForegroundCompat(buildNotification(running))
                } else if (started) {
                    @Suppress("DEPRECATION")
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    } else {
                        stopForeground(true)
                    }
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        started = true
        startForegroundCompat(buildNotification(sessionManagerRunningCount()))
        // A short command may finish before Android delivers onStartCommand.
        if (sessionManagerRunningCount() == 0) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelfResult(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
        AppLog.i(LogCategory.PROCESS, "TerminalSessionService destroyed")
    }

    private fun sessionManagerRunningCount(): Int = sessionManager.runningCount.value

    private fun startForegroundCompat(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(sessionCount: Int): Notification {
        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("SkullShell — $sessionCount active session${if (sessionCount == 1) "" else "s"}")
            .setContentText("Tap to return to your terminal")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(openIntent)
            .setOngoing(true)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "Terminal sessions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while SkullShell terminal sessions are running"
            }
            manager.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "terminal_sessions"
        private const val NOTIFICATION_ID = 1001
    }
}
