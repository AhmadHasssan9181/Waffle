package com.noobdev.waffle.api

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
import org.maplibre.android.MapLibre
import org.maplibre.android.location.LocationComponentActivationOptions
import org.maplibre.android.location.modes.CameraMode
import org.maplibre.android.location.modes.RenderMode
import org.maplibre.android.maps.MapView

// MapLibre API call and data classes with a location aware map setup
/*
    * MapLibre Maps SDK integration
    * Documentation: https://maplibre.org/maplibre-gl-native/
    * This file contains functions to interact with the MapLibre Maps SDK
    * to display maps and location data.
* */

val apikey: String = "U7fg3KGqTysSBCZJpaNH"
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

@OptIn(ExperimentalStdlibApi::class, ExperimentalPermissionsApi::class)
@Composable
fun LocationAwareMap(currentStyleIndex: Int) {
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
    LaunchedEffect(Unit) {
        if (!hasLocationPermission) {
            permissionState.launchMultiplePermissionRequest()
        }
    }

    LaunchedEffect(currentStyleIndex) {
        mapboxMap?.setStyle(mapTilerStyles.values.elementAt(currentStyleIndex)) { style ->

            if (hasLocationPermission) {
                try {
                    val options = LocationComponentActivationOptions
                        .builder(context, style)
                        .useDefaultLocationEngine(true)
                        .build()

                    mapboxMap?.locationComponent?.apply {
                        activateLocationComponent(options)
                        isLocationComponentEnabled = true
                        cameraMode = CameraMode.TRACKING
                        renderMode = RenderMode.COMPASS
                    }

                } catch (e: SecurityException) {
                    e.printStackTrace()
                }
            }
        }
    }
    AndroidView(
        factory = { ctx ->
            MapView(ctx).also { mv ->
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    mapboxMap = map
                    map.setStyle(
                        mapTilerStyles.values.elementAt(currentStyleIndex)
                    ) { style ->
                        if (hasLocationPermission) {
                            try {
                                val options = LocationComponentActivationOptions
                                    .builder(ctx, style)
                                    .useDefaultLocationEngine(true)
                                    .build()
                                map.locationComponent.apply {
                                    activateLocationComponent(options)
                                    isLocationComponentEnabled = true
                                    cameraMode = CameraMode.TRACKING
                                    renderMode = RenderMode.COMPASS
                                }
                            }catch (e: SecurityException) {
                                e.printStackTrace()}
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