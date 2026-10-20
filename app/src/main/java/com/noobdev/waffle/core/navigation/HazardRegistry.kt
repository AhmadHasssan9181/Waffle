package com.noobdev.waffle.core.navigation

import com.noobdev.waffle.data.model.DisasterAlert
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single in-memory source of truth for "places a route should not go through right now".
 *
 * Alerts arrive from Firestore (backend), the on-device emergency feed and FCM. All of them end up
 * here as [HazardZone]s, so the map, the notification logic and the router all agree on the same
 * geometry.
 */
object HazardRegistry {

    private val _hazards = MutableStateFlow<List<HazardZone>>(emptyList())
    val hazards: StateFlow<List<HazardZone>> = _hazards.asStateFlow()

    fun updateFromAlerts(alerts: List<DisasterAlert>) {
        val zones = alerts.mapNotNull { BlastRadius.fromAlert(it) }
        update(zones)
    }

    fun update(zones: List<HazardZone>) {
        _hazards.value =
                zones
                        .groupBy { it.id }
                        .map { (_, duplicates) -> duplicates.first() }
                        .sortedWith(
                                compareByDescending<HazardZone> { severityRank(it.severity) }
                                        .thenByDescending { it.radiusMeters }
                        )
    }

    fun add(zone: HazardZone) {
        update(_hazards.value + zone)
    }

    fun remove(id: String) {
        update(_hazards.value.filterNot { it.id == id })
    }

    fun clear() {
        _hazards.value = emptyList()
    }

    fun active(): List<HazardZone> = _hazards.value

    fun hazardsNear(point: GeoPoint, radiusMeters: Double): List<HazardZone> =
            _hazards.value.filter { hazard ->
                GeoGeometry.haversineMeters(point, hazard.center) <=
                        hazard.radiusMeters + radiusMeters
            }

    fun hazardsForRoute(route: List<GeoPoint>, marginMeters: Double = 150.0): List<RouteHazardHit> =
            GeoGeometry.findHazardHits(route, _hazards.value, marginMeters)

    /**
     * True when the user is inside (or within [bufferMeters] of) a hazard. Used to escalate
     * notifications and to warn before navigation starts.
     */
    fun dangerAt(point: GeoPoint, bufferMeters: Double = 0.0): HazardZone? =
            _hazards.value
                    .filter {
                        GeoGeometry.haversineMeters(point, it.center) <=
                                it.radiusMeters + bufferMeters
                    }
                    .maxByOrNull { severityRank(it.severity) }

    private fun severityRank(severity: String): Int =
            when (severity.uppercase()) {
                "CRITICAL" -> 4
                "WARNING" -> 3
                "INFO" -> 2
                "SAFE" -> 1
                else -> 0
            }
}
