package com.noobdev.waffle.core.routing

import com.noobdev.waffle.core.navigation.GeoPoint
import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assume.assumeTrue
import org.junit.Test

class LocalRoutingEngineAlternativeTest {

    @Test
    fun alternativeRouteRequestStillReturnsTheBestRoute() {
        val graph = File("src/main/assets/graph-cache")
        assumeTrue("bundled graph is not available in this checkout", graph.exists())
        val engine = LocalRoutingEngine(graph.absolutePath)

        val response =
                engine.calculateRoutes(
                        start = GeoPoint(33.6844, 73.0479),
                        end = GeoPoint(33.7294, 73.0931),
                        profile = "car",
                        maxPaths = 3
                )

        println("alternative errors=${response.errors} paths=${response.all.size}")
        assertFalse(response.hasErrors())
        assertFalse(response.all.isEmpty())
    }
}
