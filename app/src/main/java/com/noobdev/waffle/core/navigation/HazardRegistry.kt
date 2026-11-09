package com.noobdev.waffle.core.navigation

import com.noobdev.waffle.data.model.DisasterAlert
import com.noobdev.waffle.data.model.StatusLevel
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Single in-memory source of truth for "places a route should not go through right now".
 *
 * Alerts arrive from Firestore (backend), the on-device emergency feed and FCM. All of them end up
 * here as [HazardZone]s, so the notification logic and the router agree on the same geometry.
 *
 * Hazards are routing input only: they are deliberately not drawn on the map. A red circle over a
 * months-old import told the user nothing actionable and just made the map look broken.
 */
object HazardRegistry {

    /**
     * Alerts older than this stop creating keep-out zones. The public feeds (USGS, GDACS) are
     * live, so this only drops historical imports such as the 2022 flood extents, which otherwise
     * rerouted every trip around Islamabad forever.
     */
    private const val MAX_HAZARD_AGE_MILLIS = 7L * 24 * 60 * 60 * 1000

    private val _hazards = MutableStateFlow<List<HazardZone>>(emptyList())
    val hazards: StateFlow<List<HazardZone>> = _hazards.asStateFlow()

    fun updateFromAlerts(alerts: List<DisasterAlert>, nowMillis: Long = System.currentTimeMillis()) {
        var resolved = 0
        var stale = 0
        val zones =
                alerts
                        .filter {
                            val keep = it.status != StatusLevel.SAFE
                            if (!keep) resolved++
                            keep
                        }
                        .filter {
                            val keep = !isStale(it.timestamp, nowMillis)
                            if (!keep) stale++
                            keep
                        }
                        .mapNotNull { BlastRadius.fromAlert(it) }
        update(zones)
        android.util.Log.d(
                "HazardRegistry",
                "kept ${zones.size} of ${alerts.size} alerts " +
                        "(dropped $resolved resolved, $stale older than 7d)"
        )
    }

    /**
     * True when the alert is older than [MAX_HAZARD_AGE_MILLIS]. Unparseable or missing timestamps
     * are treated as current: dropping a live alert is worse than keeping a stale one.
     */
    private fun isStale(timestamp: String?, nowMillis: Long): Boolean {
        val text = timestamp?.trim().orEmpty()
        if (text.isEmpty()) return false
        val parsed =
                runCatching { Instant.parse(text) }.getOrNull()
                        ?: runCatching {
                                    LocalDateTime.parse(text).toInstant(ZoneOffset.UTC)
                                }
                                .getOrNull()
                        ?: return false
        return nowMillis - parsed.toEpochMilli() > MAX_HAZARD_AGE_MILLIS
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
