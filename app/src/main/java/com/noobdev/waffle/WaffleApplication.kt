package com.noobdev.waffle

import android.app.Application
import com.noobdev.waffle.database.AlertRepository
import com.noobdev.waffle.database.AppDatabase
import com.noobdev.waffle.notifications.NotificationHelper

class WaffleApplication : Application() {

    lateinit var database: AppDatabase
        private set

    lateinit var repository: AlertRepository
        private set

    lateinit var notificationHelper: NotificationHelper
        private set

    override fun onCreate() {
        super.onCreate()

        database = AppDatabase.getDatabase(this)
        repository = AlertRepository(database.alertDao(), this)
        notificationHelper = NotificationHelper(this)

        // Start syncing alerts globally right when app starts
        repository.startRealTimeSync()
    }
}

