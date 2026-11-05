package com.noobdev.waffle.core.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.noobdev.waffle.MainActivity
import com.noobdev.waffle.R
import com.noobdev.waffle.core.prefs.PreferencesManager
import com.noobdev.waffle.data.remote.RouteResult
import com.noobdev.waffle.data.remote.formatDistance
import com.noobdev.waffle.data.remote.formatDuration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Keeps navigation alive when the map screen is not. It owns [NavigationController], shows the
 * ongoing turn-by-turn notification, and holds a partial wake lock. The UI reads
 * [NavigationSession] and can be destroyed/recreated without stopping the trip.
 */
class NavigationService : Service() {

    companion object {
        const val ACTION_START = "com.noobdev.waffle.navigation.START"
        const val ACTION_STOP = "com.noobdev.waffle.navigation.STOP"
        const val ACTION_SHOW_FLOATING = "com.noobdev.waffle.navigation.SHOW_FLOATING"
        const val ACTION_HIDE_FLOATING = "com.noobdev.waffle.navigation.HIDE_FLOATING"
        const val ACTION_TOGGLE_VOICE = "com.noobdev.waffle.navigation.TOGGLE_VOICE"
        private const val CHANNEL_ID = "waffle_navigation"
        private const val NOTIFICATION_ID = 4242
        private const val FLOATING_NOTIFICATION_ID = 4243
        private const val WAKE_TAG = "Waffle:Navigation"

        fun start(context: Context) {
            val intent =
                    Intent(context, NavigationService::class.java).setAction(ACTION_START)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            val intent =
                    Intent(context, NavigationService::class.java).setAction(ACTION_STOP)
            context.startService(intent)
        }

        fun showFloating(context: Context) {
            val intent =
                    Intent(context, NavigationService::class.java)
                            .setAction(ACTION_SHOW_FLOATING)
            // The overlay is requested from the visible activity. Use a normal service
            // start here: the service is already foreground during navigation, and a
            // startForegroundService() that races with an inactive session can throw
            // ForegroundServiceDidNotStartInTimeException. Never let a failed background
            // service start crash the activity on the way out.
            runCatching { context.startService(intent) }
        }

        fun hideFloating(context: Context) {
            val intent =
                    Intent(context, NavigationService::class.java)
                            .setAction(ACTION_HIDE_FLOATING)
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var controller: NavigationController? = null
    private var controllerRoute: RouteResult? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var foregroundStarted = false
    private var floatingOverlay: FloatingNavigationOverlay? = null
    private lateinit var preferences: PreferencesManager
    private lateinit var voice: NavigationVoice
    private var voiceEnabled = true

    override fun onCreate() {
        super.onCreate()
        createChannel()
        preferences = PreferencesManager(applicationContext)
        voice = NavigationVoice(this)
        serviceScope.launch {
            preferences.voiceEnabled.collect { enabled ->
                voiceEnabled = enabled
                voice.setEnabled(enabled)
                if (foregroundStarted) {
                    notificationManager()
                            .notify(NOTIFICATION_ID, buildNotification(NavigationSession.snapshot()))
                }
            }
        }
        serviceScope.launch {
            NavigationSession.state.collect { snapshot ->
                voice.onSnapshot(snapshot)
                if (snapshot.active) {
                    if (!foregroundStarted) {
                        startForegroundCompat(buildNotification(snapshot))
                    } else {
                        notificationManager().notify(NOTIFICATION_ID, buildNotification(snapshot))
                    }
                } else if (foregroundStarted) {
                    hideFloatingInternal()
                    stopForegroundCompat()
                    stopSelf()
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_TOGGLE_VOICE -> {
                serviceScope.launch { preferences.setVoiceEnabled(!voiceEnabled) }
                return START_STICKY
            }
            ACTION_STOP -> {
                controller?.stop()
                controller = null
                controllerRoute = null
                hideFloatingInternal()
                NavigationSession.update { NavigationSnapshot() }
                NavigationStore.clear(this)
                stopForegroundCompat()
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_SHOW_FLOATING -> {
                val active = NavigationSession.snapshot().takeIf { it.active }
                if (active != null) {
                    startForegroundCompat(buildNotification(active))
                    showFloatingInternal()
                } else {
                    stopSelf()
                }
                return START_STICKY
            }
            ACTION_HIDE_FLOATING -> {
                hideFloatingInternal()
                return START_STICKY
            }
        }

        val snapshot =
                NavigationSession.snapshot().takeIf { it.active }
                        ?: NavigationStore.load(this)?.also {
                            NavigationSession.restoreInternal(it)
                        }
        if (snapshot == null || !snapshot.active) {
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundCompat(buildNotification(snapshot))
        acquireWakeLock()
        // A new NavigationSession.start() publishes a different RouteResult. If the service was
        // already running for an old trip (for example the user planned a second destination
        // without pressing Exit), reusing the old controller would keep tracking the old route
        // and announce the old arrival. Restart the controller for the new trip.
        if (controller == null || controllerRoute !== snapshot.route) {
            controller?.stop()
            controller = NavigationController(this, snapshot).also { it.start() }
            controllerRoute = snapshot.route
        }
        return START_STICKY
    }

    override fun onDestroy() {
        hideFloatingInternal()
        controller?.stop()
        controller = null
        runCatching { voice.shutdown() }
        serviceScope.cancel()
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showFloatingInternal() {
        if (!Settings.canDrawOverlays(this)) {
            notifyFloatingPermission()
            return
        }
        if (floatingOverlay?.isShowing() == true) return
        runCatching { floatingOverlay?.hide() }
        val sizeStep =
                getSharedPreferences("waffle_floating", MODE_PRIVATE)
                        .getInt("size_step", 0)
        val overlay =
                runCatching {
                                FloatingNavigationOverlay(
                                                this,
                                                getSystemService(Context.WINDOW_SERVICE)
                                                        as WindowManager,
                                                sizeStep = sizeStep
                                        )
                                        .takeIf { it.show() }
                        }
                        .onFailure { Log.e("NavigationService", "Floating map failed", it) }
                        .getOrNull()
        floatingOverlay = overlay
        if (overlay == null) notifyFloatingFailed()
    }

    private fun notifyFloatingPermission() {
        val settingsIntent =
                PendingIntent.getActivity(
                        this,
                        4,
                        Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                android.net.Uri.parse("package:$packageName")
                        ),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val notification =
                NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_navigation)
                        .setContentTitle("Floating map needs permission")
                        .setContentText("Allow display over other apps to keep the map visible.")
                        .setContentIntent(settingsIntent)
                        .setAutoCancel(true)
                        .build()
        notificationManager().notify(FLOATING_NOTIFICATION_ID, notification)
    }

    private fun notifyFloatingFailed() {
        val openIntent =
                PendingIntent.getActivity(
                        this,
                        5,
                        Intent(this, MainActivity::class.java).apply {
                            flags =
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("open_navigation", true)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val notification =
                NotificationCompat.Builder(this, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_navigation)
                        .setContentTitle("Floating map unavailable")
                        .setContentText("Tap to open navigation and try again.")
                        .setContentIntent(openIntent)
                        .setAutoCancel(true)
                        .build()
        notificationManager().notify(FLOATING_NOTIFICATION_ID, notification)
    }

    private fun hideFloatingInternal() {
        runCatching { floatingOverlay?.hide() }
        floatingOverlay = null
    }

    private fun buildNotification(snapshot: NavigationSnapshot): Notification {
        val instruction = snapshot.instruction
        val detail =
                buildString {
                        append(formatDistance(snapshot.remainingDistance))
                        append(" · ")
                        append(formatDuration(snapshot.remainingDuration))
                        if (snapshot.isRerouting) append(" · rerouting")
                        if (snapshot.wrongWay) append(" · wrong way")
                        if (snapshot.arrived) append(" · arrived")
                }

        val openIntent =
                Intent(this, MainActivity::class.java).apply {
                    flags =
                            Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra("open_navigation", true)
                }
        val contentIntent =
                PendingIntent.getActivity(
                        this,
                        0,
                        openIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val stopIntent =
                PendingIntent.getService(
                        this,
                        1,
                        Intent(this, NavigationService::class.java).setAction(ACTION_STOP),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val floatIntent =
                PendingIntent.getService(
                        this,
                        2,
                        Intent(this, NavigationService::class.java)
                                .setAction(ACTION_SHOW_FLOATING),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val pipIntent =
                PendingIntent.getActivity(
                        this,
                        3,
                        Intent(this, MainActivity::class.java).apply {
                            flags =
                                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                                            Intent.FLAG_ACTIVITY_CLEAR_TOP
                            putExtra("enter_pip", true)
                        },
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val voiceIntent =
                PendingIntent.getService(
                        this,
                        6,
                        Intent(this, NavigationService::class.java)
                                .setAction(ACTION_TOGGLE_VOICE),
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

        return NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_navigation)
                .setContentTitle(instruction)
                .setContentText(detail)
                .setContentIntent(contentIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .addAction(0, "Float", floatIntent)
                .addAction(0, "PiP", pipIntent)
                .addAction(0, if (voiceEnabled) "Mute" else "Unmute", voiceIntent)
                .addAction(0, "Exit", stopIntent)
                .build()
    }

    private fun startForegroundCompat(notification: Notification) {
        if (foregroundStarted) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                        NOTIFICATION_ID,
                        notification,
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
        } catch (security: SecurityException) {
            // Location permission can be revoked while a trip is active, or the OS can reject a
            // location FGS started from the background. Fail closed instead of crashing the app.
            Log.e("NavigationService", "Location foreground service rejected", security)
            stopSelf()
        }
    }

    private fun stopForegroundCompat() {
        if (!foregroundStarted) return
        stopForeground(STOP_FOREGROUND_REMOVE)
        foregroundStarted = false
    }

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        val power = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
                power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_TAG).apply {
                    setReferenceCounted(false)
                    // Navigation can run for hours, but a forgotten wake lock must not survive
                    // forever. The service releases it on stop/destroy; this is a safety net.
                    acquire(12L * 60L * 60L * 1000L)
                }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel =
                NotificationChannel(
                                CHANNEL_ID,
                                "Navigation",
                                NotificationManager.IMPORTANCE_LOW
                        )
                        .apply {
                            description = "Turn-by-turn navigation"
                            setShowBadge(false)
                            enableVibration(false)
                            setSound(null, null)
                        }
        notificationManager().createNotificationChannel(channel)
    }

    private fun notificationManager(): NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
}
