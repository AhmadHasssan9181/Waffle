package com.noobdev.waffle.feature.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.noobdev.waffle.data.remote.*
import com.noobdev.waffle.core.navigation.CameraTransitionManager
import com.noobdev.waffle.core.navigation.GeoGeometry
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.feature.flood.Sector
import com.noobdev.waffle.feature.flood.loadSectorData
import com.noobdev.waffle.core.navigation.HazardRegistry
import com.noobdev.waffle.core.navigation.HazardZone
import com.noobdev.waffle.core.navigation.NavigationSession
import com.noobdev.waffle.core.navigation.NavigationSnapshot
import com.noobdev.waffle.core.navigation.OffRouteMonitor
import com.noobdev.waffle.core.navigation.PipState
import com.noobdev.waffle.core.navigation.RouteSimulator
import com.noobdev.waffle.core.navigation.SimulationSpeed
import com.noobdev.waffle.core.navigation.SimulationState
import com.noobdev.waffle.core.navigation.TrafficState
import com.noobdev.waffle.core.network.NetworkState
import com.noobdev.waffle.core.navigation.VehicleLights
import com.noobdev.waffle.core.navigation.isNightTime
import com.noobdev.waffle.core.navigation.turnSignalFor
import com.noobdev.waffle.core.prefs.PreferencesManager
import com.noobdev.waffle.core.prefs.SpeedUnit
import com.noobdev.waffle.core.routing.OfflineRoutingManager
import com.noobdev.waffle.core.routing.RouteOptions
import com.noobdev.waffle.core.routing.RoutePlan
import com.noobdev.waffle.core.routing.RouteRepository
import com.noobdev.waffle.core.routing.RouteSource
import com.noobdev.waffle.design.theme.*
import com.noobdev.waffle.feature.shops.ShopDetailSheet
import com.noobdev.waffle.feature.shops.data.Shop
import com.noobdev.waffle.feature.shops.data.ShopCategory
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource

// ============================================
// GOOGLE MAPS-STYLE MAP SCREEN
// ============================================

enum class MapScreenState {
        IDLE, // Default - search bar visible
        SEARCHING, // Search field active
        ROUTE_PREVIEW, // Showing route before starting navigation
        NAVIGATING // Turn-by-turn navigation active
}

/** Whether the current search result becomes the destination or an intermediate stop. */
enum class SearchPurpose {
        DESTINATION,
        STOP
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(navController: NavController, currentStyleIndex: Int) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val preferencesManager = remember { PreferencesManager(context) }
        val speedUnit by preferencesManager.speedUnit.collectAsState(initial = SpeedUnit.KMH)
        val vehicleIconKey by
                preferencesManager.vehicleIcon.collectAsState(initial = "SEDAN")
        val savedLocation by
                preferencesManager.userLocation.collectAsState(initial = null to null)
        val vehicleIcon = remember(vehicleIconKey) { VehicleIcon.fromKey(vehicleIconKey) }
        val navSession by NavigationSession.state.collectAsState()
        val inPip by PipState.active.collectAsState()

        val shopsViewModel: MapShopsViewModel = viewModel()
        val mapShops by shopsViewModel.shops.collectAsState()
        val selectedShop by shopsViewModel.selected.collectAsState()
        // Search results open a Google-Maps-style place sheet before routing.
        var selectedPlace by remember { mutableStateOf<GeoSearchResult?>(null) }
        var shopFilter by remember { mutableStateOf<ShopCategory?>(null) }
        val visibleShops =
                remember(mapShops, shopFilter) {
                        if (shopFilter == null) emptyList()
                        else mapShops.filter { it.category == shopFilter }
                }

        // Map instance
        var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

        // Direct reference to the route GeoJsonSource for fast in-place updates (vanishing trail)
        var routeSource by remember { mutableStateOf<GeoJsonSource?>(null) }

        // Screen state
        var screenState by MapSessionState.screenState
        var navigationCameraReady by remember { mutableStateOf(false) }

        // Map style - this will be passed up to change the map
        val darkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
        var selectedStyleIndex by remember(currentStyleIndex, darkUi) {
                mutableStateOf(
                        if (currentStyleIndex >= 0) currentStyleIndex
                        else autoStyleIndex(darkUi)
                )
        }

        // Location
        var currentLat by MapSessionState.currentLat
        var currentLng by MapSessionState.currentLng
        var hasLocation by MapSessionState.hasLocation
        // Indoor/offline starts often have no fresh GPS fix. Keep the last persisted location
        // visible so the map still has a location blip and a sensible route origin.
        LaunchedEffect(savedLocation, screenState) {
            val lat = savedLocation.first
            val lng = savedLocation.second
            if (!hasLocation &&
                            screenState == MapScreenState.IDLE &&
                            lat != null &&
                            lng != null
            ) {
                currentLat = lat
                currentLng = lng
                hasLocation = true
            }
        }
        var lastAlertLocationSavedAt by remember { mutableStateOf(0L) }
        var lastAlertLocationLat by remember { mutableStateOf(0.0) }
        var lastAlertLocationLng by remember { mutableStateOf(0.0) }
        var centeredOnFirstFix by remember { mutableStateOf(false) }

        /** Feed the background alert worker without writing DataStore on every GPS tick. */
        fun persistAlertLocation(lat: Double, lng: Double) {
                val now = System.currentTimeMillis()
                val moved =
                        if (lastAlertLocationSavedAt == 0L) Double.MAX_VALUE
                        else
                                GeoGeometry.haversineMeters(
                                        GeoPoint(lastAlertLocationLat, lastAlertLocationLng),
                                        GeoPoint(lat, lng)
                                )
                if (now - lastAlertLocationSavedAt < 60_000L && moved < 500.0) return
                lastAlertLocationSavedAt = now
                lastAlertLocationLat = lat
                lastAlertLocationLng = lng
                MapSessionState.routeScope.launch {
                        runCatching { preferencesManager.setUserLocation(lat, lng) }
                }
        }

        // Search & Routing
        var searchQuery by remember { mutableStateOf("") }
        var selectedDestination by MapSessionState.selectedDestination
        var droppedPin by MapSessionState.droppedPin
        var routeStops by MapSessionState.routeStops
        var searchPurpose by remember { mutableStateOf(SearchPurpose.DESTINATION) }
        var showStopPicker by remember { mutableStateOf(false) }
        var stopPickerCategory by remember { mutableStateOf<ShopCategory?>(null) }
        var routeResult by MapSessionState.routeResult
        var routeAlternatives by MapSessionState.routeAlternatives
        var selectedAlternativeIndex by MapSessionState.selectedAlternativeIndex
        var routeOptions by MapSessionState.routeOptions
        var walkRouteResult by MapSessionState.walkRouteResult
        var bikeRouteResult by MapSessionState.bikeRouteResult
        var selectedProfile by MapSessionState.selectedProfile
        val isMotorcycleMode = selectedProfile == "driving-motorcycle"
        var isLoadingRoute by remember { mutableStateOf(false) }
        var isLoadingWalk by remember { mutableStateOf(false) }
        var offlineRouteDebugMessage by remember { mutableStateOf<String?>(null) }
        var activeRouteSource by remember { mutableStateOf<RouteSource?>(null) }
        var avoidedHazardCount by remember { mutableStateOf(0) }
        var hazardWarning by remember { mutableStateOf<HazardZone?>(null) }
        var lastAutoReroutedHazardId by remember { mutableStateOf<String?>(null) }
        val hazards by HazardRegistry.hazards.collectAsState()

        // Deviation handling. The monitor is deliberately plain Kotlin so its timing rules can be
        // unit-tested without a device.
        val offRouteMonitor = remember { OffRouteMonitor() }
        var isRerouting by remember { mutableStateOf(false) }
        var navigationNotice by remember { mutableStateOf<String?>(null) }
        var wrongWayDetected by remember { mutableStateOf(false) }


        /** The route that belongs to the currently selected travel mode. */
        fun activeRouteFor(profile: String): RouteResult? =
                when (profile) {
                        "foot-walking" -> walkRouteResult ?: routeResult
                        "cycling-regular" -> bikeRouteResult ?: routeResult
                        else -> routeResult
                }

        fun isWalkingProfile(profile: String): Boolean = profile == "foot-walking"
        fun isBikeProfile(profile: String): Boolean = profile == "cycling-regular"
        fun isMotorcycleProfile(profile: String): Boolean = profile == "driving-motorcycle"

        fun offlineResponseToRouteResult(
                response: com.graphhopper.GHResponse
        ): RouteResult? {
                if (response.hasErrors()) return null

                val bestPath = response.best
                val coordinates =
                        bestPath.points.map { point -> Pair(point.lat, point.lon) }

                if (coordinates.isEmpty()) return null

                return RouteResult(
                        distanceMeters = bestPath.distance,
                        durationSeconds = bestPath.time / 1000.0,
                        coordinates = coordinates,
                        instructions = listOf("Offline route"),
                        success = true
                )
        }

        // Navigation - keep last non-null route to prevent crash during exit animation
        var currentInstructionIndex by MapSessionState.currentInstructionIndex
        var navigationInstructions by MapSessionState.navigationInstructions
        var remainingDistance by MapSessionState.remainingDistance
        var remainingDuration by MapSessionState.remainingDuration
        var distanceToNextTurn by MapSessionState.distanceToNextTurn
        var currentBearing by MapSessionState.currentBearing
        var currentSpeedMps by MapSessionState.currentSpeedMps
        var speedLimitKmh by remember { mutableStateOf<Int?>(null) }
        var lastSpeedLimitCheckAt by remember { mutableStateOf(0L) }
        var trafficFlow by remember { mutableStateOf<TrafficFlow?>(null) }

        /** The speed used for the live ETA: TomTom flow, then GPS, then the route average. */
        fun liveSpeedMps(route: RouteResult?): Double {
                val routeAverage =
                        if (route != null && route.durationSeconds > 0.0)
                                route.distanceMeters / route.durationSeconds
                        else if (selectedProfile == "driving-car") 11.0 else 1.4
                val trafficSpeed = trafficFlow?.currentSpeedKmh?.div(3.6)
                val trafficAdjusted =
                        trafficSpeed
                                ?.takeIf { it > 1.0 }
                                ?.let {
                                    (routeAverage * 0.7 + it * 0.3)
                                            .coerceIn(
                                                    routeAverage * 0.5,
                                                    routeAverage * 1.5
                                            )
                                }
                return trafficAdjusted ?: routeAverage.coerceAtLeast(1.0)
        }
        // Holds the last non-null route so NavigationModeUI never crashes during exit animation
        var lastNavigationRoute by remember { mutableStateOf<RouteResult?>(null) }

        var blinkOn by remember { mutableStateOf(true) }
        var braking by remember { mutableStateOf(false) }
        var lastSpeed by remember { mutableStateOf(0f) }
        val night = remember { isNightTime() }
        val currentTurnSignal =
                if (distanceToNextTurn in 1.0..100.0) {
                        turnSignalFor(
                                lastNavigationRoute
                                        ?.steps
                                        ?.getOrNull(currentInstructionIndex)
                                        ?.maneuver
                        )
                } else {
                        com.noobdev.waffle.core.navigation.TurnSignal.NONE
                }
        LaunchedEffect(currentTurnSignal) {
                if (currentTurnSignal == com.noobdev.waffle.core.navigation.TurnSignal.NONE) {
                        blinkOn = true
                        return@LaunchedEffect
                }
                while (true) {
                        blinkOn = !blinkOn
                        delay(450)
                }
        }
        LaunchedEffect(currentSpeedMps) {
                braking =
                        currentSpeedMps < 1.5f ||
                                (lastSpeed > 0f && currentSpeedMps < lastSpeed - 0.6f)
                lastSpeed = currentSpeedMps
        }
        val vehicleLights =
                VehicleLights(
                        headlights = night,
                        braking = braking,
                        turnSignal = currentTurnSignal,
                        blinkOn = blinkOn
                )

        // Camera & User Interaction
        var isUserPanMode by remember { mutableStateOf(false) }
        var lastPanTime by remember { mutableStateOf(0L) }

        // Simulation Mode
        val routeSimulator = MapSessionState.routeSimulator
        var isSimulationMode by MapSessionState.isSimulationMode
        var selectedSimSpeed by MapSessionState.selectedSimSpeed
        var showSimControls by remember { mutableStateOf(false) }
        var showVehiclePicker by remember { mutableStateOf(false) }
        // A simulated deviation pauses the route-follower; the reroute restarts it on the new line.
        var pendingSimulationRestart by MapSessionState.pendingSimulationRestart

        // Collect simulation state
        val simPosition by routeSimulator.currentPosition.collectAsState()
        val simSpeed by routeSimulator.currentSpeed.collectAsState()
        val simBearing by routeSimulator.currentBearing.collectAsState()
        val simGpsActive by routeSimulator.isGpsActive.collectAsState()
        val simProgress by routeSimulator.progress.collectAsState()
        val simArrived by routeSimulator.hasArrived.collectAsState()
        val simSegmentIndex by routeSimulator.currentSegmentIndex.collectAsState()

        LaunchedEffect(isSimulationMode, screenState) {
                SimulationState.setActive(
                        isSimulationMode && screenState == MapScreenState.NAVIGATING
                )
        }
        LaunchedEffect(
                isSimulationMode,
                screenState,
                currentLat,
                currentLng,
                currentBearing,
                currentSpeedMps,
                currentInstructionIndex,
                remainingDistance,
                remainingDuration,
                distanceToNextTurn,
                selectedProfile,
                selectedStyleIndex,
                simSegmentIndex,
                routeStops,
                selectedDestination,
                lastNavigationRoute
        ) {
                if (isSimulationMode && screenState == MapScreenState.NAVIGATING) {
                        val route = activeRouteFor(selectedProfile)
                        if (route != null) {
                                SimulationState.publish(
                                        NavigationSnapshot(
                                                active = true,
                                                route = route,
                                                destination = selectedDestination,
                                                profile = selectedProfile,
                                                styleIndex = selectedStyleIndex,
                                                vehicleIconKey = vehicleIcon.storageKey,
                                                stops = routeStops,
                                                lat = currentLat,
                                                lng = currentLng,
                                                bearing = currentBearing,
                                                speedMps = currentSpeedMps,
                                                instructionIndex = currentInstructionIndex,
                                                remainingDistance = remainingDistance,
                                                remainingDuration = remainingDuration,
                                                distanceToNextTurn = distanceToNextTurn,
                                                matchedIndex = simSegmentIndex
                                        )
                                )
                        }
                } else {
                        SimulationState.clearSnapshot()
                }
        }

