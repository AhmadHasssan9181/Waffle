package com.noobdev.waffle.navigation

import android.util.Log
import kotlin.math.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.maplibre.android.geometry.LatLng

/**
 * Route Simulator for testing navigation without actually driving.
 *
 * Features:
 * - Simulates movement along a route at configurable speeds
 * - Supports GPS cutoff simulation (tunnel mode)
 * - Variable speed profiles (slow city, fast highway, mixed)
 * - Dead reckoning when GPS is "cut"
 */
class RouteSimulator {

    companion object {
        private const val TAG = "RouteSimulator"

        // Speed presets (m/s)
        const val SPEED_WALKING = 1.4f // ~5 km/h
        const val SPEED_CITY = 11.0f // ~40 km/h
        const val SPEED_HIGHWAY = 28.0f // ~100 km/h
        const val SPEED_VARIABLE = -1f // Use auto-variable speed

        // Update interval
        const val UPDATE_INTERVAL_MS = 100L // 10 updates per second for smooth rendering
    }

    // Simulation state
    private val _currentPosition = MutableStateFlow<LatLng?>(null)
    val currentPosition: StateFlow<LatLng?> = _currentPosition.asStateFlow()

    private val _currentSpeed = MutableStateFlow(0f)
    val currentSpeed: StateFlow<Float> = _currentSpeed.asStateFlow()

    private val _currentBearing = MutableStateFlow(0.0)
    val currentBearing: StateFlow<Double> = _currentBearing.asStateFlow()

    private val _isGpsActive = MutableStateFlow(true)
    val isGpsActive: StateFlow<Boolean> = _isGpsActive.asStateFlow()

    private val _isSimulationRunning = MutableStateFlow(false)
    val isSimulationRunning: StateFlow<Boolean> = _isSimulationRunning.asStateFlow()

    private val _progress = MutableStateFlow(0f) // 0.0 to 1.0
    val progress: StateFlow<Float> = _progress.asStateFlow()

    private val _hasArrived = MutableStateFlow(false)
    val hasArrived: StateFlow<Boolean> = _hasArrived.asStateFlow()

    // Internal state
    private var simulationJob: Job? = null
    private var routeCoordinates: List<Pair<Double, Double>> = emptyList()

    private val _currentSegmentIndex = MutableStateFlow(0)
    val currentSegmentIndex: StateFlow<Int> = _currentSegmentIndex.asStateFlow()

    private var segmentProgress = 0.0 // 0.0 to 1.0 within segment
    private var targetSpeed = SPEED_CITY
    private var useVariableSpeed = false

    // Dead reckoning state
    private var lastKnownPosition: LatLng? = null
    private var lastKnownBearing: Double = 0.0
    private var deadReckoningDistance = 0.0

    /**
     * Start simulating movement along a route.
     *
     * @param coordinates Route coordinates as List of Pair(lat, lng)
     * @param speedMps Speed in meters per second, or SPEED_VARIABLE for auto
     * @param scope Coroutine scope for simulation
     */
    fun startSimulation(
            coordinates: List<Pair<Double, Double>>,
            speedMps: Float = SPEED_CITY,
            scope: CoroutineScope
    ) {
        if (coordinates.size < 2) {
            Log.e(TAG, "Route must have at least 2 points")
            return
        }

        stopSimulation()

        routeCoordinates = coordinates
        _currentSegmentIndex.value = 0
        segmentProgress = 0.0
        useVariableSpeed = speedMps == SPEED_VARIABLE
        targetSpeed = if (useVariableSpeed) SPEED_CITY else speedMps
        _hasArrived.value = false
        _isGpsActive.value = true

        val startPos = LatLng(coordinates[0].first, coordinates[0].second)
        _currentPosition.value = startPos
        lastKnownPosition = startPos

        Log.d(TAG, "Starting simulation: ${coordinates.size} points, speed=$speedMps m/s")

        _isSimulationRunning.value = true

        simulationJob =
                scope.launch {
                    while (isActive && _currentSegmentIndex.value < routeCoordinates.size - 1) {
                        delay(UPDATE_INTERVAL_MS)
                        updatePosition()
                    }

                    // Arrived at destination
                    _hasArrived.value = true
                    _isSimulationRunning.value = false
                    _progress.value = 1f
                    Log.d(TAG, "Simulation complete - arrived at destination")
                }
    }

