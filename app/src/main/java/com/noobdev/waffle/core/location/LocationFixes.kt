package com.noobdev.waffle.core.location

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * One fresh-then-cached location helper for lightweight screens (Home, Rescue).
 *
 * Navigation owns its own continuous callback; screens that only need a coordinate should not
 * duplicate permission, timeout and fallback logic.
 */
suspend fun currentOrLastLocation(context: Context): Pair<Double, Double>? {
    val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
    if (!granted) return null

    return suspendCancellableCoroutine { continuation ->
        val client = LocationServices.getFusedLocationProviderClient(context)
        var resolved = false
        fun finish(location: android.location.Location?) {
            if (resolved || !continuation.isActive) return
            resolved = true
            continuation.resume(location?.let { it.latitude to it.longitude })
        }
        try {
            client.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { location ->
                        if (location != null) {
                            finish(location)
                        } else {
                            client.lastLocation
                                    .addOnSuccessListener { finish(it) }
                                    .addOnFailureListener { finish(null) }
                        }
                    }
                    .addOnFailureListener {
                        client.lastLocation
                                .addOnSuccessListener { finish(it) }
                                .addOnFailureListener { finish(null) }
                    }
        } catch (security: SecurityException) {
            finish(null)
        }
    }
}
