package com.noobdev.waffle.feature.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import java.io.File
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.FloatingActionButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.noobdev.waffle.BuildConfig
import com.noobdev.waffle.R
import com.noobdev.waffle.core.network.NetworkState
import org.maplibre.android.MapLibre
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.LocationComponentOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

// MapLibre API call and data classes with a location aware map setup
/*
    * MapLibre Maps SDK integration
    * Documentation: https://maplibre.org/maplibre-gl-native/
    * This file contains functions to interact with the MapLibre Maps SDK
    * to display maps and location data.
* */

val apikey: String = BuildConfig.MAPTILER_API_KEY
val mapTilerStyles = mapOf(
    "Basic" to "https://api.maptiler.com/maps/basic/style.json?key=$apikey",
    "Streets" to "https://api.maptiler.com/maps/streets/style.json?key=$apikey",
    "Outdoor" to "https://api.maptiler.com/maps/outdoor/style.json?key=$apikey",
    "Bright" to "https://api.maptiler.com/maps/bright/style.json?key=$apikey",
    "Dark" to "https://api.maptiler.com/maps/darkmatter/style.json?key=$apikey",
    "Satellite" to "https://api.maptiler.com/maps/satellite/style.json?key=$apikey",
    "Hybrid" to "https://api.maptiler.com/maps/hybrid/style.json?key=$apikey",
    "Topo" to "https://api.maptiler.com/maps/topo/style.json?key=$apikey"
)

/**
 * Keyless global vector basemaps used while online. They carry full labels and do not hit the
 * MapTiler quota that was producing HTTP 429s on this device. The downloaded Pakistan PMTiles
 * pack remains the offline basemap.
 */
val openFreeMapStyles = mapOf(
    "Basic" to "https://tiles.openfreemap.org/styles/positron",
    "Streets" to "https://tiles.openfreemap.org/styles/liberty",
    "Outdoor" to "https://tiles.openfreemap.org/styles/bright",
    "Bright" to "https://tiles.openfreemap.org/styles/bright",
    "Dark" to "https://basemaps.cartocdn.com/gl/dark-matter-gl-style/style.json"
)

/** Index for a named style, or Streets when the name is unknown. */
fun styleIndexFor(name: String): Int = mapTilerStyles.keys.indexOf(name).takeIf { it >= 0 } ?: 1

/** Style used when the user has not picked one: dark chrome deserves a dark basemap. */
fun autoStyleIndex(darkUi: Boolean): Int = styleIndexFor(if (darkUi) "Dark" else "Streets")

/**
 * The offline packs are downloaded from a specific style. Keeping the style URL canonical means a
 * cached pack always has a matching style to load when the network drops.
 */
object MapStyleResolver {

    const val OFFLINE_STYLE_NAME = "Streets"

    fun offlineBasemap(): String = mapTilerStyles.getValue(OFFLINE_STYLE_NAME)

    fun styleUrlFor(layer: com.noobdev.waffle.feature.offline.OfflineLayer): String =
            mapTilerStyles[layer.styleName] ?: offlineBasemap()

    fun resolve(context: android.content.Context, index: Int): String {
        val requestedName =
                mapTilerStyles.keys.elementAtOrNull(index) ?: OFFLINE_STYLE_NAME
        if (NetworkState.isOnline(context)) {
            return openFreeMapStyles[requestedName]
                    ?: mapTilerStyles.getValue(requestedName)
        }

        // Offline: use the requested style if that layer was downloaded, otherwise the first
        // downloaded layer, otherwise Streets (the map will show cached tiles if any exist).
        val installedLayers =
                com.noobdev.waffle.feature.offline.OfflineLayer.entries.filter { layer ->
                    com.noobdev.waffle.feature.offline.OfflineMapManager.installed.value.any { region ->
                        region.id.endsWith(":${layer.key}")
                    }
                }
        val chosen =
                installedLayers.firstOrNull { it.styleName == requestedName }
                        ?: installedLayers.firstOrNull()
                        ?: com.noobdev.waffle.feature.offline.OfflineLayer.STREETS
        return mapTilerStyles[chosen.styleName] ?: offlineBasemap()
    }

