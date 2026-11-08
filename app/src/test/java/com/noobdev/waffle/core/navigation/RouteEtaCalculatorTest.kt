package com.noobdev.waffle.core.navigation

import com.noobdev.waffle.data.remote.NavigationStep
import com.noobdev.waffle.data.remote.RouteResult
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteEtaCalculatorTest {

    @Test
    fun `eta does not swing wildly when vehicle speed spikes or stops at red light`() {
        val route =
                RouteResult(
                        distanceMeters = 12_000.0,
                        durationSeconds = 900.0, // 15 min (~13.3 m/s avg)
                        coordinates = listOf(33.68 to 73.04, 33.72 to 73.09),
                        instructions = listOf("Head north", "Turn right", "Arrive"),
                        steps =
                                listOf(
                                        NavigationStep("Head north", 6_000.0, 500.0, 0, 0),
                                        NavigationStep("Turn right", 6_000.0, 400.0, 1, 1),
                                        NavigationStep("Arrive", 0.0, 0.0, 4, 1)
                                ),
                        success = true
                )

        val expectedMps = route.distanceMeters / route.durationSeconds
        var rolling = expectedMps
        var remainingSec = route.durationSeconds

        // Simulate a sudden hard pull to 30 m/s (108 km/h) for 5 fixes
        for (i in 1..5) {
            rolling = RouteEtaCalculator.updateRollingSpeed(rolling, 30f, expectedMps)
            remainingSec =
                    RouteEtaCalculator.estimateRemainingSeconds(
                            route = route,
                            remainingDistanceMeters = 11_800.0,
                            rollingSpeedMps = rolling,
                            currentStepIndex = 1,
                            distanceToNextTurnMeters = 5_800.0,
                            previousRemainingSeconds = remainingSec
                    )
        }
        // Even at 108 km/h, a 15-minute (900s) trip must NOT collapse to 6 minutes (360s);
        // it should remain near 850..900s.
        assertTrue("ETA collapsed too far on speed spike: $remainingSec", remainingSec > 840.0)

        // Now simulate a full stop (0 m/s) at a red light for 10 fixes
        val beforeStop = remainingSec
        for (i in 1..10) {
            rolling = RouteEtaCalculator.updateRollingSpeed(rolling, 0f, expectedMps)
            remainingSec =
                    RouteEtaCalculator.estimateRemainingSeconds(
                            route = route,
                            remainingDistanceMeters = 11_800.0,
                            rollingSpeedMps = rolling,
                            currentStepIndex = 1,
                            distanceToNextTurnMeters = 5_800.0,
                            previousRemainingSeconds = remainingSec
                    )
        }
        // Stopping at a red light for 10 seconds must not inflate ETA by minutes.
        assertTrue(
                "ETA inflated wildly at red light: before=$beforeStop after=$remainingSec",
                remainingSec - beforeStop < 20.0
        )
    }
}

