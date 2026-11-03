package com.noobdev.waffle

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.noobdev.waffle.feature.alerts.data.EmergencySyncScheduler
import com.noobdev.waffle.data.local.AlertRepository
import com.noobdev.waffle.data.local.AppDatabase
import com.noobdev.waffle.core.navigation.HazardRegistry
import com.noobdev.waffle.core.navigation.LaneGuidanceRepository
import com.noobdev.waffle.core.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.maplibre.android.MapLibre
import org.maplibre.android.WellKnownTileServer

class WaffleApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: AlertRepository
        private set

    lateinit var notificationHelper: NotificationHelper
        private set

    override fun onCreate() {
        super.onCreate()

        // The navigation foreground service can create the floating map after the activity is
        // gone, so MapLibre must be initialised at process start, not only in MainActivity.
        MapLibre.getInstance(this, BuildConfig.MAPTILER_API_KEY, WellKnownTileServer.MapLibre)

        database = AppDatabase.getDatabase(this)
        repository = AlertRepository(database.alertDao(), this)
        notificationHelper = NotificationHelper(this)

        // Load cached hazards immediately so the first route already avoids known danger areas,
        // then keep the public feeds fresh in the background.
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                val cached = database.alertDao().getAllAlerts().map { it.toDomainModel() }
                HazardRegistry.updateFromAlerts(cached)
                Log.d("WaffleApplication", "Loaded ${cached.size} cached alerts into hazard registry")
            }
            runCatching { LaneGuidanceRepository.ensureLoaded(this@WaffleApplication) }
        }
        EmergencySyncScheduler.schedule(this)
        EmergencySyncScheduler.syncNow(this)

        // Authenticate anonymously before syncing alerts globally
        FirebaseAuth.getInstance().signInAnonymously().addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e("WaffleApplication", "Anonymous Auth Failed: ${task.exception?.message}")
            }
            repository.startRealTimeSync()
        }
    }
}
