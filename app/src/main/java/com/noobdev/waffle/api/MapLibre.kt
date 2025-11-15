package com.noobdev.waffle.api

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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


@OptIn(ExperimentalStdlibApi::class, ExperimentalPermissionsApi::class)
@Composable
fun LocationAwareMap() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var mapview by remember { mutableStateOf<MapView?>(null) }
    MapLibre.getInstance(context)
    val locationPermissions= listOf(
        android.Manifest.permission.ACCESS_FINE_LOCATION,
        android.Manifest.permission.ACCESS_COARSE_LOCATION
    )
    val permissionState = rememberMultiplePermissionsState(locationPermissions)
    val hasLocationPermission = permissionState.allPermissionsGranted
    LaunchedEffect(Unit) {
        if(!hasLocationPermission){
            permissionState.launchMultiplePermissionRequest()
        }
    }
    AndroidView(
        factory = {
            ctx ->
            MapView(ctx).also {mv->
                mv.onCreate(null)
                mv.getMapAsync { map ->
                    map.setStyle("https://tiles.planetiler.com/dark-matter/style.json") { style ->

                        if (hasLocationPermission) {
                            try {
                                val options = LocationComponentActivationOptions
                                    .builder(ctx, style)
                                    .useDefaultLocationEngine(true)
                                    .build()

                                map.locationComponent.activateLocationComponent(options)
                                map.locationComponent.isLocationComponentEnabled = true
                                map.locationComponent.cameraMode = CameraMode.TRACKING
                                map.locationComponent.renderMode = RenderMode.COMPASS

                            } catch (e: SecurityException) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
                mapview = mv
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> mapview?.onStart()
                Lifecycle.Event.ON_RESUME -> mapview?.onResume()
                Lifecycle.Event.ON_PAUSE -> mapview?.onPause()
                Lifecycle.Event.ON_STOP -> mapview?.onStop()
                Lifecycle.Event.ON_DESTROY -> mapview?.onDestroy()
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}