package com.noobdev.waffle.core.navigation

/**
 * Decides when a live GPS fix has genuinely left the planned route.
 *
 * A route is drawn on road centrelines and GPS wanders, so one reading 30 m to the side means
 * nothing. The monitor waits for several consecutive fixes beyond [thresholdMeters] before asking
 * for a new route, and accepts an unmistakable jump ([immediateMeters]) at once.
 *
 * Retries are rate-limited by [retryCooldownMillis]. Without that, a driver parked 60 m from the
 * line would ask the router for a new route every two seconds, which is both wasteful and
 * distracting.
 */
class OffRouteMonitor(
        private val thresholdMeters: Double = 45.0,
        private val consecutiveFixes: Int = 3,
        private val immediateMeters: Double = 110.0,
        private val retryCooldownMillis: Long = 10_000L,
        private val maxAccuracyMeters: Float = 40f
) {

    data class Decision(
            val isOffRoute: Boolean,
            val shouldReroute: Boolean,
            val distanceMeters: Double
    )

    private var offRouteFixes = 0
    private var lastAttemptAtMillis: Long = -retryCooldownMillis

    /**
     * Feed one fix. [distanceMeters] is the raw GPS distance to the active route (not the distance
     * after snapping), [accuracyMeters] comes from the location fix, and [nowMillis] is injectable
     * so the cooldown can be tested.
     */
    fun onFix(
            distanceMeters: Double,
            accuracyMeters: Float,
            nowMillis: Long = System.currentTimeMillis()
    ): Decision {
        if (!distanceMeters.isFinite()) {
            return Decision(isOffRoute = offRouteFixes > 0, shouldReroute = false, distanceMeters)
        }

        // A bad fix is not evidence of a wrong turn. Keep the previous state and wait.
        if (accuracyMeters > maxAccuracyMeters) {
            return Decision(isOffRoute = offRouteFixes > 0, shouldReroute = false, distanceMeters)
        }

        if (distanceMeters > thresholdMeters) {
            offRouteFixes++
        } else {
            offRouteFixes = 0
        }

        val confirmed = offRouteFixes >= consecutiveFixes || distanceMeters >= immediateMeters
        val offCooldown = nowMillis - lastAttemptAtMillis >= retryCooldownMillis

        return Decision(
                isOffRoute = offRouteFixes > 0,
                shouldReroute = confirmed && offCooldown,
                distanceMeters = distanceMeters
        )
    }

    /** Call after a reroute attempt (successful or not) to start the cooldown. */
    fun markAttempt(nowMillis: Long = System.currentTimeMillis()) {
        lastAttemptAtMillis = nowMillis
        offRouteFixes = 0
    }

    fun reset() {
        offRouteFixes = 0
        lastAttemptAtMillis = -retryCooldownMillis
    }
}