    /**
     * The Pakistan-wide OpenMapTiles archive from the offline bundle. PMTiles is supported by
     * MapLibre Native 11.7+ directly, so this avoids a local HTTP tile server and a 500 MB
     * MapTiler download. It is intentionally label-free: fonts and sprites stay optional, while
     * Waffle's own pins, route and labels remain on top.
     */
    /**
     * The new Pakistan vector stack: OpenMapTiles basemap + Overture places, with all labels and
     * POIs. It is kept in app external storage because the two PMTiles archives are ~500 MB.
     */
    fun localVectorStyle(context: Context, styleIndex: Int): String? {
        val dirs = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
        fun first(name: String): File? = dirs.map { File(it, name) }.firstOrNull { it.isFile }

        val vector =
                first("pakistan_vector.pmtiles")
                        ?: return logMissingPack(context, "pakistan_vector.pmtiles")
        val places =
                first("pakistan_places.pmtiles")
                        ?: return logMissingPack(context, "pakistan_places.pmtiles")
        val fontRoot =
                dirs.map { File(it, "fonts") }.firstOrNull { it.isDirectory }
                        ?: return logMissingPack(context, "fonts/ (extract fonts.zip)")
        val spriteRoot =
                dirs.map { File(it, "style") }
                        .firstOrNull { File(it, "sprite.json").isFile }
                        ?: return logMissingPack(context, "style/sprite.json (extract sprite.zip)")

        val requestedName = mapTilerStyles.keys.elementAtOrNull(styleIndex) ?: OFFLINE_STYLE_NAME
        val theme =
                when (requestedName) {
                    "Dark" -> "dark"
                    "Outdoor" -> "field"
                    else -> "light"
                }
        val styleAsset = "vector_styles/style_${theme}_both.json"
        val template =
                runCatching {
                            context.assets.open(styleAsset).bufferedReader().use { it.readText() }
                        }
                        .getOrNull()
                        ?: return null
        return template
                .replace(
                        "pmtiles://pakistan_vector.pmtiles",
                        "pmtiles://file://${vector.absolutePath}"
                )
                .replace(
                        "pmtiles://pakistan_places.pmtiles",
                        "pmtiles://file://${places.absolutePath}"
                )
                .replace(
                        "assets/fonts/{fontstack}/{range}.pbf",
                        "file://${fontRoot.absolutePath}/{fontstack}/{range}.pbf"
                )
                .replace(
                        "assets/sprite/sprite",
                        "file://${spriteRoot.absolutePath}/sprite"
                )
    }

    /** Logs which pack piece is missing, so `adb logcat` explains the online fallback. */
    private fun logMissingPack(context: Context, missing: String): String? {
        val status = MapPackStatus.read(context)
        Log.i(
                "MapStyles",
                "Local map pack incomplete (missing $missing); using the online basemap. " +
                        "vector=${MapPackStatus.formatSize(status.vectorBytes)} " +
                        "places=${MapPackStatus.formatSize(status.placesBytes)} " +
                        "search=${MapPackStatus.formatSize(status.searchBytes)} " +
                        "fonts=${status.hasFonts} sprite=${status.hasSprite}"
        )
        return null
    }

