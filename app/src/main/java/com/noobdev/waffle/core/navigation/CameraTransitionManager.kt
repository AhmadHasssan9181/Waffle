package com.noobdev.waffle.core.navigation

import android.util.Log
import kotlin.math.*
import kotlinx.coroutines.*
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Manages Google Maps-style camera transitions and navigation choreography.
 *
 * Features:
 * - "Swoop" transition when starting navigation (tilt, rotate, zoom)
 * - Speed-dependent zoom (zoom out at high speed, in at low speed)
 * - Bottom-offset centering (position marker in lower third)
 * - Bearing calculation for heading-up mode
 */
object CameraTransitionManager {

    private const val TAG = "CameraTransition"

    // Navigation camera settings
    const val NAV_TILT_DEGREES = 55.0 // Pitch toward horizon
    const val NAV_ZOOM_STREET = 17.0 // Street-level zoom
    const val NAV_ZOOM_HIGHWAY = 14.0 // Highway zoom (see further)
    const val NAV_ZOOM_MEDIUM = 16.0 // Medium speed zoom

    // Speed thresholds (m/s)
    const val SPEED_HIGH = 20.0f // ~72 km/h - highway
    const val SPEED_MEDIUM = 10.0f // ~36 km/h - city driving

    // Transition durations (ms)
    const val SWOOP_DURATION = 1500
    const val ZOOM_ADJUST_DURATION = 800
    const val RECENTER_DURATION = 500

