package com.noobdev.waffle.core.routing

import com.noobdev.waffle.data.remote.NavigationStep
import com.noobdev.waffle.data.remote.RouteResult
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteMergeTest {

    @Test
    fun `legs share one boundary point and step indices stay aligned`() {
        val first = leg(listOf(0.0 to 0.0, 1.0 to 1.0), 100.0, 10.0, "first", RouteSource.OFFLINE)
        val second = leg(listOf(1.0 to 1.0, 2.0 to 2.0), 50.0, 5.0, "second", RouteSource.OFFLINE)

        val merged = RouteRepository.mergeRoutePlans(listOf(first, second))

        assertEquals(3, merged.result.coordinates.size)
        assertEquals(listOf(0.0 to 0.0, 1.0 to 1.0, 2.0 to 2.0), merged.result.coordinates)
        assertEquals(150.0, merged.result.distanceMeters, 0.001)
        assertEquals(15.0, merged.result.durationSeconds, 0.001)
        assertEquals(listOf("first", "second"), merged.result.instructions)
        assertEquals(listOf(0, 1), merged.result.steps.map { it.wayPointIndex })
        assertEquals(RouteSource.OFFLINE, merged.source)
    }

    @Test
    fun `a leg that does not share a point is appended without losing it`() {
        val first = leg(listOf(0.0 to 0.0, 1.0 to 1.0), 100.0, 10.0, "first", RouteSource.OFFLINE)
        val second = leg(listOf(1.5 to 1.5, 2.0 to 2.0), 50.0, 5.0, "second", RouteSource.ONLINE)

        val merged = RouteRepository.mergeRoutePlans(listOf(first, second))

        assertEquals(4, merged.result.coordinates.size)
        assertEquals(listOf(0, 2), merged.result.steps.map { it.wayPointIndex })
        assertEquals(RouteSource.ONLINE, merged.source)
    }

    @Test
    fun `hazards avoided by any leg are kept once`() {
        val hazard =
                com.noobdev.waffle.core.navigation.HazardZone(
                        id = "flood-1",
                        title = "Flood",
                        type = "flood",
                        severity = "WARNING",
                        center = com.noobdev.waffle.core.navigation.GeoPoint(0.5, 0.5),
                        radiusMeters = 500.0
                )
        val first =
                leg(listOf(0.0 to 0.0, 1.0 to 1.0), 100.0, 10.0, "first", RouteSource.OFFLINE)
                        .copy(avoidedHazards = listOf(hazard))
        val second =
                leg(listOf(1.0 to 1.0, 2.0 to 2.0), 50.0, 5.0, "second", RouteSource.OFFLINE)
                        .copy(avoidedHazards = listOf(hazard))

        val merged = RouteRepository.mergeRoutePlans(listOf(first, second))

        assertEquals(1, merged.avoidedHazards.size)
        assertTrue(merged.success)
    }

    private fun leg(
            coordinates: List<Pair<Double, Double>>,
            distance: Double,
            duration: Double,
            instruction: String,
            source: RouteSource
    ): RoutePlan =
            RoutePlan(
                    result =
                            RouteResult(
                                    distanceMeters = distance,
                                    durationSeconds = duration,
                                    coordinates = coordinates,
                                    instructions = listOf(instruction),
                                    steps =
                                            listOf(
                                                    NavigationStep(
                                                            instruction = instruction,
                                                            distance = distance,
                                                            duration = duration,
                                                            type = 0,
                                                            wayPointIndex = 0
                                                    )
                                            ),
                                    success = true
                            ),
                    source = source
            )
}
