package com.noobdev.waffle.feature.alerts.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.noobdev.waffle.data.local.AppDatabase
import com.noobdev.waffle.core.navigation.BlastRadius
import com.noobdev.waffle.core.navigation.GeoGeometry
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.core.navigation.HazardRegistry
import com.noobdev.waffle.core.notifications.NotificationHelper
import com.noobdev.waffle.data.model.StatusLevel
import com.noobdev.waffle.core.prefs.PreferencesManager
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.flow.first

/**
 * Background sync for the public emergency feeds.
 *
 * Runs even when the Firebase backend is unreachable. New warning/critical alerts notify only if
 * they are close enough to matter to this user, which keeps the notification stream trustworthy.
 */
class EmergencySyncWorker(
        appContext: Context,
        workerParameters: WorkerParameters
) : CoroutineWorker(appContext, workerParameters) {

    override suspend fun doWork(): Result {
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.alertDao()
        val preferences = PreferencesManager(applicationContext)

        val sync = EmergencyFeedRepository().sync(dao)
        if (sync.fetched.isEmpty() && sync.errors.size >= 2) {
            return Result.retry()
        }

        // Refresh the routing hazard registry from the offline cache. Even if notifications are
        // off, the next route should still avoid active danger areas.
        val cachedAlerts = dao.getAllAlerts().map { it.toDomainModel() }
        HazardRegistry.updateFromAlerts(cachedAlerts)

        notifyRelevantNewAlerts(sync.newAlerts, preferences)
        return Result.success()
    }

    private suspend fun notifyRelevantNewAlerts(
            alerts: List<com.noobdev.waffle.data.model.DisasterAlert>,
            preferences: PreferencesManager
    ) {
        if (alerts.isEmpty()) return
        if (!preferences.notificationsEnabled.first()) return

        val (latitude, longitude) = preferences.userLocation.first()
        if (latitude == null || longitude == null) return

        val floodEnabled = preferences.floodAlertsEnabled.first()
        val earthquakeEnabled = preferences.earthquakeAlertsEnabled.first()
        val wildfireEnabled = preferences.wildfireAlertsEnabled.first()

        val userPoint = GeoPoint(latitude, longitude)
        val notificationHelper = NotificationHelper(applicationContext)

        for (alert in alerts) {
            if (alert.status != StatusLevel.CRITICAL && alert.status != StatusLevel.WARNING) {
                continue
            }
            if (!typeEnabled(alert.type, floodEnabled, earthquakeEnabled, wildfireEnabled)) continue

            val zone = BlastRadius.fromAlert(alert) ?: continue
            val distance = GeoGeometry.haversineMeters(userPoint, zone.center)
            val relevantRange = zone.radiusMeters + 150_000.0 // 150 km outer warning belt

            if (distance <= relevantRange) {
                notificationHelper.showDisasterAlertNotification(alert)
            }
        }
    }

    private fun typeEnabled(
            type: String?,
            floodEnabled: Boolean,
            earthquakeEnabled: Boolean,
            wildfireEnabled: Boolean
    ): Boolean =
            when (BlastRadius.normalizeType(type)) {
                "flood" -> floodEnabled
                "earthquake" -> earthquakeEnabled
                "wildfire" -> wildfireEnabled
                else -> true
            }
}

object EmergencySyncScheduler {

    private const val PERIODIC_WORK = "waffle_emergency_feed_sync"
    private const val IMMEDIATE_WORK = "waffle_emergency_feed_now"

    fun schedule(context: Context) {
        val constraints =
                Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

        val request =
                PeriodicWorkRequestBuilder<EmergencySyncWorker>(30, TimeUnit.MINUTES)
                        .setConstraints(constraints)
                        .setBackoffCriteria(
                                androidx.work.BackoffPolicy.EXPONENTIAL,
                                15,
                                TimeUnit.MINUTES
                        )
                        .build()

        WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(
                        PERIODIC_WORK,
                        ExistingPeriodicWorkPolicy.KEEP,
                        request
                )
    }

    /** Ask for a sync now (app start, pull-to-refresh, or after regaining signal). */
    fun syncNow(context: Context) {
        val constraints =
                Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()

        val request =
                OneTimeWorkRequestBuilder<EmergencySyncWorker>()
                        .setConstraints(constraints)
                        .build()

        WorkManager.getInstance(context)
                .enqueueUniqueWork(IMMEDIATE_WORK, ExistingWorkPolicy.REPLACE, request)
    }
}
