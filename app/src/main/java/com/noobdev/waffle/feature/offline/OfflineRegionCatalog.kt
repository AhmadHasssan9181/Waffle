package com.noobdev.waffle.feature.offline

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.tan

/**
 * Layers a user can keep on the device. MapLibre can cache any style, including raster satellite
 * imagery; the difference is size. Vector street packs are tens of MB, satellite packs are
 * hundreds, so every target carries its own zoom cap and size estimate.
 */
enum class OfflineLayer(
        val key: String,
        val styleName: String,
        val label: String,
        val detail: String,
        val raster: Boolean
) {
    STREETS(
            key = "streets",
            styleName = "Streets",
            label = "Streets",
            detail = "Roads, labels and places",
            raster = false
    ),
    SATELLITE(
            key = "satellite",
            styleName = "Satellite",
            label = "Satellite",
            detail = "Aerial imagery · largest download",
            raster = true
    ),
    OUTDOOR(
            key = "outdoor",
            styleName = "Outdoor",
            label = "Terrain",
            detail = "Trails, contours and land cover",
            raster = false
    ),
    DARK(
            key = "dark",
            styleName = "Dark",
            label = "Dark",
            detail = "Low-light driving map",
            raster = false
    )
}

/**
 * A predefined area. Bounds are intentionally tight: every extra zoom level quadruples the number
 * of tiles, so a city pack stays usable while a country pack stays small and zoomed out.
 */
data class OfflineRegionPack(
        val id: String,
        val name: String,
        val subtitle: String,
        val south: Double,
        val west: Double,
        val north: Double,
        val east: Double,
        val minZoom: Double = 6.0,
        val maxZoom: Double = 15.0
) {
    fun target(layer: OfflineLayer): OfflineDownloadTarget = OfflineDownloadTarget(this, layer)

    fun maxZoomFor(layer: OfflineLayer): Double {
        if (!layer.raster) return maxZoom
        // Satellite tiles are ~4x larger. Cap them one zoom below the street pack; this is the
        // same trade-off Google Maps makes when it limits offline imagery detail.
        return minOf(maxZoom, 14.0)
    }

    fun estimatedTileCount(layer: OfflineLayer): Long {
        var total = 0L
        var zoom = minZoom.toInt()
        val max = maxZoomFor(layer).toInt()
        while (zoom <= max) {
            total += tilesAtZoom(zoom)
            zoom++
        }
        return total
    }

    fun estimatedMegabytes(layer: OfflineLayer): Int {
        // Measured against MapTiler vector tiles (~25-35 KB) and satellite JPEG tiles
        // (~90-140 KB). Round up so the UI never promises less than the download needs.
        val kilobytesPerTile = if (layer.raster) 110.0 else 32.0
        val megabytes = estimatedTileCount(layer) * kilobytesPerTile / 1024.0
        return maxOf(1, ceil(megabytes).toInt())
    }

    private fun tilesAtZoom(zoom: Int): Long {
        val scale = 1 shl zoom
        val minX = floor((west + 180.0) / 360.0 * scale).toLong()
        val maxX = floor((east + 180.0) / 360.0 * scale).toLong()
        val minY = floor(latToTileY(north, scale)).toLong()
        val maxY = floor(latToTileY(south, scale)).toLong()

        val width = (maxX - minX + 1).coerceAtLeast(0)
        val height = (maxY - minY + 1).coerceAtLeast(0)
        return width * height
    }

    private fun latToTileY(latitude: Double, scale: Int): Double {
        val clamped = latitude.coerceIn(-85.05112878, 85.05112878)
        val radians = Math.toRadians(clamped)
        return (1.0 - ln(tan(radians) + 1.0 / kotlin.math.cos(radians)) / Math.PI) / 2.0 * scale
    }
}

data class OfflineDownloadTarget(val pack: OfflineRegionPack, val layer: OfflineLayer) {
    val id: String = "${pack.id}:${layer.key}"
    val displayName: String = "${pack.name} · ${layer.label}"
    val minZoom: Double = pack.minZoom
    val maxZoom: Double = pack.maxZoomFor(layer)
    val estimatedMegabytes: Int = pack.estimatedMegabytes(layer)
}

object OfflineRegionCatalog {

    val islamabad = OfflineRegionPack(
            id = "pk-islamabad",
            name = "Islamabad & Rawalpindi",
            subtitle = "Capital region, full detail",
            south = 33.45,
            west = 72.85,
            north = 33.95,
            east = 73.35,
            maxZoom = 15.0
    )

    val lahore = OfflineRegionPack(
            id = "pk-lahore",
            name = "Lahore",
            subtitle = "City + ring road",
            south = 31.35,
            west = 74.15,
            north = 31.70,
            east = 74.55,
            maxZoom = 15.0
    )

    val karachi = OfflineRegionPack(
            id = "pk-karachi",
            name = "Karachi",
            subtitle = "Metro area and coast",
            south = 24.70,
            west = 66.75,
            north = 25.10,
            east = 67.30,
            maxZoom = 14.0
    )

    val peshawar = OfflineRegionPack(
            id = "pk-peshawar",
            name = "Peshawar",
            subtitle = "City and surrounding districts",
            south = 33.88,
            west = 71.35,
            north = 34.18,
            east = 71.72,
            maxZoom = 15.0
    )

    val multan = OfflineRegionPack(
            id = "pk-multan",
            name = "Multan",
            subtitle = "City core",
            south = 30.05,
            west = 71.35,
            north = 30.35,
            east = 71.65,
            maxZoom = 14.0
    )

    val pakistan = OfflineRegionPack(
            id = "pk-overview",
            name = "Pakistan overview",
            subtitle = "Whole country, zoomed out",
            south = 23.5,
            west = 60.5,
            north = 37.2,
            east = 77.5,
            minZoom = 5.0,
            maxZoom = 9.0
    )

    val all: List<OfflineRegionPack> =
            listOf(islamabad, lahore, karachi, peshawar, multan, pakistan)

    fun byId(id: String): OfflineRegionPack? = all.firstOrNull { it.id == id }
}
