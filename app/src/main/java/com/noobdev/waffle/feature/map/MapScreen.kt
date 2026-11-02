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
import com.noobdev.waffle.feature.flood.loadSectorData
import com.noobdev.waffle.feature.flood.sectors
import com.noobdev.waffle.data.remote.*
import com.noobdev.waffle.core.navigation.CameraTransitionManager
import com.noobdev.waffle.core.navigation.GeoGeometry
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.core.navigation.HazardRegistry
import com.noobdev.waffle.core.navigation.HazardZone
import com.noobdev.waffle.core.navigation.NavigationManager
import com.noobdev.waffle.core.navigation.OffRouteMonitor
import com.noobdev.waffle.core.navigation.RouteSimulator
import com.noobdev.waffle.core.navigation.SimulationSpeed
import com.noobdev.waffle.core.routing.OfflineRoutingManager
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
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource

// ============================================
// GOOGLE MAPS-STYLE MAP SCREEN
// ============================================

enum class MapScreenState {
        IDLE, // Default - search bar visible
        SEARCHING, // Search field active
        ROUTE_PREVIEW, // Showing route before starting navigation
        NAVIGATING, // Turn-by-turn navigation active
        LAYERS // Layer/style picker open
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MapScreen(navController: NavController, currentStyleIndex: Int) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()

        val shopsViewModel: MapShopsViewModel = viewModel()
        val mapShops by shopsViewModel.shops.collectAsState()
        val selectedShop by shopsViewModel.selected.collectAsState()
        var shopFilter by remember { mutableStateOf<ShopCategory?>(null) }
        val visibleShops =
                remember(mapShops, shopFilter) {
                        if (shopFilter == null) mapShops
                        else mapShops.filter { it.category == shopFilter }
                }

        // Map instance
        var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

        // Direct reference to the route GeoJsonSource for fast in-place updates (vanishing trail)
        var routeSource by remember { mutableStateOf<GeoJsonSource?>(null) }

        // Screen state
        var screenState by remember { mutableStateOf(MapScreenState.IDLE) }
        
        // Track the current flood sector for cycling
        var currentFloodSectorIndex by remember { mutableStateOf(0) }

        // Map style - this will be passed up to change the map
        val darkUi = MaterialTheme.colorScheme.background.luminance() < 0.5f
        var selectedStyleIndex by remember(currentStyleIndex, darkUi) {
                mutableStateOf(
                        if (currentStyleIndex >= 0) currentStyleIndex
                        else autoStyleIndex(darkUi)
                )
        }

        // Location
        var currentLat by remember { mutableStateOf(30.3753) }
        var currentLng by remember { mutableStateOf(69.3451) }
        var hasLocation by remember { mutableStateOf(false) }

        // Search & Routing
        var searchQuery by remember { mutableStateOf("") }
        var selectedDestination by remember { mutableStateOf<GeoSearchResult?>(null) }
        var routeResult by remember { mutableStateOf<RouteResult?>(null) }
        var walkRouteResult by remember { mutableStateOf<RouteResult?>(null) }
        var selectedProfile by remember { mutableStateOf("driving-car") }
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
        // Incremented on every start/exit so a slow reroute from a previous trip cannot land in a
        // new navigation session.
        var navigationSessionId by remember { mutableStateOf(0) }

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
        var currentInstructionIndex by remember { mutableStateOf(0) }
        var navigationInstructions by remember { mutableStateOf<List<String>>(emptyList()) }
        var remainingDistance by remember { mutableStateOf(0.0) }
        var remainingDuration by remember { mutableStateOf(0.0) }
        var currentBearing by remember { mutableStateOf(0.0) }
        // Holds the last non-null route so NavigationModeUI never crashes during exit animation
        var lastNavigationRoute by remember { mutableStateOf<RouteResult?>(null) }

        // Camera & User Interaction
        var isUserPanMode by remember { mutableStateOf(false) }
        var lastPanTime by remember { mutableStateOf(0L) }

        // Simulation Mode
        val routeSimulator = remember { RouteSimulator() }
        var isSimulationMode by remember { mutableStateOf(false) }
        var selectedSimSpeed by remember { mutableStateOf(SimulationSpeed.CITY) }
        var showSimControls by remember { mutableStateOf(false) }

