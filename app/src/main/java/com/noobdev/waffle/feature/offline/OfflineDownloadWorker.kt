package com.noobdev.waffle.feature.offline

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import com.noobdev.waffle.MainActivity
import com.noobdev.waffle.R
import kotlinx.coroutines.delay

/**
 * Holds the process in a foreground state while MapLibre writes an offline region.
 *
 * WorkManager owns the restart: if the process is killed, the unique foreground work is re-created
 * and [OfflineMapManager.resumeIncomplete] re-attaches to any incomplete native regions.
 */
class OfflineDownloadWorker(
        appContext: Context,
        params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        createChannel()
        setForeground(foregroundInfo(0))
        OfflineMapManager.resumeIncomplete(applicationContext)

        var idleTicks = 0
        while (!isStopped) {
            val statuses = OfflineMapManager.downloads.value
            val active =
                    statuses.values.any {
                        it.state == OfflineDownloadState.QUEUED ||
                                it.state == OfflineDownloadState.DOWNLOADING
                    }
            val progress =
                    statuses.values
                            .filter { it.state != OfflineDownloadState.COMPLETE }
                            .map { it.progress }
                            .maxOrNull()
                            ?: 0f
            setForeground(foregroundInfo((progress * 100).toInt()))
            if (active) {
                idleTicks = 0
                delay(1_000)
            } else {
                idleTicks++
                if (idleTicks >= 5) break
                delay(1_000)
            }
        }
        return Result.success()
    }

    private fun foregroundInfo(progress: Int): ForegroundInfo {
        val intent =
                Intent(applicationContext, MainActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
                }
        val pending =
                PendingIntent.getActivity(
                        applicationContext,
                        9,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
        val notification: Notification =
                NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                        .setSmallIcon(R.drawable.ic_stat_navigation)
                        .setContentTitle("Downloading offline map")
                        .setContentText(
                                if (progress in 1..99) "$progress% complete"
                                else "Preparing map tiles"
                        )
                        .setProgress(100, progress.coerceIn(0, 100), progress <= 0)
                        .setOngoing(true)
                        .setContentIntent(pending)
                        .setPriority(NotificationCompat.PRIORITY_LOW)
                        .build()
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                    NOTIFICATION_ID,
                    notification,
                    android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            ForegroundInfo(NOTIFICATION_ID, notification)
        }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager =
                applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
                        as NotificationManager
        manager.createNotificationChannel(
                NotificationChannel(
                        CHANNEL_ID,
                        "Offline map downloads",
                        NotificationManager.IMPORTANCE_LOW
                )
        )
    }

    companion object {
        private const val CHANNEL_ID = "waffle_offline_downloads"
        private const val NOTIFICATION_ID = 7301
        private const val WORK_NAME = "waffle_offline_download"

        fun start(context: Context) {
            WorkManager.getInstance(context)
                    .enqueueUniqueWork(
                            WORK_NAME,
                            ExistingWorkPolicy.REPLACE,
                            OneTimeWorkRequestBuilder<OfflineDownloadWorker>().build()
                    )
        }
    }
}
