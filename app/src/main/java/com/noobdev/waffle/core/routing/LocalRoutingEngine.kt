package com.noobdev.waffle.core.routing

import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.GraphHopper
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import com.graphhopper.routing.ev.MaxSpeed
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.util.shapes.GHPoint
import com.noobdev.waffle.core.navigation.GeoPoint
import kotlin.math.roundToInt

/**
 * Offline routing engine backed by GraphHopper 1.0.
 *
 * GraphHopper dropped offline support after 1.0, so this deliberately stays on 1.0 and the routing
 * graph in assets must be generated with the same version (see tools/graph-builder). `forMobile()`
 * switches GraphHopper into its in-memory, read-only mode which is what an Android app wants.
 */
class LocalRoutingEngine(graphFolderAbsolutePath: String) {

    private val hopper: GraphHopper =
            GraphHopper().apply {
                forMobile()
                setGraphHopperLocation(graphFolderAbsolutePath)
                setProfiles(
                        Profile("car").setVehicle("car").setWeighting("fastest"),
                        Profile("foot").setVehicle("foot").setWeighting("shortest"),
                        Profile("bike").setVehicle("bike").setWeighting("fastest")
                )
                // GraphHopper's mobile mode does not allow disabling CH per request, so every
                // profile needs a CH preparation. The bundled graph is built the same way.
                getCHPreparationHandler()
                        .setCHProfiles(
                                CHProfile("car"),
                                CHProfile("foot"),
                                CHProfile("bike")
                        )
                setAllowWrites(false)
                importOrLoad()
            }

    val isReady: Boolean
        get() = hopper.graphHopperStorage != null

    fun calculateRoute(
            start: GeoPoint,
            end: GeoPoint,
            profile: String = "car",
            waypoints: List<GeoPoint> = emptyList()
    ): GHResponse {
        val points = ArrayList<GHPoint>(waypoints.size + 2)
        points.add(GHPoint(start.latitude, start.longitude))
        waypoints.forEach { points.add(GHPoint(it.latitude, it.longitude)) }
        points.add(GHPoint(end.latitude, end.longitude))

        val normalizedProfile = normalizeProfile(profile)
        val request = GHRequest(points).apply {
            setProfile(normalizedProfile)
            setLocale("en")
        }
        return hopper.route(request)
    }

    /**
     * The OSM `maxspeed` value for the road under [lat]/[lon], in km/h, or null when the way has no
     * signed limit. The graph already encodes this value; no extra data file is needed.
     */
    fun speedLimitKmhAt(lat: Double, lon: Double): Int? {
        val maxSpeed =
                hopper.encodingManager.getDecimalEncodedValue(MaxSpeed.KEY) ?: return null
        val result = hopper.locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES)
        if (!result.isValid) return null
        val edge = result.closestEdge ?: return null
        val limit = edge.get(maxSpeed)
        if (!limit.isFinite() ||
                        limit <= 0.0 ||
                        limit >= MaxSpeed.UNLIMITED_SIGN_SPEED
        ) {
            return null
        }
        return limit.roundToInt()
    }

    private fun normalizeProfile(profile: String): String =
            when (profile.lowercase()) {
                "foot", "foot-walking", "walking" -> "foot"
                "bike", "cycling-regular", "cycling", "bicycle" -> "bike"
                else -> "car"
            }
}