    /**
     * Execute the "swoop" transition when navigation starts. This is the cinematic animation that
     * transitions from planner to pilot mode:
     * - Tilt camera toward horizon (~55°)
     * - Rotate to heading-up (bearing = route direction)
     * - Zoom to street level
     * - Position marker in bottom third of screen
     */
    fun executeNavigationStartTransition(
            map: MapLibreMap,
            currentPosition: LatLng,
            nextWaypoint: LatLng?,
            onComplete: () -> Unit = {}
    ) {
        val bearing =
                if (nextWaypoint != null) {
                    calculateBearing(currentPosition, nextWaypoint)
                } else {
                    0.0 // Default north if no waypoint
                }

        val cameraPosition =
                CameraPosition.Builder()
                        .target(currentPosition)
                        .zoom(NAV_ZOOM_STREET)
                        .tilt(NAV_TILT_DEGREES)
                        .bearing(bearing)
                        .build()

        // Set camera padding to push the position to bottom third
        // [left, top, right, bottom] - we add padding to TOP to push marker down
        map.setPadding(0, 400, 0, 0)

        map.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                SWOOP_DURATION,
                object : MapLibreMap.CancelableCallback {
                    override fun onCancel() {}
                    override fun onFinish() {
                        Log.d(TAG, "Swoop transition complete")
                        onComplete()
                    }
                }
        )
    }

    /**
     * Update camera based on current speed. High speed = zoom out, lower tilt (see further ahead)
     * Low speed = zoom in, higher tilt (focus on immediate turns)
     */
    fun updateCameraForSpeed(
            map: MapLibreMap,
            currentPosition: LatLng,
            bearing: Double,
            speedMps: Float
    ) {
        // Continuous speed-based framing: walking stays close, faster travel pulls
        // the camera back and flattens the tilt. This avoids the old hard jumps
        // between street/city/highway zoom buckets.
        val speedKmh = speedMps.toDouble() * 3.6
        val speedFraction = (speedKmh / 70.0).coerceIn(0.0, 1.0)
        val zoom = 17.5 + (13.5 - 17.5) * speedFraction
        val tilt = 58.0 + (40.0 - 58.0) * speedFraction

        val cameraPosition =
                CameraPosition.Builder()
                        .target(currentPosition)
                        .zoom(zoom)
                        .tilt(tilt)
                        .bearing(bearing)
                        .build()

        map.animateCamera(
                CameraUpdateFactory.newCameraPosition(cameraPosition),
                ZOOM_ADJUST_DURATION
        )
    }

    /**
     * Update camera position during navigation (called on each location update). Includes bearing,
     * maintains current zoom/tilt settings.
     */
    fun updateNavigationCamera(
            map: MapLibreMap,
            currentPosition: LatLng,
            bearing: Double,
            animate: Boolean = true
    ) {
        val cameraPosition =
                CameraPosition.Builder().target(currentPosition).bearing(bearing).build()

        if (animate) {
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 1000)
        } else {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }

    /**
     * Frame the camera for Android's compact system PiP window: clear the full-screen 400px top
     * padding (which otherwise pushes the 3D vehicle behind the bottom PiP bar), apply a small
     * bottom padding for the PiP HUD bar, and use a flat course-up view shifted slightly ahead.
     */
    fun updatePipCamera(
            map: MapLibreMap,
            currentPosition: LatLng,
            bearing: Double,
            bottomPaddingPx: Int = 140,
            animate: Boolean = true
    ) {
        map.setPadding(0, 0, 0, bottomPaddingPx)
        val lookAhead =
                GeoGeometry.destinationMeters(
                        GeoPoint(currentPosition.latitude, currentPosition.longitude),
                        bearing,
                        55.0
                )
        val cameraPosition =
                CameraPosition.Builder()
                        .target(LatLng(lookAhead.latitude, lookAhead.longitude))
                        .zoom(15.6)
                        .tilt(0.0)
                        .bearing(bearing)
                        .build()
        if (animate) {
            map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), 450)
        } else {
            map.moveCamera(CameraUpdateFactory.newCameraPosition(cameraPosition))
        }
    }

    /**
     * Re-center camera on position with smooth animation. Called when user taps "Re-center" button
     * or after inactivity timeout.
     */
    fun recenterOnPosition(map: MapLibreMap, position: LatLng, bearing: Double) {
        val currentZoom = map.cameraPosition.zoom
        val currentTilt = map.cameraPosition.tilt

        val cameraPosition =
                CameraPosition.Builder()
                        .target(position)
                        .zoom(currentZoom)
                        .tilt(currentTilt)
                        .bearing(bearing)
                        .build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), RECENTER_DURATION)
    }

    /** Reset camera to normal map mode (no tilt, north-up). */
    fun exitNavigationMode(map: MapLibreMap, position: LatLng) {
        map.setPadding(0, 0, 0, 0) // Reset padding

        val cameraPosition =
                CameraPosition.Builder().target(position).zoom(14.0).tilt(0.0).bearing(0.0).build()

        map.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition), SWOOP_DURATION)
    }

    /**
     * Calculate bearing (direction) from one point to another. Returns degrees from north (0-360).
     */
    fun calculateBearing(from: LatLng, to: LatLng): Double {
        val lat1 = Math.toRadians(from.latitude)
        val lat2 = Math.toRadians(to.latitude)
        val dLng = Math.toRadians(to.longitude - from.longitude)

        val x = sin(dLng) * cos(lat2)
        val y = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLng)

        val bearing = Math.toDegrees(atan2(x, y))
        return (bearing + 360) % 360 // Normalize to 0-360
    }

    /**
     * Snap raw GPS coordinates to the nearest point on the route. This creates the "road snapping"
     * effect where the blue dot stays on the road.
     *
     * @param rawLat Raw GPS latitude
     * @param rawLng Raw GPS longitude
     * @param routeCoordinates List of route coordinate pairs (lat, lng)
     * @param maxSnapDistance Maximum distance (meters) to snap - if further, return raw
     * @return Snapped coordinates as Pair(lat, lng)
     */
    data class SnapResult(
            val snappedLat: Double,
            val snappedLng: Double,
            val matchedIndex: Int, // Index of the coordinate at start of closest segment
            /** Raw GPS distance to the line, even when it is too far away to snap. */
            val distanceMeters: Double = 0.0
    )

    /**
     * Snap raw GPS coordinates to the nearest point on the route. Returns closest point and the
     * index of the route segment.
     *
     * @param rawLat Raw GPS latitude
     * @param rawLng Raw GPS longitude
     * @param routeCoordinates List of route coordinate pairs (lat, lng)
     * @param maxSnapDistance Maximum distance (meters) to snap - if further, return raw
     * @return SnapResult with coordinates and index (-1 if not snapped)
     */
    fun snapToRoute(
            rawLat: Double,
            rawLng: Double,
            routeCoordinates: List<Pair<Double, Double>>,
            maxSnapDistance: Double = 50.0,
            /**
             * Last segment the navigation had reached. Searching again from the whole route lets a
             * loop or a nearby parallel road snap the puck to the far end of the trip, which used
             * to trigger an instant "Arrived" announcement. Search forward from there instead,
             * with a tiny lookback for GPS drift.
             */
            fromIndex: Int = 0
    ): SnapResult {
        if (routeCoordinates.size < 2) return SnapResult(rawLat, rawLng, -1, 0.0)

        var closestPoint = Pair(rawLat, rawLng)
        var minDistance = Double.MAX_VALUE
        var matchedIndex = -1
        val startIndex = (fromIndex - 8).coerceIn(0, routeCoordinates.size - 2)

        // Find closest point on route segments
        for (i in startIndex until routeCoordinates.size - 1) {
            val p1 = routeCoordinates[i]
            val p2 = routeCoordinates[i + 1]

            val snapped = snapToSegment(rawLat, rawLng, p1, p2)
            val distance = haversineDistance(rawLat, rawLng, snapped.first, snapped.second)

            if (distance < minDistance) {
                minDistance = distance
                closestPoint = snapped
                matchedIndex = i
            }
        }

        // Only snap if within threshold
        return if (minDistance <= maxSnapDistance) {
            SnapResult(closestPoint.first, closestPoint.second, matchedIndex, minDistance)
        } else {
            SnapResult(rawLat, rawLng, -1, minDistance)
        }
    }

    /** Snap a point to the nearest point on a line segment. */
    private fun snapToSegment(
            lat: Double,
            lng: Double,
            p1: Pair<Double, Double>,
            p2: Pair<Double, Double>
    ): Pair<Double, Double> {
        val dx = p2.second - p1.second
        val dy = p2.first - p1.first

        if (dx == 0.0 && dy == 0.0) return p1

        val t =
                maxOf(
                        0.0,
                        minOf(
                                1.0,
                                ((lng - p1.second) * dx + (lat - p1.first) * dy) /
                                        (dx * dx + dy * dy)
                        )
                )

        return Pair(p1.first + t * dy, p1.second + t * dx)
    }

    /** Calculate distance between two points in meters using Haversine formula. */
    fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)

        val a =
                sin(dLat / 2).pow(2) +
                        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)

        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return R * c
    }
}
