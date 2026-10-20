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


@Composable
fun MapLayersSheet(
        currentStyleIndex: Int,
        onStyleSelect: (Int) -> Unit,
        onFloodHistory: () -> Unit,
        onClose: () -> Unit
) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(
                        "Map type",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Map styles grid (like Google Maps)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        val styles = mapTilerStyles.keys.toList()
                        items(styles.size) { index ->
                                MapStyleCard(
                                        name = styles[index],
                                        isSelected = index == currentStyleIndex,
                                        onClick = { onStyleSelect(index) }
                                )
                        }
                }

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                        "Map details",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Flood history toggle
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(12.dp))
                                        .clickable { onFloodHistory() }
                                        .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                        Box(
                                modifier =
                                        Modifier.size(40.dp)
                                                .clip(CircleShape)
                                                .background(FloodColor.copy(alpha = 0.1f)),
                                contentAlignment = Alignment.Center
                        ) { Icon(Icons.Default.WaterDrop, null, tint = FloodColor) }
                        Column(modifier = Modifier.weight(1f)) {
                                Text("Flood History", style = MaterialTheme.typography.bodyLarge)
                                Text(
                                        "Show EMSR838 Pakistan flood data",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }
                        Icon(Icons.Default.ChevronRight, null)
                }

                Spacer(modifier = Modifier.height(32.dp))
        }
}

@Composable
fun MapStyleCard(name: String, isSelected: Boolean, onClick: () -> Unit) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable { onClick() }
        ) {
                Box(
                        modifier =
                                Modifier.size(72.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .border(
                                                width = if (isSelected) 3.dp else 1.dp,
                                                color =
                                                        if (isSelected) MaterialTheme.colorScheme.primary
                                                        else
                                                                MaterialTheme.colorScheme
                                                                        .outlineVariant,
                                                shape = RoundedCornerShape(12.dp)
                                        )
                                        .background(
                                                when (name) {
                                                        "Dark" -> Color(0xFF1A1A2E)
                                                        "Satellite", "Hybrid" -> Color(0xFF2D4739)
                                                        else -> Color(0xFFE8F4EA)
                                                }
                                        ),
                        contentAlignment = Alignment.Center
                ) {
                        Icon(
                                imageVector =
                                        when (name) {
                                                "Satellite", "Hybrid" -> Icons.Default.Satellite
                                                "Dark" -> Icons.Default.DarkMode
                                                "Topo" -> Icons.Default.Terrain
                                                else -> Icons.Default.Map
                                        },
                                contentDescription = null,
                                tint =
                                        when (name) {
                                                "Dark" -> Color.White
                                                "Satellite", "Hybrid" -> Color.White
                                                else -> MaterialTheme.colorScheme.primary
                                        },
                                modifier = Modifier.size(28.dp)
                        )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                        text = name,
                        style = MaterialTheme.typography.labelMedium,
                        color =
                                if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurface
                )
        }
}

// ============================================
// HELPER FUNCTIONS
// ============================================

/**
 * Calculate distance between two coordinates using Haversine formula
 * @return distance in meters
 */
