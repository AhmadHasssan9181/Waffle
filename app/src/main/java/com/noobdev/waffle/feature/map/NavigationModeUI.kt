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
import androidx.compose.material.icons.rounded.*
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
import kotlin.math.roundToInt
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
fun NavigationModeUI(
        routeResult: RouteResult,
        remainingDistance: Double,
        remainingDuration: Double,
        currentInstruction: String,
        currentIndex: Int,
        totalInstructions: Int,
        speedMps: Float = 0f,
        navigationNotice: String? = null,
        isRerouting: Boolean = false,
        wrongWay: Boolean = false,
        onNext: () -> Unit,
        onExit: () -> Unit
) {
        Box(modifier = Modifier.fillMaxSize()) {
                NavigationSpeedBadge(
                        speedMps = speedMps,
                        modifier =
                                Modifier.align(Alignment.BottomStart)
                                        .padding(start = 16.dp, bottom = 150.dp)
                )

                // Top instruction bar
                Card(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding(),
                        shape = RoundedCornerShape(0.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                )
                ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                                AnimatedVisibility(
                                        visible = wrongWay,
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(
                                                                        DangerRed.copy(alpha = 0.12f)
                                                                )
                                                                .padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 10.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                Icon(
                                                        imageVector = Icons.Default.Warning,
                                                        contentDescription = null,
                                                        tint = DangerRed,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                                Spacer(Modifier.width(10.dp))
                                                Text(
                                                        "Wrong way — turn around",
                                                        style = MaterialTheme.typography.titleSmall,
                                                        color = DangerRed,
                                                        fontWeight = FontWeight.Bold
                                                )
                                        }
                                }
                                if (wrongWay) Spacer(Modifier.height(12.dp))

                                Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically
                                ) {
                                        Box(
                                                Modifier.size(52.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                                MaterialTheme.colorScheme.primary
                                                                        .copy(alpha = 0.14f)
                                                        ),
                                                contentAlignment = Alignment.Center
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                getDirectionIcon(
                                                                        currentInstruction
                                                                ),
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary,
                                                        modifier = Modifier.size(32.dp)
                                                )
                                        }
                                        Spacer(Modifier.width(16.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                        currentInstruction.ifEmpty {
                                                                "Continue straight"
                                                        },
                                                        style = MaterialTheme.typography.titleLarge,
                                                        color = MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Bold,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                        }
                                }

                                // Off-route / reroute feedback. Without this, a new route
                                // appears silently and it is impossible to tell whether the
                                // app noticed the deviation.
                                AnimatedVisibility(
                                        visible = isRerouting || !navigationNotice.isNullOrBlank(),
                                        enter = fadeIn() + expandVertically(),
                                        exit = fadeOut() + shrinkVertically()
                                ) {
                                        Row(
                                                modifier =
                                                        Modifier.padding(top = 12.dp)
                                                                .fillMaxWidth()
                                                                .clip(RoundedCornerShape(12.dp))
                                                                .background(
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                                                .copy(alpha = 0.12f)
                                                                )
                                                                .padding(
                                                                        horizontal = 12.dp,
                                                                        vertical = 9.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically
                                        ) {
                                                if (isRerouting) {
                                                        CircularProgressIndicator(
                                                                modifier = Modifier.size(14.dp),
                                                                strokeWidth = 2.dp,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .primary
                                                        )
                                                } else {
                                                        Icon(
                                                                imageVector =
                                                                        Icons.Default.Refresh,
                                                                contentDescription = null,
                                                                tint =
                                                                        MaterialTheme.colorScheme
                                                                                .primary,
                                                                modifier = Modifier.size(16.dp)
                                                        )
                                                }
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                        text =
                                                                navigationNotice
                                                                        ?: "Rerouting…",
                                                        style =
                                                                MaterialTheme.typography.bodySmall,
                                                        color =
                                                                MaterialTheme.colorScheme.onSurface,
                                                        fontWeight = FontWeight.Medium,
                                                        maxLines = 2,
                                                        overflow = TextOverflow.Ellipsis
                                                )
                                        }
                                }
                        }
                }

                // Bottom bar
                Card(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                        colors =
                                CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surface
                                )
                ) {
                        Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                        ) {
                                Column {
                                        Text(
                                                formatDuration(
                                                        if (remainingDuration > 0) remainingDuration
                                                        else routeResult.durationSeconds
                                                ),
                                                style = MaterialTheme.typography.titleLarge,
                                                fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                                "${formatDistance(
                                                        if (remainingDistance > 0) remainingDistance
                                                        else routeResult.distanceMeters
                                                )} remaining",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        // Next step button
                                        FilledTonalButton(
                                                onClick = onNext,
                                                enabled = currentIndex < totalInstructions - 1
                                        ) { Text("Next") }

                                        // Exit navigation
                                        FilledTonalButton(
                                                onClick = onExit,
                                                colors =
                                                        ButtonDefaults.filledTonalButtonColors(
                                                                containerColor =
                                                                        DangerRed.copy(
                                                                                alpha = 0.1f
                                                                        ),
                                                                contentColor = DangerRed
                                                        )
                                        ) { Text("Exit") }
                                }
                        }
                }
        }
}

/** Small Google-Maps-style speed bubble, bottom-left, above the navigation bar. */
@Composable
private fun NavigationSpeedBadge(speedMps: Float, modifier: Modifier = Modifier) {
        val kmh = (speedMps.coerceAtLeast(0f) * 3.6f).roundToInt()
        Surface(
                modifier = modifier.size(58.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                border =
                        BorderStroke(
                                1.dp,
                                MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                        ),
                shadowElevation = 4.dp
        ) {
                Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                ) {
                        Text(
                                "$kmh",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                                "km/h",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                }
        }
}

fun getDirectionIcon(instruction: String): ImageVector {
        val lower = instruction.lowercase()
        return when {
                lower.contains("u-turn") || lower.contains("u turn") || lower.contains("turn around") ->
                        if (lower.contains("right")) Icons.Rounded.UTurnRight
                        else Icons.Rounded.UTurnLeft
                lower.contains("roundabout") || lower.contains("rotary") ->
                        if (lower.contains("left")) Icons.Rounded.RoundaboutLeft
                        else Icons.Rounded.RoundaboutRight
                lower.contains("merge") || lower.contains("motorway") || lower.contains("freeway") ->
                        Icons.Rounded.CallMerge
                lower.contains("ramp") || lower.contains("exit") ->
                        if (lower.contains("left")) Icons.Rounded.RampLeft
                        else Icons.Rounded.RampRight
                lower.contains("sharp left") -> Icons.Rounded.TurnSharpLeft
                lower.contains("sharp right") -> Icons.Rounded.TurnSharpRight
                lower.contains("slight left") || lower.contains("bear left") ->
                        Icons.Rounded.TurnSlightLeft
                lower.contains("slight right") || lower.contains("bear right") ->
                        Icons.Rounded.TurnSlightRight
                lower.contains("keep left") || lower.contains("fork left") ->
                        Icons.Rounded.ForkLeft
                lower.contains("keep right") || lower.contains("fork right") ->
                        Icons.Rounded.ForkRight
                lower.contains("left") -> Icons.Rounded.TurnLeft
                lower.contains("right") -> Icons.Rounded.TurnRight
                lower.contains("arrive") ||
                        lower.contains("destination") ||
                        lower.contains("via point") -> Icons.Rounded.Flag
                lower.contains("continue") ||
                        lower.contains("straight") ||
                        lower.contains("head") -> Icons.Rounded.Straight
                else -> Icons.Rounded.Navigation
        }
}

// ============================================
// MAP LAYERS SHEET
// ============================================