    /** Stop the simulation. */
    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _isSimulationRunning.value = false
    }

    /**
     * Toggle GPS on/off to simulate tunnel/signal loss. When GPS is off, dead reckoning kicks in.
     */
    fun toggleGps() {
        val wasActive = _isGpsActive.value
        _isGpsActive.value = !wasActive

        if (!_isGpsActive.value) {
            // Entering "tunnel" - save state for dead reckoning
            lastKnownPosition = _currentPosition.value
            lastKnownBearing = _currentBearing.value
            deadReckoningDistance = 0.0
            Log.d(TAG, "GPS cut - entering dead reckoning mode")
        } else {
            Log.d(TAG, "GPS restored")
        }
    }

    /** Set GPS active state (for testing specific scenarios). */
    fun setGpsActive(active: Boolean) {
        if (_isGpsActive.value != active) {
            toggleGps()
        }
    }

    /** Change simulation speed on the fly. */
    fun setSpeed(speedMps: Float) {
        useVariableSpeed = speedMps == SPEED_VARIABLE
        if (!useVariableSpeed) {
            targetSpeed = speedMps
            _currentSpeed.value = speedMps
        }
    }

    /** Update position based on speed and current segment. */
    private fun updatePosition() {
        if (_currentSegmentIndex.value >= routeCoordinates.size - 1) return

        val p1 = routeCoordinates[_currentSegmentIndex.value]
        val p2 = routeCoordinates[_currentSegmentIndex.value + 1]

        // Calculate segment length
        val segmentLength =
                CameraTransitionManager.haversineDistance(p1.first, p1.second, p2.first, p2.second)

        // Auto-adjust speed for variable mode
        if (useVariableSpeed) {
            targetSpeed = calculateVariableSpeed(segmentLength)
        }
        _currentSpeed.value = targetSpeed

        // Calculate how far we move this tick
        val distanceThisTick = targetSpeed * (UPDATE_INTERVAL_MS / 1000.0)

        // Calculate new progress within segment
        val progressIncrement = distanceThisTick / segmentLength
        segmentProgress += progressIncrement

        // Check if we've completed this segment
        while (segmentProgress >= 1.0 && _currentSegmentIndex.value < routeCoordinates.size - 1) {
            segmentProgress -= 1.0
            _currentSegmentIndex.value++
            if (_currentSegmentIndex.value >= routeCoordinates.size - 1) {
                segmentProgress = 1.0
                break
            }
        }

        // Interpolate position
        val currentP1 = routeCoordinates[_currentSegmentIndex.value]
        val currentP2 =
                routeCoordinates[minOf(_currentSegmentIndex.value + 1, routeCoordinates.size - 1)]

        val newLat = currentP1.first + (currentP2.first - currentP1.first) * segmentProgress
        val newLng = currentP1.second + (currentP2.second - currentP1.second) * segmentProgress
        val newPosition = LatLng(newLat, newLng)

        // Calculate bearing
        val bearing =
                CameraTransitionManager.calculateBearing(
                        LatLng(currentP1.first, currentP1.second),
                        LatLng(currentP2.first, currentP2.second)
                )
        _currentBearing.value = bearing

        // Update position based on GPS state
        if (_isGpsActive.value) {
            _currentPosition.value = newPosition
            lastKnownPosition = newPosition
            lastKnownBearing = bearing
        } else {
            // Dead reckoning - estimate position from last known
            deadReckoningDistance += distanceThisTick
            val estimatedPosition =
                    projectPosition(lastKnownPosition!!, lastKnownBearing, deadReckoningDistance)
            _currentPosition.value = estimatedPosition
            Log.d(TAG, "Dead reckoning: ${deadReckoningDistance}m from last known")
        }

        // Update overall progress
        val totalSegments = routeCoordinates.size - 1
        _progress.value = (_currentSegmentIndex.value + segmentProgress.toFloat()) / totalSegments
    }

    /**
     * Calculate variable speed based on segment characteristics. Longer straight segments = highway
     * speed Short segments = city/turning speed
     */
    private fun calculateVariableSpeed(segmentLength: Double): Float {
        return when {
            segmentLength > 500 -> SPEED_HIGHWAY // Long segment = highway
            segmentLength > 100 -> SPEED_CITY // Medium = city
            else -> SPEED_WALKING * 3 // Short segment = slow/turning
        }
    }

    /** Project a position forward along a bearing. Used for dead reckoning when GPS is cut. */
    private fun projectPosition(
            start: LatLng,
            bearingDegrees: Double,
            distanceMeters: Double
    ): LatLng {
        val R = 6371000.0 // Earth radius in meters
        val lat1 = Math.toRadians(start.latitude)
        val lng1 = Math.toRadians(start.longitude)
        val bearing = Math.toRadians(bearingDegrees)
        val angularDistance = distanceMeters / R

        val lat2 =
                asin(
                        sin(lat1) * cos(angularDistance) +
                                cos(lat1) * sin(angularDistance) * cos(bearing)
                )

        val lng2 =
                lng1 +
                        atan2(
                                sin(bearing) * sin(angularDistance) * cos(lat1),
                                cos(angularDistance) - sin(lat1) * sin(lat2)
                        )

        return LatLng(Math.toDegrees(lat2), Math.toDegrees(lng2))
    }

    /** Get distance remaining to destination. */
    fun getRemainingDistance(): Double {
        if (routeCoordinates.isEmpty() || _currentSegmentIndex.value >= routeCoordinates.size - 1) {
            return 0.0
        }

        var remaining = 0.0

        // Current segment remaining
        val p1 = routeCoordinates[_currentSegmentIndex.value]
        val p2 = routeCoordinates[_currentSegmentIndex.value + 1]
        val currentSegmentDist =
                CameraTransitionManager.haversineDistance(p1.first, p1.second, p2.first, p2.second)
        remaining += currentSegmentDist * (1 - segmentProgress)

        // Add remaining segments
        for (i in (_currentSegmentIndex.value + 1) until routeCoordinates.size - 1) {
            val sp1 = routeCoordinates[i]
            val sp2 = routeCoordinates[i + 1]
            remaining +=
                    CameraTransitionManager.haversineDistance(
                            sp1.first,
                            sp1.second,
                            sp2.first,
                            sp2.second
                    )
        }

        return remaining
    }

    /** Get estimated time remaining based on current speed. */
    fun getRemainingTime(): Double {
        val distance = getRemainingDistance()
        val speed = _currentSpeed.value
        return if (speed > 0) distance / speed else 0.0
    }
}

/** Simulation speed presets for easy selection. */
enum class SimulationSpeed(val speedMps: Float, val displayName: String) {
    WALKING(RouteSimulator.SPEED_WALKING, "Walking (5 km/h)"),
    CITY(RouteSimulator.SPEED_CITY, "City (40 km/h)"),
    HIGHWAY(RouteSimulator.SPEED_HIGHWAY, "Highway (100 km/h)"),
    VARIABLE(RouteSimulator.SPEED_VARIABLE, "Variable (Auto)")
}
