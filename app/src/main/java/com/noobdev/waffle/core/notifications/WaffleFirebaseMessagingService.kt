package com.noobdev.waffle.core.notifications

import android.util.Log
import com.noobdev.waffle.BuildConfig
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.noobdev.waffle.data.model.DisasterAlert
import com.noobdev.waffle.data.model.StatusLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WaffleFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        if (BuildConfig.DEBUG) Log.d("FCM", "From: ${remoteMessage.from}")

        // Check if message contains a data payload (for background processing)
        if (remoteMessage.data.isNotEmpty()) {
            if (BuildConfig.DEBUG) Log.d("FCM", "Received data payload: ${remoteMessage.data.keys}")

            // Parse alert from data payload
            val data = remoteMessage.data
            var enumStatus: StatusLevel? = null
            try {
                enumStatus = StatusLevel.valueOf(data["status"] ?: "INFO")
            } catch (e: Exception) {}

            val alert = DisasterAlert(
                id = data["id"],
                title = data["title"],
                location_name = data["locationName"],
                type = data["type"],
                status = enumStatus,
                timestamp = data["timestamp"]
            )

            NotificationHelper(applicationContext).showDisasterAlertNotification(alert)
        }

        // Check if message contains a notification payload (Firebase does this natively, but we can override or log it)
        remoteMessage.notification?.let {
            if (BuildConfig.DEBUG) Log.d("FCM", "Received notification payload")
            // We can also trigger from notification directly if we want
            val alert = DisasterAlert(
                title = it.title,
                type = "Alert",
                location_name = it.body
            )
            NotificationHelper(applicationContext).showDisasterAlertNotification(alert)
        }
    }

    override fun onNewToken(token: String) {
        // Never log the registration token. It is a bearer-like identifier for push delivery.
        if (BuildConfig.DEBUG) Log.d("FCM", "Registration token refreshed")
        // Send the token to the backend here if per-device targeting is added later.
    }
}