    fun localPmtilesStyle(context: Context): String? {
        val dirs = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
        val protomapsFile =
                dirs.map { File(it, "pakistan_protomaps.pmtiles") }
                        .firstOrNull { it.isFile }
        val file =
                (if (protomapsFile != null) listOf(protomapsFile) else emptyList()) +
                        dirs.flatMap { dir ->
                            listOf(
                                    File(dir, "pakistan_vector.pmtiles"),
                                    File(dir, "pakistan.pmtiles"),
                                    File(dir, "rawalpindi.pmtiles")
                            )
                        }.filter { it.isFile }
        val chosen = file.firstOrNull() ?: return null
        val url = "pmtiles://file://${chosen.absolutePath}"
        val fontRoot =
                listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
                        .map { File(it, "fonts") }
                        .firstOrNull { it.isDirectory }
        val spriteRoot =
                listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
                        .map { File(it, "style") }
                        .firstOrNull { File(it, "sprite.json").isFile }
        if (protomapsFile != null && fontRoot != null) {
            runCatching {
                        val template =
                                context.assets
                                        .open("protomaps_style.json")
                                        .bufferedReader()
                                        .use { it.readText() }
                        return template
                                .replace("__PMTILES__", url)
                                .replace(
                                        "__GLYPHS__",
                                        "file://${fontRoot.absolutePath}/{fontstack}/{range}.pbf"
                                )
                    }
                    .onFailure { Log.w("MapStyles", "Could not load Protomaps style", it) }
        }
        if (fontRoot != null && spriteRoot != null) {
            runCatching {
                        val template =
                                context.assets
                                        .open("pakistan_style.json")
                                        .bufferedReader()
                                        .use { it.readText() }
                        return template
                                .replace("__PMTILES__", url)
                                .replace(
                                        "__GLYPHS__",
                                        "file://${fontRoot.absolutePath}/{fontstack}/{range}.pbf"
                                )
                                .replace(
                                        "__SPRITE__",
                                        "file://${spriteRoot.absolutePath}/sprite"
                                )
                    }
                    .onFailure { Log.w("MapStyles", "Could not load full Pakistan style", it) }
        }
        val glyphs =
                fontRoot?.let {
                    "\"glyphs\": \"file://${it.absolutePath}/{fontstack}/{range}.pbf\","
                } ?: ""
        val labels =
                if (fontRoot != null) {
                    """
                    ,
                    {"id":"place-labels","type":"symbol","source":"base","source-layer":"place","minzoom":7,
                     "layout":{"text-field":["coalesce",["get","name:latin"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],7,10,14,16],"text-anchor":"center"},
                     "paint":{"text-color":"#E2E8F0","text-halo-color":"#0B1220","text-halo-width":1.4}},
                    {"id":"road-labels","type":"symbol","source":"base","source-layer":"transportation_name","minzoom":12,
                     "layout":{"text-field":["coalesce",["get","name:latin"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],12,9,15,12],"symbol-placement":"line","text-rotation-alignment":"map"},
                     "paint":{"text-color":"#94A3B8","text-halo-color":"#0B1220","text-halo-width":1.2}},
                    {"id":"water-labels","type":"symbol","source":"base","source-layer":"water_name","minzoom":10,
                     "layout":{"text-field":["coalesce",["get","name:latin"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":11,"text-anchor":"center"},
                     "paint":{"text-color":"#67E8F9","text-halo-color":"#0B1220","text-halo-width":1.2}}
                    """.trimIndent()
                } else ""
        return """
        {
          "version": 8,
          "name": "Waffle Pakistan offline",
          $glyphs
          "sources": {
            "base": {
              "type": "vector",
              "url": "$url",
              "attribution": "© OpenMapTiles © OpenStreetMap contributors"
            }
          },
          "layers": [
            {"id":"background","type":"background","paint":{"background-color":"#0B1220"}},
            {"id":"landcover","type":"fill","source":"base","source-layer":"landcover","paint":{"fill-color":"#10291F","fill-opacity":0.9}},
            {"id":"landuse","type":"fill","source":"base","source-layer":"landuse","paint":{"fill-color":"#132033","fill-opacity":0.8}},
            {"id":"park","type":"fill","source":"base","source-layer":"park","paint":{"fill-color":"#0F2E26","fill-opacity":0.9}},
            {"id":"water","type":"fill","source":"base","source-layer":"water","paint":{"fill-color":"#0B2A3A"}},
            {"id":"waterway","type":"line","source":"base","source-layer":"waterway","paint":{"line-color":"#0E7490","line-width":["interpolate",["linear"],["zoom"],8,0.6,14,2.2]}},
            {"id":"building","type":"fill","source":"base","source-layer":"building","minzoom":13,"paint":{"fill-color":"#1E293B","fill-opacity":0.7}},
            {"id":"road-casing","type":"line","source":"base","source-layer":"transportation","paint":{"line-color":"#080D17","line-width":["interpolate",["linear"],["zoom"],8,1,14,9]}},
            {"id":"road","type":"line","source":"base","source-layer":"transportation","paint":{"line-color":"#64748B","line-width":["interpolate",["linear"],["zoom"],8,0.5,14,5]}},
            {"id":"boundary","type":"line","source":"base","source-layer":"boundary","paint":{"line-color":"#F59E0B","line-opacity":0.28,"line-width":1}}
            $labels
          ]
        }
        """.trimIndent()
    }

