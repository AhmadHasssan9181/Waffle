package com.noobdev.waffle.feature.map

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.noobdev.waffle.feature.flood.loadSectorData
import com.noobdev.waffle.feature.flood.sectors
import com.noobdev.waffle.data.remote.*
import com.noobdev.waffle.core.navigation.CameraTransitionManager
import com.noobdev.waffle.core.navigation.GeoGeometry
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.core.navigation.HazardRegistry
import com.noobdev.waffle.core.navigation.HazardZone
import com.noobdev.waffle.core.navigation.NavigationManager
import com.noobdev.waffle.core.navigation.RouteSimulator
import com.noobdev.waffle.core.navigation.SimulationSpeed
import com.noobdev.waffle.core.routing.OfflineRoutingManager
import com.noobdev.waffle.core.routing.RouteRepository
import com.noobdev.waffle.core.routing.RouteSource
import com.noobdev.waffle.design.theme.*
import kotlin.math.max
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.FillLayer
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.LineLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.*
import org.maplibre.android.style.sources.GeoJsonSource


fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val earthRadius = 6371000.0 // meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a =
                Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(lat1)) *
                                Math.cos(Math.toRadians(lat2)) *
                                Math.sin(dLng / 2) *
                                Math.sin(dLng / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return earthRadius * c
}

/** Helper to update route source with vanishing trail */
fun updateRouteSource(map: MapLibreMap, coordinates: List<Pair<Double, Double>>, startIndex: Int) {        map.getStyle { style ->
                if (coordinates.size <= startIndex) return@getStyle

                // Vanishing trail: only show coordinates from startIndex to end
                val remainingCoords = coordinates.subList(max(0, startIndex), coordinates.size)
                if (remainingCoords.isEmpty()) return@getStyle

                val coordsString =
                        remainingCoords.joinToString(",") { pair ->
                                "[${pair.second},${pair.first}]"
                        }
                val geoJson =
                        """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordsString]}}"""

                val source = style.getSourceAs<GeoJsonSource>("route-source")
                if (source != null) {
                        source.setGeoJson(geoJson)
                } else {
                        // Create if not exists
                        val newSource = GeoJsonSource("route-source", geoJson)
                        style.addSource(newSource)
                        val layer = LineLayer("route-layer", "route-source")
                        layer.setProperties(
                                lineColor(AndroidColor.parseColor("#F2A33C")),
                                lineWidth(6f),
                                lineOpacity(0.9f),
                                lineCap(Property.LINE_CAP_ROUND),
                                lineJoin(Property.LINE_JOIN_ROUND)
                        )
                        style.addLayer(layer)
                }
        }
}

/**
 * Distance still to drive along [coordinates], starting from the segment the location snapped to.
 * The first leg runs from the current position to the end of that segment, so the number does not
 * jump when a fix lands mid-segment.
 */
fun remainingRouteMeters(
        coordinates: List<Pair<Double, Double>>,
        segmentIndex: Int,
        currentLat: Double,
        currentLng: Double
): Double {
        if (coordinates.size < 2) return 0.0
        val start = segmentIndex.coerceIn(0, coordinates.size - 2)
        val next = coordinates[start + 1]
        var total = calculateDistance(currentLat, currentLng, next.first, next.second)
        for (i in start + 1 until coordinates.size - 1) {
                val a = coordinates[i]
                val b = coordinates[i + 1]
                total += calculateDistance(a.first, a.second, b.first, b.second)
        }
        return total
}

/**
 * Fast direct update of the route GeoJsonSource for vanishing trail effect. Unlike
 * updateRouteSource, this skips the async getStyle() callback by using the already-stored source
 * reference – crucial for smooth 1-second updates during navigation.
 */
fun trimRouteSource(
        source: GeoJsonSource?,
        coordinates: List<Pair<Double, Double>>,
        startIndex: Int,
        currentLatLng: LatLng? = null
) {
        if (source == null || coordinates.size <= startIndex) return
        
        val safeStartIndex = max(0, startIndex)
        val remainingCoords = if (safeStartIndex + 1 < coordinates.size) {
            coordinates.subList(safeStartIndex + 1, coordinates.size).toMutableList()
        } else {
            mutableListOf()
        }
        
        if (currentLatLng != null) {
            remainingCoords.add(0, Pair(currentLatLng.latitude, currentLatLng.longitude))
        } else {
            remainingCoords.add(0, coordinates[safeStartIndex])
        }

        if (remainingCoords.size < 2) return

        val coordsString = remainingCoords.joinToString(",") { "[${it.second},${it.first}]" }
        val geoJson =
                """{"type":"Feature","geometry":{"type":"LineString","coordinates":[$coordsString]}}"""
        source.setGeoJson(geoJson)
}
