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
import com.noobdev.waffle.feature.shops.data.ShopCategory
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


@Composable
fun RoutePreviewCard(
        destination: GeoSearchResult?,
        routeResult: RouteResult,
        selectedProfile: String,
        walkRouteResult: RouteResult?,
        bikeRouteResult: RouteResult? = null,
        isLoadingWalk: Boolean,
        onProfileChange: (String) -> Unit,
        onStartNavigation: () -> Unit,
        onClose: () -> Unit,
        isSimulationMode: Boolean = false,
        onSimulationModeChange: (Boolean) -> Unit = {},
        selectedSimSpeed: SimulationSpeed = SimulationSpeed.CITY,
        onSimSpeedChange: (SimulationSpeed) -> Unit = {},
        routeSource: RouteSource? = null,
        avoidedHazardCount: Int = 0,
        hazardWarning: HazardZone? = null,
        onAvoidHazard: (() -> Unit)? = null,
        stops: List<GeoSearchResult> = emptyList(),
        onAddStop: (() -> Unit)? = null,
        onRemoveStop: ((GeoSearchResult) -> Unit)? = null,
        onQuickStop: ((ShopCategory) -> Unit)? = null
) {
        val isDriving = selectedProfile == "driving-car"
        val isBiking = selectedProfile == "cycling-regular"
        val isWalking = selectedProfile == "foot-walking"
        val currentRoute =
                when {
                        isDriving -> routeResult
                        isBiking -> bikeRouteResult ?: routeResult
                        else -> walkRouteResult ?: routeResult
                }

        Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(20.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
                Column(modifier = Modifier.padding(16.dp)) {
                        // Header
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column {
                                        Text(
                                                formatDuration(currentRoute.durationSeconds),
                                                style = MaterialTheme.typography.headlineMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                                "${formatDistance(currentRoute.distanceMeters)} • ${if (stops.isNotEmpty()) "${stops.size} stop(s) • " else ""}${destination?.name ?: "Destination"}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }
                                IconButton(onClick = onClose) { Icon(Icons.Default.Close, "Close") }
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
                                                listOf(
                                                                ShopCategory.FUEL,
                                                                ShopCategory.FOOD,
                                                                ShopCategory.STAY
                                                        )
                                                        .forEach { category ->
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

                        // Offline/online source + whether hazards were avoided
                        if (routeSource != null || avoidedHazardCount > 0) {
                                Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 6.dp)
                                ) {
                                        if (routeSource == RouteSource.OFFLINE) {
                                                Icon(
                                                        Icons.Default.CloudOff,
                                                        null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = SafeGreen
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                        "Offline route — works without signal",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = SafeGreen
                                                )
                                        } else if (routeSource == RouteSource.ONLINE) {
                                                Icon(
                                                        Icons.Default.Cloud,
                                                        null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                        "Online route",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant
                                                )
                                        }
                                        if (avoidedHazardCount > 0) {
                                                if (routeSource != null) {
                                                        Text(
                                                                "  •  ",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                                Icon(
                                                        Icons.Default.Warning,
                                                        null,
                                                        modifier = Modifier.size(14.dp),
                                                        tint = MaterialTheme.colorScheme.error
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                        "avoiding $avoidedHazardCount hazard(s)",
                                                        style = MaterialTheme.typography.labelMedium,
                                                        color = MaterialTheme.colorScheme.error
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

                        // Route options - Drive / Bike / Walk
                        Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                                // Drive option
                                Surface(
                                        onClick = { onProfileChange("driving-car") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        color =
                                                if (isDriving)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.10f
                                                        )
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                        border =
                                                if (isDriving)
                                                        BorderStroke(
                                                                2.dp,
                                                                MaterialTheme.colorScheme.primary
                                                        )
                                                else null
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 10.dp,
                                                                        vertical = 10.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                        ) {
                                                Icon(
                                                        Icons.Default.DirectionsCar,
                                                        null,
                                                        tint =
                                                                if (isDriving)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(5.dp))
                                                Text(
                                                        formatDuration(
                                                                routeResult.durationSeconds
                                                        ),
                                                        style =
                                                                MaterialTheme.typography.labelMedium,
                                                        color =
                                                                if (isDriving)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                )
                                        }
                                }

                                // Bike option
                                Surface(
                                        onClick = { onProfileChange("cycling-regular") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        color =
                                                if (isBiking)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.10f
                                                        )
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                        border =
                                                if (isBiking)
                                                        BorderStroke(
                                                                2.dp,
                                                                MaterialTheme.colorScheme.primary
                                                        )
                                                else null
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 10.dp,
                                                                        vertical = 10.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                        ) {
                                                Icon(
                                                        Icons.Default.DirectionsBike,
                                                        null,
                                                        tint =
                                                                if (isBiking)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(5.dp))
                                                Text(
                                                        if (bikeRouteResult != null)
                                                                formatDuration(
                                                                        bikeRouteResult
                                                                                .durationSeconds
                                                                )
                                                        else "Bike",
                                                        style =
                                                                MaterialTheme.typography.labelMedium,
                                                        color =
                                                                if (isBiking)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                )
                                        }
                                }

                                // Walk option
                                Surface(
                                        onClick = { onProfileChange("foot-walking") },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(20.dp),
                                        color =
                                                if (isWalking)
                                                        MaterialTheme.colorScheme.primary.copy(
                                                                alpha = 0.10f
                                                        )
                                                else MaterialTheme.colorScheme.surfaceVariant,
                                        border =
                                                if (isWalking)
                                                        BorderStroke(
                                                                2.dp,
                                                                MaterialTheme.colorScheme.primary
                                                        )
                                                else null
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .padding(
                                                                        horizontal = 10.dp,
                                                                        vertical = 10.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                        ) {
                                                Icon(
                                                        Icons.Default.DirectionsWalk,
                                                        null,
                                                        tint =
                                                                if (isWalking)
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                else
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant,
                                                        modifier = Modifier.size(18.dp)
                                                )
                                                Spacer(Modifier.width(5.dp))
                                                if (isLoadingWalk) {
                                                        CircularProgressIndicator(
                                                                modifier = Modifier.size(14.dp),
                                                                strokeWidth = 2.dp
                                                        )
                                                } else {
                                                        Text(
                                                                if (walkRouteResult != null)
                                                                        formatDuration(
                                                                                walkRouteResult
                                                                                        .durationSeconds
                                                                        )
                                                                else "Walk",
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .labelMedium,
                                                                color =
                                                                        if (isWalking)
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .primary
                                                                        else
                                                                                MaterialTheme
                                                                                        .colorScheme
                                                                                        .onSurfaceVariant
                                                        )
                                                }
                                        }
                                }
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
                                                                Text(
                                                                        speed.displayName
                                                                                .split(" ")
                                                                                .first(),
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
}

// ============================================
// NAVIGATION MODE UI
// ============================================