    /**
     * A complete downloaded region can still be used when an online style request fails (for
     * example MapTiler returns HTTP 429 for this network). Prefer the region containing the
     * current fix, otherwise the first complete pack, so a blank basemap can recover.
     */
    fun offlineRegionContaining(
            latitude: Double,
            longitude: Double
    ): com.noobdev.waffle.feature.offline.InstalledOfflineRegion? =
            com.noobdev.waffle.feature.offline.OfflineMapManager.installedRegionFor(
                            latitude,
                            longitude
                    )
                    ?.takeIf { !it.styleUrl.isNullOrBlank() }

    fun cachedOfflineRegion(
            latitude: Double?,
            longitude: Double?
    ): com.noobdev.waffle.feature.offline.InstalledOfflineRegion? {
        val installed =
                com.noobdev.waffle.feature.offline.OfflineMapManager.installed.value
        val containing =
                if (latitude != null && longitude != null) {
                    com.noobdev.waffle.feature.offline.OfflineMapManager.installedRegionFor(
                            latitude,
                            longitude
                    )
                } else null
        return containing
                ?: installed.firstOrNull {
                    it.complete && !it.styleUrl.isNullOrBlank()
                }
    }

    /**
     * Sprite-free vector style JSON for [org.maplibre.android.snapshotter.MapSnapshotter] in the
     * floating navigation overlay.
     *
     * Matches the main map's tile source and color theme ([styleIndex]: Light, Dark, or Outdoor /
     * Field) instead of falling back to MapTiler's rate-limited tiles/v3 endpoint:
     * 1. Uses the local `pakistan_vector.pmtiles` / `pakistan.pmtiles` archive when installed.
     * 2. Uses OpenFreeMap's keyless Planet vector tiles when online without a local PMTiles pack.
     * 3. Falls back to cached MapTiler vector tiles only when offline without PMTiles.
     *
     * Remote sprite sheets are omitted so a network error can never trigger MapLibre Native's
     * sprite-error abort inside the snapshotter; local glyphs are included when present on disk so
     * road and place names still render cleanly.
     */
    fun snapshotStyleJson(context: Context, styleIndex: Int): String {
        val dirs = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
        val localPmtiles =
                dirs.flatMap { dir ->
                            listOf(
                                    File(dir, "pakistan_vector.pmtiles"),
                                    File(dir, "pakistan.pmtiles"),
                                    File(dir, "rawalpindi.pmtiles")
                            )
                        }
                        .firstOrNull { it.isFile }
        val fontRoot = dirs.map { File(it, "fonts") }.firstOrNull { it.isDirectory }
        val online = NetworkState.isOnline(context)

        val requestedName = mapTilerStyles.keys.elementAtOrNull(styleIndex) ?: OFFLINE_STYLE_NAME
        val isDark = requestedName == "Dark"
        val isField = requestedName == "Outdoor"

        val bg =
                when {
                    isDark -> "#0E1621"
                    isField -> "#F1EFE7"
                    else -> "#F8F5EE"
                }
        val landuse =
                when {
                    isDark -> "#131F2E"
                    isField -> "#E7E4D8"
                    else -> "#EFECE4"
                }
        val park =
                when {
                    isDark -> "#112A22"
                    isField -> "#CFE3C8"
                    else -> "#D9EAD3"
                }
        val water =
                when {
                    isDark -> "#0B293B"
                    isField -> "#8CC5DE"
                    else -> "#9FD3E8"
                }
        val waterway =
                when {
                    isDark -> "#125E7A"
                    isField -> "#5AA9C8"
                    else -> "#6CB7D4"
                }
        val building =
                when {
                    isDark -> "#1B2838"
                    isField -> "#DFDBD0"
                    else -> "#E5E0D5"
                }
        val minorRoadCasing =
                when {
                    isDark -> "#091019"
                    isField -> "#D5CFC2"
                    else -> "#DCD6C9"
                }
        val minorRoad =
                when {
                    isDark -> "#33475E"
                    isField -> "#FFFFFF"
                    else -> "#FFFFFF"
                }
        val majorRoadCasing =
                when {
                    isDark -> "#091019"
                    isField -> "#C8B89A"
                    else -> "#D1C2A5"
                }
        val majorRoad =
                when {
                    isDark -> "#4F6885"
                    isField -> "#FFF8E8"
                    else -> "#FFF9EB"
                }
        val highwayCasing =
                when {
                    isDark -> "#0A1018"
                    else -> "#C48A2C"
                }
        val highway =
                when {
                    isDark -> "#D99B38"
                    else -> "#F6C26B"
                }
        val textPrimary = if (isDark) "#E2E8F0" else "#1E293B"
        val textSecondary = if (isDark) "#94A3B8" else "#475569"
        val textHalo = if (isDark) "#0E1621" else "#FFFFFF"

        val sourceBlock =
                if (localPmtiles != null) {
                    """
                    "base": {
                      "type": "vector",
                      "url": "pmtiles://file://${localPmtiles.absolutePath}"
                    }
                    """.trimIndent()
                } else if (online) {
                    """
                    "base": {
                      "type": "vector",
                      "url": "https://tiles.openfreemap.org/planet"
                    }
                    """.trimIndent()
                } else {
                    val tiles = "https://api.maptiler.com/tiles/v3/{z}/{x}/{y}?key=$apikey"
                    """
                    "base": {
                      "type": "vector",
                      "tiles": ["$tiles"],
                      "minzoom": 0,
                      "maxzoom": 14
                    }
                    """.trimIndent()
                }

        val glyphsLine =
                when {
                    fontRoot != null ->
                            "\"glyphs\": \"file://${fontRoot.absolutePath}/{fontstack}/{range}.pbf\","
                    online && localPmtiles == null ->
                            "\"glyphs\": \"https://tiles.openfreemap.org/fonts/{fontstack}/{range}.pbf\","
                    else -> ""
                }

        val hasGlyphs = glyphsLine.isNotEmpty()
        val labelLayers =
                if (hasGlyphs) {
                    """
                    ,
                    {"id":"road-labels","type":"symbol","source":"base","source-layer":"transportation_name","minzoom":13,
                     "layout":{"text-field":["coalesce",["get","name:en"],["get","name:latin"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],13,9.5,16,12],"symbol-placement":"line","text-rotation-alignment":"map"},
                     "paint":{"text-color":"$textSecondary","text-halo-color":"$textHalo","text-halo-width":1.4}},
                    {"id":"place-labels","type":"symbol","source":"base","source-layer":"place","minzoom":8,
                     "layout":{"text-field":["coalesce",["get","name:en"],["get","name:latin"],["get","name"]],"text-font":["Noto Sans Regular"],"text-size":["interpolate",["linear"],["zoom"],8,10,15,14],"text-anchor":"center"},
                     "paint":{"text-color":"$textPrimary","text-halo-color":"$textHalo","text-halo-width":1.5}}
                    """.trimIndent()
                } else ""

        return """
        {
          "version": 8,
          "name": "Waffle floating map",
          $glyphsLine
          "sources": {
            $sourceBlock
          },
          "layers": [
            {"id":"background","type":"background","paint":{"background-color":"$bg"}},
            {"id":"landcover","type":"fill","source":"base","source-layer":"landcover","paint":{"fill-color":"$park","fill-opacity":0.55}},
            {"id":"landuse","type":"fill","source":"base","source-layer":"landuse","paint":{"fill-color":"$landuse","fill-opacity":0.85}},
            {"id":"park","type":"fill","source":"base","source-layer":"park","paint":{"fill-color":"$park","fill-opacity":0.85}},
            {"id":"water","type":"fill","source":"base","source-layer":"water","paint":{"fill-color":"$water"}},
            {"id":"waterway","type":"line","source":"base","source-layer":"waterway","paint":{"line-color":"$waterway","line-width":["interpolate",["linear"],["zoom"],8,0.6,15,2.2]}},
            {"id":"building","type":"fill","source":"base","source-layer":"building","minzoom":13,"paint":{"fill-color":"$building","fill-opacity":0.78}},
            {"id":"road-minor-casing","type":"line","source":"base","source-layer":"transportation","filter":["!in","class","motorway","trunk","primary","secondary"],"paint":{"line-color":"$minorRoadCasing","line-width":["interpolate",["linear"],["zoom"],11,1.2,16,7]}},
            {"id":"road-minor","type":"line","source":"base","source-layer":"transportation","filter":["!in","class","motorway","trunk","primary","secondary"],"paint":{"line-color":"$minorRoad","line-width":["interpolate",["linear"],["zoom"],11,0.6,16,4.8]}},
            {"id":"road-major-casing","type":"line","source":"base","source-layer":"transportation","filter":["in","class","primary","secondary"],"paint":{"line-color":"$majorRoadCasing","line-width":["interpolate",["linear"],["zoom"],9,1.8,16,10]}},
            {"id":"road-major","type":"line","source":"base","source-layer":"transportation","filter":["in","class","primary","secondary"],"paint":{"line-color":"$majorRoad","line-width":["interpolate",["linear"],["zoom"],9,1.1,16,7.2]}},
            {"id":"road-highway-casing","type":"line","source":"base","source-layer":"transportation","filter":["in","class","motorway","trunk"],"paint":{"line-color":"$highwayCasing","line-width":["interpolate",["linear"],["zoom"],7,1.8,16,12]}},
            {"id":"road-highway","type":"line","source":"base","source-layer":"transportation","filter":["in","class","motorway","trunk"],"paint":{"line-color":"$highway","line-width":["interpolate",["linear"],["zoom"],7,1.1,16,8.5]}},
            {"id":"boundary","type":"line","source":"base","source-layer":"boundary","paint":{"line-color":"#F59E0B","line-opacity":0.28,"line-width":1}}
            $labelLayers
          ]
        }
        """.trimIndent()
    }
}

