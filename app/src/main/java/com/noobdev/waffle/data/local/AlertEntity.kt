package com.noobdev.waffle.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.noobdev.waffle.data.model.StatusLevel
import com.noobdev.waffle.data.model.DisasterAlert

@Entity(tableName = "disaster_alerts")
data class AlertEntity(
    @PrimaryKey
    val id: String,
    val title: String?,
    val location_name: String?,
    val type: String?,
    val status: StatusLevel?,
    val timestamp: String?,
    val latitude: Double?,
    val longitude: Double?,
    val details: Map<String, Any>?
) {
    fun toDomainModel(): DisasterAlert {
        return DisasterAlert(
            id = id,
            title = title,
            location_name = location_name,
            type = type,
            status = status,
            timestamp = timestamp,
            latitude = latitude,
            longitude = longitude,
            details = details
        )
    }
}

fun DisasterAlert.toEntity(): AlertEntity? {
    if (id == null) return null
    return AlertEntity(
        id = id,
        title = title,
        location_name = location_name,
        type = type,
        status = status,
        timestamp = timestamp,
        latitude = latitude,
        longitude = longitude,
        details = details
    )
}