        // Collect simulation state
        val simPosition by routeSimulator.currentPosition.collectAsState()
        val simSpeed by routeSimulator.currentSpeed.collectAsState()
        val simBearing by routeSimulator.currentBearing.collectAsState()
        val simGpsActive by routeSimulator.isGpsActive.collectAsState()
        val simRunning by routeSimulator.isSimulationRunning.collectAsState()
        val simProgress by routeSimulator.progress.collectAsState()
        val simArrived by routeSimulator.hasArrived.collectAsState()
        val simSegmentIndex by routeSimulator.currentSegmentIndex.collectAsState()

        // Bottom sheet (unused but kept for future)
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

        /**
         * Shared "take me there" action: closes the sheet/search, plans with hazards avoided and
         * shows the route preview. Used by search results and by a tapped shop pin.
         */
        val planRouteTo: (GeoSearchResult) -> Unit = { result ->
                selectedDestination = result
                searchQuery = result.name
                screenState = MapScreenState.IDLE
                navigationNotice = null
                isRerouting = false
                offRouteMonitor.reset()
                scope.launch {
                        isLoadingRoute = true
                        offlineRouteDebugMessage = null
                        runCatching {
                                        RouteRepository.plan(
                                                start = GeoPoint(currentLat, currentLng),
                                                end = GeoPoint(result.lat, result.lng),
                                                profile = "car",
                                                hazards = HazardRegistry.active()
                                        )
                                }
                                .onSuccess { plan ->
                                        if (plan.success) {
                                                routeResult = plan.result
                                                activeRouteSource = plan.source
                                                avoidedHazardCount = plan.avoidedHazards.size
                                                walkRouteResult = null
                                                navigationInstructions = plan.result.instructions
                                                currentInstructionIndex = 0
                                                screenState = MapScreenState.ROUTE_PREVIEW
                                                offlineRouteDebugMessage =
                                                        buildString {
                                                                append(
                                                                        if (plan.source ==
                                                                                        RouteSource
                                                                                                .OFFLINE
                                                                                )
                                                                                "Offline route"
                                                                        else "Online route"
                                                                )
                                                                if (plan.avoidedHazards.isNotEmpty()) {
                                                                        append(
                                                                                " · avoiding ${plan.avoidedHazards.size} hazard(s)"
                                                                        )
                                                                }
                                                        }
                                        } else {
                                                offlineRouteDebugMessage =
                                                        "Route failed: ${plan.result.errorMessage}"
                                        }
                                }
                                .onFailure { error ->
                                        offlineRouteDebugMessage =
                                                "Route crashed: ${error.message}"
                                }
                        isLoadingRoute = false
                }
        }

        // Shops around the current fix: dots on the map, tap for the same sheet as Nearby.
        LaunchedEffect(hasLocation, currentLat, currentLng, screenState) {
                if (hasLocation && screenState != MapScreenState.NAVIGATING) {
                        shopsViewModel.loadAround(currentLat, currentLng)
                }
        }
        // Pins are neighbourhood furniture, not turn-by-turn furniture.
        MapShopMarkers(
                map = mapInstance,
                styleIndex = selectedStyleIndex,
                shops = if (screenState == MapScreenState.NAVIGATING) emptyList() else visibleShops
        )

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
         * Re-plan from the live fix after the driver leaves the line.
         *
         * The start point is the raw GPS fix, not the snapped route point: the whole point of this
         * route is to get back from where the car actually is. The resulting route replaces the
         * active one, so the next location callback snaps to the new geometry.
         */
        fun rerouteFromDeviation(rawLat: Double, rawLng: Double) {
                if (isRerouting) return
                val destination = selectedDestination ?: return
                val walk = selectedProfile == "foot-walking"
                val activeHazards = hazards
                val session = navigationSessionId

                isRerouting = true
                navigationNotice = "Off route · finding a new way…"

                scope.launch {
                        val plan =
                                planRouteOffMain(
                                        startLat = rawLat,
                                        startLng = rawLng,
                                        destinationLat = destination.lat,
                                        destinationLng = destination.lng,
                                        profile = if (walk) "foot" else "car",
                                        hazards = activeHazards
                                )

                        // The driver may have exited or started a new trip while we were planning.
                        if (session != navigationSessionId ||
                                        screenState != MapScreenState.NAVIGATING
                        ) {
                                return@launch
                        }

                        if (plan != null && plan.success) {
                                if (walk) {
                                        walkRouteResult = plan.result
                                } else {
                                        routeResult = plan.result
                                }
                                lastNavigationRoute = plan.result
                                navigationInstructions = plan.result.instructions
                                currentInstructionIndex = 0
                                activeRouteSource = plan.source
                                avoidedHazardCount = plan.avoidedHazards.size
                                remainingDistance = plan.result.distanceMeters
                                remainingDuration = plan.result.durationSeconds
                                navigationNotice =
                                        "New route · ${formatDistance(plan.result.distanceMeters)} · ${formatDuration(plan.result.durationSeconds)}"
                                offlineRouteDebugMessage = "Rerouted from current location"
                                plan.avoidedHazards.firstOrNull()?.let {
                                        lastAutoReroutedHazardId = it.id
                                }
                        } else {
                                navigationNotice = "Off route · no new route found"
                                offlineRouteDebugMessage =
                                        "Reroute failed: ${plan?.result?.errorMessage ?: "no route"}"
                        }

                        offRouteMonitor.markAttempt()
                        isRerouting = false
                }
        }

