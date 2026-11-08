package com.noobdev.waffle.core.navigation

import com.noobdev.waffle.data.remote.RouteResult
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Stable, road-class-aware ETA estimator used by live GPS navigation, UI fallback callbacks, and
 * [RouteSimulator].
 *
 * Why previous ETAs shifted wildly:
 * Dividing the entire `remainingDistance` by the car's instantaneous speed (`distance / speedMps`)
 * assumes the driver will maintain their current 1-second speed (whether 100 km/h on a ramp or
 * 10 km/h approaching a turn) for the rest of a 20 km trip.
 *
 * How this estimator works:
 * 1. **Step-aware baseline (`baselineRemainingSeconds`)**: Computes the router's planned time for
 *    the remaining portion of the current step plus all upcoming steps, blended with the route's
 *    distance-proportioned duration so every meter of progress reduces the baseline smoothly.
 * 2. **Near-field vs. far-field split**: The vehicle's heavily smoothed rolling speed only adjusts
 *    the immediate upcoming horizon (up to 1.2 km ahead) within a clamped `[0.75x, 1.35x]` band.
 *    Roads further ahead keep their planned step durations (modulated by live traffic flow when
 *    available).
 * 3. **Slew-rate limiting (`smoothDuration`)**: Prevents sudden multi-minute jumps on individual
 *    ticks while still converging quickly after a reroute.
 */
object RouteEtaCalculator {

    /** Maximum lookahead distance (meters) influenced by the vehicle's current rolling speed. */
    private const val LOCAL_HORIZON_METERS = 1_200.0

    /**
     * Updates the exponentially smoothed rolling speed (m/s).
     *
     * When the vehicle is stopped or crawling at an intersection (`rawSpeedMps < minMovingSpeed`),
     * we decay the rolling pace very gently toward the route's expected speed rather than collapsing
     * it toward zero.
     */
    fun updateRollingSpeed(
            currentRollingMps: Double,
            rawSpeedMps: Float,
            expectedRouteMps: Double,
            profile: String = "driving-car"
    ): Double {
        val minMoving =
                when (profile) {
                    "foot-walking", "foot" -> 0.45
                    "cycling-regular", "bike" -> 1.4
                    else -> 2.8 // ~10 km/h: below this is stop-and-go / intersection creep
                }
        val baseline = expectedRouteMps.coerceAtLeast(1.0)
        val seed = if (currentRollingMps > 0.5) currentRollingMps else baseline
        val sample = rawSpeedMps.toDouble().coerceAtLeast(0.0)

        return if (sample >= minMoving) {
            // Clamp extreme GPS spikes before feeding the slow EMA (alpha = 0.12).
            val clampedSample = sample.coerceIn(baseline * 0.45, baseline * 1.65)
            seed * 0.88 + clampedSample * 0.12
        } else {
            // Stopped at a red light or turning: hold the rolling pace nearly steady, drifting
            // very slowly (2% per fix) toward a slightly conservative baseline.
            seed * 0.98 + (baseline * 0.85) * 0.02
        }
    }

    /**
     * Computes the router's planned remaining duration (seconds) from [remainingDistanceMeters] and
     * the remaining [RouteResult.steps].
     */
    fun baselineRemainingSeconds(
            route: RouteResult?,
            remainingDistanceMeters: Double,
            currentStepIndex: Int = -1,
            distanceToNextTurnMeters: Double = 0.0,
            profile: String = "driving-car"
    ): Double {
        if (remainingDistanceMeters <= 1.0) return 0.0
        val defaultSpeed =
                when (profile) {
                    "foot-walking", "foot" -> 1.4
                    "cycling-regular", "bike" -> 4.5
                    else -> 11.0
                }
        if (route == null || route.distanceMeters <= 1.0 || route.durationSeconds <= 1.0) {
            return remainingDistanceMeters / defaultSpeed
        }

        val totalDist = route.distanceMeters.coerceAtLeast(1.0)
        val distRatio = (remainingDistanceMeters / totalDist).coerceIn(0.0, 1.25)
        val proportionalSeconds = route.durationSeconds * distRatio

        val steps = route.steps
        val totalStepDuration = steps.sumOf { it.duration }
        if (steps.isEmpty() || totalStepDuration <= 1.0 || currentStepIndex !in steps.indices) {
            return proportionalSeconds
        }

        // In NavigationController / MapScreen, `currentStepIndex` is the index of the UPCOMING
        // turn step (the first step whose `wayPointIndex > matchedIndex`), and
        // `distanceToNextTurnMeters` is the distance along the route to that turn.
        // That means the driver is currently traversing `steps[currentStepIndex - 1]`!
        val activeTraversalIndex = (currentStepIndex - 1).coerceAtLeast(0)
        val activeStep = steps[activeTraversalIndex]
        val activeStepRemainingSec =
                if (activeStep.distance > 5.0 && distanceToNextTurnMeters > 0.0) {
                    val frac = (distanceToNextTurnMeters / activeStep.distance).coerceIn(0.0, 1.0)
                    activeStep.duration * frac
                } else {
                    0.0
                }

        var futureStepsSec = 0.0
        val startFuture =
                if (currentStepIndex > activeTraversalIndex) currentStepIndex
                else activeTraversalIndex + 1
        for (i in startFuture until steps.size) {
            futureStepsSec += steps[i].duration.coerceAtLeast(0.0)
        }

        val rawStepRemaining = activeStepRemainingSec + futureStepsSec
        // Scale by `route.durationSeconds / totalStepDuration` in case motorcycle factor or
        // calibration scaled the route summary duration.
        val scaledStepRemaining = rawStepRemaining * (route.durationSeconds / totalStepDuration)

        // Blend step-based remaining time (65%) with distance-proportional remaining time (35%)
        // so coarse waypoint indices never cause step-boundary jumps, while still respecting
        // fast highway vs. slow street segments.
        return scaledStepRemaining * 0.65 + proportionalSeconds * 0.35
    }

