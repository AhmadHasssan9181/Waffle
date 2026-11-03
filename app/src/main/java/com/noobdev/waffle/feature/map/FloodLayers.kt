package com.noobdev.waffle.feature.map

import org.maplibre.android.maps.MapLibreMap

private val floodLayerIds =
        listOf(
                "layer-depth",
                "layer-water",
                "layer-extent",
                "layer-roads",
                "layer-builtup",
                "layer-footprint",
                "layer-limit"
        )

private val floodSourceIds =
        floodLayerIds.map { "source-$it" }

/** Removes every Copernicus flood layer/source that [loadSectorData] can add. */
internal fun clearFloodLayers(map: MapLibreMap) {
    map.getStyle { style ->
        floodLayerIds.forEach { runCatching { style.removeLayer(it) } }
        floodSourceIds.forEach { runCatching { style.removeSource(it) } }
    }
}
