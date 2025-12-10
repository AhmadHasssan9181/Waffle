package com.noobdev.waffle.utils

import androidx.compose.runtime.Composable
import com.noobdev.waffle.map.ParentMapScreen
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression.heatmapDensity
import org.maplibre.android.style.expressions.Expression.interpolate
import org.maplibre.android.style.expressions.Expression.linear
import org.maplibre.android.style.expressions.Expression.literal
import org.maplibre.android.style.expressions.Expression.rgb
import org.maplibre.android.style.expressions.Expression.rgba
import org.maplibre.android.style.expressions.Expression.stop
import org.maplibre.android.style.expressions.Expression.zoom
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.PropertyFactory.heatmapColor
import org.maplibre.android.style.layers.PropertyFactory.heatmapOpacity
import org.maplibre.android.style.layers.PropertyFactory.heatmapRadius
import org.maplibre.android.style.sources.GeoJsonSource
import java.util.Random

// --- YOUR COMPOSABLE ---
@Composable
fun WaffleDisasterScreen() {
    // Reusing your generic parent screen, but injecting Heatmap logic
    ParentMapScreen(
        mapConfiguration = { map ->
            addRandomDisasterHeatmap(map)
        }
    )
}

// --- HELPER FUNCTIONS ---

/**
 * 1. Generates random points.
 * 2. Adds them as a Source to the map.
 * 3. Adds a HeatmapLayer to visualize the density of those points.
 */
fun addRandomDisasterHeatmap(map: MapLibreMap) {
    map.getStyle { style ->

        val sourceId = "random-disaster-source"
        val layerId = "random-disaster-heatmap-layer"

        // Prevent adding duplicate layers if the style reloads
        if (style.getSource(sourceId) != null) return@getStyle

        // --- A. GENERATE DATA ---
        // Generate 1000 random points centered roughly around Northern Pakistan
        val geoJsonData = generateRandomGeoJsonPoints(
            count = 1000,
            centerLat = 34.5, centerLon = 73.0, latSpread = 1.5, lonSpread = 2.0
        )

        // --- B. ADD SOURCE ---
        style.addSource(GeoJsonSource(sourceId, geoJsonData))

        // --- C. ADD HEATMAP LAYER ---
        // A HeatmapLayer visualizes data density. It doesn't draw the exact points,
        // but rather a "cloud" showing where points are clustered.
        val heatmapLayer = HeatmapLayer(layerId, sourceId)

        heatmapLayer.setProperties(
            // 1. Color Ramp: Defines the color gradient based on density (0.0 to 1.0)
            // 0.0 density = transparent blue
            // 1.0 density = deep red (hot!)
            heatmapColor(
                interpolate(
                    linear(), heatmapDensity(),
                    literal(0.0), rgba(33, 102, 172, 0), // Transparent start
                    literal(0.2), rgb(103, 169, 207), // Light blue
                    literal(0.4), rgb(209, 229, 240), // Lighter blueish white
                    literal(0.6), rgb(253, 219, 199), // Light orange
                    literal(0.8), rgb(239, 138, 98),  // Orange red
                    literal(1.0), rgb(178, 24, 43)    // Deep red
                )
            ),

            // 2. Radius: How "wide" the glow of each point is.
            // We increase radius as you zoom in so points connect better.
            heatmapRadius(
                interpolate(
                    linear(), zoom(),
                    stop(0, 10),   // Zoom level 0 -> 10px radius
                    stop(12, 40)   // Zoom level 12 -> 40px radius
                )
            ),

            // 3. Opacity: Overall transparency of the layer
            heatmapOpacity(0.8f)
        )

        // Try to add the heatmap *below* road labels so street names are readable over the heat.
        // If "road_label" doesn't exist in the current style, add it to the top.
        if (style.getLayer("road_label") != null) {
            style.addLayerBelow(heatmapLayer, "road_label")
        } else {
            style.addLayer(heatmapLayer)
        }

        // Move camera to view the generated data automatically
        map.easeCamera(CameraUpdateFactory.newLatLngZoom(LatLng(34.5, 73.0), 7.0))
    }
}

/**
 * Helper to generate a simple GeoJSON FeatureCollection string of random points.
 *
 * Example output format:
 * { "type": "FeatureCollection", "features": [
 * { "type": "Feature", "geometry": { "type": "Point", "coordinates": [73.123, 34.567] }}, ...
 * ]}
 */
fun generateRandomGeoJsonPoints(
    count: Int,
    centerLat: Double,
    centerLon: Double,
    latSpread: Double,
    lonSpread: Double
): String {
    val featuresList = mutableListOf<String>()
    val random = Random(System.currentTimeMillis())

    repeat(count) {
        // Generate random offsets based on a gaussian (bell curve) distribution
        // so points cluster near the center naturally.
        val latOffset = random.nextGaussian() * latSpread
        val lonOffset = random.nextGaussian() * lonSpread

        val lat = centerLat + latOffset
        val lon = centerLon + lonOffset

        // Add a single GeoJSON Feature string
        featuresList.add(
            """
            { "type": "Feature", "properties": {}, "geometry": { "type": "Point", "coordinates": [$lon, $lat] }}
            """.trimIndent()
        )
    }

    // Combine into a FeatureCollection
    return """
        { "type": "FeatureCollection", "features": [ ${featuresList.joinToString(",")} ] }
    """.trimIndent()
}