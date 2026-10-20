package com.noobdev.waffle.feature.map

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
import com.noobdev.waffle.core.network.NetworkState
import org.maplibre.android.MapLibre
import org.maplibre.android.location.LocationComponentActivationOptions
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
            return mapTilerStyles.getValue(requestedName)
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
}

@OptIn(ExperimentalStdlibApi::class, ExperimentalPermissionsApi::class)
@Composable
fun LocationAwareMap(
        currentStyleIndex: Int,
        modifier: Modifier = Modifier,
        followUser: Boolean = true,
        onMapReady: (MapLibreMap) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapView by remember { mutableStateOf<MapView?>(null) }
    var mapboxMap by remember { mutableStateOf<org.maplibre.android.maps.MapLibreMap?>(null) }
    MapLibre.getInstance(context)
    val locationPermissions = listOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val permissionState = rememberMultiplePermissionsState(locationPermissions)
    val hasLocationPermission = permissionState.allPermissionsGranted
    val styleUrl = remember(currentStyleIndex) { MapStyleResolver.resolve(context, currentStyleIndex) }
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(styleUrl) {
        mapboxMap?.setStyle(styleUrl) { style ->

            if (hasLocationPermission) {
                try {
                    val options = LocationComponentActivationOptions
                        .builder(context, style)
                        .useDefaultLocationEngine(true)
                        .build()

                    mapboxMap?.locationComponent?.apply {
                        activateLocationComponent(options)
                        isLocationComponentEnabled = true
                        cameraMode = if (followUser) CameraMode.TRACKING else CameraMode.NONE
                        renderMode = RenderMode.COMPASS
                    }

                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
    }
    // Navigation owns the camera (zoom/tilt/bearing); don't let the location puck fight it.
    LaunchedEffect(followUser) {
        mapboxMap?.locationComponent?.cameraMode =
                if (followUser) CameraMode.TRACKING else CameraMode.NONE
    }
    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { mv ->
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapboxMap = map
                    map.setStyle(styleUrl) { style ->
                        if (hasLocationPermission) {
                            try {
                                val options = LocationComponentActivationOptions
                                    .builder(ctx, style)
                                    .useDefaultLocationEngine(true)
                                    .build()
                                map.locationComponent.apply {
                                    activateLocationComponent(options)
                                    isLocationComponentEnabled = true
                                    cameraMode =
                                            if (followUser) CameraMode.TRACKING
                                            else CameraMode.NONE
                                    renderMode = RenderMode.COMPASS
                                }
                            }catch (e: SecurityException) {
                                e.printStackTrace()}
                        }
                        onMapReady(map)
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