        // Bottom sheet (unused but kept for future)
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        /**
         * Plans the current destination with the current intermediate stops. Car, bike and walk are
         * planned separately, but all include the same stops.
         */
        fun planTrip(profile: String) {
                MapSessionState.routePlanGeneration++
                val generation = MapSessionState.routePlanGeneration
                val destination = selectedDestination ?: return
                if (!hasLocation) {
                        isLoadingRoute = false
                        isLoadingWalk = false
                        offlineRouteDebugMessage =
                                "Waiting for a GPS fix before routing. Move near a window and try again."
                        return
                }
                val stops = routeStops
                val walking = isWalkingProfile(profile)
                val biking = isBikeProfile(profile)
                val motorcycle = isMotorcycleProfile(profile)
                val planOptions =
                        if (profile == "driving-car" || motorcycle) routeOptions
                        else RouteOptions()

                MapSessionState.routeScope.launch {
                        isLoadingWalk = walking
                        isLoadingRoute = !walking
                        offlineRouteDebugMessage = null

                        val plan =
                                planRouteOffMain(
                                        startLat = currentLat,
                                        startLng = currentLng,
                                        destinationLat = destination.lat,
                                        destinationLng = destination.lng,
                                        profile =
                                                when {
                                                        walking -> "foot"
                                                        biking -> "bike"
                                                        motorcycle -> "driving-motorcycle"
                                                        else -> "car"
                                                },
                                        hazards = HazardRegistry.active(),
                                        stops = stops.map { GeoPoint(it.lat, it.lng) },
                                        preferOffline = !NetworkState.isOnline(context),
                                        options = planOptions
                                )

                        // A newer destination or mode change has already been requested.
                        if (generation != MapSessionState.routePlanGeneration) return@launch

                        if (plan != null &&
                                        plan.success &&
                                        plan.result.coordinates.size >= 2
                        ) {
                                selectedProfile = profile
                                if (walking) {
                                        walkRouteResult = plan.result
                                        routeAlternatives = emptyList()
                                } else if (biking) {
                                        bikeRouteResult = plan.result
                                        routeAlternatives = emptyList()
                                } else {
                                        routeResult = plan.result
                                        routeAlternatives = listOf(plan.result) + plan.alternatives
                                }
                                selectedAlternativeIndex = 0
                                if (!walking) walkRouteResult = null
                                if (!biking) bikeRouteResult = null
                                activeRouteSource = plan.source
                                avoidedHazardCount = plan.avoidedHazards.size
                                navigationInstructions = plan.result.instructions
                                currentInstructionIndex = 0
                                screenState = MapScreenState.ROUTE_PREVIEW
                                val routeCoordinates = plan.result.coordinates
                                if (routeCoordinates.size >= 2) {
                                        shopsViewModel.loadAlongRoute(
                                                listOf(
                                                                routeCoordinates.first(),
                                                                routeCoordinates[
                                                                        routeCoordinates.size / 2],
                                                                routeCoordinates.last()
                                                        )
                                                        .map {
                                                                GeoPoint(it.first, it.second)
                                                        }
                                        )
                                }
                                offlineRouteDebugMessage =
                                        buildString {
                                                append(
                                                        if (plan.source == RouteSource.OFFLINE)
                                                                "Offline route"
                                                        else "Online route"
                                                )
                                                if (stops.isNotEmpty()) {
                                                        append(" · ${stops.size} stop(s)")
                                                }
                                                if (plan.avoidedHazards.isNotEmpty()) {
                                                        append(
                                                                " · avoiding ${plan.avoidedHazards.size} hazard(s)"
                                                        )
                                                }
                                        }
                        } else {
                                offlineRouteDebugMessage =
                                        if (plan?.success == true)
                                                "You are already at the destination."
                                        else
                                                "Route failed: ${plan?.result?.errorMessage ?: "no route"}"
                        }

                        if (walking) isLoadingWalk = false else isLoadingRoute = false
                }
        }

        fun addStop(result: GeoSearchResult) {
                val destination = selectedDestination ?: return
                val point = GeoPoint(result.lat, result.lng)
                if (result.lat == destination.lat && result.lng == destination.lng) return
                if (GeoGeometry.haversineMeters(GeoPoint(currentLat, currentLng), point) < 50.0) return
                if (routeStops.any {
                                GeoGeometry.haversineMeters(
                                        GeoPoint(it.lat, it.lng),
                                        point
                                ) < 50.0
                        }
                ) {
                        return
                }
                routeStops = routeStops + result
                planTrip(selectedProfile)
        }

        fun removeStop(stop: GeoSearchResult) {
                routeStops = routeStops - stop
                planTrip(selectedProfile)
        }

        /**
         * Adds the closest cached shop of [category] that sits reasonably near the current route.
         * This is the "I need fuel on the way" shortcut; search remains available for anything
         * more specific.
         */
        fun addQuickStop(category: ShopCategory) {
                val activeRoute = activeRouteFor(selectedProfile)
                if (activeRoute == null || activeRoute.coordinates.size < 2) return

                val candidates =
                        mapShops.filter { shop ->
                                shop.category == category &&
                                        routeStops.none {
                                                it.lat == shop.lat && it.lng == shop.lon
                                        }
                        }
                if (candidates.isEmpty()) {
                        offlineRouteDebugMessage =
                                "No ${category.shortLabel.lowercase()} found nearby"
                        return
                }

                val routePoints = activeRoute.coordinates.map { GeoPoint(it.first, it.second) }
                val closest =
                        candidates.minByOrNull { shop ->
                                val point = GeoPoint(shop.lat, shop.lon)
                                routePoints.minOf { GeoGeometry.haversineMeters(point, it) }
                        } ?: return
                val detour =
                        routePoints.minOf {
                                GeoGeometry.haversineMeters(
                                        GeoPoint(closest.lat, closest.lon),
                                        it
                                )
                        }
                if (detour > 2_500.0) {
                        offlineRouteDebugMessage =
                                "No ${category.shortLabel.lowercase()} near this route"
                        return
                }

                addStop(
                        GeoSearchResult(
                                name = closest.name,
                                address = closest.address ?: "",
                                lat = closest.lat,
                                lng = closest.lon,
                                type = "shop"
                        )
                )
        }

        val planRouteTo: (GeoSearchResult) -> Unit = { result ->
                selectedDestination = result
                droppedPin = null
                routeStops = emptyList()
                searchPurpose = SearchPurpose.DESTINATION
                searchQuery = result.name
                screenState = MapScreenState.IDLE
                navigationNotice = null
                isRerouting = false
                offRouteMonitor.reset()
                // Choosing a new destination must end a previous trip. Otherwise its live
                // NavigationSession keeps publishing and can pull the screen back into
                // navigation while the new route preview is opening.
                if (NavigationSession.isActive()) NavigationSession.stop(context)
                routeSimulator.stopSimulation()
                planTrip("driving-car")
        }

        // The service owns the trip. Mirror its snapshot into the local UI state; this is what makes
        // switching tabs or reopening the app resume the same navigation.
        LaunchedEffect(navSession) {
                if (navSession.active) {
                        val route = navSession.route ?: return@LaunchedEffect
                        routeResult = route
                        selectedDestination = navSession.destination
                        selectedProfile = navSession.profile
                        routeStops = navSession.stops
                        navigationInstructions = route.instructions
                        currentInstructionIndex = navSession.instructionIndex
                        remainingDistance = navSession.remainingDistance
                        remainingDuration = navSession.remainingDuration
                        distanceToNextTurn = navSession.distanceToNextTurn
                        // During Test Mode the simulator owns the live position, bearing
                        // and speed. Do not let the service's real GPS fix overwrite them.
                        if (!isSimulationMode) {
                                if (navSession.lat != 0.0 || navSession.lng != 0.0) {
                                        currentLat = navSession.lat
                                        currentLng = navSession.lng
                                        currentBearing = navSession.bearing
                                        hasLocation = true
                                        persistAlertLocation(navSession.lat, navSession.lng)
                                }
                                currentSpeedMps = navSession.speedMps
                        }
                        speedLimitKmh = navSession.speedLimitKmh
                        wrongWayDetected = navSession.wrongWay
                        isRerouting = navSession.isRerouting
                        navigationNotice = navSession.notice
                        lastNavigationRoute = route
                        screenState = MapScreenState.NAVIGATING
                }
        }

        LaunchedEffect(navSession.active) {
                if (!navSession.active) navigationCameraReady = false
                if (!navSession.active &&
                                !isSimulationMode &&
                                screenState == MapScreenState.NAVIGATING
                ) {
                        routeResult = null
                        walkRouteResult = null
                        bikeRouteResult = null
                        selectedDestination = null
                        routeStops = emptyList()
                        screenState = MapScreenState.IDLE
                }
        }

        // A restored trip has no start-navigation swoop. Put the camera at street level so the
        // 3D car and route do not open at world zoom.
        LaunchedEffect(navSession.active, mapInstance, isSimulationMode) {
                if (isSimulationMode) return@LaunchedEffect
                if (!navSession.active) {
                        navigationCameraReady = false
                        return@LaunchedEffect
                }
                if (navigationCameraReady) return@LaunchedEffect
                val map = mapInstance ?: return@LaunchedEffect
                if (navSession.lat == 0.0 && navSession.lng == 0.0) return@LaunchedEffect
                map.setPadding(0, 400, 0, 0)
                map.animateCamera(
                        CameraUpdateFactory.newCameraPosition(
                                CameraPosition.Builder()
                                        .target(LatLng(navSession.lat, navSession.lng))
                                        .zoom(17.0)
                                        .tilt(55.0)
                                        .bearing(navSession.bearing)
                                        .build()
                        ),
                        900
                )
                navigationCameraReady = true
        }

        LaunchedEffect(
                navSession.lat,
                navSession.lng,
                navSession.bearing,
                navSession.active,
                inPip,
                isSimulationMode
        ) {
                if (isSimulationMode) return@LaunchedEffect
                if (navSession.active &&
                                screenState == MapScreenState.NAVIGATING &&
                                (navSession.lat != 0.0 || navSession.lng != 0.0)
                ) {
                        val mock =
                                android.location.Location("navigation-session").apply {
                                        latitude = navSession.lat
                                        longitude = navSession.lng
                                        bearing = navSession.bearing.toFloat()
                                        time = System.currentTimeMillis()
                                        accuracy = 5f
                                }
                        mapInstance?.locationComponent?.forceLocationUpdate(mock)
                        if (!isUserPanMode) {
                                mapInstance?.let { map ->
                                        if (inPip) {
                                                // PiP is a tiny window: use a flat, course-up
                                                // view so the route ahead stays visible above the
                                                // compact instruction bar.
                                                map.animateCamera(
                                                        CameraUpdateFactory.newCameraPosition(
                                                                CameraPosition.Builder()
                                                                        .target(
                                                                                LatLng(
                                                                                        navSession.lat,
                                                                                        navSession.lng
                                                                                )
                                                                        )
                                                                        .zoom(15.2)
                                                                        .tilt(0.0)
                                                                        .bearing(navSession.bearing)
                                                                        .build()
                                                        ),
                                                        450
                                                )
                                        } else {
                                                CameraTransitionManager
                                                        .updateNavigationCamera(
                                                                map,
                                                                LatLng(
                                                                        navSession.lat,
                                                                        navSession.lng
                                                                ),
                                                                navSession.bearing
                                                        )
                                        }
                                }
                        }
                }
        }

        // The full-screen map keeps MapLibre's logo/attribution; the tiny PiP window shows a
        // compact attribution in its instruction bar instead.
        LaunchedEffect(mapInstance, inPip) {
                runCatching {
                        mapInstance?.uiSettings?.isLogoEnabled = !inPip
                        mapInstance?.uiSettings?.isAttributionEnabled = !inPip
                }
        }

        LaunchedEffect(
                navSession.lat,
                navSession.lng,
                navSession.matchedIndex,
                navSession.route,
                isSimulationMode
        ) {
                if (!isSimulationMode && navSession.active) {
                        val route = navSession.route ?: return@LaunchedEffect
                        if (navSession.lat == 0.0 && navSession.lng == 0.0) {
                                return@LaunchedEffect
                        }
                        // Re-snap every fix instead of trusting a stale matched index.
                        // This keeps the line starting at the vehicle even for fast
                        // bike/moto fixes where the service index can lag behind.
                        val snapped =
                                CameraTransitionManager.snapToRoute(
                                        navSession.lat,
                                        navSession.lng,
                                        route.coordinates,
                                        80.0,
                                        fromIndex =
                                                navSession.matchedIndex
                                                        .takeIf { it >= 0 }
                                                        ?: 0
                                )
                        val start =
                                if (snapped.matchedIndex >= 0) snapped.matchedIndex
                                else navSession.matchedIndex
                        trimRouteSource(
                                routeSource,
                                route.coordinates,
                                start,
                                LatLng(navSession.lat, navSession.lng)
                        )
                }
        }

        // Shops around the current fix: dots on the map, tap for the same sheet as Nearby.
        LaunchedEffect(hasLocation, currentLat, currentLng, screenState) {
                if (hasLocation &&
                                (screenState == MapScreenState.IDLE ||
                                        screenState == MapScreenState.SEARCHING)
                ) {
                        shopsViewModel.loadAround(currentLat, currentLng)
                }
        }
        // Pins are neighbourhood furniture, not turn-by-turn furniture.
        MapShopMarkers(
                map = mapInstance,
                styleIndex = selectedStyleIndex,
                shops = if (screenState == MapScreenState.NAVIGATING) emptyList() else visibleShops
        )

        if (hasLocation) {
                val useSessionPosition =
                        !isSimulationMode &&
                                navSession.active &&
                                (navSession.lat != 0.0 || navSession.lng != 0.0)
                AnimatedPuck3D(
                        map = mapInstance,
                        styleIndex = selectedStyleIndex,
                        targetLat = if (useSessionPosition) navSession.lat else currentLat,
                        targetLng = if (useSessionPosition) navSession.lng else currentLng,
                        targetBearing =
                                if (useSessionPosition) navSession.bearing else currentBearing,
                        travelMode = selectedProfile,
                        vehicleIcon = vehicleIcon,
                        isNavigating = screenState == MapScreenState.NAVIGATING,
                        lights = vehicleLights,
                        speedMps = currentSpeedMps.toDouble()
                )
        }

