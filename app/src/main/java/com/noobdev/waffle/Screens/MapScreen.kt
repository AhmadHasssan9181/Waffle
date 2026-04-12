package com.noobdev.waffle.Screens

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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.noobdev.waffle.Sketching.loadSectorData
import com.noobdev.waffle.Sketching.sectors
import com.noobdev.waffle.api.*
import com.noobdev.waffle.navigation.CameraTransitionManager
import com.noobdev.waffle.navigation.NavigationManager
import com.noobdev.waffle.navigation.RouteSimulator
import com.noobdev.waffle.navigation.SimulationSpeed
import com.noobdev.waffle.routing.OfflineRoutingManager
import com.noobdev.waffle.ui.theme.*
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
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

        // Map instance
        var mapInstance by remember { mutableStateOf<MapLibreMap?>(null) }

        // Direct reference to the route GeoJsonSource for fast in-place updates (vanishing trail)
        var routeSource by remember { mutableStateOf<GeoJsonSource?>(null) }

        // Screen state
        var screenState by remember { mutableStateOf(MapScreenState.IDLE) }
        
        // Track the current flood sector for cycling
        var currentFloodSectorIndex by remember { mutableStateOf(0) }

        // Map style - this will be passed up to change the map
        var selectedStyleIndex by remember { mutableStateOf(currentStyleIndex) }

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

        // GPS Location Client
        val fusedLocationClient = remember {
                LocationServices.getFusedLocationProviderClient(context)
        }

        // Live location tracking during navigation (real GPS or simulation)
        DisposableEffect(screenState, isSimulationMode) {
                if (screenState == MapScreenState.NAVIGATING) {
                        // Initialize remaining distance from route
                        val activeRoute =
                                if (selectedProfile == "driving-car") routeResult
                                else (walkRouteResult ?: routeResult)
                        remainingDistance = activeRoute?.distanceMeters ?: 0.0
                        remainingDuration = activeRoute?.durationSeconds ?: 0.0

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

                                                                // Calculate bearing from previous
                                                                // position
                                                                val newBearing =
                                                                        CameraTransitionManager
                                                                                .calculateBearing(
                                                                                        LatLng(
                                                                                                previousLat,
                                                                                                previousLng
                                                                                        ),
                                                                                        LatLng(
                                                                                                snapResult
                                                                                                        .snappedLat,
                                                                                                snapResult
                                                                                                        .snappedLng
                                                                                        )
                                                                                )
                                                                currentBearing = newBearing
                                                                previousLat = snapResult.snappedLat
                                                                previousLng = snapResult.snappedLng

                                                                // Calculate remaining distance
                                                                selectedDestination?.let { dest ->
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
        LaunchedEffect(routeResult) {
                routeResult?.let { result ->
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
                                                lineColor(AndroidColor.parseColor("#4285F4")),
                                                lineWidth(6f),
                                                lineOpacity(0.9f),
                                                lineCap(Property.LINE_CAP_ROUND),
                                                lineJoin(Property.LINE_JOIN_ROUND)
                                        )
                                        style.addLayer(layer)
                                        // Store reference for direct future updates
                                        routeSource = newSource

                                        // Animate camera to show full route bounds
                                        if (result.coordinates.size >= 2) {
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
                        onMapReady = { map ->
                                mapInstance = map
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

                                        // Calculate route
                                        scope.launch {
                                                isLoadingRoute = true
                                                offlineRouteDebugMessage = null

                                                val offlineEngine =
                                                        OfflineRoutingManager.getEngineOrNull()

                                                if (offlineEngine == null) {
                                                        offlineRouteDebugMessage =
                                                                "Offline engine is not ready"
                                                        isLoadingRoute = false
                                                        return@launch
                                                }

                                                runCatching {
                                                        offlineEngine.calculateRoute(
                                                                startLat = currentLat,
                                                                startLng = currentLng,
                                                                endLat = result.lat,
                                                                endLng = result.lng
                                                        )
                                                }
                                                        .onSuccess { response ->
                                                                val route =
                                                                        offlineResponseToRouteResult(
                                                                                response
                                                                        )

                                                                if (route != null) {
                                                                        routeResult = route
                                                                        walkRouteResult = null
                                                                        navigationInstructions =
                                                                                route.instructions
                                                                        currentInstructionIndex = 0
                                                                        screenState =
                                                                                MapScreenState
                                                                                        .ROUTE_PREVIEW
                                                                        offlineRouteDebugMessage =
                                                                                "Offline route rendered"
                                                                } else {
                                                                        offlineRouteDebugMessage =
                                                                                "Offline route failed"
                                                                }
                                                        }
                                                        .onFailure { error ->
                                                                offlineRouteDebugMessage =
                                                                        "Offline route crashed: ${error.message}"
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

                                                        val offlineEngine =
                                                                OfflineRoutingManager
                                                                        .getEngineOrNull()

                                                        if (offlineEngine == null) {
                                                                offlineRouteDebugMessage =
                                                                        "Offline engine is not ready"
                                                                isLoadingRoute = false
                                                                return@launch
                                                        }

                                                        runCatching {
                                                                offlineEngine.calculateRoute(
                                                                        startLat = 33.6844,
                                                                        startLng = 73.0479,
                                                                        endLat = 33.7294,
                                                                        endLng = 73.0931
                                                                )
                                                        }
                                                                .onSuccess { response ->
                                                                        val route =
                                                                                offlineResponseToRouteResult(
                                                                                        response
                                                                                )

                                                                        if (route == null) {
                                                                                offlineRouteDebugMessage =
                                                                                        "Offline route failed"
                                                                        } else {
                                                                                routeResult = route
                                                                                walkRouteResult = null
                                                                                navigationInstructions =
                                                                                        listOf(
                                                                                                "Offline route test"
                                                                                        )
                                                                                currentInstructionIndex =
                                                                                        0
                                                                                selectedDestination =
                                                                                        GeoSearchResult(
                                                                                                name =
                                                                                                        "Offline Test Route",
                                                                                                address =
                                                                                                        "GraphHopper test",
                                                                                                lat =
                                                                                                        33.7294,
                                                                                                lng =
                                                                                                        73.0931,
                                                                                                type =
                                                                                                        "debug"
                                                                                        )
                                                                                searchQuery =
                                                                                        "Offline Test Route"
                                                                                screenState =
                                                                                        MapScreenState
                                                                                                .ROUTE_PREVIEW
                                                                                offlineRouteDebugMessage =
                                                                                        "Offline route rendered"
                                                                        }
                                                                }
                                                                .onFailure { error ->
                                                                        offlineRouteDebugMessage =
                                                                                "Offline route crashed: ${error.message}"
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
                                                "Test Offline Route",
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
                                modifier = Modifier.align(Alignment.BottomCenter)
                        ) {
                                RoutePreviewCard(
                                        destination = selectedDestination,
                                        routeResult = currentRouteResult,
                                        selectedProfile = selectedProfile,
                                        walkRouteResult = walkRouteResult,
                                        isLoadingWalk = isLoadingWalk,
                                        onProfileChange = { profile ->
                                                selectedProfile = profile
                                                if (profile == "foot-walking" &&
                                                                walkRouteResult == null &&
                                                                selectedDestination != null
                                                ) {
                                                        scope.launch {
                                                                isLoadingWalk = true
                                                                walkRouteResult =
                                                                        getRoute(
                                                                                startLat = currentLat,
                                                                                startLng = currentLng,
                                                                                endLat =
                                                                                        selectedDestination!!
                                                                                                .lat,
                                                                                endLng =
                                                                                        selectedDestination!!
                                                                                                .lng,
                                                                                profile = "foot-walking"
                                                                        )
                                                                isLoadingWalk = false
                                                        }
                                                }
                                        },
                                        onStartNavigation = {
                                                currentInstructionIndex = 0
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
                        onNext = {
                                if (currentInstructionIndex < navigationInstructions.size - 1) {
                                        currentInstructionIndex++
                                }
                        },
                        onExit = {
                                // Stop simulation if running
                                routeSimulator.stopSimulation()
                                isSimulationMode = false

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

        offlineRouteDebugMessage?.let { message ->
                Box(
                        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 24.dp)
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

        // === EMERGENCY FAB (Always visible) ===
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
                                        .padding(
                                                bottom =
                                                        if (screenState ==
                                                                        MapScreenState.ROUTE_PREVIEW
                                                        )
                                                                140.dp
                                                        else 80.dp
                                        )
                ) { Icon(Icons.Default.Phone, "Emergency 1122") }
        }
}
// ============================================
// GOOGLE-STYLE SEARCH BAR
// ============================================

@Composable
fun GoogleSearchBar(
        query: String,
        onQueryChange: (String) -> Unit,
        isSearching: Boolean,
        onClear: () -> Unit,
        onBack: () -> Unit,
        onVoice: () -> Unit,
        onProfile: () -> Unit,
        onClick: () -> Unit
) {
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        LaunchedEffect(isSearching) {
                if (isSearching) {
                        focusRequester.requestFocus()
                }
        }

        Card(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Back/Menu button
                        IconButton(onClick = if (isSearching) onBack else onClick) {
                                Icon(
                                        imageVector =
                                                if (isSearching) Icons.Default.ArrowBack
                                                else Icons.Default.Menu,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        // Search field
                        if (isSearching) {
                                OutlinedTextField(
                                        value = query,
                                        onValueChange = onQueryChange,
                                        placeholder = { Text("Search here") },
                                        modifier =
                                                Modifier.weight(1f).focusRequester(focusRequester),
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = Color.Transparent,
                                                        unfocusedBorderColor = Color.Transparent
                                                ),
                                        singleLine = true,
                                        keyboardOptions =
                                                KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions =
                                                KeyboardActions(
                                                        onSearch = { focusManager.clearFocus() }
                                                )
                                )
                        } else {
                                Text(
                                        text = if (query.isEmpty()) "Search here" else query,
                                        modifier =
                                                Modifier.weight(1f)
                                                        .clickable { onClick() }
                                                        .padding(vertical = 12.dp),
                                        color =
                                                if (query.isEmpty())
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge
                                )
                        }

                        // Clear/Voice button
                        if (query.isNotEmpty()) {
                                IconButton(onClick = onClear) { Icon(Icons.Default.Clear, "Clear") }
                        } else {
                                IconButton(onClick = onVoice) {
                                        Icon(
                                                Icons.Default.Mic,
                                                "Voice",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                        }

                        // Profile/Avatar
                        IconButton(onClick = onProfile) {
                                Icon(
                                        Icons.Default.AccountCircle,
                                        "Profile",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(32.dp)
                                )
                        }
                }
        }
}

// ============================================
// SEARCH RESULTS CARD
// ============================================

@Composable
fun SearchResultsCard(query: String, onResultClick: (GeoSearchResult) -> Unit) {
        var suggestions by remember { mutableStateOf<List<GeoSearchResult>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }

        LaunchedEffect(query) {
                if (query.isBlank()) {
                        suggestions = getQuickCitySuggestions()
                } else if (query.length >= 2) {
                        isLoading = true
                        suggestions =
                                withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        OfflineGeocoder.search(query)
                                }
                        isLoading = false
                } else {
                        suggestions = emptyList()
                }
        }

        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
                Column {
                        // Quick actions row
                        if (query.isBlank()) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                        QuickActionChip(
                                                Icons.Default.LocalGasStation,
                                                "Petrol",
                                                Color(0xFFFF9800)
                                        )
                                        QuickActionChip(
                                                Icons.Default.Restaurant,
                                                "Food",
                                                Color(0xFF4CAF50)
                                        )
                                        QuickActionChip(
                                                Icons.Default.LocalHospital,
                                                "Hospital",
                                                DangerRed
                                        )
                                        QuickActionChip(
                                                Icons.Default.LocalPharmacy,
                                                "Pharmacy",
                                                Color(0xFF2196F3)
                                        )
                                }
                                HorizontalDivider()
                        }

                        if (isLoading) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                        } else if (suggestions.isEmpty() && query.isNotBlank()) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth().padding(
                                                        horizontal = 16.dp,
                                                        vertical = 24.dp
                                                )
                                ) {
                                        Text(
                                                text = "No offline matches found",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }
                        }

                        // Results
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(suggestions) { result ->
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .clickable { onResultClick(result) }
                                                                .padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 12.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                when (result.type) {
                                                                        "hospital" ->
                                                                                Icons.Default
                                                                                        .LocalHospital
                                                                        "emergency" ->
                                                                                Icons.Default
                                                                                        .Emergency
                                                                        "city" ->
                                                                                Icons.Default
                                                                                        .LocationCity
                                                                        else -> Icons.Default.Place
                                                                },
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                                result.name,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyLarge
                                                        )
                                                        Text(
                                                                result.address,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                                Icon(
                                                        Icons.Default.NorthEast,
                                                        null,
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun QuickActionChip(icon: ImageVector, label: String, color: Color) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {}
        ) {
                Box(
                        modifier =
                                Modifier.size(48.dp)
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
        }
}

// ============================================
// ROUTE PREVIEW CARD - with working Walk/Drive
// ============================================

@Composable
fun RoutePreviewCard(
        destination: GeoSearchResult?,
        routeResult: RouteResult,
        selectedProfile: String,
        walkRouteResult: RouteResult?,
        isLoadingWalk: Boolean,
        onProfileChange: (String) -> Unit,
        onStartNavigation: () -> Unit,
        onClose: () -> Unit,
        isSimulationMode: Boolean = false,
        onSimulationModeChange: (Boolean) -> Unit = {},
        selectedSimSpeed: SimulationSpeed = SimulationSpeed.CITY,
        onSimSpeedChange: (SimulationSpeed) -> Unit = {}
) {
        val isDriving = selectedProfile == "driving-car"
        val currentRoute = if (isDriving) routeResult else (walkRouteResult ?: routeResult)

        Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        // Header
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column {
                                        Text(
                                                formatDuration(currentRoute.durationSeconds),
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = Color(0xFF4285F4)
                                        )
                                        Text(
                                                "${formatDistance(currentRoute.distanceMeters)} • ${destination?.name ?: "Destination"}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Route options - WORKING Drive/Walk toggle
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                // Drive option
                                Surface(
                                        onClick = { onProfileChange("driving-car") },
                                        shape = RoundedCornerShape(20.dp),
                                        color =
                                                if (isDriving) Color(0xFF4285F4).copy(alpha = 0.1f)
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                        border =
                                                if (isDriving) BorderStroke(2.dp, Color(0xFF4285F4))
                                                else null
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 10.dp
                                                        ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                Icon(
                                                        Icons.Default.DirectionsCar,
                                                        null,
                                                        tint =
                                                                if (isDriving) Color(0xFF4285F4)
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                                Text(
                                                        formatDuration(routeResult.durationSeconds),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color =
                                                                if (isDriving) Color(0xFF4285F4)
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                )
                                        }
                                }

                                // Walk option
                                Surface(
                                        onClick = { onProfileChange("foot-walking") },
                                        shape = RoundedCornerShape(20.dp),
                                        color =
                                                if (!isDriving) Color(0xFF4285F4).copy(alpha = 0.1f)
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                        border =
                                                if (!isDriving)
                                                        BorderStroke(2.dp, Color(0xFF4285F4))
                                                else null
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 16.dp,
                                                                vertical = 10.dp
                                                        ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                                Icon(
                                                        Icons.Default.DirectionsWalk,
                                                        null,
                                                        tint =
                                                                if (!isDriving) Color(0xFF4285F4)
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                                if (isLoadingWalk) {
                                                        CircularProgressIndicator(
                                                                modifier = Modifier.size(16.dp),
                                                                strokeWidth = 2.dp
                                                        )
                                                } else if (walkRouteResult != null) {
                                                        Text(
                                                                formatDuration(
                                                                        walkRouteResult
                                                                                .durationSeconds
                                                                ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelLarge,
                                                                color =
                                                                        if (!isDriving)
                                                                                Color(0xFF4285F4)
                                                                        else
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                        )
                                                } else {
                                                        Text(
                                                                "Walk",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Simulation mode toggle
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        Icon(
                                                Icons.Default.Science,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint =
                                                        if (isSimulationMode) Color(0xFF9C27B0)
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                        )
                                        Text(
                                                "Test Mode",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color =
                                                        if (isSimulationMode) Color(0xFF9C27B0)
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                        )
                                }
                                Switch(
                                        checked = isSimulationMode,
                                        onCheckedChange = onSimulationModeChange,
                                        colors =
                                                SwitchDefaults.colors(
                                                        checkedThumbColor = Color(0xFF9C27B0),
                                                        checkedTrackColor =
                                                                Color(0xFF9C27B0).copy(alpha = 0.5f)
                                                )
                                )
                        }

                        // Speed selection (shown when simulation mode is on)
                        AnimatedVisibility(visible = isSimulationMode) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                        SimulationSpeed.entries.forEach { speed ->
                                                FilterChip(
                                                        selected = selectedSimSpeed == speed,
                                                        onClick = { onSimSpeedChange(speed) },
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
                                                        colors =
                                                                FilterChipDefaults.filterChipColors(
                                                                        selectedContainerColor =
                                                                                Color(0xFF9C27B0)
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.2f
                                                                                        ),
                                                                        selectedLabelColor =
                                                                                Color(0xFF9C27B0)
                                                                ),
                                                        modifier = Modifier.weight(1f)
                                                )
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Start button
                        Button(
                                onClick = onStartNavigation,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor =
                                                        if (isSimulationMode) Color(0xFF9C27B0)
                                                        else Color(0xFF4285F4)
                                        )
                        ) {
                                Icon(
                                        if (isSimulationMode) Icons.Default.PlayArrow
                                        else Icons.Default.Navigation,
                                        null,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        if (isSimulationMode) "Start Simulation" else "Start",
                                        style = MaterialTheme.typography.titleMedium
                                )
                        }
                }
        }
}

// ============================================
// NAVIGATION MODE UI
// ============================================

@Composable
fun NavigationModeUI(
        routeResult: RouteResult,
        remainingDistance: Double,
        remainingDuration: Double,
        currentInstruction: String,
        currentIndex: Int,
        totalInstructions: Int,
        onNext: () -> Unit,
        onExit: () -> Unit
) {
        Box(modifier = Modifier.fillMaxSize()) {
                // Top instruction bar (green like Google Maps)
                Card(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                        shape = RoundedCornerShape(0.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F9D58))
                ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Icon(
                                                imageVector = getDirectionIcon(currentInstruction),
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        currentInstruction.ifEmpty {
                                                                "Continue straight"
                                                        },
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = Color.White,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                        }
                                }
                        }
                }

                // Bottom bar
                Card(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                )
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column {
                                        Text(
                                                formatDuration(
                                                        if (remainingDuration > 0) remainingDuration
                                                        else routeResult.durationSeconds
                                                ),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                                "${formatDistance(
                                                        if (remainingDistance > 0) remainingDistance
                                                        else routeResult.distanceMeters
                                                )} remaining",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Next step button
                                        FilledTonalButton(
                                                onClick = onNext,
                                                enabled = currentIndex < totalInstructions - 1
                                        ) { Text("Next") }

                                        // Exit navigation
                                        FilledTonalButton(
                                                onClick = onExit,
                                                colors =
                                                        ButtonDefaults.filledTonalButtonColors(
                                                                containerColor =
                                                                        DangerRed.copy(
                                                                                alpha = 0.1f
                                                                        ),
                                                                contentColor = DangerRed
                                                        )
                                        ) { Text("Exit") }
                                }
                        }
                }
        }
}

fun getDirectionIcon(instruction: String): ImageVector {
        val lower = instruction.lowercase()
        return when {
                lower.contains("left") -> Icons.Default.KeyboardArrowLeft
                lower.contains("right") -> Icons.Default.KeyboardArrowRight
                lower.contains("arrive") -> Icons.Default.Flag
                lower.contains("destination") -> Icons.Default.Flag
                else -> Icons.AutoMirrored.Filled.ArrowForward
        }
}

// ============================================
// MAP LAYERS SHEET
// ============================================

@Composable
fun MapLayersSheet(
        currentStyleIndex: Int,
        onStyleSelect: (Int) -> Unit,
        onFloodHistory: () -> Unit,
        onClose: () -> Unit
) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                        "Map type",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Map styles grid (like Google Maps)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val styles = mapTilerStyles.keys.toList()
                        items(styles.size) { index ->
                                MapStyleCard(
                                        name = styles[index],
                                        isSelected = index == currentStyleIndex,
                                        onClick = { onStyleSelect(index) }
                                )
                        }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                        "Map details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Flood history toggle
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onFloodHistory() }
                                        .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Box(
                                modifier =
                                        Modifier.size(40.dp)
                                                .clip(CircleShape)
                                                .background(FloodColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.WaterDrop, null, tint = FloodColor) }
                        Column(modifier = Modifier.weight(1f)) {
                                Text("Flood History", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                        "Show EMSR838 Pakistan flood data",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                        Icon(Icons.Default.ChevronRight, null)
                }

                Spacer(modifier = Modifier.height(32.dp))
        }
}

@Composable
fun MapStyleCard(name: String, isSelected: Boolean, onClick: () -> Unit) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onClick() }
        ) {
                Box(
                        modifier =
                                Modifier.size(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color =
                                                        if (isSelected) Color(0xFF4285F4)
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant,
                                                shape = RoundedCornerShape(12.dp)
                                        )
                                        .background(
                                                when (name) {
                                                        "Dark" -> Color(0xFF1A1A2E)
                                                        "Satellite", "Hybrid" -> Color(0xFF2D4739)
                                                        else -> Color(0xFFE8F4EA)
                                                }
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        Icon(
                                imageVector =
                                        when (name) {
                                                "Satellite", "Hybrid" -> Icons.Default.Satellite
                                                "Dark" -> Icons.Default.DarkMode
                                                "Topo" -> Icons.Default.Terrain
                                                else -> Icons.Default.Map
                                        },
                                contentDescription = null,
                                tint =
                                        when (name) {
                                                "Dark" -> Color.White
                                                "Satellite", "Hybrid" -> Color.White
                                                else -> MaterialTheme.colorScheme.primary
                                        },
                                modifier = Modifier.size(28.dp)
                        )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color =
                                if (isSelected) Color(0xFF4285F4)
                                else MaterialTheme.colorScheme.onSurface
                )
        }
}

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Calculate distance between two coordinates using Haversine formula
 * @return distance in meters
 */
fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(lat1)) *
                                Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLng / 2) *
                                Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
}