        // Live location tracking during navigation (real GPS or simulation)
        DisposableEffect(screenState, isSimulationMode) {
                if (screenState == MapScreenState.NAVIGATING) {
                        // Initialize remaining distance from route
                        val initialRoute =
                                if (selectedProfile == "driving-car") routeResult
                                else (walkRouteResult ?: routeResult)
                        remainingDistance = initialRoute?.distanceMeters ?: 0.0
                        remainingDuration = initialRoute?.durationSeconds ?: 0.0

                        if (isSimulationMode) {
                                // Simulation mode - use RouteSimulator
                                onDispose { routeSimulator.stopSimulation() }
                        } else {
                                // Real GPS mode
                                val locationRequest =
                                        LocationRequest.Builder(
                                                        Priority.PRIORITY_HIGH_ACCURACY,
                                                        1500L // Update every 1.5 seconds for
                                                        // smoother tracking
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
                                                                        if (selectedProfile ==
                                                                                        "foot-walking"
                                                                        )
                                                                                (walkRouteResult
                                                                                        ?: routeResult)
                                                                        else routeResult

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
                                                                // fix, never the snapped one.
                                                                if (activeRoute != null) {
                                                                        val decision =
                                                                                offRouteMonitor.onFix(
                                                                                        snapResult.distanceMeters,
                                                                                        location.accuracy
                                                                                )
                                                                        if (decision.shouldReroute) {
                                                                                rerouteFromDeviation(
                                                                                        location.latitude,
                                                                                        location.longitude
                                                                                )
                                                                        }
                                                                }

                                                                currentLat = snapResult.snappedLat
                                                                currentLng = snapResult.snappedLng
                                                                hasLocation = true

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
                                                                                else
                                                                                        max(
                                                                                                0,
                                                                                                nextStepIndex -
                                                                                                        1
                                                                                        )

                                                                        if (currentInstructionIndex !=
                                                                                        currentStepIndex
                                                                        ) {
                                                                                currentInstructionIndex =
                                                                                        currentStepIndex
                                                                        }
                                                                }

                                                                // Heading comes from the raw GPS
                                                                // movement, not the snapped points:
                                                                // after a reroute the snap can jump
                                                                // to a different road and would spin
                                                                // the camera.
                                                                val movedMeters =
                                                                        calculateDistance(
                                                                                previousLat,
                                                                                previousLng,
                                                                                location.latitude,
                                                                                location.longitude
                                                                        )
                                                                val newBearing =
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
                                                                currentBearing = newBearing
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
                                                                                val averageSpeed =
                                                                                        if (route
                                                                                                        .durationSeconds >
                                                                                                        0.0
                                                                                        )
                                                                                                (route.distanceMeters /
                                                                                                        route.durationSeconds)
                                                                                                        .coerceAtLeast(
                                                                                                                1.0
                                                                                                        )
                                                                                        else if (selectedProfile ==
                                                                                                        "driving-car"
                                                                                        )
                                                                                                11.0
                                                                                        else 1.4
                                                                                remainingDuration =
                                                                                        routeRemaining /
                                                                                                averageSpeed
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
                                                                                val speedMps =
                                                                                        if (selectedProfile ==
                                                                                                        "driving-car"
                                                                                        )
                                                                                                11.0
                                                                                        else 1.4
                                                                                remainingDuration =
                                                                                        toDestination /
                                                                                                speedMps
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
                                                                                                newBearing
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
                        }
                } else {
                        onDispose {}
                }
        }