/**
 * The blue dot becomes a small travel-mode badge: car, bike or walk. The bearing arrow stays
 * Honey so heading is readable in both themes.
 */
private fun locationOptions(
        context: Context,
        travelMode: String,
        hidePuck: Boolean
): LocationComponentOptions {
    val puck =
            when (travelMode) {
                "cycling-regular" -> R.drawable.ic_nav_puck_bike
                "foot-walking" -> R.drawable.ic_nav_puck_walk
                else -> R.drawable.ic_nav_puck_car
            }
    val foreground = if (hidePuck) R.drawable.ic_nav_puck_transparent else puck
    val bearing =
            if (hidePuck) R.drawable.ic_nav_puck_transparent
            else R.drawable.ic_nav_bearing
    val builder =
            LocationComponentOptions.builder(context)
                    .foregroundDrawable(foreground)
                    .bearingDrawable(bearing)
    if (hidePuck) {
        builder.accuracyAlpha(0f)
                .accuracyColor(Color.TRANSPARENT)
                .accuracyAnimationEnabled(false)
                .pulseEnabled(false)
                .pulseColor(Color.TRANSPARENT)
                .pulseAlpha(0f)
    }
    return builder.build()
}

@OptIn(ExperimentalStdlibApi::class, ExperimentalPermissionsApi::class)
@SuppressLint("MissingPermission")
@Composable
fun LocationAwareMap(
        currentStyleIndex: Int,
        modifier: Modifier = Modifier,
        followUser: Boolean = true,
        travelMode: String = "driving-car",
        hidePuck: Boolean = false,
        onMapReady: (MapLibreMap) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var online by remember { mutableStateOf(NetworkState.isOnline(context)) }
    DisposableEffect(context) {
        val manager =
                context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val callback =
                object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        online = NetworkState.isOnline(context)
                    }

                    override fun onLost(network: Network) {
                        online = NetworkState.isOnline(context)
                    }

                    override fun onCapabilitiesChanged(
                            network: Network,
                            networkCapabilities: android.net.NetworkCapabilities
                    ) {
                        online = NetworkState.isOnline(context)
                    }
                }
        runCatching { manager?.registerDefaultNetworkCallback(callback) }
        onDispose { runCatching { manager?.unregisterNetworkCallback(callback) } }
    }
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapboxMap by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    MapLibre.getInstance(context)
    val locationPermissions = listOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val permissionState = rememberMultiplePermissionsState(locationPermissions)
    val hasLocationPermission = permissionState.allPermissionsGranted
    val styleUrl =
            remember(currentStyleIndex, online) {
                MapStyleResolver.resolve(context, currentStyleIndex)
            }
    val localVector =
            remember(currentStyleIndex) {
                MapStyleResolver.localVectorStyle(context, currentStyleIndex)
            }
    val inPakistan =
            MapSessionState.currentLat.value in 23.3..37.2 &&
                    MapSessionState.currentLng.value in 60.8..77.3
    val useLocalVector = localVector != null && (!online || inPakistan)
    val localPmtiles = remember(currentStyleIndex) { MapStyleResolver.localPmtilesStyle(context) }
    val mapTilerOffline =
            remember(online, MapSessionState.hasLocation.value, MapSessionState.currentLat.value,
                    MapSessionState.currentLng.value) {
                if (!online && MapSessionState.hasLocation.value) {
                    MapStyleResolver.offlineRegionContaining(
                            MapSessionState.currentLat.value,
                            MapSessionState.currentLng.value
                    )
                } else null
            }
    val useLocalPmtiles =
            localPmtiles != null && !online && !useLocalVector && mapTilerOffline == null
    fun applyStyle(map: MapLibreMap, onLoaded: (org.maplibre.android.maps.Style) -> Unit) {
        if (useLocalVector) {
            map.setStyle(
                    org.maplibre.android.maps.Style.Builder().fromJson(localVector!!),
                    onLoaded
            )
            return
        }
        val cachedStyle = mapTilerOffline?.styleUrl
        if (!online && cachedStyle != null) {
            // Prefer the MapTiler offline pack that the user already downloaded. It has the
            // provider's labels, sprites and styling, and covers the local area better than the
            // older Pakistan PMTiles archive.
            map.setStyle(cachedStyle, onLoaded)
        } else if (useLocalPmtiles) {
            map.setStyle(
                    org.maplibre.android.maps.Style.Builder().fromJson(localPmtiles!!),
                    onLoaded
            )
        } else {
            map.setStyle(styleUrl, onLoaded)
        }
    }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(styleUrl, localVector, localPmtiles, online, mapTilerOffline, useLocalVector) {
        mapboxMap?.let { map ->
            applyStyle(map) { style ->
                if (hasLocationPermission) {
                try {
                    val options = LocationComponentActivationOptions
                        .builder(context, style)
                        .useDefaultLocationEngine(true)
                        .locationComponentOptions(locationOptions(context, travelMode, hidePuck))
                        .build()

                    mapboxMap?.locationComponent?.apply {
                        activateLocationComponent(options)
                        isLocationComponentEnabled = !hidePuck
                        cameraMode = if (followUser) CameraMode.TRACKING else CameraMode.NONE
                        renderMode = RenderMode.COMPASS
                    }

                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
                }
            }
        }
    }
    // Navigation owns the camera (zoom/tilt/bearing); don't let the location puck fight it.
    LaunchedEffect(followUser) {
        runCatching {
            mapboxMap?.locationComponent?.cameraMode =
                    if (followUser) CameraMode.TRACKING else CameraMode.NONE
        }
    }
    // Swap the puck badge when the travel mode changes.
    LaunchedEffect(travelMode, hidePuck) {
        if (!hasLocationPermission) return@LaunchedEffect
        mapboxMap?.let { map ->
            map.getStyle { style ->
                runCatching {
                    val options = LocationComponentActivationOptions
                        .builder(context, style)
                        .useDefaultLocationEngine(true)
                        .locationComponentOptions(locationOptions(context, travelMode, hidePuck))
                        .build()
                    map.locationComponent.apply {
                        activateLocationComponent(options)
                        isLocationComponentEnabled = !hidePuck
                        cameraMode =
                                if (followUser) CameraMode.TRACKING else CameraMode.NONE
                        renderMode = RenderMode.COMPASS
                    }
                }
            }
        }
    }
    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { mv ->
                var triedOfflineFallback = false
                mv.addOnDidFailLoadingMapListener {
                    if (triedOfflineFallback) return@addOnDidFailLoadingMapListener
                    val latitude =
                            if (MapSessionState.hasLocation.value)
                                    MapSessionState.currentLat.value
                            else null
                    val longitude =
                            if (MapSessionState.hasLocation.value)
                                    MapSessionState.currentLng.value
                            else null
                    val pmtiles = localPmtiles ?: MapStyleResolver.localPmtilesStyle(ctx)
                    if (pmtiles != null) {
                        triedOfflineFallback = true
                        MapLibre.setConnected(false)
                        mapboxMap?.setStyle(
                                org.maplibre.android.maps.Style.Builder().fromJson(pmtiles)
                        ) { _ ->
                            mapboxMap?.let { map ->
                                onMapReady(map)
                                map.cameraPosition =
                                        org.maplibre.android.camera.CameraPosition.Builder()
                                                .target(
                                                        org.maplibre.android.geometry.LatLng(
                                                                latitude ?: 30.3753,
                                                                longitude ?: 69.3451
                                                        )
                                                )
                                                .zoom(if (latitude != null) 14.0 else 5.5)
                                                .build()
                            }
                        }
                        return@addOnDidFailLoadingMapListener
                    }

                    val region = MapStyleResolver.cachedOfflineRegion(latitude, longitude)
                    val fallback = region?.styleUrl
                    if (fallback.isNullOrBlank() || fallback == styleUrl) {
                        return@addOnDidFailLoadingMapListener
                    }
                    triedOfflineFallback = true
                    // A provider error such as HTTP 429 must not leave a beige screen. Cached
                    // regions already contain the style and tiles, so force MapLibre onto disk.
                    MapLibre.setConnected(false)
                    mapboxMap?.setStyle(fallback) { _ ->
                        mapboxMap?.let { map ->
                            onMapReady(map)
                            map.cameraPosition =
                                    org.maplibre.android.camera.CameraPosition.Builder()
                                            .target(
                                                    org.maplibre.android.geometry.LatLng(
                                                            latitude
                                                                    ?: ((region.south + region.north) / 2.0),
                                                            longitude
                                                                    ?: ((region.west + region.east) / 2.0)
                                                    )
                                            )
                                            .zoom(if (latitude != null) 15.0 else 11.5)
                                            .build()
                        }
                    }
                }
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapboxMap = map
                    // The map is gesture-first; hide MapLibre's native compass chrome.
                    map.uiSettings.isCompassEnabled = false
                    applyStyle(map) { style ->
                        if (hasLocationPermission) {
                            try {
                                val options = LocationComponentActivationOptions
                                    .builder(ctx, style)
                                    .useDefaultLocationEngine(true)
                                    .locationComponentOptions(locationOptions(ctx, travelMode, hidePuck))
                                    .build()
                                map.locationComponent.apply {
                                    activateLocationComponent(options)
                                    isLocationComponentEnabled = !hidePuck
                                    cameraMode =
                                            if (followUser) CameraMode.TRACKING
                                            else CameraMode.NONE
                                    renderMode = RenderMode.COMPASS
                                }
                            }catch (e: SecurityException) {
                                e.printStackTrace()}
                        }
                        onMapReady(map)
                        if (!NetworkState.isOnline(ctx) &&
                                        !MapSessionState.hasLocation.value
                        ) {
                            MapStyleResolver.cachedOfflineRegion(null, null)?.let { region ->
                                map.cameraPosition =
                                        org.maplibre.android.camera.CameraPosition.Builder()
                                                .target(
                                                        org.maplibre.android.geometry.LatLng(
                                                                (region.south + region.north) / 2.0,
                                                                (region.west + region.east) / 2.0
                                                        )
                                                )
                                                .zoom(11.5)
                                                .build()
                            }
                        }
                    }
                }
                mapView = mv
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapView?.onStart()
                Lifecycle.Event.ON_RESUME -> mapView?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView?.onPause()
                Lifecycle.Event.ON_STOP -> mapView?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapView?.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
