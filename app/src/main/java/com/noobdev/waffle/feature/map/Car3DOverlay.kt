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
import com.noobdev.waffle.core.navigation.TurnSignal
import com.noobdev.waffle.core.navigation.VehicleLights
import kotlinx.coroutines.delay
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
        modelKey: String,
        visible: Boolean,
        speedMps: Float = 0f,
        turnSignal: TurnSignal = TurnSignal.NONE,
        night: Boolean = false,
        modifier: Modifier = Modifier
) {
    val lat = remember { Animatable(targetLat.toFloat()) }
    val lng = remember { Animatable(targetLng.toFloat()) }
    val bearing = remember { Animatable(targetBearing.toFloat()) }
    var initialized by remember { mutableStateOf(false) }
    var cameraTick by remember { mutableIntStateOf(0) }
    var blinkOn by remember { mutableStateOf(true) }
    var braking by remember { mutableStateOf(false) }
    var lastSpeed by remember { mutableStateOf(0f) }

    LaunchedEffect(turnSignal) {
        if (turnSignal == TurnSignal.NONE) {
            blinkOn = true
            return@LaunchedEffect
        }
        while (true) {
            blinkOn = !blinkOn
            delay(450)
        }
    }
    LaunchedEffect(speedMps) {
        braking = speedMps < 0.5f || (lastSpeed > 0f && speedMps < lastSpeed - 0.6f)
        lastSpeed = speedMps
    }
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

    if (!visible) return

    // Reading the camera revision in the composable body forces a redraw on every camera move;
    // the projection is then resolved in the draw phase, so the car cannot lag behind a zoom.
    val cameraRevision = cameraTick

    Canvas(modifier = modifier) {
        val mapLibre = map ?: return@Canvas
        if (cameraRevision < 0) return@Canvas
        val point =
                runCatching {
                            mapLibre.projection.toScreenLocation(
                                    LatLng(lat.value.toDouble(), lng.value.toDouble())
                            )
                        }
                        .getOrNull()
                        ?: return@Canvas
        val mapBearing = mapLibre.cameraPosition.bearing.toFloat()
        val relative = ((bearing.value - mapBearing + 540f) % 360f) - 180f
        val lights =
                VehicleLights(
                        headlights = night,
                        braking = braking,
                        turnSignal = turnSignal,
                        blinkOn = blinkOn
                )
        drawIntoCanvas { canvas ->
            val drawn =
                    GltfCarRenderer.draw(
                            context = context,
                            canvas = canvas.nativeCanvas,
                            centerX = point.x,
                            centerY = point.y,
                            sizePx = sizePx,
                            bearingDegrees = relative,
                            modelKey = modelKey,
                            lights = lights
                    )
            if (!drawn) {
                Car3DRenderer.draw(
                        canvas = canvas.nativeCanvas,
                        centerX = point.x,
                        centerY = point.y,
                        sizePx = sizePx,
                        bearingDegrees = relative,
                        modelKey = modelKey
                )
            }
        }
    }
}
