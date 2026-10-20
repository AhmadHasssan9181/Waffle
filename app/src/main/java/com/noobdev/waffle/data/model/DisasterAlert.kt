package com.noobdev.waffle.data.model

import com.google.firebase.firestore.PropertyName
import com.google.gson.annotations.SerializedName

/**
 * A single alert, from Firebase, the public emergency feeds, or the local Room cache.
 *
 * The field names are snake_case because they come straight from Firestore documents and the
 * USGS/GDACS payloads. Keep them stable: they are persisted in Room and written to Firestore.
 */
data class DisasterAlert(
        val id: String? = null,
        val title: String? = null,
        @SerializedName("location_name") var location_name: String? = null,
        val type: String? = null, // earthquake, flood, wildfire, storm
        val status: StatusLevel? = null,
        val timestamp: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val details: Map<String, Any>? = emptyMap()
)