    /**
     * Estimates the remaining duration in seconds, combining the route's road-class baseline with a
     * bounded local speed adjustment and optional live traffic flow.
     */
    fun estimateRemainingSeconds(
            route: RouteResult?,
            remainingDistanceMeters: Double,
            rollingSpeedMps: Double,
            currentStepIndex: Int = -1,
            distanceToNextTurnMeters: Double = 0.0,
            trafficPaceMps: Double? = null,
            profile: String = "driving-car",
            previousRemainingSeconds: Double = 0.0
    ): Double {
        if (remainingDistanceMeters <= 5.0) return 0.0

        val expectedSpeedMps =
                if (route != null && route.durationSeconds > 1.0 && route.distanceMeters > 1.0) {
                    (route.distanceMeters / route.durationSeconds).coerceAtLeast(1.0)
                } else {
                    when (profile) {
                        "foot-walking", "foot" -> 1.4
                        "cycling-regular", "bike" -> 4.5
                        else -> 11.0
                    }
                }

        val baseRemaining =
                baselineRemainingSeconds(
                        route = route,
                        remainingDistanceMeters = remainingDistanceMeters,
                        currentStepIndex = currentStepIndex,
                        distanceToNextTurnMeters = distanceToNextTurnMeters,
                        profile = profile
                )

        // Split remaining distance into the local horizon (up to 1.2 km ahead) and the far route.
        val localDist = min(remainingDistanceMeters, LOCAL_HORIZON_METERS)
        val localFraction = (localDist / remainingDistanceMeters).coerceIn(0.0, 1.0)
        val localBaseSec = baseRemaining * localFraction
        val farBaseSec = baseRemaining * (1.0 - localFraction)

        // How much slower/faster is our rolling speed compared to the route's expected speed?
        // Clamped tightly to [0.78, 1.30] so local speed variations only nudge the near-field time.
        val effectiveRolling =
                if (rollingSpeedMps > 0.5) rollingSpeedMps else expectedSpeedMps
        val localTimeMultiplier =
                (expectedSpeedMps / effectiveRolling).coerceIn(0.78, 1.30)

        // Far-field traffic multiplier (only if online TomTom traffic flow is present).
        val trafficMultiplier =
                if (trafficPaceMps != null && trafficPaceMps > 1.0) {
                    val blendedTraffic = expectedSpeedMps * 0.55 + trafficPaceMps * 0.45
                    (expectedSpeedMps / blendedTraffic).coerceIn(0.85, 1.45)
                } else {
                    1.0
                }

        val rawEstimate = localBaseSec * localTimeMultiplier + farBaseSec * trafficMultiplier

        return smoothDuration(
                previousSeconds = previousRemainingSeconds,
                targetSeconds = rawEstimate,
                remainingDistanceMeters = remainingDistanceMeters,
                routeDurationSeconds = route?.durationSeconds ?: rawEstimate
        )
    }

    /**
     * Smooths transitions between consecutive ETA updates so the HUD clock and minute readout never
     * flicker or jump wildly. If `previousSeconds` is uninitialized or from a different route
     * (large discontinuity after reroute), snaps directly to `targetSeconds`.
     */
    fun smoothDuration(
            previousSeconds: Double,
            targetSeconds: Double,
            remainingDistanceMeters: Double,
            routeDurationSeconds: Double
    ): Double {
        if (previousSeconds <= 0.0 || remainingDistanceMeters <= 5.0) {
            return targetSeconds.coerceAtLeast(0.0)
        }
        val delta = targetSeconds - previousSeconds
        // If a reroute changed total duration by more than 35% (and > 120s), snap to new route.
        if (abs(delta) > max(120.0, routeDurationSeconds * 0.35)) {
            return targetSeconds.coerceAtLeast(0.0)
        }
        // Otherwise apply a gentle low-pass filter (85% previous, 15% new) and cap per-update
        // upward drift so red lights or braking never cause a sudden +1 min jump.
        val blended = previousSeconds * 0.85 + targetSeconds * 0.15
        val maxIncreasePerTick = 1.5
        val maxDecreasePerTick = 4.0
        return blended.coerceIn(
                        previousSeconds - maxDecreasePerTick,
                        previousSeconds + maxIncreasePerTick
                )
                .coerceAtLeast(0.0)
    }
}

