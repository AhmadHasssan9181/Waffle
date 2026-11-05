package com.noobdev.waffle.feature.map

import androidx.compose.runtime.mutableStateOf
import com.noobdev.waffle.core.navigation.RouteSimulator
import com.noobdev.waffle.core.navigation.SimulationSpeed
import com.noobdev.waffle.core.routing.RouteOptions
import com.noobdev.waffle.data.remote.GeoSearchResult
import com.noobdev.waffle.data.remote.RouteResult
import org.maplibre.android.geometry.LatLng
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Map state outlives a single Map tab composition. Switching to Home/Nearby and back
 * should not throw away a planned route, a running simulation, or the camera position.
 */
internal object MapSessionState {
    val screenState = mutableStateOf(MapScreenState.IDLE)
    val selectedProfile = mutableStateOf("driving-car")
    val selectedDestination = mutableStateOf<GeoSearchResult?>(null)
    val droppedPin = mutableStateOf<LatLng?>(null)
    val routeResult = mutableStateOf<RouteResult?>(null)
    val routeAlternatives = mutableStateOf<List<RouteResult>>(emptyList())
    val selectedAlternativeIndex = mutableStateOf(0)
    val routeOptions = mutableStateOf(RouteOptions())
    val walkRouteResult = mutableStateOf<RouteResult?>(null)
    val bikeRouteResult = mutableStateOf<RouteResult?>(null)
    val routeStops = mutableStateOf<List<GeoSearchResult>>(emptyList())
    val navigationInstructions = mutableStateOf<List<String>>(emptyList())
    val currentInstructionIndex = mutableStateOf(0)
    val remainingDistance = mutableStateOf(0.0)
    val remainingDuration = mutableStateOf(0.0)
    val distanceToNextTurn = mutableStateOf(0.0)
    val currentLat = mutableStateOf(30.3753)
    val currentLng = mutableStateOf(69.3451)
    val currentBearing = mutableStateOf(0.0)
    val currentSpeedMps = mutableStateOf(0f)
    val hasLocation = mutableStateOf(false)
    val isSimulationMode = mutableStateOf(false)
    val selectedSimSpeed = mutableStateOf(SimulationSpeed.VARIABLE)

    val routeSimulator = RouteSimulator()

    /** Keeps route planning alive while the Map tab is off-screen. */
    val routeScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    /** Newest route request; older ORS responses must not replace it. */
    var routePlanGeneration = 0

    /** Keeps a Test Mode walk/drive alive while the Map tab is off-screen. */
    val simulationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
}
