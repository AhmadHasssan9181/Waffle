package com.noobdev.waffle.core.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.noobdev.waffle.core.routing.OfflineRoutingManager
import com.noobdev.waffle.core.routing.RouteRepository
import com.noobdev.waffle.data.remote.GeoSearchResult
import com.noobdev.waffle.data.remote.RouteResult
import com.noobdev.waffle.data.remote.formatDistance
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.geometry.LatLng

/**
 * Owns the live navigation loop. It lives inside [NavigationService], not the map composable, so
 * switching tabs, backgrounding the app, or entering picture-in-picture never stops the trip.
 */
class NavigationController(
        context: Context,
        initial: NavigationSnapshot
) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val fused: FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(appContext)
    private val offRouteMonitor = OffRouteMonitor()

    private var route: RouteResult? = initial.route
    private var destination: GeoSearchResult? = initial.destination
    private var profile: String = initial.profile
    private var stops: List<GeoSearchResult> = initial.stops
    private val hazards = HazardRegistry.active()

    private var lat = initial.lat
    private var lng = initial.lng
    private var bearing = initial.bearing
    private var speedMps = initial.speedMps
    private var lastMovingPaceMps = 0.0
    private var instructionIndex = initial.instructionIndex
    private var remainingDistance = initial.remainingDistance
    private var remainingDuration = initial.remainingDuration
    private var distanceToNextTurn = initial.distanceToNextTurn
    private var matchedIndex = initial.matchedIndex
    private var speedLimitKmh = initial.speedLimitKmh
    private var wrongWay = false
    private var arrived = false
    private var isRerouting = false
    private var notice: String? = null
    private var noticeJob: Job? = null
    private var lastSpeedLimitCheckAt = 0L
    private var lastAutoReroutedHazardId: String? = null
    private var previousLat = initial.lat
    private var previousLng = initial.lng
    private var arrivalAnnounced = false
    @Volatile private var stopped = false

    private val locationCallback =
            object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    result.lastLocation?.let(::onFix)
                }
            }

    fun start() {
        if (route == null || destination == null) return
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) !=
                        PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        val request =
                LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1_000L)
                        .setMinUpdateDistanceMeters(1f)
                        .build()
        runCatching {
            fused.requestLocationUpdates(request, locationCallback, Looper.getMainLooper())
            fused.lastLocation.addOnSuccessListener { location ->
                if (location != null) onFix(location)
            }
        }
    }

    fun stop() {
        stopped = true
        runCatching { fused.removeLocationUpdates(locationCallback) }
        noticeJob?.cancel()
        scope.cancel()
    }

    private fun onFix(location: Location) {
        if (stopped) return
        val activeRoute = route ?: return
        val coords = activeRoute.coordinates
        if (coords.size < 2) return

        val snap =
                CameraTransitionManager.snapToRoute(
                        location.latitude,
                        location.longitude,
                        coords
                )

        val moved =
                GeoGeometry.haversineMeters(
                        GeoPoint(previousLat, previousLng),
                        GeoPoint(location.latitude, location.longitude)
                )
        val travelBearing =
                when {
                        location.hasBearing() && location.speed > 1f ->
                                location.bearing.toDouble()
                        moved > 5.0 ->
                                CameraTransitionManager.calculateBearing(
                                        LatLng(previousLat, previousLng),
                                        LatLng(location.latitude, location.longitude)
                                )
                        else -> bearing
                }
        val routeBearing =
                if (snap.matchedIndex in 0 until coords.size - 1) {
                    val a = coords[snap.matchedIndex]
                    val b = coords[snap.matchedIndex + 1]
                    CameraTransitionManager.calculateBearing(
                            LatLng(a.first, a.second),
                            LatLng(b.first, b.second)
                    )
                } else null

        val decision =
                offRouteMonitor.onFix(
                        distanceMeters = snap.distanceMeters,
                        accuracyMeters = location.accuracy,
                        routeBearingDegrees = routeBearing,
                        travelBearingDegrees = travelBearing,
                        speedMps = location.speed
                )
        wrongWay = decision.isWrongWay

        if (decision.shouldReroute && !isRerouting) {
            reroute(
                    rawLat = location.latitude,
                    rawLng = location.longitude,
                    wrongWay = decision.isWrongWay,
                    viaStops = remainingStops(activeRoute, snap.matchedIndex)
            )
        }

        lat = snap.snappedLat
        lng = snap.snappedLng
        // Point the puck and camera along the road rather than at noisy GPS heading.
        bearing = routeBearing ?: travelBearing
        val rawSpeed = if (location.hasSpeed()) location.speed.coerceAtLeast(0f) else 0f
        speedMps =
                if (speedMps > 0f && rawSpeed > 0f) speedMps * 0.55f + rawSpeed * 0.45f
                else rawSpeed
        matchedIndex = snap.matchedIndex
        previousLat = location.latitude
        previousLng = location.longitude

        val nextStepIndex =
                activeRoute.steps.indexOfFirst { it.wayPointIndex > snap.matchedIndex }
        instructionIndex =
                if (nextStepIndex == -1) activeRoute.steps.lastIndex
                else nextStepIndex
        distanceToNextTurn =
                if (nextStepIndex != -1) {
                    GeoGeometry.distanceToIndexMeters(
                            coords,
                            snap.matchedIndex,
                            activeRoute.steps[nextStepIndex].wayPointIndex,
                            snap.snappedLat,
                            snap.snappedLng
                    )
                } else 0.0

        remainingDistance =
                GeoGeometry.remainingRouteMeters(
                        coords,
                        snap.matchedIndex,
                        snap.snappedLat,
                        snap.snappedLng
                )
        val routeAverage =
                if (activeRoute.durationSeconds > 0)
                        activeRoute.distanceMeters / activeRoute.durationSeconds
                else if (profile == "foot-walking") 1.4 else 11.0
        // Blend the current pace with the route estimate. Pure route-average ETA is
        // wildly optimistic when traffic is slower than the router assumed; pure
        // instantaneous speed makes a red light send the ETA to the moon.
        val movingThreshold =
                when (profile) {
                        "foot-walking" -> 0.5
                        "cycling-regular" -> 1.5
                        else -> 3.0
                }
        if (speedMps > movingThreshold) lastMovingPaceMps = speedMps.toDouble()
        // If TomTom has a flow sample for this road, use it as the expected pace.
        // The router's free-flow average is too optimistic in congestion.
        val trafficPace = TrafficState.paceMps
        val expectedPace =
                if (trafficPace != null && trafficPace > 0.8)
                        (routeAverage * 0.35 + trafficPace * 0.65)
                                .coerceAtMost(routeAverage * 1.3)
                else routeAverage
        val pace =
                when {
                        speedMps > movingThreshold ->
                                speedMps.toDouble() * 0.9 + expectedPace * 0.1
                        speedMps > movingThreshold * 0.3 ->
                                speedMps.toDouble() * 0.5 +
                                        (if (lastMovingPaceMps > movingThreshold)
                                                        lastMovingPaceMps
                                                else expectedPace) *
                                                0.5
                        else ->
                                if (lastMovingPaceMps > movingThreshold) lastMovingPaceMps
                                else expectedPace
                }
                        .coerceAtLeast(0.8)
        val newDuration = remainingDistance / pace
        remainingDuration =
                if (remainingDuration > 0) remainingDuration * 0.25 + newDuration * 0.75
                else newDuration
        arrived = remainingDistance < 25.0
        if (arrived && !arrivalAnnounced) {
            arrivalAnnounced = true
            showNotice("Arrived at destination")
        } else if (!arrived) {
            arrivalAnnounced = false
        }

        val now = SystemClock.elapsedRealtime()
        if (now - lastSpeedLimitCheckAt > 2_000L) {
            speedLimitKmh =
                    OfflineRoutingManager.speedLimitKmhAt(location.latitude, location.longitude)
            lastSpeedLimitCheckAt = now
        }

        val passed = passedStops(activeRoute, snap.matchedIndex)
        if (passed.isNotEmpty()) {
            stops = stops - passed.toSet()
            showNotice("Stop reached · continuing")
            NavigationStore.save(appContext, NavigationSession.snapshot())
        }

        maybeRerouteForHazard(activeRoute, snap, location.latitude, location.longitude)
        publish()
    }

    private fun reroute(
            rawLat: Double,
            rawLng: Double,
            wrongWay: Boolean,
            viaStops: List<GeoSearchResult>
    ) {
        val target = destination ?: return
        if (isRerouting) return
        isRerouting = true
        showNotice(if (wrongWay) "Wrong way · turn around…" else "Off route · finding a new way…")

        scope.launch {
            val plan =
                    withContext(Dispatchers.Default) {
                            runCatching {
                                            RouteRepository.planWithStops(
                                                    start = GeoPoint(rawLat, rawLng),
                                                    end = GeoPoint(target.lat, target.lng),
                                                    stops =
                                                            viaStops.map {
                                                                    GeoPoint(it.lat, it.lng)
                                                            },
                                                    profile = engineProfile(profile),
                                                    hazards = hazards
                                            )
                                    }
                                    .getOrNull()
                    }
            if (plan != null && plan.success) {
                route = plan.result
                stops = viaStops
                instructionIndex = 0
                matchedIndex = 0
                distanceToNextTurn = 0.0
                arrived = false
                arrivalAnnounced = false
                remainingDistance = plan.result.distanceMeters
                remainingDuration = plan.result.durationSeconds
                showNotice(
                        if (wrongWay) "Wrong way · turn around"
                        else "New route · ${formatDistance(plan.result.distanceMeters)}"
                )
                NavigationStore.save(appContext, NavigationSession.snapshot())
            } else {
                showNotice(
                        if (wrongWay) "Wrong way · no new route"
                        else "Off route · no new route found"
                )
            }
            offRouteMonitor.markAttempt()
            isRerouting = false
            publish()
        }
    }

    private fun maybeRerouteForHazard(
            activeRoute: RouteResult,
            snap: CameraTransitionManager.SnapResult,
            rawLat: Double,
            rawLng: Double
    ) {
        if (isRerouting || hazards.isEmpty()) return
        val points = activeRoute.coordinates.map { GeoPoint(it.first, it.second) }
        val hit =
                GeoGeometry.findHazardHits(points, hazards, 60.0)
                        .firstOrNull { it.hazard.id != lastAutoReroutedHazardId }
                        ?: return
        val distance = GeoGeometry.haversineMeters(GeoPoint(rawLat, rawLng), hit.hazard.center)
        if (distance > hit.hazard.radiusMeters + 5_000.0) return
        lastAutoReroutedHazardId = hit.hazard.id
        reroute(
                rawLat,
                rawLng,
                wrongWay = false,
                viaStops = remainingStops(activeRoute, snap.matchedIndex)
        )
    }

    private fun remainingStops(
            route: RouteResult?,
            currentIndex: Int
    ): List<GeoSearchResult> {
        if (stops.isEmpty()) return emptyList()
        if (route == null || currentIndex < 0) return stops
        return stops.filter { stop ->
            val nearest = nearestIndex(route, stop) ?: return@filter true
            nearest > currentIndex + 1
        }
    }

    private fun passedStops(
            route: RouteResult?,
            currentIndex: Int
    ): List<GeoSearchResult> {
        if (route == null || currentIndex < 1 || stops.isEmpty()) return emptyList()
        return stops.filter { stop ->
            val nearest = nearestIndex(route, stop) ?: return@filter false
            nearest < currentIndex - 1
        }
    }

    private fun nearestIndex(route: RouteResult, stop: GeoSearchResult): Int? =
            route.coordinates.indices.minByOrNull { index ->
                val point = route.coordinates[index]
                GeoGeometry.haversineMeters(
                        GeoPoint(stop.lat, stop.lng),
                        GeoPoint(point.first, point.second)
                )
            }

    private fun showNotice(text: String) {
        notice = text
        noticeJob?.cancel()
        noticeJob =
                scope.launch {
                    delay(6_000)
                    if (notice == text) {
                        notice = null
                        publish()
                    }
                }
        publish()
    }

    private fun publish() {
        if (stopped) return
        val activeRoute = route ?: return
        NavigationSession.update { current ->
            current.copy(
                    active = true,
                    route = activeRoute,
                    destination = destination,
                    profile = profile,
                    stops = stops,
                    lat = lat,
                    lng = lng,
                    bearing = bearing,
                    speedMps = speedMps,
                    instructionIndex = instructionIndex,
                    remainingDistance = remainingDistance,
                    remainingDuration = remainingDuration,
                    distanceToNextTurn = distanceToNextTurn,
                    notice = notice,
                    isRerouting = isRerouting,
                    wrongWay = wrongWay,
                    speedLimitKmh = speedLimitKmh,
                    matchedIndex = matchedIndex,
                    arrived = arrived
            )
        }
    }

    private fun engineProfile(profile: String): String =
            when (profile) {
                "foot-walking" -> "foot"
                "cycling-regular" -> "bike"
                else -> "car"
            }
}
