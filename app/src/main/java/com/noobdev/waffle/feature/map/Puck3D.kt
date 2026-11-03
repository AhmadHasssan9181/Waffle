package com.noobdev.waffle.feature.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.noobdev.waffle.R
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconPitchAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconRotate
import org.maplibre.android.style.layers.PropertyFactory.iconRotationAlignment
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * Keeps the puck animation in its own composable. Reading the animated values here means only this
 * small subtree recomposes at 60 fps instead of the whole map screen.
 */
@Composable
internal fun AnimatedPuck3D(
        map: MapLibreMap?,
        styleIndex: Int,
        targetLat: Double,
        targetLng: Double,
        targetBearing: Double,
        travelMode: String,
        vehicleIcon: VehicleIcon = VehicleIcon.SEDAN,
        isNavigating: Boolean = false
) {
    val lat = remember { Animatable(targetLat.toFloat()) }
    val lng = remember { Animatable(targetLng.toFloat()) }
    val bearing = remember { Animatable(targetBearing.toFloat()) }
    var initialized by remember { mutableStateOf(false) }

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

    Puck3D(
            map = map,
            styleIndex = styleIndex,
            lat = lat.value.toDouble(),
            lng = lng.value.toDouble(),
            bearing = bearing.value.toDouble(),
            travelMode = travelMode,
            vehicleIcon = vehicleIcon,
            isNavigating = isNavigating
    )
}

/**
 * The 3D navigation puck.
 *
 * MapLibre's location component draws a flat dot and its Marker API cannot rotate, so the puck is a
 * SymbolLayer whose `iconRotate` follows the GPS bearing. The bitmap itself is an extruded arrow with
 * a small car/bike/walk badge, so it reads as 3D without a 3D engine.
 */
@Composable
internal fun Puck3D(
        map: MapLibreMap?,
        styleIndex: Int,
        lat: Double,
        lng: Double,
        bearing: Double,
        travelMode: String,
        vehicleIcon: VehicleIcon = VehicleIcon.SEDAN,
        isNavigating: Boolean = false
) {
    val context = LocalContext.current
    val isCarMode = travelMode == "driving-car"
    val iconName =
            "puck-3d-$travelMode-${vehicleIcon.storageKey}-${if (isNavigating) "nav" else "dot"}"
    val drawableRes =
            when {
                !isNavigating -> R.drawable.ic_puck_dot
                travelMode == "cycling-regular" -> R.drawable.ic_puck_bike_3d
                travelMode == "foot-walking" -> R.drawable.ic_puck_walk_3d
                isCarMode && vehicleIcon == VehicleIcon.ARROW -> R.drawable.ic_puck_arrow_3d
                else -> vehicleIcon.drawable
            }
    val iconSize = if (isNavigating) 0.55f else 0.30f
    val bitmap =
            remember(drawableRes) {
                BitmapFactory.decodeResource(context.resources, drawableRes)
            }
    var source by remember { mutableStateOf<GeoJsonSource?>(null) }

    LaunchedEffect(map, styleIndex, iconName, bitmap) {
        val mapLibre = map ?: return@LaunchedEffect
        mapLibre.getStyle { style ->
            runCatching {
                style.removeImage(iconName)
                style.addImage(iconName, bitmap)

                source = null
                val geoJson = puckGeoJson(lat, lng, bearing, iconName, iconSize)
                val existing = style.getSourceAs<GeoJsonSource>("puck-source")
                val puckSource =
                        existing
                                ?: GeoJsonSource("puck-source", geoJson).also {
                                    style.addSource(it)
                                }
                puckSource.setGeoJson(geoJson)
                source = puckSource

                if (style.getLayer("puck-layer") == null) {
                    val layer =
                            SymbolLayer("puck-layer", "puck-source")
                                    .withProperties(
                                            iconImage(Expression.get("icon")),
                                            iconSize(Expression.get("size")),
                                            iconRotate(Expression.get("bearing")),
                                            iconRotationAlignment(
                                                    Property.ICON_ROTATION_ALIGNMENT_MAP
                                            ),
                                            iconPitchAlignment(
                                                    Property.ICON_PITCH_ALIGNMENT_VIEWPORT
                                            ),
                                            iconAllowOverlap(true),
                                            iconIgnorePlacement(true),
                                            iconAnchor(Property.ICON_ANCHOR_CENTER)
                                    )
                    if (style.getLayer("route-layer") != null) {
                        style.addLayerAbove(layer, "route-layer")
                    } else {
                        style.addLayer(layer)
                    }
                }
            }
        }
    }

    LaunchedEffect(source, lat, lng, bearing, iconName, iconSize) {
        source?.setGeoJson(puckGeoJson(lat, lng, bearing, iconName, iconSize))
    }

    DisposableEffect(map) {
        onDispose {
            source = null
            map?.getStyle { style ->
                runCatching {
                    style.removeLayer("puck-layer")
                    style.removeSource("puck-source")
                }
            }
        }
    }
}

private fun puckGeoJson(
        lat: Double,
        lng: Double,
        bearing: Double,
        iconName: String,
        iconSize: Float
): String =
        JSONObject()
                .put("type", "Feature")
                .put(
                        "geometry",
                        JSONObject()
                                .put("type", "Point")
                                .put(
                                        "coordinates",
                                        JSONArray().put(lng).put(lat)
                                )
                )
                .put(
                        "properties",
                        JSONObject()
                                .put("bearing", bearing)
                                .put("icon", iconName)
                                .put("size", iconSize)
                )
                .toString()