        // Destination pin and long-press dropped pin.
        LaunchedEffect(mapInstance, selectedStyleIndex, selectedDestination, droppedPin) {
                val map = mapInstance ?: return@LaunchedEffect
                map.getStyle { style ->
                        runCatching {
                                fun image(name: String, color: Int) {
                                        if (style.getImage(name) == null) {
                                                style.addImage(name, MapPins.bitmap(color))
                                        }
                                }
                                image("destination-pin", AndroidColor.parseColor("#F2A33C"))
                                image("dropped-pin", AndroidColor.parseColor("#E05252"))

                                fun updatePin(
                                        sourceId: String,
                                        layerId: String,
                                        imageName: String,
                                        lat: Double?,
                                        lng: Double?
                                ) {
                                        val source = style.getSourceAs<GeoJsonSource>(sourceId)
                                        val layer = style.getLayer(layerId) as? SymbolLayer
                                        if (lat == null || lng == null) {
                                                layer?.setProperties(visibility(Property.NONE))
                                                return
                                        }
                                        val geoJson =
                                                """{"type":"Feature","geometry":{"type":"Point","coordinates":[$lng,$lat]}}"""
                                        if (source == null) {
                                                style.addSource(GeoJsonSource(sourceId, geoJson))
                                        } else {
                                                source.setGeoJson(geoJson)
                                        }
                                        if (layer == null) {
                                                val pin =
                                                        SymbolLayer(layerId, sourceId)
                                                                .withProperties(
                                                                        iconImage(imageName),
                                                                        iconAnchor(
                                                                                Property.ICON_ANCHOR_BOTTOM
                                                                        ),
                                                                        iconAllowOverlap(true),
                                                                        iconIgnorePlacement(true),
                                                                        iconSize(1.0f)
                                                                )
                                                if (style.getLayer("puck-layer") != null) {
                                                        style.addLayerBelow(pin, "puck-layer")
                                                } else {
                                                        style.addLayer(pin)
                                                }
                                        } else {
                                                layer.setProperties(visibility(Property.VISIBLE))
                                        }
                                }

                                updatePin(
                                        "destination-source",
                                        "destination-pin",
                                        "destination-pin",
                                        selectedDestination?.lat,
                                        selectedDestination?.lng
                                )
                                updatePin(
                                        "dropped-source",
                                        "dropped-pin",
                                        "dropped-pin",
                                        droppedPin?.latitude,
                                        droppedPin?.longitude
                                )
                        }
                }
        }

