package com.noobdev.waffle.core.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CameraTransitionManagerSnapTest {

    // A short north-south line near Islamabad. 0.001 degrees of latitude is ~111 m.
    private val route = listOf(33.0000 to 73.0000, 33.0100 to 73.0000)

    @Test
    fun `a fix near the line snaps onto it and reports its offset`() {
        val result = CameraTransitionManager.snapToRoute(33.0050, 73.0002, route)

        assertEquals(0, result.matchedIndex)
        assertTrue(result.distanceMeters in 15.0..25.0)
        assertEquals(33.0050, result.snappedLat, 0.0005)
        assertEquals(73.0000, result.snappedLng, 0.0005)
    }

    @Test
    fun `a fix far from the line keeps the raw position but still reports distance`() {
        val result = CameraTransitionManager.snapToRoute(33.0050, 73.0020, route)

        assertEquals(-1, result.matchedIndex)
        assertTrue(result.distanceMeters > 150.0)
        assertEquals(33.0050, result.snappedLat, 0.000001)
        assertEquals(73.0020, result.snappedLng, 0.000001)
    }

    @Test
    fun `snapping starts from the current route segment`() {
        val longRoute =
                (0..12).map { index -> 33.0 + index * 0.001 to 73.0 }
        // The first segment is close, but navigation is already at segment 10. The old
        // whole-route search could jump backward and confuse arrival/off-route logic.
        val result =
                CameraTransitionManager.snapToRoute(
                        33.0000,
                        73.0,
                        longRoute,
                        maxSnapDistance = 5.0,
                        fromIndex = 10
                )

        assertEquals(-1, result.matchedIndex)
    }
}
