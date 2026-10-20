package com.noobdev.waffle.core.navigation

import android.util.Log
import com.noobdev.waffle.data.remote.RouteResult
import com.noobdev.waffle.core.routing.RouteRepository
import com.noobdev.waffle.core.routing.RouteSource

/**
 * Navigation facade used by the map screen.
 *
 * Routing itself lives in [RouteRepository]; this class owns navigation session state (destination,
 * current route, closures) and merges dynamic road closures into the active hazard set so the
 * detour planner treats them exactly like flood zones.
 */
object NavigationManager {

    private const val TAG = "NavigationManager"

    private val roadClosures = mutableListOf<RoadClosure>()

    private var currentRoute: RouteResult? = null
    private var destinationLat: Double = 0.0
    private var destinationLng: Double = 0.0

    @Volatile
    var lastRouteSource: RouteSource? = null
        private set

    @Volatile
    var lastAvoidedHazards: List<HazardZone> = emptyList()
        private set

    data class RoadClosure(
            val id: String,
            val name: String,
            val lat: Double,
            val lng: Double,
            val radius: Double, // meters
            val reason: String,
            val isActive: Boolean = true
    )

    data class FloodZone(
            val sectorName: String,
            val centerLat: Double,
            val centerLng: Double,
            val boundingBox: List<List<Double>>,
            val severity: String
    )

    data class NavigationState(
            val isNavigating: Boolean = false,
            val currentRoute: RouteResult? = null,
            val currentInstructionIndex: Int = 0,
            val distanceRemaining: Double = 0.0,
            val durationRemaining: Double = 0.0,
            val nextInstruction: String = "",
            val nextTurnDistance: String = "",
            val isRerouting: Boolean = false,
            val avoidingFloodZones: Int = 0,
            val avoidingClosures: Int = 0
    )

    /**
     * Historical EMSR flood sectors used to be turned into 5 km boxes here, which made every route
     * near Punjab "avoid" a flood that happened years ago. Active hazards now come from
     * [HazardRegistry] (live alerts + closures), so this hook only logs.
     */
    fun initializeFloodZones() {
        Log.d(TAG, "Hazard routing uses HazardRegistry; ${HazardRegistry.active().size} active zones")
    }

    fun addRoadClosure(closure: RoadClosure) {
        roadClosures.removeAll { it.id == closure.id }
        roadClosures.add(closure)
        Log.d(TAG, "Added road closure: ${closure.name}")
    }

    fun removeRoadClosure(id: String) {
        roadClosures.removeAll { it.id == id }
    }

    fun getActiveClosures(): List<RoadClosure> = roadClosures.filter { it.isActive }

    fun getActiveFloodZones(): List<FloodZone> =
            HazardRegistry.active().map { hazard ->
                FloodZone(
                        sectorName = hazard.title,
                        centerLat = hazard.center.latitude,
                        centerLng = hazard.center.longitude,
                        boundingBox =
                                GeoGeometry.circlePolygon(hazard.center, hazard.radiusMeters, 36)
                                        .map { listOf(it.longitude, it.latitude) },
                        severity = hazard.severity
                )
            }

    /** Calculates a route that bends around every active hazard and closure. */
    suspend fun calculateSmartRoute(
            startLat: Double,
            startLng: Double,
            endLat: Double,
            endLng: Double,
            profile: String = "car"
    ): RouteResult {
        destinationLat = endLat
        destinationLng = endLng

        val plan =
                RouteRepository.plan(
                        start = GeoPoint(startLat, startLng),
                        end = GeoPoint(endLat, endLng),
                        profile = profile,
                        hazards = activeHazards()
                )

        if (plan.success) {
            currentRoute = plan.result
            lastRouteSource = plan.source
            lastAvoidedHazards = plan.avoidedHazards
            Log.d(
                    TAG,
                    "Smart route via ${plan.source} avoiding ${plan.avoidedHazards.size} hazard(s)"
            )
        } else {
            Log.w(TAG, "Smart route failed: ${plan.result.errorMessage}")
        }

        return plan.result
    }

    suspend fun reroute(currentLat: Double, currentLng: Double): RouteResult =
            calculateSmartRoute(
                    startLat = currentLat,
                    startLng = currentLng,
                    endLat = destinationLat,
                    endLng = destinationLng
            )

    /** Nearest active hazard within [bufferMeters] of a position, for warnings. */
    fun checkFloodProximity(lat: Double, lng: Double, bufferMeters: Double = 500.0): FloodZone? {
        val hazard = HazardRegistry.dangerAt(GeoPoint(lat, lng), bufferMeters) ?: return null
        return FloodZone(
                sectorName = hazard.title,
                centerLat = hazard.center.latitude,
                centerLng = hazard.center.longitude,
                boundingBox = emptyList(),
                severity = hazard.severity
        )
    }

    fun checkClosureProximity(lat: Double, lng: Double): RoadClosure? {
        val point = GeoPoint(lat, lng)
        return roadClosures.find { closure ->
            closure.isActive &&
                    GeoGeometry.haversineMeters(point, GeoPoint(closure.lat, closure.lng)) <=
                            closure.radius
        }
    }

    fun getFormattedInstruction(instruction: String, distanceMeters: Double): String {
        val distance =
                when {
                    distanceMeters < 100 -> "${distanceMeters.toInt()} m"
                    distanceMeters < 1_000 -> "${(distanceMeters / 10).toInt() * 10} m"
                    else -> "%.1f km".format(distanceMeters / 1_000)
                }
        return "In $distance, $instruction"
    }

    fun clearNavigation() {
        currentRoute = null
        destinationLat = 0.0
        destinationLng = 0.0
        lastRouteSource = null
        lastAvoidedHazards = emptyList()
    }

    private fun activeHazards(): List<HazardZone> {
        val closures =
                roadClosures.filter { it.isActive }.map { closure ->
                    HazardZone(
                            id = "closure:${closure.id}",
                            title = closure.name,
                            type = "closure",
                            severity = "WARNING",
                            center = GeoPoint(closure.lat, closure.lng),
                            radiusMeters = closure.radius,
                            source = "closure"
                    )
                }
        return HazardRegistry.active() + closures
    }
}