        // Choosing a category should reveal it, not leave the user hunting at city zoom.
        LaunchedEffect(shopFilter, mapInstance, mapShops.size) {
                val map = mapInstance ?: return@LaunchedEffect
                val category = shopFilter ?: return@LaunchedEffect
                if (screenState != MapScreenState.IDLE) return@LaunchedEffect
                val shops = mapShops.filter { it.category == category }
                if (shops.isEmpty()) return@LaunchedEffect
                val bounds = org.maplibre.android.geometry.LatLngBounds.Builder()
                shops.forEach { shop -> bounds.include(LatLng(shop.lat, shop.lon)) }
                map.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(bounds.build(), 180),
                        900
                )
        }

        // A marker tap takes over from an open search-place sheet.
        LaunchedEffect(selectedShop) {
                if (selectedShop != null) selectedPlace = null
        }

        selectedPlace?.let { place ->
                PlaceDetailSheet(
                        place = place,
                        onDismiss = { selectedPlace = null },
                        onDirections = {
                                selectedPlace = null
                                planRouteTo(place)
                        }
                )
        }

        // Tapping a marker (or a card elsewhere) opens the shared shop sheet.
        selectedShop?.let { shop ->
                ShopDetailSheet(
                        shop = shop,
                        onDismiss = { shopsViewModel.clearSelection() },
                        onDirections = {
                                shopsViewModel.clearSelection()
                                planRouteTo(
                                        GeoSearchResult(
                                                name = shop.name,
                                                address = shop.address ?: "",
                                                lat = shop.lat,
                                                lng = shop.lon,
                                                type = "shop"
                                        )
                                )
                        },
                        primaryLabel = "Directions"
                )
        }

        // GPS Location Client
        val fusedLocationClient = remember {
                LocationServices.getFusedLocationProviderClient(context)
        }

        /**
         * Stops that are still ahead of the driver on [route]. Used when a reroute must not throw
         * away a fuel stop the driver has not reached yet.
         */
        fun remainingStops(
                route: RouteResult?,
                currentIndex: Int
        ): List<GeoSearchResult> {
                if (routeStops.isEmpty()) return emptyList()
                if (route == null || currentIndex < 0) return routeStops
                return routeStops.filter { stop ->
                        val nearest =
                                route.coordinates.indices.minByOrNull { index ->
                                        val point = route.coordinates[index]
                                        GeoGeometry.haversineMeters(
                                                GeoPoint(stop.lat, stop.lng),
                                                GeoPoint(point.first, point.second)
                                        )
                                } ?: return@filter true
                        nearest > currentIndex + 1
                }
        }

        /** Stops the driver has already passed; they are removed from the trip. */
        fun passedStops(
                route: RouteResult?,
                currentIndex: Int
        ): List<GeoSearchResult> {
                if (route == null || currentIndex < 1 || routeStops.isEmpty()) return emptyList()
                return routeStops.filter { stop ->
                        val nearest =
                                route.coordinates.indices.minByOrNull { index ->
                                        val point = route.coordinates[index]
                                        GeoGeometry.haversineMeters(
                                                GeoPoint(stop.lat, stop.lng),
                                                GeoPoint(point.first, point.second)
                                        )
                                } ?: return@filter false
                        nearest < currentIndex - 1
                }
        }

        /**
         * Re-plan from the live fix after the driver leaves the line.
         *
         * The start point is the raw GPS fix, not the snapped route point: the whole point of this
         * route is to get back from where the car actually is. The resulting route replaces the
         * active one, so the next location callback snaps to the new geometry.
         */
        fun rerouteFromDeviation(
                rawLat: Double,
                rawLng: Double,
                wrongWay: Boolean = false,
                viaStops: List<GeoSearchResult> = emptyList()
        ) {
                if (isRerouting) return
                val destination = selectedDestination ?: return
                val walking = isWalkingProfile(selectedProfile)
                val biking = isBikeProfile(selectedProfile)
                val motorcycle = isMotorcycleProfile(selectedProfile)
                val activeHazards = hazards
                val session = MapSessionState.navigationSessionId
                val planOptions =
                        if (selectedProfile == "driving-car" || motorcycle) routeOptions
                        else RouteOptions()

                isRerouting = true
                if (wrongWay) wrongWayDetected = true
                navigationNotice =
                        if (wrongWay) "Wrong way · turn around…"
                        else "Off route · finding a new way…"

                MapSessionState.routeScope.launch {
                        val plan =
                                planRouteOffMain(
                                        startLat = rawLat,
                                        startLng = rawLng,
                                        destinationLat = destination.lat,
                                        destinationLng = destination.lng,
                                        profile =
                                                when {
                                                        walking -> "foot"
                                                        biking -> "bike"
                                                        motorcycle -> "driving-motorcycle"
                                                        else -> "car"
                                                },
                                        hazards = activeHazards,
                                        stops = viaStops.map { GeoPoint(it.lat, it.lng) },
                                        preferOffline = !NetworkState.isOnline(context),
                                        options = planOptions
                                )

                        // The driver may have exited or started a new trip while we were planning.
                        if (session != MapSessionState.navigationSessionId ||
                                        screenState != MapScreenState.NAVIGATING
                        ) {
                                return@launch
                        }

                        if (plan != null && plan.success) {
                                if (walking) {
                                        walkRouteResult = plan.result
                                } else if (biking) {
                                        bikeRouteResult = plan.result
                                } else {
                                        routeResult = plan.result
                                }
                                lastNavigationRoute = plan.result
                                navigationInstructions = plan.result.instructions
                                currentInstructionIndex = 0
                                activeRouteSource = plan.source
                                avoidedHazardCount = plan.avoidedHazards.size
                                routeStops = viaStops
                                remainingDistance = plan.result.distanceMeters
                                remainingDuration = plan.result.durationSeconds
                                navigationNotice =
                                        if (wrongWay) "Wrong way · turn around"
                                        else
                                                "New route · ${formatDistance(plan.result.distanceMeters)} · ${formatDuration(plan.result.durationSeconds)}"
                                offlineRouteDebugMessage =
                                        if (wrongWay) "Wrong-way reroute"
                                        else "Rerouted from current location"
                                plan.avoidedHazards.firstOrNull()?.let {
                                        lastAutoReroutedHazardId = it.id
                                }
                                if (isSimulationMode && pendingSimulationRestart) {
                                        pendingSimulationRestart = false
                                        routeSimulator.startSimulation(
                                                coordinates = plan.result.coordinates,
                                                speedMps = selectedSimSpeed.speedMps,
                                                profile = selectedProfile,
                                                scope = MapSessionState.simulationScope
                                        )
                                }
                        } else {
                                pendingSimulationRestart = false
                                navigationNotice =
                                        if (wrongWay) "Wrong way · no new route"
                                        else "Off route · no new route found"
                                offlineRouteDebugMessage =
                                        "Reroute failed: ${plan?.result?.errorMessage ?: "no route"}"
                        }

                        offRouteMonitor.markAttempt()
                        isRerouting = false
                }
        }

        /**
         * Test hook for simulation mode: pretend the driver missed a turn by ~200 m, feed that
         * position through the same detector the live GPS uses, then let the normal reroute path
         * take over. The route-follower is paused at the off-route point and restarted on the new
         * line once a route is found.
         */
        fun simulateDeviation() {
                if (!isSimulationMode || screenState != MapScreenState.NAVIGATING) return
                if (isRerouting) return
                if (selectedDestination == null) return
                val position = simPosition ?: return
                val route = activeRouteFor(selectedProfile)
                if (route == null || route.coordinates.size < 2) return

                val index = simSegmentIndex.coerceIn(0, route.coordinates.size - 2)
                val a = route.coordinates[index]
                val b = route.coordinates[index + 1]
                val heading =
                        CameraTransitionManager.calculateBearing(
                                LatLng(a.first, a.second),
                                LatLng(b.first, b.second)
                        )
                // Try both shoulders and keep the one that is genuinely furthest from the line.
                val candidates =
                        listOf(heading + 90.0, heading - 90.0).map { side ->
                                side to
                                        GeoGeometry.destinationMeters(
                                                GeoPoint(position.latitude, position.longitude),
                                                side,
                                                200.0
                                        )
                        }
                val (side, deviated) =
                        candidates.maxByOrNull { (_, point) ->
                                CameraTransitionManager.snapToRoute(
                                                point.latitude,
                                                point.longitude,
                                                route.coordinates
                                        )
                                        .distanceMeters
                        } ?: return
                val distance =
                        CameraTransitionManager.snapToRoute(
                                        deviated.latitude,
                                        deviated.longitude,
                                        route.coordinates
                                )
                                .distanceMeters

                // Run the real detector first. If the chosen point is somehow still close to the
                // route, keep the simulation running rather than freezing it for nothing.
                offRouteMonitor.reset()
                var decision = offRouteMonitor.onFix(distance, 5f)
                repeat(2) { decision = offRouteMonitor.onFix(distance, 5f) }
                if (!decision.shouldReroute) {
                        navigationNotice = "Simulated deviation · too close to the route"
                        return
                }

                routeSimulator.stopSimulation()
                pendingSimulationRestart = true
                currentLat = deviated.latitude
                currentLng = deviated.longitude
                currentBearing = ((side % 360.0) + 360.0) % 360.0
                hasLocation = true

                val mockLocation =
                        android.location.Location("simulated-deviation").apply {
                                latitude = deviated.latitude
                                longitude = deviated.longitude
                                bearing = currentBearing.toFloat()
                                speed = 0f
                                accuracy = 5f
                                time = System.currentTimeMillis()
                        }
                mapInstance?.locationComponent?.forceLocationUpdate(mockLocation)
                if (!isUserPanMode) {
                        mapInstance?.let { map ->
                                CameraTransitionManager.updateNavigationCamera(
                                        map,
                                        LatLng(deviated.latitude, deviated.longitude),
                                        currentBearing
                                )
                        }
                }

                rerouteFromDeviation(
                        deviated.latitude,
                        deviated.longitude,
                        viaStops = remainingStops(route, index)
                )
        }

        /**
         * Test hook for simulation mode: pretend the car is still on the route but pointing the
         * wrong way (a U-turn or a one-way entered backwards). The distance-to-line is zero, so
         * this exercises the bearing half of [OffRouteMonitor] and the wrong-way warning.
         */
        fun simulateWrongWay() {
                if (!isSimulationMode || screenState != MapScreenState.NAVIGATING) return
                if (isRerouting) return
                if (selectedDestination == null) return
                val position = simPosition ?: return
                val route = activeRouteFor(selectedProfile)
                if (route == null || route.coordinates.size < 2) return

                val index = simSegmentIndex.coerceIn(0, route.coordinates.size - 2)
                val a = route.coordinates[index]
                val b = route.coordinates[index + 1]
                val routeBearing =
                        CameraTransitionManager.calculateBearing(
                                LatLng(a.first, a.second),
                                LatLng(b.first, b.second)
                        )
                val wrongBearing = (routeBearing + 180.0) % 360.0
                val testSpeed = maxOf(selectedSimSpeed.speedMps, 5f)

                offRouteMonitor.reset()
                var decision =
                        offRouteMonitor.onFix(
                                distanceMeters = 0.0,
                                accuracyMeters = 5f,
                                routeBearingDegrees = routeBearing,
                                travelBearingDegrees = wrongBearing,
                                speedMps = testSpeed
                        )
                repeat(3) {
                        decision =
                                offRouteMonitor.onFix(
                                        distanceMeters = 0.0,
                                        accuracyMeters = 5f,
                                        routeBearingDegrees = routeBearing,
                                        travelBearingDegrees = wrongBearing,
                                        speedMps = testSpeed
                                )
                }
                if (!decision.shouldReroute) {
                        navigationNotice = "Wrong-way test · not enough movement"
                        return
                }

                routeSimulator.stopSimulation()
                pendingSimulationRestart = true
                currentLat = position.latitude
                currentLng = position.longitude
                currentBearing = wrongBearing
                hasLocation = true
                wrongWayDetected = true

                val mockLocation =
                        android.location.Location("simulated-wrong-way").apply {
                                latitude = position.latitude
                                longitude = position.longitude
                                bearing = wrongBearing.toFloat()
                                speed = testSpeed
                                accuracy = 5f
                                time = System.currentTimeMillis()
                        }
                mapInstance?.locationComponent?.forceLocationUpdate(mockLocation)
                if (!isUserPanMode) {
                        mapInstance?.let { map ->
                                CameraTransitionManager.updateNavigationCamera(
                                        map,
                                        LatLng(position.latitude, position.longitude),
                                        wrongBearing
                                )
                        }
                }

                rerouteFromDeviation(
                        position.latitude,
                        position.longitude,
                        wrongWay = true,
                        viaStops = remainingStops(route, index)
                )
        }

        // Live location tracking during navigation (real GPS or simulation)
        DisposableEffect(screenState, isSimulationMode) {
                if (screenState == MapScreenState.NAVIGATING) {
                        // Initialize remaining distance from route
                        val initialRoute = activeRouteFor(selectedProfile)
                        remainingDistance = initialRoute?.distanceMeters ?: 0.0
                        remainingDuration = initialRoute?.durationSeconds ?: 0.0

                        if (isSimulationMode) {
                                // Simulation mode - use RouteSimulator. Do not stop it on
                                // dispose: switching tabs or entering PiP should keep the
                                // simulated trip alive.
                                onDispose {}
                        } else if (!NavigationSession.isActive()) {
                                // Real GPS mode
                                val locationRequest =
                                        LocationRequest.Builder(
                                                        Priority.PRIORITY_HIGH_ACCURACY,
                                                        1000L // Match the camera animation
                                                        // for smoother tracking
                                                        )
                                                .build()

                                var previousLat = currentLat
                                var previousLng = currentLng

                                val locationCallback =
                                        object : LocationCallback() {
                                                override fun onLocationResult(
                                                        result: LocationResult
                                                ) {
                                                        result.lastLocation?.let { location ->
                                                                // Read the route here, not when the
                                                                // callback was created: a reroute
                                                                // replaces it while navigation runs.
                                                                val activeRoute =
                                                                        activeRouteFor(
                                                                                selectedProfile
                                                                        )

                                                                // Heading comes from the raw GPS
                                                                // movement, not the snapped points:
                                                                // after a reroute the snap can jump
                                                                // to another road and would spin
                                                                // the camera.
                                                                val movedMeters =
                                                                        calculateDistance(
                                                                                previousLat,
                                                                                previousLng,
                                                                                location.latitude,
                                                                                location.longitude
                                                                        )
                                                                val travelBearing =
                                                                        when {
                                                                                location.hasBearing() &&
                                                                                                location.speed >
                                                                                                                1.0f ->
                                                                                        location.bearing
                                                                                                .toDouble()
                                                                                movedMeters > 5.0 ->
                                                                                        CameraTransitionManager
                                                                                                .calculateBearing(
                                                                                                        LatLng(
                                                                                                                previousLat,
                                                                                                                previousLng
                                                                                                        ),
                                                                                                        LatLng(
                                                                                                                location.latitude,
                                                                                                                location.longitude
                                                                                                        )
                                                                                                )
                                                                                else -> currentBearing
                                                                        }

                                                                // Apply road snapping if we have a
                                                                // route
                                                                val snapResult =
                                                                        activeRoute?.coordinates
                                                                                ?.let { coords ->
                                                                                        CameraTransitionManager
                                                                                                .snapToRoute(
                                                                                                        location.latitude,
                                                                                                        location.longitude,
                                                                                                        coords
                                                                                                )
                                                                                }
                                                                                ?: CameraTransitionManager
                                                                                        .SnapResult(
                                                                                                location.latitude,
                                                                                                location.longitude,
                                                                                                -1
                                                                                        )

                                                                // Deviation is judged on the raw
                                                                // fix, never the snapped one. The
                                                                // route bearing lets us catch a
                                                                // wrong-way/U-turn even when the car
                                                                // never leaves the line.
                                                                if (activeRoute != null) {
                                                                        val routeBearing =
                                                                                if (snapResult.matchedIndex in
                                                                                                0 until
                                                                                                        activeRoute
                                                                                                                .coordinates
                                                                                                                .size -
                                                                                                                1
                                                                                ) {
                                                                                        val a =
                                                                                                activeRoute
                                                                                                        .coordinates[
                                                                                                        snapResult
                                                                                                                .matchedIndex]
                                                                                        val b =
                                                                                                activeRoute
                                                                                                        .coordinates[
                                                                                                        snapResult
                                                                                                                .matchedIndex +
                                                                                                                1]
                                                                                        CameraTransitionManager
                                                                                                .calculateBearing(
                                                                                                        LatLng(
                                                                                                                a.first,
                                                                                                                a.second
                                                                                                        ),
                                                                                                        LatLng(
                                                                                                                b.first,
                                                                                                                b.second
                                                                                                        )
                                                                                                )
                                                                                } else null
                                                                        val decision =
                                                                                offRouteMonitor.onFix(
                                                                                        distanceMeters =
                                                                                                snapResult
                                                                                                        .distanceMeters,
                                                                                        accuracyMeters =
                                                                                                location.accuracy,
                                                                                        routeBearingDegrees =
                                                                                                routeBearing,
                                                                                        travelBearingDegrees =
                                                                                                travelBearing,
                                                                                        speedMps =
                                                                                                location.speed
                                                                                )
                                                                        wrongWayDetected =
                                                                                decision.isWrongWay
                                                                        if (decision.shouldReroute) {
                                                                                rerouteFromDeviation(
                                                                                        location.latitude,
                                                                                        location.longitude,
                                                                                        wrongWay =
                                                                                                decision.isWrongWay,
                                                                                        viaStops =
                                                                                                remainingStops(
                                                                                                        activeRoute,
                                                                                                        snapResult
                                                                                                                .matchedIndex
                                                                                                )
                                                                                )
                                                                        }
                                                                }

                                                                currentLat = snapResult.snappedLat
                                                                currentLng = snapResult.snappedLng
                                                                hasLocation = true
                                                                currentSpeedMps =
                                                                        if (location.hasSpeed())
                                                                                location.speed
                                                                                        .coerceAtLeast(
                                                                                                0f
                                                                                        )
                                                                        else 0f
                                                                speedLimitKmh =
                                                                        OfflineRoutingManager
                                                                                .speedLimitKmhAt(
                                                                                        location.latitude,
                                                                                        location.longitude
                                                                                )

                                                                // Update route source for vanishing
                                                                // trail
                                                                if (snapResult.matchedIndex != -1 &&
                                                                                activeRoute != null
                                                                ) {
                                                                        trimRouteSource(
                                                                                routeSource,
                                                                                activeRoute.coordinates,
                                                                                snapResult.matchedIndex,
                                                                                LatLng(currentLat, currentLng)
                                                                        )
                                                                }

                                                                // Update instruction index based on
                                                                // matched route index
                                                                if (snapResult.matchedIndex != -1 &&
                                                                                activeRoute != null
                                                                ) {
                                                                        // Find step that starts
                                                                        // after current index
                                                                        val nextStepIndex =
                                                                                activeRoute.steps
                                                                                        .indexOfFirst {
                                                                                                it.wayPointIndex >
                                                                                                        snapResult
                                                                                                                .matchedIndex
                                                                                        }
                                                                        val currentStepIndex =
                                                                                if (nextStepIndex ==
                                                                                                -1
                                                                                )
                                                                                        activeRoute
                                                                                                .steps
                                                                                                .lastIndex
                                                                                else nextStepIndex

                                                                        distanceToNextTurn =
                                                                                if (nextStepIndex !=
                                                                                                -1
                                                                                )
                                                                                        distanceToRouteIndexMeters(
                                                                                                activeRoute
                                                                                                        .coordinates,
                                                                                                snapResult
                                                                                                        .matchedIndex,
                                                                                                activeRoute
                                                                                                        .steps[
                                                                                                        nextStepIndex]
                                                                                                        .wayPointIndex,
                                                                                                snapResult
                                                                                                        .snappedLat,
                                                                                                snapResult
                                                                                                        .snappedLng
                                                                                        )
                                                                                else 0.0

                                                                        if (currentInstructionIndex !=
                                                                                        currentStepIndex
                                                                        ) {
                                                                                currentInstructionIndex =
                                                                                        currentStepIndex
                                                                        }

                                                                        val passed =
                                                                                passedStops(
                                                                                        activeRoute,
                                                                                        snapResult
                                                                                                .matchedIndex
                                                                                )
                                                                        if (passed.isNotEmpty()) {
                                                                                routeStops =
                                                                                        routeStops -
                                                                                                passed.toSet()
                                                                                navigationNotice =
                                                                                        "Stop reached · continuing"
                                                                        }
                                                                }

                                                                // Reuse the raw travel bearing computed
                                                                // above; it was already used for
                                                                // wrong-way detection.
                                                                currentBearing = travelBearing
                                                                previousLat = location.latitude
                                                                previousLng = location.longitude

                                                                // Calculate remaining distance. Follow the
                                                                // route when we are on it; fall back to
                                                                // straight-line only while off-route.
                                                                selectedDestination?.let { dest ->
                                                                        val route = activeRoute
                                                                        val routeRemaining =
                                                                                if (route !=
                                                                                                null &&
                                                                                                snapResult
                                                                                                        .matchedIndex !=
                                                                                                        -1
                                                                                )
                                                                                        remainingRouteMeters(
                                                                                                route.coordinates,
                                                                                                snapResult
                                                                                                        .matchedIndex,
                                                                                                snapResult
                                                                                                        .snappedLat,
                                                                                                snapResult
                                                                                                        .snappedLng
                                                                                        )
                                                                                else null

                                                                        if (routeRemaining !=
                                                                                        null &&
                                                                                        route !=
                                                                                                null
                                                                        ) {
                                                                                remainingDistance =
                                                                                        routeRemaining
                                                                                val newDuration =
                                                                                        routeRemaining /
                                                                                                liveSpeedMps(
                                                                                                        route
                                                                                                )
                                                                                remainingDuration =
                                                                                        if (remainingDuration >
                                                                                                        0.0
                                                                                        )
                                                                                                remainingDuration *
                                                                                                        0.6 +
                                                                                                        newDuration *
                                                                                                                0.4
                                                                                        else newDuration
                                                                        } else {
                                                                                val toDestination =
                                                                                        calculateDistance(
                                                                                                snapResult
                                                                                                        .snappedLat,
                                                                                                snapResult
                                                                                                        .snappedLng,
                                                                                                dest.lat,
                                                                                                dest.lng
                                                                                        )
                                                                                remainingDistance =
                                                                                        toDestination
                                                                                val newDuration =
                                                                                        toDestination /
                                                                                                liveSpeedMps(
                                                                                                        route
                                                                                                )
                                                                                remainingDuration =
                                                                                        if (remainingDuration >
                                                                                                        0.0
                                                                                        )
                                                                                                remainingDuration *
                                                                                                        0.6 +
                                                                                                        newDuration *
                                                                                                                0.4
                                                                                        else newDuration
                                                                        }
                                                                }

                                                                // Update camera with bearing (only
                                                                // if not panning)
                                                                if (!isUserPanMode) {
                                                                        mapInstance?.let { map ->
                                                                                CameraTransitionManager
                                                                                        .updateNavigationCamera(
                                                                                                map,
                                                                                                LatLng(
                                                                                                        snapResult
                                                                                                                .snappedLat,
                                                                                                        snapResult
                                                                                                                .snappedLng
                                                                                                ),
                                                                                                travelBearing
                                                                                        )
                                                                        }
                                                                }
                                                        }
                                                }
                                        }

                                try {
                                        if (ContextCompat.checkSelfPermission(
                                                        context,
                                                        Manifest.permission.ACCESS_FINE_LOCATION
                                                ) == PackageManager.PERMISSION_GRANTED
                                        ) {
                                                fusedLocationClient.requestLocationUpdates(
                                                        locationRequest,
                                                        locationCallback,
                                                        null
                                                )
                                        }
                                } catch (e: SecurityException) {}

                                onDispose {
                                        fusedLocationClient.removeLocationUpdates(locationCallback)
                                }
                        } else {
                                onDispose {}
                        }
                } else {
                        onDispose {}
                }
        }

        // Simulation state updates
        LaunchedEffect(simPosition, simBearing, simSegmentIndex, simSpeed, simProgress) {
                if (isSimulationMode &&
                                simPosition != null &&
                                screenState == MapScreenState.NAVIGATING
                ) {
                        currentLat = simPosition!!.latitude
                        currentLng = simPosition!!.longitude
                        currentBearing = simBearing
                        currentSpeedMps = simSpeed
                        hasLocation = true
                        val now = System.currentTimeMillis()
                        if (now - lastSpeedLimitCheckAt > 2_000L) {
                                speedLimitKmh =
                                        OfflineRoutingManager.speedLimitKmhAt(
                                                simPosition!!.latitude,
                                                simPosition!!.longitude
                                        )
                                lastSpeedLimitCheckAt = now
                        }

                        // Update route source for vanishing trail - use direct source ref
                        val activeRoute = activeRouteFor(selectedProfile)
                        if (activeRoute != null && simSegmentIndex >= 0) {
                                trimRouteSource(
                                        routeSource,
                                        activeRoute.coordinates,
                                        simSegmentIndex,
                                        LatLng(currentLat, currentLng)
                                )
                        }

                        // Update instruction index based on simulator segment index
                        if (activeRoute != null) {
                                val nextStepIndex =
                                        activeRoute.steps.indexOfFirst {
                                                it.wayPointIndex > simSegmentIndex
                                        }
                                val currentStepIndex =
                                        if (nextStepIndex == -1) activeRoute.steps.lastIndex
                                        else nextStepIndex

                                distanceToNextTurn =
                                        if (nextStepIndex != -1)
                                                distanceToRouteIndexMeters(
                                                        activeRoute.coordinates,
                                                        simSegmentIndex,
                                                        activeRoute.steps[nextStepIndex]
                                                                .wayPointIndex,
                                                        currentLat,
                                                        currentLng
                                                )
                                        else 0.0

                                if (currentInstructionIndex != currentStepIndex) {
                                        currentInstructionIndex = currentStepIndex
                                }

                                val passed = passedStops(activeRoute, simSegmentIndex)
                                if (passed.isNotEmpty()) {
                                        routeStops = routeStops - passed.toSet()
                                        navigationNotice = "Stop reached · continuing"
                                }
                        }

                        // Force update blue dot location on map
                        val mockLocation =
                                android.location.Location("simulated").apply {
                                        latitude = simPosition!!.latitude
                                        longitude = simPosition!!.longitude
                                        bearing = simBearing.toFloat()
                                        time = System.currentTimeMillis()
                                        accuracy = 5f
                                }
                        mapInstance?.locationComponent?.forceLocationUpdate(mockLocation)

                        // Update remaining distance/time from simulator
                        remainingDistance = routeSimulator.getRemainingDistance()
                        remainingDuration = routeSimulator.getRemainingTime()

                        // Update camera if not in user pan mode
                        if (!isUserPanMode) {
                                mapInstance?.let { map ->
                                        // Use speed-dependent camera but ensure we don't fight the
                                        // location component
                                        // actually we disabled location engine so we are free to
                                        // animate
                                        CameraTransitionManager.updateCameraForSpeed(
                                                map,
                                                simPosition!!,
                                                simBearing,
                                                simSpeed
                                        )
                                }
                        }
                }
        }

        // Handle simulation arrival
        LaunchedEffect(simArrived) {
                if (simArrived && isSimulationMode) {
                        // Show arrival message (navigation continues but simulator stops)
                        currentInstructionIndex = navigationInstructions.size - 1
                        remainingDistance = 0.0
                        remainingDuration = 0.0
                        distanceToNextTurn = 0.0
                        currentSpeedMps = 0f
                }
        }

        // Auto-recenter after user panning timeout (5 seconds)
        LaunchedEffect(isUserPanMode, lastPanTime) {
                if (isUserPanMode && screenState == MapScreenState.NAVIGATING) {
                        delay(5000L)
                        if (System.currentTimeMillis() - lastPanTime >= 5000L) {
                                isUserPanMode = false
                                mapInstance?.let { map ->
                                        CameraTransitionManager.recenterOnPosition(
                                                map,
                                                LatLng(currentLat, currentLng),
                                                currentBearing
                                        )
                                }
                        }
                }
        }

        // Keep the idle-map location fresh too. Route planning used to read currentLat/currentLng
        // from a single lastLocation snapshot taken when the Map tab opened. After moving a few
        // hundred metres the blue dot moved, but the next route still started at the old place and
        // could snap straight to the destination ("Arrived" on the first fix). Navigation owns its
        // own high-accuracy callback, so this one only runs outside navigation and Test Mode.
        DisposableEffect(screenState, isSimulationMode) {
                val idle = screenState != MapScreenState.NAVIGATING && !isSimulationMode
                val hasPermission =
                        ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED
                if (!idle || !hasPermission) {
                        onDispose {}
                } else {
                        val request =
                                LocationRequest.Builder(
                                                Priority.PRIORITY_HIGH_ACCURACY,
                                                2_000L
                                        )
                                        .setMinUpdateDistanceMeters(2f)
                                        .build()
                        val callback =
                                object : LocationCallback() {
                                        override fun onLocationResult(result: LocationResult) {
                                                val location = result.lastLocation ?: return
                                                if (NavigationSession.isActive()) return
                                                currentLat = location.latitude
                                                currentLng = location.longitude
                                                hasLocation = true
                                                if (location.hasBearing() &&
                                                                location.speed > 1f
                                                ) {
                                                        currentBearing =
                                                                location.bearing.toDouble()
                                                }
                                                if (location.hasSpeed()) {
                                                        currentSpeedMps =
                                                                location.speed.coerceAtLeast(0f)
                                                }
                                                persistAlertLocation(
                                                        location.latitude,
                                                        location.longitude
                                                )
                                        }
                                }
                        try {
                                fusedLocationClient.requestLocationUpdates(
                                        request,
                                        callback,
                                        null
                                )
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                        if (!NavigationSession.isActive() && location != null) {
                                                currentLat = location.latitude
                                                currentLng = location.longitude
                                                hasLocation = true
                                        }
                                }
                        } catch (e: SecurityException) {}
                        onDispose { fusedLocationClient.removeLocationUpdates(callback) }
                }
        }

        // The first GPS fix often arrives after onMapReady, which leaves the camera at the
        // country-level fallback. Center once when an idle map gets its first real fix.
        LaunchedEffect(mapInstance, hasLocation, currentLat, currentLng, screenState) {
                val map = mapInstance ?: return@LaunchedEffect
                if (!hasLocation || centeredOnFirstFix) return@LaunchedEffect
                if (screenState != MapScreenState.IDLE &&
                                screenState != MapScreenState.SEARCHING
                ) {
                        return@LaunchedEffect
                }
                centeredOnFirstFix = true
                map.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                                LatLng(currentLat, currentLng),
                                16.0
                        ),
                        800
                )
        }

        // Draw route when available – stores direct source reference for fast updates
        LaunchedEffect(
                routeResult,
                walkRouteResult,
                bikeRouteResult,
                selectedProfile,
                selectedStyleIndex,
                mapInstance,
                inPip
        ) {
                val drawnRoute = activeRouteFor(selectedProfile)
                drawnRoute?.let { result ->
                        if (result.success && result.coordinates.isNotEmpty()) {
                                mapInstance?.getStyle { style ->
                                        // Remove existing route layer/source if any
                                        try {
                                                style.removeLayer("route-casing")
                                                style.removeLayer("route-layer")
                                                style.removeSource("route-source")
                                        } catch (e: Exception) {}
                                        routeSource = null

                                        // Build GeoJSON for the full route
                                        val coordsStr =
                                                result.coordinates.joinToString(",") {
                                                        "[${it.second},${it.first}]"
                                                }
                                        val geoJson =
                                                """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordsStr]}}"""

                                        val newSource = GeoJsonSource("route-source", geoJson)
                                        style.addSource(newSource)
                                        val walkingRoute = isWalkingProfile(selectedProfile)
                                        if (walkingRoute) {
                                                // Walking directions use dots instead of a solid
                                                // road line, so footpaths read differently from
                                                // driving and cycling routes.
                                                val dots = LineLayer("route-layer", "route-source")
                                                dots.setProperties(
                                                        lineColor(
                                                                AndroidColor.parseColor(
                                                                        "#2F80ED"
                                                                )
                                                        ),
                                                        lineWidth(8f),
                                                        lineOpacity(1f),
                                                        lineCap(Property.LINE_CAP_ROUND),
                                                        lineJoin(Property.LINE_JOIN_ROUND),
                                                        lineDasharray(arrayOf(0.1f, 1.8f))
                                                )
                                                if (style.getLayer("puck-layer") != null) {
                                                        style.addLayerBelow(dots, "puck-layer")
                                                } else {
                                                        style.addLayer(dots)
                                                }
                                        } else {
                                                // White casing under a saturated blue line. The
                                                // old honey line vanished into the yellow streets.
                                                val casing =
                                                        LineLayer(
                                                                "route-casing",
                                                                "route-source"
                                                        )
                                                casing.setProperties(
                                                        lineColor(AndroidColor.WHITE),
                                                        lineWidth(11f),
                                                        lineOpacity(0.95f),
                                                        lineCap(Property.LINE_CAP_ROUND),
                                                        lineJoin(Property.LINE_JOIN_ROUND)
                                                )
                                                val layer =
                                                        LineLayer(
                                                                "route-layer",
                                                                "route-source"
                                                        )
                                                layer.setProperties(
                                                        lineColor(
                                                                AndroidColor.parseColor(
                                                                        "#2F80ED"
                                                                )
                                                        ),
                                                        lineWidth(7f),
                                                        lineOpacity(1f),
                                                        lineCap(Property.LINE_CAP_ROUND),
                                                        lineJoin(Property.LINE_JOIN_ROUND)
                                                )
                                                if (style.getLayer("puck-layer") != null) {
                                                        style.addLayerBelow(casing, "puck-layer")
                                                        style.addLayerBelow(layer, "puck-layer")
                                                } else {
                                                        style.addLayer(casing)
                                                        style.addLayer(layer)
                                                }
                                        }
                                        // Store reference for direct future updates
                                        routeSource = newSource

                                        // Animate camera to show full route bounds. During
                                        // navigation the camera belongs to the driver: a
                                        // reroute must not yank it out to the whole route.
                                        if (result.coordinates.size >= 2 &&
                                                        screenState != MapScreenState.NAVIGATING
                                        ) {
                                                val bounds =
                                                        org.maplibre.android.geometry.LatLngBounds
                                                                .Builder()
                                                result.coordinates.forEach { pair ->
                                                        bounds.include(
                                                                LatLng(pair.first, pair.second)
                                                        )
                                                }
                                                mapInstance?.animateCamera(
                                                        CameraUpdateFactory.newLatLngBounds(
                                                                bounds.build(),
                                                                200
                                                        ),
                                                        1500
                                                )
                                        }
                                }
                        }
                }
        }

        // === INTERMEDIATE STOPS (fuel / food / hotel on the way) ===
        LaunchedEffect(routeStops, mapInstance, selectedStyleIndex) {
                val map = mapInstance ?: return@LaunchedEffect
                map.getStyle { style ->
                        runCatching {
                                val features = JSONArray()
                                routeStops.forEach { stop ->
                                        features.put(
                                                JSONObject()
                                                        .put("type", "Feature")
                                                        .put(
                                                                "geometry",
                                                                JSONObject()
                                                                        .put("type", "Point")
                                                                        .put(
                                                                                "coordinates",
                                                                                JSONArray()
                                                                                        .put(stop.lng)
                                                                                        .put(stop.lat)
                                                                        )
                                                        )
                                                        .put(
                                                                "properties",
                                                                JSONObject()
                                                                        .put("name", stop.name)
                                                        )
                                        )
                                }
                                val geoJson =
                                        JSONObject()
                                                .put("type", "FeatureCollection")
                                                .put("features", features)
                                                .toString()

                                val existing =
                                        style.getSourceAs<GeoJsonSource>("stops-source")
                                if (existing != null) {
                                        existing.setGeoJson(geoJson)
                                        style.getLayer("stops-marker")
                                                ?.setProperties(
                                                        visibility(
                                                                if (routeStops.isEmpty())
                                                                        Property.NONE
                                                                else Property.VISIBLE
                                                        )
                                                )
                                } else if (routeStops.isNotEmpty()) {
                                        style.addSource(
                                                GeoJsonSource("stops-source", geoJson)
                                        )
                                        style.addLayer(
                                                CircleLayer("stops-marker", "stops-source")
                                                        .withProperties(
                                                                circleColor(
                                                                        AndroidColor.WHITE
                                                                ),
                                                                circleRadius(6f),
                                                                circleStrokeWidth(3f),
                                                                circleStrokeColor(
                                                                        AndroidColor.parseColor(
                                                                                "#F2A33C"
                                                                        )
                                                                ),
                                                                circleOpacity(0.98f)
                                                        )
                                                        .apply { minZoom = 8f }
                                        )
                                }
                        }
                }
        }

        // === ACTIVE HAZARD ZONES (blast radius circles) ===
        LaunchedEffect(hazards, selectedStyleIndex, mapInstance) {
                val map = mapInstance ?: return@LaunchedEffect
                map.getStyle { style ->
                        try {
                                val features = JSONArray()
                                hazards.forEach { hazard ->
                                        val ring = JSONArray()
                                        GeoGeometry.circlePolygon(
                                                        hazard.center,
                                                        hazard.radiusMeters,
                                                        48
                                                )
                                                .forEach { point ->
                                                        ring.put(
                                                                JSONArray()
                                                                        .put(point.longitude)
                                                                        .put(point.latitude)
                                                        )
                                                }
                                        features.put(
                                                JSONObject()
                                                        .put("type", "Feature")
                                                        .put(
                                                                "geometry",
                                                                JSONObject()
                                                                        .put("type", "Polygon")
                                                                        .put(
                                                                                "coordinates",
                                                                                JSONArray().put(ring)
                                                                        )
                                                        )
                                                        .put(
                                                                "properties",
                                                                JSONObject()
                                                                        .put("id", hazard.id)
                                                                        .put("title", hazard.title)
                                                                        .put(
                                                                                "severity",
                                                                                hazard.severity
                                                                        )
                                                                        .put("type", hazard.type)
                                                        )
                                        )
                                }
                                val geoJson =
                                        JSONObject()
                                                .put("type", "FeatureCollection")
                                                .put("features", features)
                                                .toString()

                                val existing =
                                        style.getSourceAs<GeoJsonSource>("hazard-source")
                                if (existing != null) {
                                        existing.setGeoJson(geoJson)
                                } else {
                                        style.addSource(
                                                GeoJsonSource("hazard-source", geoJson)
                                        )
                                        style.addLayer(
                                                FillLayer("hazard-fill", "hazard-source")
                                                        .withProperties(
                                                                fillColor(
                                                                        AndroidColor.parseColor(
                                                                                "#E53935"
                                                                        )
                                                                ),
                                                                fillOpacity(0.16f)
                                                        )
                                        )
                                        style.addLayer(
                                                LineLayer("hazard-outline", "hazard-source")
                                                        .withProperties(
                                                                lineColor(
                                                                        AndroidColor.parseColor(
                                                                                "#B71C1C"
                                                                        )
                                                                ),
                                                                lineWidth(1.5f),
                                                                lineOpacity(0.75f)
                                                        )
                                        )
                                }
                        } catch (_: Exception) {
                                // Style was mid-reload; the next recomposition retries.
                        }
                }
        }

        // === FOCUS PIN: arrive here from "Nearby", an alert, or a flood sector ===
        var focusTarget by remember { mutableStateOf<MapTarget?>(null) }
        var activeFloodSector by remember { mutableStateOf<Sector?>(null) }
        LaunchedEffect(Unit) {
                focusTarget = MapFocus.consume()
                focusTarget?.let { target -> searchQuery = target.label }
        }
        LaunchedEffect(mapInstance, selectedStyleIndex, focusTarget) {
                val map = mapInstance ?: return@LaunchedEffect
                val target = focusTarget ?: return@LaunchedEffect
                if (target.floodSector != null) {
                        activeFloodSector = target.floodSector
                        focusTarget = null
                        map.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                        LatLng(target.lat, target.lon),
                                        target.zoom
                                ),
                                800
                        )
                        return@LaunchedEffect
                }
                activeFloodSector = null
                map.getStyle { style ->
                        try {
                                try {
                                        style.removeLayer("focus-halo")
                                        style.removeLayer("focus-dot")
                                        style.removeSource("focus-source")
                                } catch (_: Exception) {}
                                val geoJson =
                                        JSONObject()
                                                .put("type", "Feature")
                                                .put(
                                                        "geometry",
                                                        JSONObject()
                                                                .put("type", "Point")
                                                                .put(
                                                                        "coordinates",
                                                                        JSONArray()
                                                                                .put(target.lon)
                                                                                .put(target.lat)
                                                                )
                                                )
                                                .put("properties", JSONObject())
                                                .toString()
                                style.addSource(GeoJsonSource("focus-source", geoJson))
                                style.addLayer(
                                        CircleLayer("focus-halo", "focus-source")
                                                .withProperties(
                                                        circleColor(
                                                                AndroidColor.parseColor("#4FB3D9")
                                                        ),
                                                        circleRadius(30f),
                                                        circleOpacity(0.18f),
                                                        circleBlur(0.4f)
                                                )
                                )
                                style.addLayer(
                                        CircleLayer("focus-dot", "focus-source")
                                                .withProperties(
                                                        circleColor(
                                                                AndroidColor.parseColor("#F2A33C")
                                                        ),
                                                        circleRadius(9f),
                                                        circleStrokeWidth(3f),
                                                        circleStrokeColor(AndroidColor.WHITE)
                                                )
                                )
                                map.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                                LatLng(target.lat, target.lon),
                                                target.zoom
                                        ),
                                        800
                                )
                        } catch (_: Exception) {
                                // Style was mid-reload.
                        }
                }
        }

        LaunchedEffect(mapInstance, selectedStyleIndex, activeFloodSector) {
                val map = mapInstance ?: return@LaunchedEffect
                val sector = activeFloodSector
                if (sector == null) {
                        clearFloodLayers(map)
                } else {
                        loadSectorData(context, map, sector)
                }
        }

        // Route status messages are transient: the card carries the lasting state.
        LaunchedEffect(offlineRouteDebugMessage) {
                if (offlineRouteDebugMessage != null) {
                        delay(4000)
                        offlineRouteDebugMessage = null
                }
        }

        // Navigation notices ("off route", "new route", hazard detour) are also transient. While a
        // reroute is in flight the message must stay up, so only start the timer after it settles.
        LaunchedEffect(navigationNotice, isRerouting) {
                if (!isRerouting && navigationNotice != null) {
                        delay(6000)
                        navigationNotice = null
                }
        }

        // In a simulation the route-follower is restarted on the correct heading immediately, so
        // the wrong-way warning is a timed demo. In real GPS each fix refreshes it instead.
        LaunchedEffect(wrongWayDetected, isSimulationMode) {
                if (isSimulationMode && wrongWayDetected) {
                        delay(10_000)
                        wrongWayDetected = false
                }
        }

        // === HAZARD CHECK: does the active route cut through a danger zone? ===
        LaunchedEffect(hazards, routeResult, walkRouteResult, bikeRouteResult, selectedProfile) {
                val activeRoute = activeRouteFor(selectedProfile)
                if (activeRoute == null ||
                                !activeRoute.success ||
                                activeRoute.coordinates.size < 2 ||
                                hazards.isEmpty()
                ) {
                        hazardWarning = null
                        return@LaunchedEffect
                }
                val points = activeRoute.coordinates.map { GeoPoint(it.first, it.second) }
                hazardWarning =
                        GeoGeometry.findHazardHits(points, hazards, 60.0).firstOrNull()?.hazard
        }

        // === AUTO-REROUTE: while navigating, replan if a hazard is close ahead ===
        LaunchedEffect(hazardWarning, screenState) {
                if (NavigationSession.isActive()) return@LaunchedEffect
                val hazard = hazardWarning ?: return@LaunchedEffect
                if (screenState != MapScreenState.NAVIGATING || !hasLocation) {
                        return@LaunchedEffect
                }
                if (isRerouting) return@LaunchedEffect
                if (hazard.id == lastAutoReroutedHazardId) return@LaunchedEffect

                val distanceToHazard =
                        GeoGeometry.haversineMeters(
                                GeoPoint(currentLat, currentLng),
                                hazard.center
                        )
                if (distanceToHazard > hazard.radiusMeters + 5_000.0) return@LaunchedEffect

                val destination = selectedDestination ?: return@LaunchedEffect
                val isWalking = isWalkingProfile(selectedProfile)
                val isBiking = isBikeProfile(selectedProfile)
                val isMotorcycle = isMotorcycleProfile(selectedProfile)
                val activeRoute = activeRouteFor(selectedProfile)
                val currentIndex =
                        activeRoute?.coordinates?.indices?.minByOrNull { index ->
                                val point = activeRoute.coordinates[index]
                                GeoGeometry.haversineMeters(
                                        GeoPoint(currentLat, currentLng),
                                        GeoPoint(point.first, point.second)
                                )
                        } ?: -1
                val viaStops = remainingStops(activeRoute, currentIndex)
                val planOptions =
                        if (selectedProfile == "driving-car" || isMotorcycle) routeOptions
                        else RouteOptions()

                isRerouting = true
                navigationNotice = "Hazard ahead · finding a safe detour…"
                try {
                        val plan =
                                planRouteOffMain(
                                        startLat = currentLat,
                                        startLng = currentLng,
                                        destinationLat = destination.lat,
                                        destinationLng = destination.lng,
                                        profile =
                                                when {
                                                        isWalking -> "foot"
                                                        isBiking -> "bike"
                                                        isMotorcycle -> "driving-motorcycle"
                                                        else -> "car"
                                                },
                                        hazards = hazards,
                                        stops = viaStops.map { GeoPoint(it.lat, it.lng) },
                                        preferOffline = !NetworkState.isOnline(context),
                                        options = planOptions
                                )

                        if (plan != null && plan.success) {
                                if (isWalking) {
                                        walkRouteResult = plan.result
                                } else if (isBiking) {
                                        bikeRouteResult = plan.result
                                } else {
                                        routeResult = plan.result
                                }
                                lastNavigationRoute = plan.result
                                navigationInstructions = plan.result.instructions
                                currentInstructionIndex = 0
                                activeRouteSource = plan.source
                                avoidedHazardCount = plan.avoidedHazards.size
                                routeStops = viaStops
                                remainingDistance = plan.result.distanceMeters
                                remainingDuration = plan.result.durationSeconds
                                lastAutoReroutedHazardId = hazard.id
                                navigationNotice = "Rerouted around ${hazard.title}"
                                offlineRouteDebugMessage = "Rerouted around ${hazard.title}"
                        } else {
                                navigationNotice = "Hazard ahead · no safe detour found"
                                offlineRouteDebugMessage =
                                        "Hazard detour failed: ${plan?.result?.errorMessage ?: "no route"}"
                        }
                } finally {
                        isRerouting = false
                }
        }

        // === LIVE TRAFFIC: TomTom flow at the current position, every 45 seconds ===
        LaunchedEffect(screenState) {
                if (screenState == MapScreenState.NAVIGATING) {
                        try {
                                while (true) {
                                        val flow =
                                                TomTomTrafficRepository.flowAt(
                                                        currentLat,
                                                        currentLng
                                                )
                                        trafficFlow = flow
                                        TrafficState.paceMps =
                                                flow?.currentSpeedKmh
                                                        ?.div(3.6)
                                                        ?.takeIf { it > 1.0 }
                                        delay(45_000)
                                }
                        } finally {
                                // Leaving the Map tab must not leave the service using the last
                                // road's flow speed for the rest of the trip.
                                trafficFlow = null
                                TrafficState.paceMps = null
                        }
                } else {
                        trafficFlow = null
                        TrafficState.paceMps = null
                }
        }

        // Cleanup: remove route from map and reset camera when this composable leaves composition
        DisposableEffect(Unit) {
                onDispose {
                        mapInstance?.getStyle { style ->
                                try {
                                        style.removeLayer("route-casing")
                                        style.removeLayer("route-layer")
                                } catch (_: Exception) {}
                                try {
                                        style.removeSource("route-source")
                                } catch (_: Exception) {}
                                try {
                                        style.removeLayer("stops-marker")
                                } catch (_: Exception) {}
                                try {
                                        style.removeSource("stops-source")
                                } catch (_: Exception) {}
                        }
                        mapInstance?.setPadding(0, 0, 0, 0)
                        routeSource = null
                }
        }

        Box(modifier = Modifier.fillMaxSize()) {
                // === THE MAP ===
                LocationAwareMap(
                        currentStyleIndex = selectedStyleIndex,
                        modifier = Modifier.fillMaxSize(),
                        followUser = screenState != MapScreenState.NAVIGATING,
                        travelMode = selectedProfile,
                        hidePuck = true,
                        onMapReady = { map ->
                                mapInstance = map

                                // Tap a shop dot → open its detail sheet.
                                map.addOnMapClickListener { latLng ->
                                        val point = map.projection.toScreenLocation(latLng)
                                        val cluster =
                                                map.queryRenderedFeatures(
                                                        point,
                                                        "shops-cluster"
                                                )
                                        if (cluster.isNotEmpty()) {
                                                map.animateCamera(
                                                        CameraUpdateFactory.newLatLngZoom(
                                                                latLng,
                                                                map.cameraPosition.zoom + 2.0
                                                        ),
                                                        300
                                                )
                                                return@addOnMapClickListener true
                                        }

                                        val shopId =
                                                map.queryRenderedFeatures(point, "shops-symbol")
                                                        .firstOrNull()
                                                        ?.properties()
                                                        ?.get("shopId")
                                                        ?.asString
                                        val shop =
                                                shopId?.let { id ->
                                                        shopsViewModel.shops.value.firstOrNull {
                                                                it.id == id
                                                        }
                                                }
                                        if (shop != null) {
                                                shopsViewModel.select(shop)
                                                return@addOnMapClickListener true
                                        }

                                        // Overture places from the local vector style.
                                        val place =
                                                map.queryRenderedFeatures(point, "places")
                                                        .firstOrNull()
                                        val placeName =
                                                place?.properties()?.get("name")?.asString
                                        if (!placeName.isNullOrBlank()) {
                                                val props = place.properties()
                                                val latLng = map.projection.fromScreenLocation(point)
                                                val type =
                                                        props?.get("bcat")?.asString
                                                                ?: props?.get("cat")?.asString
                                                                        ?: "place"
                                                shopsViewModel.select(
                                                        Shop(
                                                                id = "overture:$placeName:${latLng.latitude}:${latLng.longitude}",
                                                                name = placeName,
                                                                category =
                                                                        ShopCategory.fromOfflineType(type),
                                                                lat = latLng.latitude,
                                                                lon = latLng.longitude,
                                                                address =
                                                                        props?.get("addr")?.asString
                                                                                ?.ifBlank { null },
                                                                phone =
                                                                        props?.get("phone")?.asString
                                                                                ?.ifBlank { null },
                                                                website =
                                                                        props?.get("website")?.asString
                                                                                ?.ifBlank { null }
                                                        )
                                                )
                                                return@addOnMapClickListener true
                                        }
                                        false
                                }
                                // Long-press drops a pin that can be used as a destination.
                                map.addOnMapLongClickListener { latLng ->
                                        if (screenState == MapScreenState.IDLE) {
                                                droppedPin = latLng
                                                map.animateCamera(
                                                        CameraUpdateFactory.newLatLng(latLng),
                                                        300
                                                )
                                                true
                                        } else {
                                                false
                                        }
                                }
                                // Center on Pakistan, the user, or an active restored trip.
                                val restored = NavigationSession.snapshot()
                                if (restored.active &&
                                                (restored.lat != 0.0 || restored.lng != 0.0)
                                ) {
                                        map.setPadding(0, 400, 0, 0)
                                        map.animateCamera(
                                                CameraUpdateFactory.newCameraPosition(
                                                        CameraPosition.Builder()
                                                                .target(
                                                                        LatLng(
                                                                                restored.lat,
                                                                                restored.lng
                                                                        )
                                                                )
                                                                .zoom(17.0)
                                                                .tilt(55.0)
                                                                .bearing(restored.bearing)
                                                                .build()
                                                ),
                                                900
                                        )
                                } else {
                                        map.animateCamera(
                                                CameraUpdateFactory.newLatLngZoom(
                                                        LatLng(currentLat, currentLng),
                                                        if (hasLocation) 14.0 else 5.0
                                                ),
                                                1000
                                        )
                                }
                                // Detect user panning to show re-center button
                                 map.addOnCameraMoveStartedListener { reason ->
                                         if (reason ==
                                                         MapLibreMap.OnCameraMoveStartedListener
                                                                 .REASON_API_GESTURE &&
                                                         screenState == MapScreenState.NAVIGATING
                                         ) {
                                                 isUserPanMode = true
                                                 lastPanTime = System.currentTimeMillis()
                                         }
                                 }
                         }
                 )

                // === OSM ATTRIBUTION (required when shop data is drawn) ===
                if (visibleShops.isNotEmpty() &&
                                screenState != MapScreenState.NAVIGATING) {
                        Surface(
                                modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp),
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.82f)
                        ) {
                                Text(
                                        text = "© OpenStreetMap",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier =
                                                Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                        }
                }

                // Dropped-pin action card (long-press anywhere on the idle map).
                droppedPin?.let { pin ->
                        if (screenState == MapScreenState.IDLE && !inPip) {
                                Card(
                                        modifier =
                                                Modifier.align(Alignment.BottomCenter)
                                                        .padding(
                                                                start = 16.dp,
                                                                end = 16.dp,
                                                                bottom = 176.dp
                                                        ),
                                        shape = RoundedCornerShape(20.dp),
                                        colors =
                                                CardDefaults.cardColors(
                                                        containerColor =
                                                                MaterialTheme.colorScheme
                                                                        .surface
                                                )
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 14.dp,
                                                                        vertical = 10.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        Icons.Default.Place,
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                                "Dropped pin",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .titleSmall,
                                                                fontWeight = FontWeight.Bold
                                                        )
                                                        Text(
                                                                "%.5f, %.5f".format(
                                                                        pin.latitude,
                                                                        pin.longitude
                                                                ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                                TextButton(
                                                        onClick = {
                                                                selectedDestination =
                                                                        GeoSearchResult(
                                                                                name = "Dropped pin",
                                                                                address =
                                                                                        "%.5f, %.5f"
                                                                                                .format(
                                                                                                        pin.latitude,
                                                                                                        pin.longitude
                                                                                                ),
                                                                                lat = pin.latitude,
                                                                                lng = pin.longitude,
                                                                                type = "pin"
                                                                        )
                                                                routeStops = emptyList()
                                                                searchPurpose =
                                                                        SearchPurpose.DESTINATION
                                                                searchQuery = "Dropped pin"
                                                                screenState = MapScreenState.IDLE
                                                                if (NavigationSession.isActive()) {
                                                                        NavigationSession.stop(context)
                                                                }
                                                                routeSimulator.stopSimulation()
                                                                planTrip(selectedProfile)
                                                                droppedPin = null
                                                        }
                                                ) { Text("Route") }
                                                IconButton(
                                                        onClick = { droppedPin = null }
                                                ) {
                                                        Icon(
                                                                Icons.Default.Close,
                                                                contentDescription = "Clear pin"
                                                        )
                                                }
                                        }
                                }
                        }
                }

                // === GOOGLE MAPS-STYLE SEARCH BAR (Top) ===
                AnimatedVisibility(
                        visible =
                                (screenState == MapScreenState.IDLE ||
                                        screenState == MapScreenState.SEARCHING) && !inPip,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut(),
                        modifier =
                                Modifier.align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 2.dp)
                ) {
                        GoogleSearchBar(
                                query = searchQuery,
                                onQueryChange = {
                                        searchQuery = it
                                        screenState = MapScreenState.SEARCHING
                                },
                                isSearching = screenState == MapScreenState.SEARCHING,
                                onClear = {
                                        searchQuery = ""
                                        if (searchPurpose == SearchPurpose.DESTINATION) {
                                                screenState = MapScreenState.IDLE
                                        }
                                },
                                onBack = {
                                        if (searchPurpose == SearchPurpose.STOP &&
                                                        selectedDestination != null
                                        ) {
                                                searchPurpose = SearchPurpose.DESTINATION
                                                searchQuery = selectedDestination?.name ?: ""
                                                screenState = MapScreenState.ROUTE_PREVIEW
                                        } else {
                                                searchQuery = ""
                                                screenState = MapScreenState.IDLE
                                        }
                                },
                                onClick = { screenState = MapScreenState.SEARCHING },
                                placeholder =
                                        if (searchPurpose == SearchPurpose.STOP) "Add a stop"
                                        else "Search here"
                        )
                }

                // === SHOP CATEGORY CHIPS ===
                AnimatedVisibility(
                        visible = screenState == MapScreenState.IDLE && mapShops.isNotEmpty() && !inPip,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier =
                                Modifier.align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                        .padding(top = 74.dp)
                ) {
                        ShopFilterRow(
                                shops = mapShops,
                                selected = shopFilter,
                                onSelect = { shopFilter = it }
                        )
                }

                // === SEARCH RESULTS (Below search bar) ===
                AnimatedVisibility(
                        visible = screenState == MapScreenState.SEARCHING && !inPip,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically(),
                        modifier =
                                Modifier.align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                        .padding(top = 72.dp, start = 16.dp, end = 16.dp)
                ) {
                        SearchResultsCard(
                                query = searchQuery,
                                onResultClick = { result ->
                                        if (searchPurpose == SearchPurpose.STOP) {
                                                addStop(result)
                                                searchPurpose = SearchPurpose.DESTINATION
                                                searchQuery = ""
                                                screenState = MapScreenState.ROUTE_PREVIEW
                                        } else {
                                                // Google Maps opens the place first. The user
                                                // chooses Directions from the action sheet.
                                                selectedPlace = result
                                                selectedDestination = result
                                                droppedPin = null
                                                searchQuery = result.name
                                                screenState = MapScreenState.IDLE
                                                shopsViewModel.clearSelection()
                                                mapInstance?.animateCamera(
                                                        CameraUpdateFactory.newLatLngZoom(
                                                                LatLng(result.lat, result.lng),
                                                                16.5
                                                        ),
                                                        500
                                                )
                                        }
                                }
                        )
                }

                // === QUICK ACTION BUTTONS (Google Maps style - bottom left) ===
                AnimatedVisibility(
                        visible = screenState == MapScreenState.IDLE && !inPip,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier =
                                Modifier.align(Alignment.BottomStart)
                                        .padding(16.dp)
                                        .padding(bottom = 80.dp)
                ) {
                        FloatingActionButton(
                                onClick = { showVehiclePicker = true },
                                containerColor = MaterialTheme.colorScheme.surface,
                                contentColor = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(48.dp)
                        ) {
                                Icon(
                                        Icons.Default.DirectionsCar,
                                        "Change vehicle icon",
                                        modifier = Modifier.size(22.dp)
                                )
                        }
                }

                // === MAP CONTROLS (Right side - Google Maps style) ===
                if (!inPip) {
                Column(
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                        // Compass (appears when rotated)
                        FloatingActionButton(
                                onClick = {
                                        mapInstance?.animateCamera(
                                                CameraUpdateFactory.bearingTo(0.0),
                                                500
                                        )
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(44.dp)
                        ) { Icon(Icons.Default.Explore, "North", modifier = Modifier.size(22.dp)) }

                        // My Location
                        FloatingActionButton(
                                onClick = {
                                        if (hasLocation) {
                                                mapInstance?.animateCamera(
                                                        CameraUpdateFactory.newLatLngZoom(
                                                                LatLng(currentLat, currentLng),
                                                                16.0
                                                        ),
                                                        500
                                                )
                                        }
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(48.dp)
                        ) {
                                Icon(
                                        Icons.Default.MyLocation,
                                        "My Location",
                                        tint =
                                                if (hasLocation) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        // Re-center after the user pans away. Kept below the normal map
                        // controls so it never covers the compass/location buttons.
                        AnimatedVisibility(
                                visible =
                                        isUserPanMode &&
                                                screenState == MapScreenState.NAVIGATING
                        ) {
                                FloatingActionButton(
                                        onClick = {
                                                isUserPanMode = false
                                                mapInstance?.let { map ->
                                                        CameraTransitionManager
                                                                .recenterOnPosition(
                                                                        map,
                                                                        LatLng(
                                                                                currentLat,
                                                                                currentLng
                                                                        ),
                                                                        currentBearing
                                                                )
                                                }
                                        },
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = Color.White,
                                        modifier = Modifier.size(48.dp)
                                ) {
                                        Icon(
                                                Icons.Default.MyLocation,
                                                "Re-center",
                                                modifier = Modifier.size(22.dp)
                                        )
                                }
                        }
                }
                }

                // === FLOOD LEGEND (bottom-left, above the car button) ===
                AnimatedVisibility(
                        visible = activeFloodSector != null,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier =
                                Modifier.align(Alignment.BottomStart)
                                        .padding(start = 16.dp, bottom = 110.dp)
                ) {
                        activeFloodSector?.let { sector ->
                                FloodLegend(
                                        sector = sector,
                                        onClose = { activeFloodSector = null }
                                )
                        }
                }
        }

        // === ROUTE PREVIEW CARD (Bottom - shows when route calculated) ===
        // A route can live in routeResult (car/moto), walkRouteResult or bikeRouteResult.
        // Do not require routeResult to be non-null: walking/biking used to hide the whole card,
        // which also hid the travel-mode choices for the next trip.
        val previewRoute =
                activeRouteFor(selectedProfile) ?: routeResult ?: walkRouteResult ?: bikeRouteResult
        previewRoute?.let {
                Box(modifier = Modifier.fillMaxSize()) {
                        AnimatedVisibility(
                                visible = screenState == MapScreenState.ROUTE_PREVIEW,
                                enter = slideInVertically { it },
                                exit = slideOutVertically { it },
                                modifier =
                                        Modifier.align(Alignment.BottomCenter)
                                                .padding(bottom = 30.dp)
                        ) {
                                RoutePreviewCard(
                                        destination = selectedDestination,
                                        routeResult = routeResult,
                                        selectedProfile = selectedProfile,
                                        walkRouteResult = walkRouteResult,
                                        bikeRouteResult = bikeRouteResult,
                                        isLoadingWalk = isLoadingWalk,
                                        isLoadingRoute = isLoadingRoute,
                                        canStart =
                                                activeRouteFor(selectedProfile)
                                                        ?.coordinates
                                                        ?.size
                                                        ?.let { it >= 2 } == true,
                                        routeSource = activeRouteSource,
                                        avoidedHazardCount = avoidedHazardCount,
                                        hazardWarning = hazardWarning,
                                        onAvoidHazard = { planTrip(selectedProfile) },
                                        onProfileChange = { profile -> planTrip(profile) },
                                        stops = routeStops,
                                        onAddStop = {
                                                stopPickerCategory = null
                                                showStopPicker = true
                                        },
                                        onRemoveStop = { removeStop(it) },
                                        onQuickStop = { category ->
                                                stopPickerCategory = category
                                                showStopPicker = true
                                        },
                                        onReorderStops = { newStops ->
                                                routeStops = newStops
                                                planTrip(selectedProfile)
                                        },
                                        alternatives =
                                                if (selectedProfile == "driving-car" ||
                                                                isMotorcycleMode
                                                )
                                                        routeAlternatives
                                                else emptyList(),
                                        selectedAlternativeIndex = selectedAlternativeIndex,
                                        onAlternativeSelected = { index ->
                                                routeAlternatives.getOrNull(index)?.let {
                                                        alternative ->
                                                        routeResult = alternative
                                                        selectedAlternativeIndex = index
                                                        navigationInstructions =
                                                                alternative.instructions
                                                        currentInstructionIndex = 0
                                                        lastNavigationRoute = alternative
                                                        remainingDistance =
                                                                alternative.distanceMeters
                                                        remainingDuration =
                                                                alternative.durationSeconds
                                                }
                                        },
                                        routeOptions = routeOptions,
                                        onRouteOptionsChange = { options ->
                                                routeOptions = options
                                                planTrip(selectedProfile)
                                        },
                                        onStartNavigation = {
                                                if (isLoadingRoute || isLoadingWalk) return@RoutePreviewCard
                                                currentInstructionIndex = 0
                                                navigationNotice = null
                                                isRerouting = false
                                                wrongWayDetected = false
                                                lastAutoReroutedHazardId = null
                                                offRouteMonitor.reset()
                                                pendingSimulationRestart = false
                                                showSimControls = false
                                                currentSpeedMps = 0f
                                                speedLimitKmh = null
                                                MapSessionState.navigationSessionId++
                                                MapSessionState.routePlanGeneration++
                                                val activeRoute =
                                                        activeRouteFor(selectedProfile)
                                                                ?: return@RoutePreviewCard
                                                if (activeRoute.coordinates.size < 2) {
                                                        navigationNotice =
                                                                "You are already at the destination."
                                                        return@RoutePreviewCard
                                                }
                                                lastNavigationRoute = activeRoute
                                                navigationInstructions = activeRoute.instructions
                                                // Do not show the previous trip's 0 m / arrival
                                                // state while waiting for the service's first fix.
                                                remainingDistance =
                                                        activeRoute.distanceMeters
                                                remainingDuration =
                                                        activeRoute.durationSeconds
                                                distanceToNextTurn = 0.0

                                                if (!isSimulationMode) {
                                                        selectedDestination?.let { destination ->
                                                                NavigationSession.start(
                                                                        context = context,
                                                                        route = activeRoute,
                                                                        destination = destination,
                                                                        profile = selectedProfile,
                                                                        stops = routeStops,
                                                                        styleIndex = selectedStyleIndex,
                                                                        vehicleIconKey = vehicleIconKey
                                                                )
                                                        }
                                                }

                                                mapInstance?.let { map ->
                                                        val nextWaypoint =
                                                                activeRoute.coordinates.getOrNull(1)
                                                                        ?.let {
                                                                                LatLng(
                                                                                        it.first,
                                                                                        it.second
                                                                                )
                                                                        }
                                                        CameraTransitionManager
                                                                .executeNavigationStartTransition(
                                                                        map = map,
                                                                        currentPosition =
                                                                                LatLng(
                                                                                        currentLat,
                                                                                        currentLng
                                                                                ),
                                                                        nextWaypoint = nextWaypoint
                                                                )
                                                }

                                                if (isSimulationMode) {
                                                        routeSimulator.startSimulation(
                                                                coordinates = activeRoute.coordinates,
                                                                speedMps = selectedSimSpeed.speedMps,
                                                                profile = selectedProfile,
                                                                scope = MapSessionState.simulationScope
                                                        )
                                                }

                                                screenState = MapScreenState.NAVIGATING
                                        },
                                        onClose = {
                                                MapSessionState.routePlanGeneration++
                                                isLoadingRoute = false
                                                isLoadingWalk = false
                                                mapInstance?.getStyle { style ->
                                                        try {
                                                                style.removeLayer("route-casing")
                                                                style.removeLayer("route-layer")
                                                                style.removeSource("route-source")
                                                        } catch (_: Exception) {}
                                                        try {
                                                                style.removeLayer("stops-marker")
                                                                style.removeSource("stops-source")
                                                        } catch (_: Exception) {}
                                                }
                                                routeResult = null
                                                walkRouteResult = null
                                                bikeRouteResult = null
                                                routeStops = emptyList()
                                                selectedDestination = null
                                                selectedProfile = "driving-car"
                                                searchQuery = ""
                                                screenState = MapScreenState.IDLE
                                        },
                                        isSimulationMode = isSimulationMode,
                                        onSimulationModeChange = { isSimulationMode = it },
                                        selectedSimSpeed = selectedSimSpeed,
                                        onSimSpeedChange = { selectedSimSpeed = it }
                                )
                        }
                }
        }

        StopPickerSheet(
                visible = showStopPicker,
                shops = mapShops,
                route = activeRouteFor(selectedProfile),
                initialCategory = stopPickerCategory,
                onSelect = { shop ->
                        showStopPicker = false
                        addStop(
                                GeoSearchResult(
                                        name = shop.name,
                                        address = shop.address ?: "",
                                        lat = shop.lat,
                                        lng = shop.lon,
                                        type = "shop"
                                )
                        )
                },
                onSearch = {
                        showStopPicker = false
                        searchPurpose = SearchPurpose.STOP
                        searchQuery = ""
                        screenState = MapScreenState.SEARCHING
                },
                onDismiss = { showStopPicker = false }
        )

        // === NAVIGATION MODE (Full turn-by-turn) ===
        // Keep a stable reference: update lastNavigationRoute whenever routeResult becomes
        // non-null.
        // This prevents NavigationModeUI from crashing during the exit fade animation.
        LaunchedEffect(routeResult) { routeResult?.let { lastNavigationRoute = it } }

        AnimatedVisibility(
                visible = screenState == MapScreenState.NAVIGATING,
                enter = fadeIn(),
                exit = fadeOut()
        ) {
                // Safe: lastNavigationRoute is only set to non-null, never cleared while visible
                val safeRoute = lastNavigationRoute ?: return@AnimatedVisibility
                val trafficLabel =
                        trafficFlow?.let { flow ->
                                when {
                                        flow.roadClosure -> "Road closed"
                                        flow.isHeavy -> "Heavy traffic"
                                        flow.isSlow -> "Slow traffic"
                                        else -> "Traffic clear"
                                }
                        }
                val trafficColor =
                        when {
                                trafficFlow?.roadClosure == true ||
                                                trafficFlow?.isHeavy == true -> DangerRed
                                trafficFlow?.isSlow == true -> WarningOrange
                                else -> SafeGreen
                        }
                NavigationModeUI(
                        routeResult = safeRoute,
                        remainingDistance = remainingDistance,
                        remainingDuration = remainingDuration,
                        currentInstruction =
                                navigationInstructions.getOrNull(currentInstructionIndex) ?: "",
                        currentIndex = currentInstructionIndex,
                        totalInstructions = navigationInstructions.size,
                        speedMps = if (isSimulationMode) simSpeed else currentSpeedMps,
                        distanceToNextTurn = distanceToNextTurn,
                        speedLimitKmh = speedLimitKmh,
                        speedUnit = speedUnit,
                        onToggleSpeedUnit = {
                                scope.launch {
                                        preferencesManager.setSpeedUnit(
                                                if (speedUnit == SpeedUnit.KMH) SpeedUnit.MPH
                                                else SpeedUnit.KMH
                                        )
                                }
                        },
                        trafficLabel = trafficLabel,
                        trafficColor = trafficColor,
                        isInPip = inPip,
                        navigationNotice = navigationNotice,
                        isRerouting = isRerouting,
                        wrongWay = wrongWayDetected,
                        onNext = {
                                if (currentInstructionIndex < navigationInstructions.size - 1) {
                                        currentInstructionIndex++
                                }
                        },
                        onExit = {
                                if (NavigationSession.isActive()) NavigationSession.stop(context)
                                // Stop simulation if running
                                routeSimulator.stopSimulation()
                                isSimulationMode = false
                                isRerouting = false
                                navigationNotice = null
                                wrongWayDetected = false
                                speedLimitKmh = null
                                offRouteMonitor.reset()
                                pendingSimulationRestart = false
                                routeStops = emptyList()
                                showSimControls = false
                                MapSessionState.navigationSessionId++
                                MapSessionState.routePlanGeneration++
                                isLoadingRoute = false
                                isLoadingWalk = false

                                // Fetch actual user location to prevent routing from stale simulation position
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
                                        val handleLocation = { locLat: Double, locLng: Double ->
                                                currentLat = locLat
                                                currentLng = locLng
                                                mapInstance?.let { map ->
                                                        // Force the blip icon itself to immediately return to user's real location
                                                        val snapLoc = android.location.Location("instant").apply {
                                                            latitude = locLat
                                                            longitude = locLng
                                                            time = System.currentTimeMillis()
                                                        }
                                                        map.locationComponent?.forceLocationUpdate(snapLoc)
                                                        CameraTransitionManager.exitNavigationMode(
                                                                map,
                                                                LatLng(currentLat, currentLng)
                                                        )
                                                }
                                        }

                                        fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                                if (location != null) {
                                                        handleLocation(location.latitude, location.longitude)
                                                } else {
                                                        fusedLocationClient.getCurrentLocation(
                                                                com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY, 
                                                                null
                                                        ).addOnSuccessListener { currLoc ->
                                                                currLoc?.let { handleLocation(it.latitude, it.longitude) }
                                                        }
                                                }
                                        }
                                } else {
                                        // Reset camera to normal top-down mode using current state if permissions fail
                                        mapInstance?.let { map ->
                                                CameraTransitionManager.exitNavigationMode(
                                                        map,
                                                        LatLng(currentLat, currentLng)
                                                )
                                        }
                                }

                                // Clear route from map
                                mapInstance?.getStyle { style ->
                                        try {
                                                style.removeLayer("route-casing")
                                                style.removeLayer("route-layer")
                                        } catch (_: Exception) {}
                                        try {
                                                style.removeSource("route-source")
                                        } catch (_: Exception) {}
                                }
                                routeSource = null
                                routeResult = null
                                walkRouteResult = null
                                bikeRouteResult = null
                                selectedDestination = null
                                searchQuery = ""
                                isUserPanMode = false
                                screenState = MapScreenState.IDLE
                        }
                )
        }
        // === SIMULATION CONTROLS (shown during navigation when sim active) ===
        Box(modifier = Modifier.fillMaxSize()) {
                // Collapsed state: a small chip so the navigation screen stays clean.
                AnimatedVisibility(
                        visible =
                                screenState == MapScreenState.NAVIGATING &&
                                        isSimulationMode &&
                                        !showSimControls &&
                                        !inPip,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier =
                                Modifier.align(Alignment.BottomStart)
                                        .padding(start = 8.dp, bottom = 240.dp)
                ) {
                        AssistChip(
                                onClick = { showSimControls = true },
                                label = {
                                        Text(
                                                "SIM",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                        )
                                },
                                leadingIcon = {
                                        Icon(
                                                Icons.Default.Science,
                                                null,
                                                modifier = Modifier.size(16.dp),
                                                tint = Sky
                                        )
                                },
                                modifier = Modifier.height(32.dp)
                        )
                }

                AnimatedVisibility(
                        visible =
                                screenState == MapScreenState.NAVIGATING &&
                                        isSimulationMode &&
                                        showSimControls &&
                                        !inPip,
                        enter = fadeIn() + scaleIn(initialScale = 0.95f),
                        exit = fadeOut() + scaleOut(targetScale = 0.95f),
                        modifier =
                                Modifier.align(Alignment.BottomStart)
                                        .padding(start = 8.dp, bottom = 240.dp)
                ) {
                        Card(
                                shape = RoundedCornerShape(12.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor =
                                                        MaterialTheme.colorScheme.surface.copy(
                                                                alpha = 0.95f
                                                        )
                                        ),
                                elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                                Column(
                                        modifier = Modifier.padding(12.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                                Icon(
                                                        Icons.Default.Science,
                                                        null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = Sky
                                                )
                                                Text(
                                                        "SIM",
                                                        style =
                                                                MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Bold
                                                )
                                                Icon(
                                                        Icons.Default.Close,
                                                        "Hide simulation controls",
                                                        modifier =
                                                                Modifier.size(16.dp)
                                                                        .clickable {
                                                                                showSimControls =
                                                                                        false
                                                                        },
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }

                                        // Speed buttons
                                        SimulationSpeed.entries.forEach { speed ->
                                                FilterChip(
                                                        selected = selectedSimSpeed == speed,
                                                        onClick = {
                                                                selectedSimSpeed = speed
                                                                routeSimulator.setSpeed(
                                                                        speed.speedMps
                                                                )
                                                        },
                                                        label = {
                                                                Text(
                                                                        speed.displayName
                                                                                .split(" ")
                                                                                .first(),
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                )
                                                        },
                                                        modifier = Modifier.height(28.dp)
                                                )
                                        }

                                        HorizontalDivider(
                                                modifier =
                                                        Modifier.width(120.dp)
                                                                .padding(vertical = 4.dp)
                                        )

                                        // GPS Toggle
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                                Icon(
                                                        if (simGpsActive) Icons.Default.GpsFixed
                                                        else Icons.Default.GpsOff,
                                                        contentDescription = null,
                                                        modifier = Modifier.size(16.dp),
                                                        tint =
                                                                if (simGpsActive) Color(0xFF4CAF50)
                                                                else Color.Gray
                                                )
                                                Switch(
                                                        checked = simGpsActive,
                                                        onCheckedChange = {
                                                                routeSimulator.toggleGps()
                                                        },
                                                        modifier = Modifier.height(24.dp)
                                                )
                                        }

                                        // Test the live reroute path without driving: jump the
                                        // puck 200 m off the line, or turn it around on the
                                        // line. Both feed the same detector and router.
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                OutlinedButton(
                                                        onClick = { simulateDeviation() },
                                                        enabled = !isRerouting && !simArrived,
                                                        modifier = Modifier.height(30.dp),
                                                        contentPadding =
                                                                PaddingValues(
                                                                        horizontal = 10.dp,
                                                                        vertical = 0.dp
                                                                )
                                                ) {
                                                        Text(
                                                                "Deviate",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                fontWeight = FontWeight.SemiBold
                                                        )
                                                }
                                                OutlinedButton(
                                                        onClick = { simulateWrongWay() },
                                                        enabled = !isRerouting && !simArrived,
                                                        modifier = Modifier.height(30.dp),
                                                        contentPadding =
                                                                PaddingValues(
                                                                        horizontal = 10.dp,
                                                                        vertical = 0.dp
                                                                )
                                                ) {
                                                        Text(
                                                                "Wrong way",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                fontWeight = FontWeight.SemiBold
                                                        )
                                                }
                                        }

                                        // Progress indicator
                                        Text(
                                                "${(simProgress * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                        )

                                        if (simArrived) {
                                                Text(
                                                        "✓ Arrived!",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = Color(0xFF4CAF50),
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                }
                        }
                }
        }

        // === VEHICLE ICON PICKER (also reachable from the car button) ===
        if (showVehiclePicker) {
                ModalBottomSheet(
                        onDismissRequest = { showVehiclePicker = false },
                        containerColor = MaterialTheme.colorScheme.surface,
                        sheetState = sheetState
                ) {
                        VehicleIconPicker(
                                current = vehicleIcon,
                                onSelect = { icon ->
                                        scope.launch {
                                                preferencesManager.setVehicleIcon(
                                                        icon.storageKey
                                                )
                                                showVehiclePicker = false
                                        }
                                },
                                onClose = { showVehiclePicker = false }
                        )
                }
        }

        // === LOADING OVERLAY ===
        if (isLoadingRoute && !inPip) {
                Box(
                        modifier =
                                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)),
                        contentAlignment = Alignment.Center
                ) {
                        Card(
                                shape = RoundedCornerShape(16.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        )
                        ) {
                                Row(
                                        modifier = Modifier.padding(24.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                        Text("Finding route...")
                                }
                        }
                }
        }

        // The route card already states offline/online; only float this when the card is not up.
        if (screenState != MapScreenState.ROUTE_PREVIEW &&
                        screenState != MapScreenState.NAVIGATING &&
                        !inPip
        ) {
                offlineRouteDebugMessage?.let { message ->
                        Box(
                                modifier =
                                        Modifier.fillMaxSize()
                                                .padding(horizontal = 16.dp)
                                                .padding(bottom = 96.dp)
                        ) {
                        Card(
                                modifier = Modifier.align(Alignment.BottomCenter),
                                shape = RoundedCornerShape(12.dp),
                                colors =
                                        CardDefaults.cardColors(
                                                containerColor = MaterialTheme.colorScheme.surface
                                        )
                        ) {
                                Text(
                                        text = message,
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyMedium
                                )
                        }
                }
        }
        }

        // === EMERGENCY FAB (hidden behind the route card while previewing/navigating) ===
        if (screenState != MapScreenState.ROUTE_PREVIEW &&
                        screenState != MapScreenState.NAVIGATING &&
                        !inPip
        ) {
                Box(modifier = Modifier.fillMaxSize()) {
                        FloatingActionButton(
                                onClick = {
                                        val intent =
                                                Intent(Intent.ACTION_DIAL).apply {
                                                        data = Uri.parse("tel:1122")
                                                }
                                        context.startActivity(intent)
                                },
                                containerColor = DangerRed,
                                contentColor = Color.White,
                                modifier =
                                        Modifier.align(Alignment.BottomEnd)
                                                .padding(16.dp)
                                                .padding(bottom = 80.dp)
                        ) {
                                Icon(Icons.Default.Phone, "Emergency 1122")
                        }
                }
        }
}
// ============================================
// GOOGLE-STYLE SEARCH BAR
// ============================================

/** Plans a route on a background dispatcher; GraphHopper is fast but not free, and ORS may wait. */
private suspend fun planRouteOffMain(
        startLat: Double,
        startLng: Double,
        destinationLat: Double,
        destinationLng: Double,
        profile: String,
        hazards: List<HazardZone>,
        stops: List<GeoPoint> = emptyList(),
        preferOffline: Boolean = true,
        options: RouteOptions = RouteOptions()
): RoutePlan? =
        withContext(Dispatchers.Default) {
                try {
                        RouteRepository.planWithStops(
                                start = GeoPoint(startLat, startLng),
                                end = GeoPoint(destinationLat, destinationLng),
                                stops = stops,
                                profile = profile,
                                hazards = hazards,
                                preferOffline = preferOffline,
                                options = options
                        )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                } catch (_: Exception) {
                        null
                }
        }
