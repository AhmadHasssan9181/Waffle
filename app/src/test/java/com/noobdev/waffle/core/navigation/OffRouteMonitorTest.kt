package com.noobdev.waffle.core.navigation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OffRouteMonitorTest {

    @Test
    fun `two noisy fixes are not enough to trigger a reroute`() {
        val monitor = OffRouteMonitor()

        val first = monitor.onFix(distanceMeters = 60.0, accuracyMeters = 8f, nowMillis = 100_000)
        val second = monitor.onFix(distanceMeters = 60.0, accuracyMeters = 8f, nowMillis = 101_500)

        assertTrue(first.isOffRoute)
        assertFalse(first.shouldReroute)
        assertTrue(second.isOffRoute)
        assertFalse(second.shouldReroute)
    }

    @Test
    fun `third consecutive fix confirms the deviation`() {
        val monitor = OffRouteMonitor()

        monitor.onFix(60.0, 8f, 100_000)
        monitor.onFix(60.0, 8f, 101_500)
        val decision = monitor.onFix(60.0, 8f, 103_000)

        assertTrue(decision.isOffRoute)
        assertTrue(decision.shouldReroute)
    }

    @Test
    fun `coming back onto the line clears the streak`() {
        val monitor = OffRouteMonitor()

        monitor.onFix(60.0, 8f, 100_000)
        monitor.onFix(60.0, 8f, 101_500)
        monitor.onFix(10.0, 8f, 103_000)
        monitor.onFix(60.0, 8f, 104_500)

        val decision = monitor.onFix(60.0, 8f, 106_000)
        assertFalse(decision.shouldReroute)
    }

    @Test
    fun `a large jump triggers immediately`() {
        val monitor = OffRouteMonitor()

        val decision = monitor.onFix(140.0, 8f, 100_000)

        assertTrue(decision.isOffRoute)
        assertTrue(decision.shouldReroute)
    }

    @Test
    fun `poor accuracy is ignored`() {
        val monitor = OffRouteMonitor()

        val decision = monitor.onFix(140.0, accuracyMeters = 80f, nowMillis = 100_000)

        assertFalse(decision.isOffRoute)
        assertFalse(decision.shouldReroute)
    }

    @Test
    fun `cooldown stops a parked car from planning constantly`() {
        val monitor = OffRouteMonitor(retryCooldownMillis = 10_000)
        monitor.onFix(140.0, 8f, 100_000)
        monitor.markAttempt(100_000)

        val duringCooldown = monitor.onFix(140.0, 8f, 105_000)
        assertTrue(duringCooldown.isOffRoute)
        assertFalse(duringCooldown.shouldReroute)

        monitor.onFix(60.0, 8f, 110_500)
        monitor.onFix(60.0, 8f, 112_000)
        val afterCooldown = monitor.onFix(60.0, 8f, 113_500)
        assertTrue(afterCooldown.shouldReroute)
    }

    @Test
    fun `reset allows an immediate reroute for the next trip`() {
        val monitor = OffRouteMonitor(retryCooldownMillis = 10_000)
        monitor.onFix(140.0, 8f, 100_000)
        monitor.markAttempt(100_000)
        monitor.reset()

        val decision = monitor.onFix(140.0, 8f, 101_000)

        assertTrue(decision.shouldReroute)
    }
}
