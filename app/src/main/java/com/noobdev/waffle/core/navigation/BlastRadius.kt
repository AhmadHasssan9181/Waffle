package com.noobdev.waffle.core.navigation

import com.noobdev.waffle.data.model.StatusLevel
import com.noobdev.waffle.data.model.DisasterAlert
import kotlin.math.PI
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Turns a disaster alert into a physical "keep out" zone.
 *
 * The numbers are deliberately conservative estimates, not scientific predictions. They exist so
 * routing can answer one question: "is this road close enough to the event that we should send the
 * user around it?" Sources for the heuristics:
 *
 *  - Earthquakes: felt-radius approximation (10^(0.43 * M - 0.8) km), then reduced for routing
 *    because only the strongly shaken core needs to be avoided.
 *  - Floods: explicit radius/area from the alert when the backend provides one, otherwise
 *    severity-based defaults used by Pakistan flood reporting.
 *  - Wildfire / storm: severity-based defaults.
 */
object BlastRadius {

    private const val MIN_RADIUS_METERS = 400.0
    private const val MAX_RADIUS_METERS = 250_000.0

    /** Radius used for drawing and for "is the user inside the hazard" checks. */
    fun displayRadiusMeters(alert: DisasterAlert): Double =
            scaledRadiusMeters(alert, routing = false)

    /**
     * Radius used by the router. Slightly smaller than the display radius: a flood extent is not a
     * perfect circle, and a route that runs just outside the reported boundary is usually safe.
     */
    fun routingRadiusMeters(alert: DisasterAlert): Double =
            scaledRadiusMeters(alert, routing = true)

    fun fromAlert(alert: DisasterAlert): HazardZone? {
        val latitude = alert.latitude ?: return null
        val longitude = alert.longitude ?: return null
        val type = normalizeType(alert.type)
        return HazardZone(
                id = alert.id ?: "$type:$latitude,$longitude:${alert.timestamp.orEmpty()}",
                title = alert.title?.takeIf { it.isNotBlank() } ?: "Active $type",
                type = type,
                severity = (alert.status ?: StatusLevel.INFO).name,
                center = GeoPoint(latitude, longitude),
                radiusMeters = displayRadiusMeters(alert),
                source = "alert",
                timestamp = alert.timestamp
        )
    }

    private fun scaledRadiusMeters(alert: DisasterAlert, routing: Boolean): Double {
        val base = baseRadiusMeters(alert)
        val severityScale = when (alert.status) {
            StatusLevel.CRITICAL -> 1.35
            StatusLevel.WARNING -> 1.0
            StatusLevel.INFO -> 0.7
            StatusLevel.SAFE -> 0.35
            null -> 1.0
        }
        val routingScale = if (routing) 0.65 else 1.0
        return (base * severityScale * routingScale)
                .coerceIn(MIN_RADIUS_METERS, MAX_RADIUS_METERS)
    }

    private fun baseRadiusMeters(alert: DisasterAlert): Double {
        val details = alert.details.orEmpty()
        return when (normalizeType(alert.type)) {
            "earthquake" -> earthquakeRadiusMeters(details)
            "flood" -> floodRadiusMeters(details, alert.status)
            "wildfire" -> wildfireRadiusMeters(alert.status)
            "storm", "cyclone", "hurricane", "typhoon" -> stormRadiusMeters(alert.status)
            "landslide" -> 2_500.0
            else -> 3_000.0
        }
    }

    private fun earthquakeRadiusMeters(details: Map<String, Any>): Double {
        val magnitude = number(details["magnitude"] ?: details["mag"])
        if (magnitude != null && magnitude > 0.0) {
            val feltRadiusKm = 10.0.pow(0.43 * magnitude - 0.8)
            return feltRadiusKm * 1_000.0
        }
        return 10_000.0
    }

    private fun floodRadiusMeters(details: Map<String, Any>, status: StatusLevel?): Double {
        val explicitKm = number(details["radius_km"] ?: details["radiusKm"])
        if (explicitKm != null && explicitKm > 0.0) return explicitKm * 1_000.0

        val areaKm2 = number(details["area_km2"] ?: details["areaKm2"])
        if (areaKm2 != null && areaKm2 > 0.0) {
            return sqrt(areaKm2 / PI) * 1_000.0
        }

        return when (status) {
            StatusLevel.CRITICAL -> 18_000.0
            StatusLevel.WARNING -> 9_000.0
            StatusLevel.INFO -> 4_000.0
            else -> 3_000.0
        }
    }

    private fun wildfireRadiusMeters(status: StatusLevel?): Double =
            when (status) {
                StatusLevel.CRITICAL -> 12_000.0
                StatusLevel.WARNING -> 5_000.0
                StatusLevel.INFO -> 1_800.0
                else -> 1_500.0
            }

    private fun stormRadiusMeters(status: StatusLevel?): Double =
            when (status) {
                StatusLevel.CRITICAL -> 90_000.0
                StatusLevel.WARNING -> 45_000.0
                StatusLevel.INFO -> 18_000.0
                else -> 15_000.0
            }

    fun normalizeType(type: String?): String {
        val value = type?.trim()?.lowercase().orEmpty()
        return when {
            value.contains("earth") || value == "eq" -> "earthquake"
            value.contains("flood") || value == "fl" -> "flood"
            value.contains("fire") || value == "wf" -> "wildfire"
            value.contains("cyclone") || value.contains("storm") || value.contains("hurricane") -> "storm"
            value.contains("landslide") -> "landslide"
            value.isBlank() -> "alert"
            else -> value
        }
    }

    private fun number(value: Any?): Double? =
            when (value) {
                is Number -> value.toDouble()
                is String -> value.replace(",", "").toDoubleOrNull()
                else -> null
            }
}
