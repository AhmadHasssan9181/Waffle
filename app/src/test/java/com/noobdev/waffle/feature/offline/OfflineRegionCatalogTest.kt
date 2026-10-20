package com.noobdev.waffle.feature.offline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OfflineRegionCatalogTest {

    @Test
    fun satelliteIsCappedAtLowerZoomThanVectorStyles() {
        val satellite = OfflineRegionCatalog.islamabad.target(OfflineLayer.SATELLITE)
        assertTrue(
                "satellite download must cap zoom below the street pack",
                satellite.maxZoom <= 14.0
        )
    }

    @Test
    fun satelliteCostsMoreThanStreetsAtTheSameZoomRange() {
        // The Pakistan overview uses the same zoom range for every layer, so the per-tile price is
        // the only difference left: raster satellite tiles are several times bigger than vectors.
        val pack = OfflineRegionCatalog.pakistan
        val streets = pack.target(OfflineLayer.STREETS)
        val satellite = pack.target(OfflineLayer.SATELLITE)

        assertTrue(
                "satellite should be estimated larger than streets at equal zoom",
                satellite.estimatedMegabytes > streets.estimatedMegabytes
        )
    }

    @Test
    fun estimatesStayWithinReasonableBounds() {
        for (pack in OfflineRegionCatalog.all) {
            for (layer in OfflineLayer.entries) {
                val megabytes = pack.estimatedMegabytes(layer)
                assertTrue("${pack.id}/${layer.key} estimate too small: $megabytes", megabytes >= 1)
                assertTrue("${pack.id}/${layer.key} estimate too large: $megabytes", megabytes < 4096)
            }
        }
    }

    @Test
    fun targetIdsAreUnique() {
        val ids =
                OfflineRegionCatalog.all.flatMap { pack ->
                    OfflineLayer.entries.map { layer -> pack.target(layer).id }
                }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun displayNameCombinesAreaAndLayer() {
        val target = OfflineRegionCatalog.lahore.target(OfflineLayer.SATELLITE)
        assertTrue(target.displayName.contains("Lahore"))
        assertTrue(target.displayName.contains("Satellite"))
    }
}