/** Helper to update route source with vanishing trail */
fun updateRouteSource(map: MapLibreMap, coordinates: List<Pair<Double, Double>>, startIndex: Int) {
        map.getStyle { style ->
                if (coordinates.size <= startIndex) return@getStyle

                // Vanishing trail: only show coordinates from startIndex to end
                val remainingCoords = coordinates.subList(max(0, startIndex), coordinates.size)
                if (remainingCoords.isEmpty()) return@getStyle

                val coordsString =
                        remainingCoords.joinToString(",") { pair ->
                                "[${pair.second},${pair.first}]"
                        }
                val geoJson =
                        """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordsString]}}"""

                val source = style.getSourceAs<GeoJsonSource>("route-source")
                if (source != null) {
                        source.setGeoJson(geoJson)
                } else {
                        // Create if not exists
                        val newSource = GeoJsonSource("route-source", geoJson)
                        style.addSource(newSource)
                        val layer = LineLayer("route-layer", "route-source")
                        layer.setProperties(
                                lineColor(AndroidColor.parseColor("#4285F4")),
                                lineWidth(6f),
                                lineOpacity(0.9f),
                                lineCap(Property.LINE_CAP_ROUND),
                                lineJoin(Property.LINE_JOIN_ROUND)
                        )
                        style.addLayer(layer)
                }
        }
}

/**
 * Fast direct update of the route GeoJsonSource for vanishing trail effect. Unlike
 * updateRouteSource, this skips the async getStyle() callback by using the already-stored source
 * reference – crucial for smooth 1-second updates during navigation.
 */
fun trimRouteSource(
        source: GeoJsonSource?,
        coordinates: List<Pair<Double, Double>>,
        startIndex: Int,
        currentLatLng: LatLng? = null
) {
        if (source == null || coordinates.size <= startIndex) return
        
        val safeStartIndex = max(0, startIndex)
        val remainingCoords = if (safeStartIndex + 1 < coordinates.size) {
            coordinates.subList(safeStartIndex + 1, coordinates.size).toMutableList()
        } else {
            mutableListOf()
        }
        
        if (currentLatLng != null) {
            remainingCoords.add(0, Pair(currentLatLng.latitude, currentLatLng.longitude))
        } else {
            remainingCoords.add(0, coordinates[safeStartIndex])
        }

        if (remainingCoords.size < 2) return

        val coordsString = remainingCoords.joinToString(",") { "[${it.second},${it.first}]" }
        val geoJson =
                """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordsString]}}"""
        source.setGeoJson(geoJson)
}
