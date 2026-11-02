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
 * bundled graph, without any network. Car, bike and foot must all be present in the graph.
 */
@RunWith(AndroidJUnit4::class)
class OfflineRerouteTest {

    @Test
    fun plansEveryTravelModeFromTheBundledGraph() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        OfflineRoutingManager.initialize(context)

        for (profile in listOf("car", "bike", "foot")) {
            val plan =
                    RouteRepository.plan(
                            start = GeoPoint(33.6844, 73.0479),
                            end = GeoPoint(33.7294, 73.0931),
                            profile = profile,
                            hazards = emptyList()
                    )

            assertTrue(
                    "expected a $profile route: ${plan.result.errorMessage}",
                    plan.success
            )
            assertEquals("$profile should use the bundled graph", RouteSource.OFFLINE, plan.source)
            assertTrue(plan.result.coordinates.size > 2)
        }
    }
}
