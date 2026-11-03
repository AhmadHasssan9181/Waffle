package com.noobdev.waffle.core.navigation

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Small, dependency-free geospatial toolkit used by hazard avoidance, navigation and the map
 * overlay. All distances are meters and all bearings are degrees clockwise from north.
 *
 * The projection helpers use a local equirectangular approximation. That is accurate to well under
 * a meter over the few-kilometer distances a route detour cares about, while staying cheap enough
 * to run on every GPS fix.
 */
data class GeoPoint(val latitude: Double, val longitude: Double)

data class HazardZone(
        val id: String,
        val title: String,
        val type: String,
        val severity: String,
        val center: GeoPoint,
        val radiusMeters: Double,
        val source: String = "alert",
        val timestamp: String? = null
)

data class RouteHazardHit(
        val hazard: HazardZone,
        val segmentIndex: Int,
        val closestPoint: GeoPoint,
        val distanceAlongRouteMeters: Double,
        val closestDistanceMeters: Double
)

object GeoGeometry {

    const val EARTH_RADIUS_M = 6_371_008.8

    private const val DEG_TO_RAD = Math.PI / 180.0
    private const val RAD_TO_DEG = 180.0 / Math.PI

    fun haversineMeters(a: GeoPoint, b: GeoPoint): Double {
        val dLat = (b.latitude - a.latitude) * DEG_TO_RAD
        val dLng = (b.longitude - a.longitude) * DEG_TO_RAD
        val lat1 = a.latitude * DEG_TO_RAD
        val lat2 = b.latitude * DEG_TO_RAD
        val h =
                sin(dLat / 2).let { it * it } +
                        cos(lat1) * cos(lat2) * sin(dLng / 2).let { it * it }
        return 2 * EARTH_RADIUS_M * atan2(sqrt(h), sqrt(1 - h))
    }