        // Simulation state updates
        LaunchedEffect(simPosition, simBearing, simSegmentIndex) {
                if (isSimulationMode &&
                                simPosition != null &&
                                screenState == MapScreenState.NAVIGATING
                ) {
                        currentLat = simPosition!!.latitude
                        currentLng = simPosition!!.longitude
                        currentBearing = simBearing
                        hasLocation = true

                        // Update route source for vanishing trail – use direct source ref
                        val activeRoute =
                                if (selectedProfile == "driving-car") routeResult
                                else (walkRouteResult ?: routeResult)
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
                                        else max(0, nextStepIndex - 1)

                                if (currentInstructionIndex != currentStepIndex) {
                                        currentInstructionIndex = currentStepIndex
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

        // Get current location
        LaunchedEffect(Unit) {
                NavigationManager.initializeFloodZones()

                val hasPermission =
                        ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.ACCESS_FINE_LOCATION
                        ) == PackageManager.PERMISSION_GRANTED

                if (hasPermission) {
                        try {
                                val fusedLocationClient =
                                        LocationServices.getFusedLocationProviderClient(context)
                                fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                                        location?.let {
                                                currentLat = it.latitude
                                                currentLng = it.longitude
                                                hasLocation = true
                                        }
                                }
                        } catch (e: SecurityException) {}
                }
        }

        // Draw route when available – stores direct source reference for fast updates
        LaunchedEffect(routeResult, walkRouteResult, selectedProfile) {
                val drawnRoute =
                        if (selectedProfile == "foot-walking") (walkRouteResult ?: routeResult)
                        else routeResult
                drawnRoute?.let { result ->
                        if (result.success && result.coordinates.isNotEmpty()) {
                                mapInstance?.getStyle { style ->
                                        // Remove existing route layer/source if any
                                        try {
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
                                        val layer = LineLayer("route-layer", "route-source")
                                        layer.setProperties(
                                                lineColor(AndroidColor.parseColor("#F2A33C")),
                                                lineWidth(6f),
                                                lineOpacity(0.9f),
                                                lineCap(Property.LINE_CAP_ROUND),
                                                lineJoin(Property.LINE_JOIN_ROUND)
                                        )
                                        style.addLayer(layer)
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

        // === FOCUS PIN: arrive here from "Nearby" or an alert ===
        var focusTarget by remember { mutableStateOf<MapTarget?>(null) }
        LaunchedEffect(Unit) {
                focusTarget = MapFocus.consume()
                focusTarget?.let { target -> searchQuery = target.label }
        }
        LaunchedEffect(mapInstance, selectedStyleIndex, focusTarget) {
                val map = mapInstance ?: return@LaunchedEffect
                val target = focusTarget ?: return@LaunchedEffect
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

        // === HAZARD CHECK: does the active route cut through a danger zone? ===
        LaunchedEffect(hazards, routeResult, walkRouteResult, selectedProfile) {
                val activeRoute =
                        if (selectedProfile == "foot-walking") (walkRouteResult ?: routeResult)
                        else routeResult
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
                val isWalking = selectedProfile == "foot-walking"

                isRerouting = true
                navigationNotice = "Hazard ahead · finding a safe detour…"
                try {
                        val plan =
                                planRouteOffMain(
                                        startLat = currentLat,
                                        startLng = currentLng,
                                        destinationLat = destination.lat,
                                        destinationLng = destination.lng,
                                        profile = if (isWalking) "foot" else "car",
                                        hazards = hazards
                                )

                        if (plan != null && plan.success) {
                                if (isWalking) {
                                        walkRouteResult = plan.result
                                } else {
                                        routeResult = plan.result
                                }
                                lastNavigationRoute = plan.result
                                navigationInstructions = plan.result.instructions
                                currentInstructionIndex = 0
                                activeRouteSource = plan.source
                                avoidedHazardCount = plan.avoidedHazards.size
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

        // Cleanup: remove route from map and reset camera when this composable leaves composition
        DisposableEffect(Unit) {
                onDispose {
                        mapInstance?.getStyle { style ->
                                try {
                                        style.removeLayer("route-layer")
                                } catch (_: Exception) {}
                                try {
                                        style.removeSource("route-source")
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
                        onMapReady = { map ->
                                mapInstance = map

                                // Tap a shop dot → open its detail sheet.
                                map.addOnMapClickListener { latLng ->
                                        val point = map.projection.toScreenLocation(latLng)
                                        val shopId =
                                                map.queryRenderedFeatures(point, "shops-dot")
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
                                                true
                                        } else {
                                                false
                                        }
                                }
                                // Center on Pakistan or user location
                                map.animateCamera(
                                        CameraUpdateFactory.newLatLngZoom(
                                                LatLng(currentLat, currentLng),
                                                if (hasLocation) 14.0 else 5.0
                                        ),
                                        1000
                                )
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

                // === GOOGLE MAPS-STYLE SEARCH BAR (Top) ===
                AnimatedVisibility(
                        visible =
                                screenState == MapScreenState.IDLE ||
                                        screenState == MapScreenState.SEARCHING,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut(),
                        modifier =
                                Modifier.align(Alignment.TopCenter)
                                        .statusBarsPadding()
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
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
                                        screenState = MapScreenState.IDLE
                                },
                                onBack = {
                                        searchQuery = ""
                                        screenState = MapScreenState.IDLE
                                },
                                onVoice = { /* Voice search */},
                                onProfile = { /* Profile */},
                                onClick = { screenState = MapScreenState.SEARCHING }
                        )
                }

                // === SHOP CATEGORY CHIPS ===
                AnimatedVisibility(
                        visible = screenState == MapScreenState.IDLE && mapShops.isNotEmpty(),
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
                        visible = screenState == MapScreenState.SEARCHING,
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
                                        selectedDestination = result
                                        searchQuery = result.name
                                        screenState = MapScreenState.IDLE

                                        // Calculate route: offline first, online fallback, hazards avoided
                                        scope.launch {
                                                isLoadingRoute = true
                                                offlineRouteDebugMessage = null

                                                runCatching {
                                                        RouteRepository.plan(
                                                                start = GeoPoint(currentLat, currentLng),
                                                                end = GeoPoint(result.lat, result.lng),
                                                                profile = "car",
                                                                hazards = HazardRegistry.active()
                                                        )
                                                }
                                                        .onSuccess { plan ->
                                                                if (plan.success) {
                                                                        routeResult = plan.result
                                                                        activeRouteSource = plan.source
                                                                        avoidedHazardCount =
                                                                                plan.avoidedHazards.size
                                                                        walkRouteResult = null
                                                                        navigationInstructions =
                                                                                plan.result.instructions
                                                                        currentInstructionIndex = 0
                                                                        screenState =
                                                                                MapScreenState
                                                                                        .ROUTE_PREVIEW
                                                                        offlineRouteDebugMessage =
                                                                                buildString {
                                                                                        append(
                                                                                                if (plan.source ==
                                                                                                                RouteSource
                                                                                                                        .OFFLINE
                                                                                                )
                                                                                                        "Offline route"
                                                                                                else "Online route"
                                                                                        )
                                                                                        if (plan.avoidedHazards.isNotEmpty()) {
                                                                                                append(
                                                                                                        " · avoiding ${plan.avoidedHazards.size} hazard(s)"
                                                                                                )
                                                                                        }
                                                                                }
                                                                } else {
                                                                        offlineRouteDebugMessage =
                                                                                "Route failed: ${plan.result.errorMessage}"
                                                                }
                                                        }
                                                        .onFailure { error ->
                                                                offlineRouteDebugMessage =
                                                                        "Route crashed: ${error.message}"
                                                        }

                                                if (screenState != MapScreenState.ROUTE_PREVIEW) {
                                                        routeResult = null
                                                }
                                                isLoadingRoute = false
                                        }
                                }
                        )
                }

                // === QUICK ACTION BUTTONS (Google Maps style - bottom left) ===
                AnimatedVisibility(
                        visible = screenState == MapScreenState.IDLE,
                        enter = fadeIn(),
                        exit = fadeOut(),
                        modifier =
                                Modifier.align(Alignment.BottomStart)
                                        .padding(16.dp)
                                        .padding(bottom = 80.dp)
                ) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                FloatingActionButton(
                                        onClick = {
                                                scope.launch {
                                                        isLoadingRoute = true
                                                        offlineRouteDebugMessage = null

                                                        val startLat = if (hasLocation) currentLat else 33.6844
                                                        val startLng = if (hasLocation) currentLng else 73.0479

                                                        runCatching {
                                                                RouteRepository.plan(
                                                                        start = GeoPoint(startLat, startLng),
                                                                        end = GeoPoint(33.7294, 73.0931),
                                                                        profile = "car",
                                                                        hazards = HazardRegistry.active()
                                                                )
                                                        }
                                                                .onSuccess { plan ->
                                                                        if (!plan.success) {
                                                                                offlineRouteDebugMessage =
                                                                                        "Route failed: ${plan.result.errorMessage}"
                                                                        } else {
                                                                                routeResult = plan.result
                                                                                activeRouteSource = plan.source
                                                                                avoidedHazardCount =
                                                                                        plan.avoidedHazards.size
                                                                                walkRouteResult = null
                                                                                navigationInstructions =
                                                                                        plan.result.instructions
                                                                                currentInstructionIndex = 0
                                                                                screenState = MapScreenState.ROUTE_PREVIEW
                                                                                offlineRouteDebugMessage =
                                                                                        buildString {
                                                                                                append(
                                                                                                        if (plan.source ==
                                                                                                                        RouteSource
                                                                                                                                .OFFLINE
                                                                                                        )
                                                                                                                "Offline route"
                                                                                                        else "Online route"
                                                                                                )
                                                                                                if (plan.avoidedHazards.isNotEmpty()) {
                                                                                                        append(
                                                                                                                " · avoiding ${plan.avoidedHazards.size} hazard(s)"
                                                                                                        )
                                                                                                }
                                                                                        }
                                                                        }
                                                                }
                                                                .onFailure { error ->
                                                                        offlineRouteDebugMessage =
                                                                                "Route crashed: ${error.message}"
                                                                }
                                                        isLoadingRoute = false
                                                }
                                        },
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(48.dp)
                                ) {
                                        Icon(
                                                Icons.Default.CloudOff,
                                                "Test offline-safe route",
                                                modifier = Modifier.size(22.dp)
                                        )
                                }

                                // Layers button
                                FloatingActionButton(
                                        onClick = { screenState = MapScreenState.LAYERS },
                                        containerColor = MaterialTheme.colorScheme.surface,
                                        contentColor = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(48.dp)
                                ) {
                                        Icon(
                                                Icons.Default.Layers,
                                                "Layers",
                                                modifier = Modifier.size(22.dp)
                                        )
                                }
                        }
                }

                // === MAP CONTROLS (Right side - Google Maps style) ===
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

                        Spacer(modifier = Modifier.height(16.dp))

                        // Zoom controls
                        FloatingActionButton(
                                onClick = {
                                        mapInstance?.animateCamera(
                                                CameraUpdateFactory.zoomIn(),
                                                300
                                        )
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(44.dp)
                        ) { Icon(Icons.Default.Add, "Zoom In") }
                        FloatingActionButton(
                                onClick = {
                                        mapInstance?.animateCamera(
                                                CameraUpdateFactory.zoomOut(),
                                                300
                                        )
                                },
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.size(44.dp)
                        ) { Icon(Icons.Default.Remove, "Zoom Out") }

                        Spacer(modifier = Modifier.height(16.dp))

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
                }
        }

        // === ROUTE PREVIEW CARD (Bottom - shows when route calculated) ===
        routeResult?.let { currentRouteResult ->
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
                                        routeResult = currentRouteResult,
                                        selectedProfile = selectedProfile,
                                        walkRouteResult = walkRouteResult,
                                        isLoadingWalk = isLoadingWalk,
                                        routeSource = activeRouteSource,
                                        avoidedHazardCount = avoidedHazardCount,
                                        hazardWarning = hazardWarning,
                                        onAvoidHazard = {
                                                scope.launch {
                                                        isLoadingRoute = true
                                                        val destination = selectedDestination
                                                        val isWalking =
                                                                selectedProfile == "foot-walking"
                                                        val plan =
                                                                if (destination != null) {
                                                                        runCatching {
                                                                                        RouteRepository.plan(
                                                                                                start =
                                                                                                        GeoPoint(
                                                                                                                currentLat,
                                                                                                                currentLng
                                                                                                        ),
                                                                                                end =
                                                                                                        GeoPoint(
                                                                                                                destination.lat,
                                                                                                                destination.lng
                                                                                                        ),
                                                                                                profile =
                                                                                                        if (isWalking)
                                                                                                                "foot"
                                                                                                        else "car",
                                                                                                hazards = hazards
                                                                                        )
                                                                                }
                                                                                .getOrNull()
                                                                } else null
                                                        if (plan != null && plan.success) {
                                                                if (isWalking) {
                                                                        walkRouteResult = plan.result
                                                                } else {
                                                                        routeResult = plan.result
                                                                        navigationInstructions =
                                                                                plan.result
                                                                                        .instructions
                                                                        currentInstructionIndex = 0
                                                                }
                                                                activeRouteSource = plan.source
                                                                avoidedHazardCount =
                                                                        plan.avoidedHazards.size
                                                                offlineRouteDebugMessage =
                                                                        "Avoiding ${plan.avoidedHazards.size} hazard(s)"
                                                                hazardWarning?.let {
                                                                        lastAutoReroutedHazardId = it.id
                                                                }
                                                        } else {
                                                                offlineRouteDebugMessage =
                                                                        "Could not find a safe detour"
                                                        }
                                                        isLoadingRoute = false
                                                }
                                        },
                                        onProfileChange = { profile ->
                                                selectedProfile = profile
                                                if (profile == "foot-walking" &&
                                                                walkRouteResult == null &&
                                                                selectedDestination != null
                                                ) {
                                                        scope.launch {
                                                                isLoadingWalk = true
                                                                val walkPlan =
        runCatching {
                RouteRepository.plan(
                        start = GeoPoint(currentLat, currentLng),
                        end = GeoPoint(
                                selectedDestination!!.lat,
                                selectedDestination!!.lng
                        ),
                        profile = "foot",
                        hazards = HazardRegistry.active()
                )
        }.getOrNull()
walkRouteResult = if (walkPlan?.success == true) walkPlan.result else null
if (walkRouteResult == null) {
        offlineRouteDebugMessage =
                "Walking route failed: " + (walkPlan?.result?.errorMessage ?: "unknown error")
}
                                                                isLoadingWalk = false
                                                        }
                                                }
                                        },
                                        onStartNavigation = {
                                                currentInstructionIndex = 0
                                                navigationNotice = null
                                                isRerouting = false
                                                lastAutoReroutedHazardId = null
                                                offRouteMonitor.reset()
                                                navigationSessionId++
                                                val activeRoute =
                                                        if (selectedProfile == "driving-car") {
                                                                currentRouteResult
                                                        } else {
                                                                walkRouteResult ?: currentRouteResult
                                                        }
                                                navigationInstructions = activeRoute.instructions

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
                                                                scope = scope
                                                        )
                                                }

                                                screenState = MapScreenState.NAVIGATING
                                        },
                                        onClose = {
                                                mapInstance?.getStyle { style ->
                                                        try {
                                                                style.removeLayer("route-layer")
                                                                style.removeSource("route-source")
                                                        } catch (_: Exception) {}
                                                }
                                                routeResult = null
                                                walkRouteResult = null
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
                NavigationModeUI(
                        routeResult = safeRoute,
                        remainingDistance = remainingDistance,
                        remainingDuration = remainingDuration,
                        currentInstruction =
                                navigationInstructions.getOrNull(currentInstructionIndex) ?: "",
                        currentIndex = currentInstructionIndex,
                        totalInstructions = navigationInstructions.size,
                        navigationNotice = navigationNotice,
                        isRerouting = isRerouting,
                        onNext = {
                                if (currentInstructionIndex < navigationInstructions.size - 1) {
                                        currentInstructionIndex++
                                }
                        },
                        onExit = {
                                // Stop simulation if running
                                routeSimulator.stopSimulation()
                                isSimulationMode = false
                                isRerouting = false
                                navigationNotice = null
                                offRouteMonitor.reset()
                                navigationSessionId++

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
                                                style.removeLayer("route-layer")
                                        } catch (_: Exception) {}
                                        try {
                                                style.removeSource("route-source")
                                        } catch (_: Exception) {}
                                }
                                routeSource = null
                                routeResult = null
                                walkRouteResult = null
                                selectedDestination = null
                                searchQuery = ""
                                isUserPanMode = false
                                screenState = MapScreenState.IDLE
                        }
                )
        }
        // === SIMULATION CONTROLS (shown during navigation when sim active) ===
        Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                        visible = screenState == MapScreenState.NAVIGATING && isSimulationMode,
                        enter = slideInVertically { -it } + fadeIn(),
                        exit = slideOutVertically { -it } + fadeOut(),
                        modifier =
                                Modifier.align(Alignment.TopEnd)
                                        .statusBarsPadding()
                                        .padding(top = 100.dp, end = 8.dp)
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
                                        Text(
                                                "🧪 SIM",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold
                                        )

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

                                        Divider(modifier = Modifier.padding(vertical = 4.dp))

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

        // === RE-CENTER BUTTON (shown when user pans away during navigation) ===
        Box(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(
                        visible = isUserPanMode && screenState == MapScreenState.NAVIGATING,
                        enter = fadeIn() + scaleIn(),
                        exit = fadeOut() + scaleOut(),
                        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 16.dp)
                ) {
                        FloatingActionButton(
                                onClick = {
                                        isUserPanMode = false
                                        mapInstance?.let { map ->
                                                CameraTransitionManager.recenterOnPosition(
                                                        map,
                                                        LatLng(currentLat, currentLng),
                                                        currentBearing
                                                )
                                        }
                                },
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = Color.White
                        ) { Icon(Icons.Default.MyLocation, "Re-center") }
                }
        }

        // === LAYERS/STYLE PICKER ===
        if (screenState == MapScreenState.LAYERS) {
                ModalBottomSheet(
                        onDismissRequest = { screenState = MapScreenState.IDLE },
                        containerColor = MaterialTheme.colorScheme.surface,
                        sheetState = sheetState
                ) {
                        MapLayersSheet(
                                currentStyleIndex = selectedStyleIndex,
                                onStyleSelect = { index ->
                                        selectedStyleIndex = index
                                        screenState = MapScreenState.IDLE
                                },
                                onFloodHistory = {
                                        screenState = MapScreenState.IDLE
                                        // Load flood data
                                        if (sectors.isNotEmpty()) {
                                                val sector = sectors[currentFloodSectorIndex]
                                                mapInstance?.let { map ->
                                                        map.animateCamera(
                                                                CameraUpdateFactory.newLatLngZoom(
                                                                        LatLng(
                                                                                sector.lat,
                                                                                sector.lon
                                                                        ),
                                                                        12.0
                                                                ),
                                                                1000
                                                        )
                                                        loadSectorData(context, map, sector)
                                                }
                                                // Cycle to the next sector for the next click
                                                currentFloodSectorIndex = (currentFloodSectorIndex + 1) % sectors.size
                                        }
                                },
                                onClose = { screenState = MapScreenState.IDLE }
                        )
                }
        }

        // === LOADING OVERLAY ===
        if (isLoadingRoute) {
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
                        screenState != MapScreenState.NAVIGATING) {
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
                        screenState != MapScreenState.NAVIGATING) {
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
        hazards: List<HazardZone>
): RoutePlan? =
        withContext(Dispatchers.Default) {
                try {
                        RouteRepository.plan(
                                start = GeoPoint(startLat, startLng),
                                end = GeoPoint(destinationLat, destinationLng),
                                profile = profile,
                                hazards = hazards
                        )
                } catch (cancelled: kotlinx.coroutines.CancellationException) {
                        throw cancelled
                } catch (_: Exception) {
                        null
                }
        }
