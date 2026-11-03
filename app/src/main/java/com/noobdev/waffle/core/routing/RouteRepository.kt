package com.noobdev.waffle.core.routing

import com.noobdev.waffle.data.remote.NavigationStep
import com.noobdev.waffle.data.remote.RouteResult
import com.noobdev.waffle.data.remote.getRoutesVia
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.core.navigation.HazardDetourPlanner
import com.noobdev.waffle.core.navigation.HazardZone

enum class RouteSource {
    OFFLINE,
    ONLINE,
    NONE
}

data class RoutePlan(
        val result: RouteResult,
        val source: RouteSource,
        val waypoints: List<GeoPoint> = emptyList(),
        val avoidedHazards: List<HazardZone> = emptyList(),
        val offlineError: String? = null,
        val alternatives: List<RouteResult> = emptyList()
) {
    val success: Boolean
        get() = result.success && source != RouteSource.NONE
}

/**
 * One routing entry point for the UI.
 *
 * Order of preference:
 *  1. GraphHopper on device (works in a blackout, which is the whole point of Waffle).
 *  2. ORS online, with the same detour way-points and avoid polygons.
 *
 * A hazard detour is calculated once and passed to both engines so the route shape is consistent
 * regardless of which one answers.
 */
object RouteRepository {

    suspend fun plan(
            start: GeoPoint,
            end: GeoPoint,
            profile: String = "car",
            hazards: List<HazardZone> = emptyList(),
            preferOffline: Boolean = true,
            options: RouteOptions = RouteOptions()
    ): RoutePlan {
        val detour = HazardDetourPlanner.plan(start, end, hazards)
        var offlineError: String? = null

        // GraphHopper 1.0 has no avoid-toll/highway/ferry weighting. If the user asks for one,
        // route online where ORS can honour it rather than silently ignoring the choice.
        val useOffline = preferOffline && !options.needsOnline
        if (useOffline) {
            OfflineRoutingManager.getEngineOrNull()?.let { engine ->
                val results =
                        runCatching {
                                    GraphHopperRouteMapper.toRouteResults(
                                            engine.calculateRoutes(
                                                    start = start,
                                                    end = end,
                                                    profile = profile,
                                                    waypoints = detour.waypoints,
                                                    maxPaths =
                                                            if (options.alternatives) 3 else 1
                                            )
                                    )
                                }
                                .getOrElse { throwable ->
                                    offlineError =
                                            throwable.message ?: throwable.javaClass.simpleName
                                    emptyList()
                                }

                val best = results.firstOrNull { it.success }
                if (best != null) {
                    return RoutePlan(
                            result = best,
                            source = RouteSource.OFFLINE,
                            waypoints = detour.waypoints,
                            avoidedHazards = detour.avoidedHazards,
                            alternatives = results.drop(1).filter { it.success }
                    )
                }
                offlineError = results.firstOrNull()?.errorMessage ?: offlineError
            }
        }

        val onlineResults =
                getRoutesVia(
                        startLat = start.latitude,
                        startLng = start.longitude,
                        endLat = end.latitude,
                        endLng = end.longitude,
                        waypoints = detour.waypoints,
                        profile = orsProfile(profile),
                        avoidHazards = detour.avoidedHazards,
                        avoidFeatures = options.avoidFeatures(),
                        alternatives = if (options.alternatives) 3 else 0
                )
        val online =
                onlineResults.firstOrNull()
                        ?: RouteResult(
                                distanceMeters = 0.0,
                                durationSeconds = 0.0,
                                coordinates = emptyList(),
                                instructions = emptyList(),
                                success = false,
                                errorMessage =
                                        if (options.needsOnline)
                                                "Avoid options need an internet connection"
                                        else "Online routing failed"
                        )

        val message =
                if (!online.success && !offlineError.isNullOrBlank()) {
                    "${online.errorMessage ?: "Online routing failed"} (offline: $offlineError)"
                } else {
                    online.errorMessage
                }

        return RoutePlan(
                result = if (message == online.errorMessage) online else online.copy(errorMessage = message),
                source = if (online.success) RouteSource.ONLINE else RouteSource.NONE,
                waypoints = detour.waypoints,
                avoidedHazards = detour.avoidedHazards,
                offlineError = offlineError,
                alternatives = onlineResults.drop(1).filter { it.success }
        )
    }

    fun orsProfile(profile: String): String =
            when (profile.lowercase()) {
                "foot", "foot-walking", "walking" -> "foot-walking"
                "bike", "cycling-regular", "cycling", "bicycle" -> "cycling-regular"
                else -> "driving-car"
            }

    /**
     * Plans start -> stops -> end by planning each leg separately and stitching the results.
     *
     * Each leg gets the same hazard avoidance, so a fuel stop does not quietly route through a
     * flood zone. The legs share their boundary coordinate, which is removed from the next leg so
     * the drawn line has no duplicate point.
     */
    suspend fun planWithStops(
            start: GeoPoint,
            end: GeoPoint,
            stops: List<GeoPoint>,
            profile: String = "car",
            hazards: List<HazardZone> = emptyList(),
            preferOffline: Boolean = true,
            options: RouteOptions = RouteOptions()
    ): RoutePlan {
        if (stops.isEmpty()) return plan(start, end, profile, hazards, preferOffline, options)

        val legs = ArrayList<RoutePlan>(stops.size + 1)
        var from = start
        for (to in stops + end) {
            val leg = plan(from, to, profile, hazards, preferOffline, options)
            if (!leg.success) return leg
            legs += leg
            from = to
        }
        return mergeRoutePlans(legs)
    }

    internal fun mergeRoutePlans(legs: List<RoutePlan>): RoutePlan {
        require(legs.isNotEmpty()) { "Cannot merge an empty list of legs" }

        val coordinates = mutableListOf<Pair<Double, Double>>()
        val instructions = mutableListOf<String>()
        val steps = mutableListOf<NavigationStep>()
        val avoided = mutableListOf<HazardZone>()
        var distance = 0.0
        var duration = 0.0
        var source = RouteSource.OFFLINE

        for (leg in legs) {
            val result = leg.result
            val sharedFirst =
                    coordinates.isNotEmpty() &&
                            result.coordinates.isNotEmpty() &&
                            coordinates.last() == result.coordinates.first()
            val offset = if (sharedFirst) coordinates.size - 1 else coordinates.size
            coordinates += if (sharedFirst) result.coordinates.drop(1) else result.coordinates
            instructions += result.instructions
            steps += result.steps.map { it.copy(wayPointIndex = it.wayPointIndex + offset) }
            distance += result.distanceMeters
            duration += result.durationSeconds
            if (leg.source == RouteSource.ONLINE) source = RouteSource.ONLINE
            avoided += leg.avoidedHazards
        }

        return RoutePlan(
                result =
                        RouteResult(
                                distanceMeters = distance,
                                durationSeconds = duration,
                                coordinates = coordinates,
                                instructions = instructions,
                                steps = steps,
                                success = true
                        ),
                source = source,
                waypoints = legs.flatMap { it.waypoints },
                avoidedHazards = avoided.distinctBy { it.id }
        )
    }
}
