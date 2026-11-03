package com.noobdev.waffle.feature.map

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.core.navigation.Car3DRenderer
import com.noobdev.waffle.core.navigation.GltfCarRenderer
import kotlinx.coroutines.launch
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap

/**
 * Compose overlay that draws the true 3D vehicle mesh above the map.
 *
 * The mesh is projected by [Car3DRenderer]; this composable only tracks the map's screen
 * projection and the interpolated GPS/bearing values. Keeping it outside the map composable means
 * the 60 fps puck does not recompose the whole MapScreen.
 */
@Composable
internal fun Car3DOverlay(
        map: MapLibreMap?,
        targetLat: Double,
        targetLng: Double,
        targetBearing: Double,
        vehicleIcon: VehicleIcon,
        visible: Boolean,
        modifier: Modifier = Modifier
) {
    val lat = remember { Animatable(targetLat.toFloat()) }
    val lng = remember { Animatable(targetLng.toFloat()) }
    val bearing = remember { Animatable(targetBearing.toFloat()) }
    var initialized by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf<Offset?>(null) }
    var cameraTick by remember { mutableIntStateOf(0) }
    val context = androidx.compose.ui.platform.LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { 100.dp.toPx() }

    LaunchedEffect(targetLat, targetLng, targetBearing) {
        if (!initialized) {
            lat.snapTo(targetLat.toFloat())
            lng.snapTo(targetLng.toFloat())
            bearing.snapTo(targetBearing.toFloat())
            initialized = true
            return@LaunchedEffect
        }
        launch { lat.animateTo(targetLat.toFloat(), tween(1000, easing = LinearEasing)) }
        launch { lng.animateTo(targetLng.toFloat(), tween(1000, easing = LinearEasing)) }
        launch {
            val current = bearing.value
            val delta = ((targetBearing.toFloat() - current + 540f) % 360f) - 180f
            bearing.animateTo(current + delta, tween(1000, easing = LinearEasing))
        }
    }

    DisposableEffect(map) {
        val listener = MapLibreMap.OnCameraMoveListener { cameraTick++ }
        map?.addOnCameraMoveListener(listener)
        onDispose { map?.removeOnCameraMoveListener(listener) }
    }

    LaunchedEffect(map, lat.value, lng.value, cameraTick, visible) {
        if (!visible || map == null) {
            screen = null
            return@LaunchedEffect
        }
        screen =
                runCatching {
                            map.projection.toScreenLocation(
                                    LatLng(lat.value.toDouble(), lng.value.toDouble())
                            )
                        }
                        .getOrNull()
                        ?.let { Offset(it.x, it.y) }
    }

    if (!visible) return

    Canvas(modifier = modifier) {
        val point = screen ?: return@Canvas
        val mapBearing = (map?.cameraPosition?.bearing ?: 0.0).toFloat()
        val relative = ((bearing.value - mapBearing + 540f) % 360f) - 180f
        drawIntoCanvas { canvas ->
            val drawn =
                    GltfCarRenderer.draw(
                            context = context,
                            canvas = canvas.nativeCanvas,
                            centerX = point.x,
                            centerY = point.y,
                            sizePx = sizePx,
                            bearingDegrees = relative,
                            modelKey = vehicleIcon.storageKey
                    )
            if (!drawn) {
                Car3DRenderer.draw(
                        canvas = canvas.nativeCanvas,
                        centerX = point.x,
                        centerY = point.y,
                        sizePx = sizePx,
                        bearingDegrees = relative,
                        modelKey = vehicleIcon.storageKey
                )
            }
        }
    }
}