    fun bearingDegrees(a: GeoPoint, b: GeoPoint): Double {
        val lat1 = a.latitude * DEG_TO_RAD
        val lat2 = b.latitude * DEG_TO_RAD
        val dLng = (b.longitude - a.longitude) * DEG_TO_RAD
        val y = sin(dLng) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)
        return (atan2(y, x) * RAD_TO_DEG + 360.0) % 360.0
    }

    fun destinationMeters(origin: GeoPoint, bearingDegrees: Double, distanceMeters: Double): GeoPoint {
        val angular = distanceMeters / EARTH_RADIUS_M
        val bearing = bearingDegrees * DEG_TO_RAD
        val lat1 = origin.latitude * DEG_TO_RAD
        val lng1 = origin.longitude * DEG_TO_RAD

        val lat2 = kotlin.math.asin(
                sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing)
        )
        val lng2 = lng1 + atan2(
                sin(bearing) * sin(angular) * cos(lat1),
                cos(angular) - sin(lat1) * sin(lat2)
        )
        return GeoPoint(lat2 * RAD_TO_DEG, lng2 * RAD_TO_DEG)
    }

    fun polylineLengthMeters(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var total = 0.0
        for (i in 0 until points.size - 1) {
            total += haversineMeters(points[i], points[i + 1])
        }
        return total
    }

    /**
     * Shortest distance from [point] to the great-circle-ish segment [a]..[b]. The point is
     * projected into a local meter plane around [a], which keeps the clamp math simple and stable.
     */
    fun pointToSegmentMeters(point: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
        val (px, py) = toLocalMeters(a, point)
        val (bx, by) = toLocalMeters(a, b)

        val lengthSquared = bx * bx + by * by
        if (lengthSquared < 1e-6) return haversineMeters(point, a)

        val t = ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
        val closestX = bx * t
        val closestY = by * t
        return hypot(px - closestX, py - closestY)
    }

    /** Point on the segment closest to [point], returned in geographic coordinates. */
    fun closestPointOnSegment(point: GeoPoint, a: GeoPoint, b: GeoPoint): GeoPoint {
        val (px, py) = toLocalMeters(a, point)
        val (bx, by) = toLocalMeters(a, b)
        val lengthSquared = bx * bx + by * by
        if (lengthSquared < 1e-6) return a

        val t = ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
        return fromLocalMeters(a, bx * t, by * t)
    }

    /**
     * Detects which active hazards cut through a route. One hit per hazard is returned (the first
     * segment that enters the radius), ordered along the direction of travel.
     */
    fun findHazardHits(
            route: List<GeoPoint>,
            hazards: List<HazardZone>,
            marginMeters: Double = 0.0
    ): List<RouteHazardHit> {
        if (route.size < 2 || hazards.isEmpty()) return emptyList()

        val hits = mutableListOf<RouteHazardHit>()
        for (hazard in hazards) {
            var accumulated = 0.0
            var best: RouteHazardHit? = null

            for (index in 0 until route.size - 1) {
                val start = route[index]
                val end = route[index + 1]
                val distance = pointToSegmentMeters(hazard.center, start, end)

                if (distance <= hazard.radiusMeters + marginMeters && best == null) {
                    val closest = closestPointOnSegment(hazard.center, start, end)
                    best = RouteHazardHit(
                            hazard = hazard,
                            segmentIndex = index,
                            closestPoint = closest,
                            distanceAlongRouteMeters =
                                    accumulated + haversineMeters(start, closest),
                            closestDistanceMeters = distance
                    )
                }
                accumulated += haversineMeters(start, end)
            }

            best?.let { hits.add(it) }
        }

        return hits.sortedBy { it.distanceAlongRouteMeters }
    }

    /**
     * Two way-points that push a route around [hazard] on the requested side. [sideSign] is +1 or
     * -1; the caller can evaluate both and keep the shorter detour. [clearanceMeters] is added on
     * top of the hazard radius so the new line does not graze the edge.
     */
    fun detourWaypoints(
            start: GeoPoint,
            end: GeoPoint,
            hazard: HazardZone,
            sideSign: Int,
            clearanceMeters: Double = 250.0
    ): List<GeoPoint> {
        val (sx, sy) = toLocalMeters(hazard.center, start)
        val (ex, ey) = toLocalMeters(hazard.center, end)
        val dx = ex - sx
        val dy = ey - sy
        val length = hypot(dx, dy)
        if (length < 1e-3) return emptyList()

        val dirX = dx / length
        val dirY = dy / length
        val perpSign = if (sideSign >= 0) 1.0 else -1.0
        val perpX = -dirY * perpSign
        val perpY = dirX * perpSign
        val offset = hazard.radiusMeters + clearanceMeters
        val along = hazard.radiusMeters * 0.9

        val first = fromLocalMeters(hazard.center, -dirX * along + perpX * offset, -dirY * along + perpY * offset)
        val second = fromLocalMeters(hazard.center, dirX * along + perpX * offset, dirY * along + perpY * offset)
        return listOf(first, second)
    }

    /** Total length of start -> waypoints -> end. Used to choose the cheaper side of a detour. */
    fun detourLengthMeters(start: GeoPoint, end: GeoPoint, waypoints: List<GeoPoint>): Double {
        if (waypoints.isEmpty()) return haversineMeters(start, end)
        var total = 0.0
        var previous = start
        for (point in waypoints) {
            total += haversineMeters(previous, point)
            previous = point
        }
        return total + haversineMeters(previous, end)
    }

    /** Builds a closed polygon approximating a circle, suitable for drawing or avoid_polygons. */
    fun circlePolygon(center: GeoPoint, radiusMeters: Double, segments: Int = 64): List<GeoPoint> {
        val safeSegments = max(8, segments)
        val points = ArrayList<GeoPoint>(safeSegments + 1)
        for (i in 0..safeSegments) {
            val bearing = 360.0 * i / safeSegments
            points.add(destinationMeters(center, bearing, radiusMeters))
        }
        return points
    }

    fun metersBetweenLatLng(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double =
            haversineMeters(GeoPoint(lat1, lng1), GeoPoint(lat2, lng2))

    /** Local east/north offset in meters of [point] relative to [origin]. */
    fun toLocalMeters(origin: GeoPoint, point: GeoPoint): Pair<Double, Double> {
        val latRad = origin.latitude * DEG_TO_RAD
        val east = (point.longitude - origin.longitude) * DEG_TO_RAD * cos(latRad) * EARTH_RADIUS_M
        val north = (point.latitude - origin.latitude) * DEG_TO_RAD * EARTH_RADIUS_M
        return east to north
    }

    fun fromLocalMeters(origin: GeoPoint, east: Double, north: Double): GeoPoint {
        val latRad = origin.latitude * DEG_TO_RAD
        val latitude = origin.latitude + (north / EARTH_RADIUS_M) * RAD_TO_DEG
        val longitude =
                origin.longitude + (east / (EARTH_RADIUS_M * cos(latRad))) * RAD_TO_DEG
        return GeoPoint(latitude, longitude)
    }

    /** True when the route passes within [radiusMeters] of the point. */
    fun routePassesNear(
            route: List<GeoPoint>,
            point: GeoPoint,
            radiusMeters: Double
    ): Boolean {
        if (route.isEmpty()) return false
        if (route.size == 1) return haversineMeters(route[0], point) <= radiusMeters
        for (index in 0 until route.size - 1) {
            if (pointToSegmentMeters(point, route[index], route[index + 1]) <= radiusMeters) {
                return true
            }
        }
        return false
    }

    /** Converts a route into the (lng, lat) pairs MapLibre / ORS expect. */
    fun toLngLatPairs(points: List<GeoPoint>): List<List<Double>> =
            points.map { listOf(it.longitude, it.latitude) }

    /** Distance still to travel along a route from the snapped segment, in meters. */
    fun remainingRouteMeters(
            coordinates: List<Pair<Double, Double>>,
            segmentIndex: Int,
            currentLat: Double,
            currentLng: Double
    ): Double {
        if (coordinates.size < 2) return 0.0
        val start = segmentIndex.coerceIn(0, coordinates.size - 2)
        val next = coordinates[start + 1]
        var total = haversineMeters(GeoPoint(currentLat, currentLng), GeoPoint(next.first, next.second))
        for (i in start + 1 until coordinates.size - 1) {
            val a = coordinates[i]
            val b = coordinates[i + 1]
            total += haversineMeters(GeoPoint(a.first, a.second), GeoPoint(b.first, b.second))
        }
        return total
    }

    /** Distance from the snapped point to a later route index, used for "In X m". */
    fun distanceToIndexMeters(
            coordinates: List<Pair<Double, Double>>,
            segmentIndex: Int,
            targetIndex: Int,
            currentLat: Double,
            currentLng: Double
    ): Double {
        if (coordinates.size < 2) return 0.0
        val start = segmentIndex.coerceIn(0, coordinates.size - 2)
        val target = targetIndex.coerceIn(start + 1, coordinates.size - 1)
        var total =
                haversineMeters(
                        GeoPoint(currentLat, currentLng),
                        GeoPoint(coordinates[start + 1].first, coordinates[start + 1].second)
                )
        for (i in start + 1 until target) {
            val a = coordinates[i]
            val b = coordinates[i + 1]
            total += haversineMeters(GeoPoint(a.first, a.second), GeoPoint(b.first, b.second))
        }
        return total
    }
}
