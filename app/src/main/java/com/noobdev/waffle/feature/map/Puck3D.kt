package com.noobdev.waffle.feature.map

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.noobdev.waffle.R
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
    val iconSize = if (isNavigating) 0.42f else 0.30f
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
                                                    Property.ICON_PITCH_ALIGNMENT_MAP
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
