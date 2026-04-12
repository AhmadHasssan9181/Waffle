package com.noobdev.waffle.notifications

import android.util.Log
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.noobdev.waffle.viewmodel.DisasterAlert
import com.noobdev.waffle.ui.components.StatusLevel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class WaffleFirebaseMessagingService : FirebaseMessagingService() {

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)

        Log.d("FCM", "From: ${remoteMessage.from}")

        // Check if message contains a data payload (for background processing)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d("FCM", "Message data payload: ${remoteMessage.data}")

            // Parse alert from data payload
            val data = remoteMessage.data
            var enumStatus: StatusLevel? = null
            try {
                enumStatus = StatusLevel.valueOf(data["status"] ?: "INFO")
            } catch (e: Exception) {}

            val alert = DisasterAlert(
                id = data["id"],
                title = data["title"],
                locationName = data["locationName"],
                type = data["type"],
                status = enumStatus,
                timestamp = data["timestamp"]
            )

            NotificationHelper(applicationContext).showDisasterAlertNotification(alert)
        }

        // Check if message contains a notification payload (Firebase does this natively, but we can override or log it)
        remoteMessage.notification?.let {
            Log.d("FCM", "Message Notification Body: ${it.body}")
            // We can also trigger from notification directly if we want
            val alert = DisasterAlert(
                title = it.title,
                type = "Alert",
                locationName = it.body
            )
            NotificationHelper(applicationContext).showDisasterAlertNotification(alert)
        }
    }

    override fun onNewToken(token: String) {
        Log.d("FCM", "Refreshed token: $token")
        // If you want to send messages to this application instance,
        // send the token to your Python app server.
    }
}
