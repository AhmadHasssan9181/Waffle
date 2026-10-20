package com.noobdev.waffle.core.routing

import com.noobdev.waffle.data.remote.RouteResult
import com.noobdev.waffle.data.remote.getRouteVia
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
        val offlineError: String? = null
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
            preferOffline: Boolean = true
    ): RoutePlan {
        val detour = HazardDetourPlanner.plan(start, end, hazards)
        var offlineError: String? = null

        if (preferOffline) {
            OfflineRoutingManager.getEngineOrNull()?.let { engine ->
                val offlineResult =
                        runCatching {
                                    GraphHopperRouteMapper.toRouteResult(
                                            engine.calculateRoute(
                                                    start = start,
                                                    end = end,
                                                    profile = profile,
                                                    waypoints = detour.waypoints
                                            )
                                    )
                                }
                                .getOrElse { throwable ->
                                    offlineError =
                                            throwable.message ?: throwable.javaClass.simpleName
                                    null
                                }

                if (offlineResult?.success == true) {
                    return RoutePlan(
                            result = offlineResult,
                            source = RouteSource.OFFLINE,
                            waypoints = detour.waypoints,
                            avoidedHazards = detour.avoidedHazards
                    )
                }
                offlineError = offlineResult?.errorMessage ?: offlineError
            }
        }

        val online =
                getRouteVia(
                        startLat = start.latitude,
                        startLng = start.longitude,
                        endLat = end.latitude,
                        endLng = end.longitude,
                        waypoints = detour.waypoints,
                        profile = orsProfile(profile),
                        avoidHazards = detour.avoidedHazards
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
                offlineError = offlineError
        )
    }

    fun orsProfile(profile: String): String =
            when (profile.lowercase()) {
                "foot", "foot-walking", "walking" -> "foot-walking"
                "bike", "cycling-regular", "cycling", "bicycle" -> "cycling-regular"
                else -> "driving-car"
            }
}
