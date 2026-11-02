package com.noobdev.waffle.core.navigation

import kotlin.math.abs

/**
 * Decides when a live GPS fix has genuinely left the planned route.
 *
 * Two different failures matter:
 *  - **Lateral**: the car is no longer on the line (missed turn, parallel street). A route is drawn
 *    on road centrelines and GPS wanders, so one reading 30 m to the side means nothing. The monitor
 *    waits for several consecutive fixes beyond [thresholdMeters], and accepts an unmistakable jump
 *    ([immediateMeters]) at once.
 *  - **Wrong way**: the car is still on the line but travelling against the route bearing (a
 *    U-turn, a one-way street entered backwards, a divided road taken on the wrong side). Distance
 *    alone can never catch this, so the monitor also compares the GPS heading with the route
 *    segment's heading.
 *
 * Retries are rate-limited by [retryCooldownMillis]. Without that, a driver parked 60 m from the
 * line would ask the router for a new route every two seconds, which is both wasteful and
 * distracting.
 */
class OffRouteMonitor(
        private val thresholdMeters: Double = 45.0,
        private val consecutiveFixes: Int = 3,
        private val immediateMeters: Double = 110.0,
        private val wrongWayFixesRequired: Int = 4,
        private val wrongWayAngleDegrees: Double = 120.0,
        private val wrongWayMinSpeedMps: Float = 3.0f,
        private val retryCooldownMillis: Long = 10_000L,
        private val maxAccuracyMeters: Float = 40f
) {

    data class Decision(
            val isOffRoute: Boolean,
            val isWrongWay: Boolean,
            val shouldReroute: Boolean,
            val distanceMeters: Double
    )

    private var offRouteFixes = 0
    private var wrongWayFixes = 0
    private var lastAttemptAtMillis: Long = -retryCooldownMillis

    /**
     * Feed one fix.
     *
     * @param distanceMeters raw GPS distance to the active route (not the distance after snapping)
     * @param accuracyMeters accuracy reported by the location fix
     * @param routeBearingDegrees heading of the route segment the fix snapped to, if any
     * @param travelBearingDegrees heading the device is actually moving
     * @param speedMps current speed; heading is ignored below [wrongWayMinSpeedMps]
     * @param nowMillis injectable clock so the cooldown can be tested
     */
    fun onFix(
            distanceMeters: Double,
            accuracyMeters: Float,
            routeBearingDegrees: Double? = null,
            travelBearingDegrees: Double? = null,
            speedMps: Float = 0f,
            nowMillis: Long = System.currentTimeMillis()
    ): Decision {
        if (!distanceMeters.isFinite()) {
            return snapshot(distanceMeters)
        }

        // A bad fix is not evidence of a wrong turn. Keep the previous state and wait.
        if (accuracyMeters > maxAccuracyMeters) {
            return snapshot(distanceMeters)
        }

        if (distanceMeters > thresholdMeters) {
            offRouteFixes++
        } else {
            offRouteFixes = 0
        }

        val headingOpposite =
                routeBearingDegrees != null &&
                        travelBearingDegrees != null &&
                        speedMps >= wrongWayMinSpeedMps &&
                        angleBetween(routeBearingDegrees, travelBearingDegrees) >=
                                wrongWayAngleDegrees
        if (headingOpposite) {
            wrongWayFixes++
        } else {
            wrongWayFixes = 0
        }

        val confirmed =
                offRouteFixes >= consecutiveFixes ||
                        distanceMeters >= immediateMeters ||
                        wrongWayFixes >= wrongWayFixesRequired
        val offCooldown = nowMillis - lastAttemptAtMillis >= retryCooldownMillis

        return Decision(
                isOffRoute = offRouteFixes > 0,
                isWrongWay = wrongWayFixes > 0,
                shouldReroute = confirmed && offCooldown,
                distanceMeters = distanceMeters
        )
    }

    /** Call after a reroute attempt (successful or not) to start the cooldown. */
    fun markAttempt(nowMillis: Long = System.currentTimeMillis()) {
        lastAttemptAtMillis = nowMillis
        offRouteFixes = 0
        wrongWayFixes = 0
    }

    fun reset() {
        offRouteFixes = 0
        wrongWayFixes = 0
        lastAttemptAtMillis = -retryCooldownMillis
    }

    private fun snapshot(distanceMeters: Double): Decision =
            Decision(
                    isOffRoute = offRouteFixes > 0,
                    isWrongWay = wrongWayFixes > 0,
                    shouldReroute = false,
                    distanceMeters = distanceMeters
            )

    /** Smallest angle between two bearings, 0..180 degrees. */
    private fun angleBetween(a: Double, b: Double): Double =
            abs((a - b + 540.0) % 360.0 - 180.0)
}
