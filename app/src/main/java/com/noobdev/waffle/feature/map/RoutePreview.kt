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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
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
import com.noobdev.waffle.feature.shops.data.ShopCategory
import com.noobdev.waffle.core.navigation.CameraTransitionManager
import com.noobdev.waffle.core.navigation.GeoGeometry
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.core.navigation.HazardRegistry
import com.noobdev.waffle.core.navigation.HazardZone
import com.noobdev.waffle.core.navigation.RouteSimulator
import com.noobdev.waffle.core.navigation.SimulationSpeed
import com.noobdev.waffle.core.routing.OfflineRoutingManager
import com.noobdev.waffle.core.routing.RouteOptions
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


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RoutePreviewCard(
        destination: GeoSearchResult?,
        routeResult: RouteResult?,
        selectedProfile: String,
        walkRouteResult: RouteResult?,
        bikeRouteResult: RouteResult? = null,
        isLoadingWalk: Boolean,
        isLoadingRoute: Boolean = false,
        canStart: Boolean = true,
        onProfileChange: (String) -> Unit,
        onStartNavigation: () -> Unit,
        onClose: () -> Unit,
        isSimulationMode: Boolean = false,
        onSimulationModeChange: (Boolean) -> Unit = {},
        selectedSimSpeed: SimulationSpeed = SimulationSpeed.VARIABLE,
        onSimSpeedChange: (SimulationSpeed) -> Unit = {},
        routeSource: RouteSource? = null,
        avoidedHazardCount: Int = 0,
        hazardWarning: HazardZone? = null,
        onAvoidHazard: (() -> Unit)? = null,
        stops: List<GeoSearchResult> = emptyList(),
        onAddStop: (() -> Unit)? = null,
        onRemoveStop: ((GeoSearchResult) -> Unit)? = null,
        onQuickStop: ((ShopCategory) -> Unit)? = null,
        onReorderStops: ((List<GeoSearchResult>) -> Unit)? = null,
        alternatives: List<RouteResult> = emptyList(),
        selectedAlternativeIndex: Int = 0,
        onAlternativeSelected: (Int) -> Unit = {},
        routeOptions: RouteOptions = RouteOptions(),
        onRouteOptionsChange: (RouteOptions) -> Unit = {}
) {
        var showStopEditor by remember { mutableStateOf(false) }
        val isCar = selectedProfile == "driving-car"
        val isMotorcycle = selectedProfile == "driving-motorcycle"
        val isDriving = isCar || isMotorcycle
        val isBiking = selectedProfile == "cycling-regular"
        val isWalking = selectedProfile == "foot-walking"
        val currentRoute =
                when {
                        isDriving -> routeResult ?: walkRouteResult ?: bikeRouteResult
                        isBiking -> bikeRouteResult ?: routeResult ?: walkRouteResult
                        else -> walkRouteResult ?: routeResult ?: bikeRouteResult
                } ?: return

        Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        // Header
                        val etaPreviewText =
                                remember(currentRoute.durationSeconds) {
                                        val etaMillis =
                                                System.currentTimeMillis() +
                                                        (currentRoute.durationSeconds.coerceAtLeast(
                                                                        0.0
                                                                ) * 1000.0)
                                                                .toLong()
                                        java.text.SimpleDateFormat(
                                                        "h:mm a",
                                                        java.util.Locale.getDefault()
                                                )
                                                .format(java.util.Date(etaMillis))
                                }
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                        formatDuration(currentRoute.durationSeconds),
                                                        style =
                                                                MaterialTheme.typography
                                                                        .headlineMedium,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Surface(
                                                        shape = RoundedCornerShape(50),
                                                        color =
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.12f)
                                                ) {
                                                        Text(
                                                                "ETA $etaPreviewText",
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 9.dp,
                                                                                vertical = 4.dp
                                                                        ),
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                }
                                        }
                                        Text(
                                                "${formatDistance(currentRoute.distanceMeters)} • ${if (stops.isNotEmpty()) "${stops.size} stop(s) • " else ""}${destination?.name ?: "Destination"}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                        )
                                }
                                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
                        }

                        if (isDriving) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                        modifier =
                                                Modifier.fillMaxWidth()
                                                        .horizontalScroll(rememberScrollState()),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                        FilterChip(
                                                selected = routeOptions.avoidTolls,
                                                onClick = {
                                                        onRouteOptionsChange(
                                                                routeOptions.copy(
                                                                        avoidTolls =
                                                                                !routeOptions
                                                                                        .avoidTolls
                                                                )
                                                        )
                                                },
                                                label = { Text("Tolls") }
                                        )
                                        FilterChip(
                                                selected = routeOptions.avoidHighways,
                                                onClick = {
                                                        onRouteOptionsChange(
                                                                routeOptions.copy(
                                                                        avoidHighways =
                                                                                !routeOptions
                                                                                        .avoidHighways
                                                                )
                                                        )
                                                },
                                                label = { Text("Highways") }
                                        )
                                        FilterChip(
                                                selected = routeOptions.avoidFerries,
                                                onClick = {
                                                        onRouteOptionsChange(
                                                                routeOptions.copy(
                                                                        avoidFerries =
                                                                                !routeOptions
                                                                                        .avoidFerries
                                                                )
                                                        )
                                                },
                                                label = { Text("Ferries") }
                                        )
                                        FilterChip(
                                                selected = routeOptions.alternatives,
                                                onClick = {
                                                        onRouteOptionsChange(
                                                                routeOptions.copy(
                                                                        alternatives =
                                                                                !routeOptions
                                                                                        .alternatives
                                                                )
                                                        )
                                                },
                                                label = { Text("Alts") }
                                        )
                                }
                        }

                        if (alternatives.size > 1) {
                                val minDurationSec =
                                        alternatives.minOfOrNull { it.durationSeconds } ?: 0.0
                                val primaryDistanceM =
                                        alternatives.firstOrNull()?.distanceMeters ?: 0.0
                                val corridorNames =
                                        remember(alternatives) {
                                                extractDistinctCorridorLabels(alternatives)
                                        }
                                Spacer(modifier = Modifier.height(10.dp))
                                LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        items(alternatives.size) { index ->
                                                val alternative = alternatives[index]
                                                val selected = selectedAlternativeIndex == index
                                                val diffMin =
                                                        ((alternative.durationSeconds -
                                                                        minDurationSec) /
                                                                        60.0)
                                                                .toInt()
                                                val isShortestDist =
                                                        index > 0 &&
                                                                alternative.distanceMeters <
                                                                        primaryDistanceM * 0.94
                                                val badgeText =
                                                        when {
                                                                index == 0 || diffMin <= 0 ->
                                                                        "Fastest"
                                                                isShortestDist ->
                                                                        "Shorter · +${diffMin}m"
                                                                diffMin == 1 -> "+1 min"
                                                                else -> "+$diffMin min"
                                                        }
                                                val badgeColor =
                                                        when {
                                                                index == 0 || diffMin <= 0 ->
                                                                        SafeGreen
                                                                isShortestDist ->
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else ->
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        }
                                                val corridor = corridorNames.getOrNull(index)
                                                Surface(
                                                        onClick = { onAlternativeSelected(index) },
                                                        shape = RoundedCornerShape(14.dp),
                                                        color =
                                                                if (selected)
                                                                        MaterialTheme.colorScheme
                                                                                .primaryContainer
                                                                                .copy(alpha = 0.85f)
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                                .copy(alpha = 0.55f),
                                                        border =
                                                                BorderStroke(
                                                                        if (selected) 1.5.dp
                                                                        else 1.dp,
                                                                        if (selected)
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                        else
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .outline
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.25f
                                                                                        )
                                                                )
                                                ) {
                                                        Column(
                                                                modifier =
                                                                        Modifier.padding(
                                                                                horizontal = 12.dp,
                                                                                vertical = 8.dp
                                                                        )
                                                        ) {
                                                                Row(
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically,
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                6.dp
                                                                                        )
                                                                ) {
                                                                        Text(
                                                                                badgeText,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                color = badgeColor
                                                                        )
                                                                }
                                                                Row(
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .Bottom,
                                                                        horizontalArrangement =
                                                                                Arrangement
                                                                                        .spacedBy(
                                                                                                6.dp
                                                                                        )
                                                                ) {
                                                                        Text(
                                                                                formatDuration(
                                                                                        alternative
                                                                                                .durationSeconds
                                                                                ),
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .titleSmall,
                                                                                fontWeight =
                                                                                        FontWeight
                                                                                                .Bold,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurface
                                                                        )
                                                                        Text(
                                                                                formatDistance(
                                                                                        alternative
                                                                                                .distanceMeters
                                                                                ),
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant
                                                                        )
                                                                }
                                                                if (!corridor.isNullOrBlank()) {
                                                                        Text(
                                                                                "via $corridor",
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                color =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant,
                                                                                maxLines = 1,
                                                                                overflow =
                                                                                        TextOverflow
                                                                                                .Ellipsis,
                                                                                modifier =
                                                                                        Modifier.widthIn(
                                                                                                max =
                                                                                                        145.dp
                                                                                        )
                                                                        )
                                                                }
                                                        }
                                                }
                                        }
                                }
                        }

                        // Stops: a compact "via" strip plus one-tap fuel/food/stay shortcuts.
                        if (onAddStop != null) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                        Text(
                                                "Via",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        LazyRow(
                                                modifier = Modifier.weight(1f),
                                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                if (stops.isEmpty()) {
                                                        item {
                                                                Text(
                                                                        "Direct",
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall,
                                                                        color =
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                                )
                                                        }
                                                }
                                                items(stops) { stop ->
                                                        Surface(
                                                                shape = RoundedCornerShape(50),
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .surfaceVariant
                                                                                .copy(alpha = 0.7f)
                                                        ) {
                                                                Row(
                                                                        modifier =
                                                                                Modifier.padding(
                                                                                        start = 10.dp,
                                                                                        end = 6.dp,
                                                                                        top = 6.dp,
                                                                                        bottom = 6.dp
                                                                                ),
                                                                        verticalAlignment =
                                                                                Alignment
                                                                                        .CenterVertically
                                                                ) {
                                                                        Icon(
                                                                                Icons.Default.Place,
                                                                                null,
                                                                                modifier =
                                                                                        Modifier.size(
                                                                                                13.dp
                                                                                        ),
                                                                                tint =
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                        )
                                                                        Spacer(
                                                                                Modifier.width(
                                                                                        4.dp
                                                                                )
                                                                        )
                                                                        Text(
                                                                                stop.name,
                                                                                style =
                                                                                        MaterialTheme
                                                                                                .typography
                                                                                                .labelSmall,
                                                                                maxLines = 1,
                                                                                overflow =
                                                                                        TextOverflow
                                                                                                .Ellipsis,
                                                                                modifier =
                                                                                        Modifier.widthIn(
                                                                                                max = 120.dp
                                                                                        )
                                                                        )
                                                                        if (onRemoveStop !=
                                                                                        null
                                                                        ) {
                                                                                Spacer(
                                                                                        Modifier.width(
                                                                                                4.dp
                                                                                        )
                                                                                )
                                                                                Icon(
                                                                                        Icons.Default.Close,
                                                                                        "Remove stop",
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                                14.dp
                                                                                                        )
                                                                                                        .clickable {
                                                                                                                onRemoveStop(
                                                                                                                        stop
                                                                                                                )
                                                                                                        },
                                                                                        tint =
                                                                                                MaterialTheme
                                                                                                        .colorScheme
                                                                                                        .onSurfaceVariant
                                                                                )
                                                                        }
                                                                }
                                                        }
                                                }
                                        }
                                        if (stops.size > 1 && onReorderStops != null) {
                                                IconButton(
                                                        onClick = { showStopEditor = true },
                                                        modifier = Modifier.size(30.dp)
                                                ) {
                                                        Icon(
                                                                Icons.Default.Edit,
                                                                contentDescription = "Edit stops",
                                                                modifier = Modifier.size(15.dp),
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                        }
                                        Surface(
                                                onClick = onAddStop,
                                                shape = RoundedCornerShape(50),
                                                color =
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.10f
                                                        ),
                                                border =
                                                        BorderStroke(
                                                                1.dp,
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.4f)
                                                        )
                                        ) {
                                                Row(
                                                        modifier =
                                                                Modifier.padding(
                                                                        horizontal = 10.dp,
                                                                        vertical = 6.dp
                                                                ),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        Icon(
                                                                Icons.Default.Add,
                                                                null,
                                                                modifier = Modifier.size(14.dp),
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                        Spacer(Modifier.width(4.dp))
                                                        Text(
                                                                "Add stop",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelSmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                }
                                        }
                                }

                                if (onQuickStop != null) {
                                        Row(
                                                modifier = Modifier.padding(top = 6.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement =
                                                        Arrangement.spacedBy(6.dp)
                                        ) {
                                                Text(
                                                        "Along the way:",
                                                        style =
                                                                MaterialTheme.typography.labelSmall,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                                LazyRow(
                                                        modifier = Modifier.weight(1f),
                                                        horizontalArrangement =
                                                                Arrangement.spacedBy(6.dp),
                                                        verticalAlignment =
                                                                Alignment.CenterVertically
                                                ) {
                                                        items(
                                                                listOf(
                                                                        ShopCategory.FUEL,
                                                                        ShopCategory.FOOD,
                                                                        ShopCategory.STAY,
                                                                        ShopCategory.HEALTH,
                                                                        ShopCategory.GROCERY
                                                                )
                                                        ) { category ->
                                                                AssistChip(
                                                                        onClick = {
                                                                                onQuickStop(
                                                                                        category
                                                                                )
                                                                        },
                                                                        label = {
                                                                                Text(
                                                                                        category
                                                                                                .shortLabel,
                                                                                        style =
                                                                                                MaterialTheme
                                                                                                        .typography
                                                                                                        .labelSmall
                                                                                )
                                                                        },
                                                                        leadingIcon = {
                                                                                Icon(
                                                                                        category.icon,
                                                                                        null,
                                                                                        modifier =
                                                                                                Modifier.size(
                                                                                                        14.dp
                                                                                                ),
                                                                                        tint =
                                                                                                category.tint
                                                                                )
                                                                        },
                                                                        modifier =
                                                                                Modifier.height(
                                                                                        30.dp
                                                                                )
                                                                )
                                                        }
                                                }
                                        }
                                }
                        }

                        // Route Safety, Surface & Complexity Summary Strip
                        val avgSpeedKmh =
                                if (currentRoute.durationSeconds > 1.0)
                                        ((currentRoute.distanceMeters / currentRoute.durationSeconds) *
                                                        3.6)
                                                .toInt()
                                                .coerceAtLeast(1)
                                else 0
                        val surfaceLabel =
                                when {
                                        isWalking -> "Footpath"
                                        isBiking -> "Cycleway"
                                        avgSpeedKmh >= 55 -> "Expressway"
                                        avgSpeedKmh >= 35 -> "Arterial"
                                        else -> "City streets"
                                }
                        val turnCount =
                                currentRoute.steps
                                        .count { step ->
                                                step.maneuver !=
                                                        com.noobdev.waffle.core.navigation
                                                                .ManeuverType.DEPART &&
                                                        step.maneuver !=
                                                                com.noobdev.waffle.core.navigation
                                                                        .ManeuverType.ARRIVE &&
                                                        step.maneuver !=
                                                                com.noobdev.waffle.core.navigation
                                                                        .ManeuverType.CONTINUE
                                        }
                                        .coerceAtLeast(
                                                (currentRoute.instructions.size - 2).coerceAtLeast(
                                                        1
                                                )
                                        )
                        Row(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(top = 8.dp)
                                                .horizontalScroll(rememberScrollState()),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                                Surface(
                                        shape = RoundedCornerShape(50),
                                        color =
                                                if (routeSource == RouteSource.OFFLINE)
                                                        SafeGreen.copy(alpha = 0.14f)
                                                else
                                                        MaterialTheme.colorScheme.surfaceVariant
                                                                .copy(alpha = 0.7f)
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 9.dp,
                                                                vertical = 4.dp
                                                        ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        if (routeSource == RouteSource.OFFLINE)
                                                                Icons.Default.CloudOff
                                                        else Icons.Default.Cloud,
                                                        null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint =
                                                                if (routeSource ==
                                                                                RouteSource.OFFLINE
                                                                )
                                                                        SafeGreen
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                        if (routeSource == RouteSource.OFFLINE)
                                                                "Offline ready"
                                                        else "Online",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color =
                                                                if (routeSource ==
                                                                                RouteSource.OFFLINE
                                                                )
                                                                        SafeGreen
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                )
                                        }
                                }
                                Surface(
                                        shape = RoundedCornerShape(50),
                                        color =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                                ) {
                                        Text(
                                                if (avgSpeedKmh > 0)
                                                        "$surfaceLabel · $avgSpeedKmh km/h avg"
                                                else surfaceLabel,
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 9.dp,
                                                                vertical = 4.dp
                                                        ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                                Surface(
                                        shape = RoundedCornerShape(50),
                                        color =
                                                MaterialTheme.colorScheme.surfaceVariant.copy(
                                                        alpha = 0.7f
                                                )
                                ) {
                                        Text(
                                                "$turnCount turns",
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 9.dp,
                                                                vertical = 4.dp
                                                        ),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                                val hazardClear = hazardWarning == null && avoidedHazardCount == 0
                                Surface(
                                        shape = RoundedCornerShape(50),
                                        color =
                                                if (hazardClear) SafeGreen.copy(alpha = 0.14f)
                                                else
                                                        MaterialTheme.colorScheme.errorContainer
                                                                .copy(alpha = 0.75f)
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.padding(
                                                                horizontal = 9.dp,
                                                                vertical = 4.dp
                                                        ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        if (hazardClear) Icons.Default.VerifiedUser
                                                        else Icons.Default.Warning,
                                                        null,
                                                        modifier = Modifier.size(13.dp),
                                                        tint =
                                                                if (hazardClear) SafeGreen
                                                                else MaterialTheme.colorScheme.error
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                        if (hazardClear) "0 flood/hazard zones"
                                                        else if (avoidedHazardCount > 0)
                                                                "Avoided $avoidedHazardCount hazard(s)"
                                                        else "Hazard nearby",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color =
                                                                if (hazardClear) SafeGreen
                                                                else MaterialTheme.colorScheme.error
                                                )
                                        }
                                }
                        }

                        // Live hazard warning for the current route
                        if (hazardWarning != null) {
                                Spacer(modifier = Modifier.height(12.dp))
                                Surface(
                                        color = MaterialTheme.colorScheme.errorContainer,
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                        Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier =
                                                        Modifier.padding(
                                                                start = 12.dp,
                                                                end = 4.dp,
                                                                top = 4.dp,
                                                                bottom = 4.dp
                                                        )
                                        ) {
                                                Icon(
                                                        Icons.Default.ReportProblem,
                                                        null,
                                                        tint = MaterialTheme.colorScheme.error,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                                "Hazard on your route",
                                                                style = MaterialTheme.typography.labelLarge,
                                                                fontWeight = FontWeight.SemiBold,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onErrorContainer
                                                        )
                                                        Text(
                                                                hazardWarning.title,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onErrorContainer,
                                                                maxLines = 2
                                                        )
                                                }
                                                TextButton(onClick = { onAvoidHazard?.invoke() }) {
                                                        Text("Avoid")
                                                }
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Route options - Drive / Moto / Bike / Walk
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                @Composable
                                fun ModeChip(
                                        selected: Boolean,
                                        icon: ImageVector,
                                        label: String,
                                        loading: Boolean = false,
                                        onClick: () -> Unit
                                ) {
                                        Surface(
                                                onClick = onClick,
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(16.dp),
                                                color =
                                                        if (selected)
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.12f)
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .surfaceVariant,
                                                border =
                                                        BorderStroke(
                                                                if (selected) 2.dp else 1.dp,
                                                                if (selected)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .outline
                                                                                .copy(alpha = 0.25f)
                                                        )
                                        ) {
                                                Column(
                                                        modifier =
                                                                Modifier.fillMaxWidth()
                                                                        .padding(
                                                                                horizontal = 6.dp,
                                                                                vertical = 8.dp
                                                                        ),
                                                        horizontalAlignment =
                                                                Alignment.CenterHorizontally,
                                                        verticalArrangement = Arrangement.Center
                                                ) {
                                                        Icon(
                                                                icon,
                                                                null,
                                                                tint =
                                                                        if (selected)
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                        else
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant,
                                                                modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(Modifier.height(4.dp))
                                                        if (loading) {
                                                                CircularProgressIndicator(
                                                                        modifier =
                                                                                Modifier.size(14.dp),
                                                                        strokeWidth = 2.dp
                                                                )
                                                        } else {
                                                                Text(
                                                                        label,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall,
                                                                        fontWeight =
                                                                                if (selected)
                                                                                        FontWeight
                                                                                                .Bold
                                                                                else
                                                                                        FontWeight
                                                                                                .Medium,
                                                                        color =
                                                                                if (selected)
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .primary
                                                                                else
                                                                                        MaterialTheme
                                                                                                .colorScheme
                                                                                                .onSurfaceVariant,
                                                                        maxLines = 1,
                                                                        overflow =
                                                                                TextOverflow.Ellipsis
                                                                )
                                                        }
                                                }
                                        }
                                }

                                ModeChip(
                                        selected = isCar,
                                        icon = Icons.Default.DirectionsCar,
                                        label =
                                                routeResult?.let {
                                                        formatDuration(it.durationSeconds)
                                                } ?: "Drive",
                                        onClick = { onProfileChange("driving-car") }
                                )
                                ModeChip(
                                        selected = isMotorcycle,
                                        icon = Icons.Default.TwoWheeler,
                                        label =
                                                routeResult?.let {
                                                        formatDuration(it.durationSeconds)
                                                } ?: "Moto",
                                        onClick = { onProfileChange("driving-motorcycle") }
                                )
                                ModeChip(
                                        selected = isBiking,
                                        icon = Icons.Default.DirectionsBike,
                                        label =
                                                bikeRouteResult?.let {
                                                        formatDuration(it.durationSeconds)
                                                } ?: "Bike",
                                        onClick = { onProfileChange("cycling-regular") }
                                )
                                ModeChip(
                                        selected = isWalking,
                                        icon = Icons.Default.DirectionsWalk,
                                        label =
                                                walkRouteResult?.let {
                                                        formatDuration(it.durationSeconds)
                                                } ?: "Walk",
                                        loading = isLoadingWalk,
                                        onClick = { onProfileChange("foot-walking") }
                                )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Simulation mode toggle
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                        Icon(
                                                Icons.Default.Science,
                                                contentDescription = null,
                                                modifier = Modifier.size(18.dp),
                                                tint =
                                                        if (isSimulationMode) Sky
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                        )
                                        Text(
                                                "Test Mode",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color =
                                                        if (isSimulationMode) Sky
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                        )
                                }
                                Switch(
                                        checked = isSimulationMode,
                                        onCheckedChange = onSimulationModeChange,
                                        colors =
                                                SwitchDefaults.colors(
                                                        checkedThumbColor = Sky,
                                                        checkedTrackColor =
                                                                Sky.copy(alpha = 0.5f)
                                                )
                                )
                        }

                        // Speed selection (shown when simulation mode is on)
                        AnimatedVisibility(visible = isSimulationMode) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                        SimulationSpeed.entries.forEach { speed ->
                                                FilterChip(
                                                        selected = selectedSimSpeed == speed,
                                                        onClick = { onSimSpeedChange(speed) },
                                                        label = {
                                                                val shortLabel =
                                                                        when (speed) {
                                                                                SimulationSpeed
                                                                                        .WALKING ->
                                                                                        "Walk"
                                                                                SimulationSpeed
                                                                                        .CITY ->
                                                                                        "City"
                                                                                SimulationSpeed
                                                                                        .HIGHWAY ->
                                                                                        "Fast"
                                                                                SimulationSpeed
                                                                                        .VARIABLE ->
                                                                                        "Auto"
                                                                        }
                                                                Text(
                                                                        shortLabel,
                                                                        style =
                                                                                MaterialTheme
                                                                                        .typography
                                                                                        .labelSmall
                                                                )
                                                        },
                                                        colors =
                                                                FilterChipDefaults.filterChipColors(
                                                                        selectedContainerColor =
                                                                                Sky
                                                                                        .copy(
                                                                                                alpha =
                                                                                                        0.2f
                                                                                        ),
                                                                        selectedLabelColor =
                                                                                Sky
                                                                ),
                                                        modifier = Modifier.weight(1f)
                                                )
                                        }
                                }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Start button
                        Button(
                                onClick = onStartNavigation,
                                enabled = canStart && !isLoadingRoute && !isLoadingWalk,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                shape = RoundedCornerShape(26.dp),
                                colors =
                                        ButtonDefaults.buttonColors(
                                                containerColor =
                                                        if (isSimulationMode) Sky
                                                        else MaterialTheme.colorScheme.primary,
                                                contentColor =
                                                        if (isSimulationMode) Ink
                                                        else MaterialTheme.colorScheme.onPrimary
                                        )
                        ) {
                                Icon(
                                        if (isSimulationMode) Icons.Default.PlayArrow
                                        else Icons.Default.Navigation,
                                        null,
                                        modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                        if (isSimulationMode) "Start Simulation" else "Start",
                                        style = MaterialTheme.typography.titleMedium
                                )
                        }
                }
        }

        if (showStopEditor) {
                ModalBottomSheet(
                        onDismissRequest = { showStopEditor = false },
                        containerColor = MaterialTheme.colorScheme.surface
                ) {
                        Column(
                                modifier =
                                        Modifier.fillMaxWidth()
                                                .padding(horizontal = 16.dp)
                                                .padding(bottom = 24.dp)
                        ) {
                                Text(
                                        "Edit stops",
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(8.dp))
                                stops.forEachIndexed { index, stop ->
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        Icons.Default.Place,
                                                        null,
                                                        modifier = Modifier.size(18.dp),
                                                        tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                        stop.name,
                                                        modifier = Modifier.weight(1f),
                                                        style =
                                                                MaterialTheme.typography
                                                                        .bodyMedium,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurface,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                                IconButton(
                                                        onClick = {
                                                                val next = stops.toMutableList()
                                                                val moved = next.removeAt(index)
                                                                next.add(index - 1, moved)
                                                                onReorderStops?.invoke(next)
                                                        },
                                                        enabled = index > 0
                                                ) {
                                                        Icon(
                                                                Icons.Default.KeyboardArrowUp,
                                                                "Move up",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                                IconButton(
                                                        onClick = {
                                                                val next = stops.toMutableList()
                                                                val moved = next.removeAt(index)
                                                                next.add(index + 1, moved)
                                                                onReorderStops?.invoke(next)
                                                        },
                                                        enabled = index < stops.lastIndex
                                                ) {
                                                        Icon(
                                                                Icons.Default.KeyboardArrowDown,
                                                                "Move down",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                                IconButton(
                                                        onClick = {
                                                                onRemoveStop?.invoke(stop)
                                                        }
                                                ) {
                                                        Icon(
                                                                Icons.Default.Close,
                                                                "Remove stop",
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                        }
                                        HorizontalDivider(
                                                color =
                                                        MaterialTheme.colorScheme.outline.copy(
                                                                alpha = 0.25f
                                                        )
                                        )
                                }
                        }
                }
        }
}

/**
 * Extracts a short, human-readable corridor road name ("Srinagar Hwy", "Margalla Rd", etc.) for
 * each route in [alternatives], preferring road names that distinguish an alternative from the
 * others.
 */
private fun extractDistinctCorridorLabels(alternatives: List<RouteResult>): List<String?> {
        val roadDistancesPerRoute: List<Map<String, Double>> =
                alternatives.map { route ->
                        val distances = LinkedHashMap<String, Double>()
                        if (route.steps.isNotEmpty()) {
                                for (step in route.steps) {
                                        val road = extractRoadFromInstruction(step.instruction)
                                        if (road != null) {
                                                distances[road] =
                                                        (distances[road] ?: 0.0) +
                                                                step.distance.coerceAtLeast(50.0)
                                        }
                                }
                        } else {
                                for (instr in route.instructions) {
                                        val road = extractRoadFromInstruction(instr)
                                        if (road != null) {
                                                distances[road] = (distances[road] ?: 0.0) + 200.0
                                        }
                                }
                        }
                        distances
                }

        val chosen = ArrayList<String?>(alternatives.size)
        val alreadyUsed = HashSet<String>()
        for (i in alternatives.indices) {
                val map = roadDistancesPerRoute[i]
                val sortedRoads = map.entries.sortedByDescending { it.value }.map { it.key }
                val distinct = sortedRoads.firstOrNull { it !in alreadyUsed }
                val pick = distinct ?: sortedRoads.firstOrNull()
                if (pick != null) alreadyUsed.add(pick)
                chosen.add(pick)
        }
        return chosen
}

private fun extractRoadFromInstruction(instruction: String): String? {
        val marker =
                when {
                        instruction.contains(" onto ", ignoreCase = true) -> " onto "
                        instruction.contains(" on ", ignoreCase = true) -> " on "
                        else -> return null
                }
        val raw =
                instruction
                        .substringAfter(marker, "")
                        .substringBefore(",")
                        .trim()
        if (raw.length < 3 ||
                        raw.equals("destination", ignoreCase = true) ||
                        raw.equals("the roundabout", ignoreCase = true)
        ) {
                return null
        }
        return raw
}

// ============================================
// NAVIGATION MODE UI
// ============================================
