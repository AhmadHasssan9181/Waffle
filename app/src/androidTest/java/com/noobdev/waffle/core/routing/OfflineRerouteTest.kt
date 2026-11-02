package com.noobdev.waffle.core.routing

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.noobdev.waffle.core.navigation.GeoPoint
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Guards the path a live reroute takes: a route can be planned from an arbitrary GPS fix with the
 * bundled graph, without any network.
 */
@RunWith(AndroidJUnit4::class)
class OfflineRerouteTest {

    @Test
    fun plansFromTheBundledGraphWithoutNetwork() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OfflineRoutingManager.initialize(context)

        val plan =
                RouteRepository.plan(
                        start = GeoPoint(33.6844, 73.0479),
                        end = GeoPoint(33.7294, 73.0931),
                        profile = "car",
                        hazards = emptyList()
                )

        assertTrue("expected a route: ${plan.result.errorMessage}", plan.success)
        assertEquals(RouteSource.OFFLINE, plan.source)
        assertTrue(plan.result.coordinates.size > 2)
    }
}
