package com.noobdev.waffle.Sketching

import androidx.compose.runtime.Composable
import com.noobdev.waffle.map.ParentMapScreen
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.HeatmapLayer
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.android.style.expressions.Expression.*
import java.net.URI

// --- 1. THE DEMO SCREEN ---
@Composable
fun HeatmapDemoScreen() {
    // This reuses your ParentMapScreen but injects the heatmap logic
    ParentMapScreen(
        mapConfiguration = { map ->
            addDisasterHeatmapSource(map)
        }
    )
}

// --- 2. THE LOGIC ---
// FIXED: Added 'map' as a parameter here so the function knows what to draw on
fun addDisasterHeatmapSource(map: MapLibreMap) {

    map.getStyle { style ->

        // 1. ADD DATA (Source)
        val sourceId = "disaster-points"
        // Check if source already exists to avoid crashes on re-renders
        if (style.getSource(sourceId) == null) {
            style.addSource(
                GeoJsonSource(
                    sourceId,
                    // Using the earthquake data you provided
                    URI("https://docs.mapbox.com/mapbox-gl-js/assets/earthquakes.geojson")
                )
            )
        }

        // 2. ADD VISUALS (Layer)
        val layerId = "disaster-heatmap"
        if (style.getLayer(layerId) == null) {
            val heatmapLayer = HeatmapLayer(layerId, sourceId)

            heatmapLayer.setProperties(
                // Color ramp: Blue (low) -> Yellow -> Red (high density)
                heatmapColor(
                    interpolate(
                        linear(), heatmapDensity(),
                        literal(0), rgba(33, 102, 172, 0),
                        literal(0.2), rgb(103, 169, 207),
                        literal(0.4), rgb(209, 229, 240),
                        literal(0.6), rgb(253, 219, 199),
                        literal(0.8), rgb(239, 138, 98),
                        literal(1), rgb(178, 24, 43)
                    )
                ),
                // Adjust radius based on zoom level
                heatmapRadius(
                    interpolate(
                        linear(), zoom(),
                        literal(0), literal(2),
                        literal(9), literal(20)
                    )
                ),
                // Opacity
                heatmapOpacity(0.7f)
            )

            // SAFETY CHECK: Try to put it below labels, but if "water_name" doesn't exist, just add it to top.
            if (style.getLayer("water_name") != null) {
                style.addLayerBelow(heatmapLayer, "water_name")
            } else {
                style.addLayer(heatmapLayer)
            }
        }
    }
}