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
fun GoogleSearchBar(
        query: String,
        onQueryChange: (String) -> Unit,
        isSearching: Boolean,
        onClear: () -> Unit,
        onBack: () -> Unit,
        onClick: () -> Unit,
        placeholder: String = "Search here"
) {
        val focusRequester = remember { FocusRequester() }
        val focusManager = LocalFocusManager.current

        LaunchedEffect(isSearching) {
                if (isSearching) {
                        focusRequester.requestFocus()
                }
        }

        Card(
                modifier = Modifier.fillMaxWidth().shadow(4.dp, RoundedCornerShape(28.dp)),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                        // Back/Menu button
                        IconButton(onClick = if (isSearching) onBack else onClick) {
                                Icon(
                                        imageVector =
                                                if (isSearching) Icons.Default.ArrowBack
                                                else Icons.Default.Menu,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                        }

                        // Search field
                        if (isSearching) {
                                OutlinedTextField(
                                        value = query,
                                        onValueChange = onQueryChange,
                                        placeholder = { Text(placeholder) },
                                        modifier =
                                                Modifier.weight(1f).focusRequester(focusRequester),
                                        colors =
                                                OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = Color.Transparent,
                                                        unfocusedBorderColor = Color.Transparent
                                                ),
                                        singleLine = true,
                                        keyboardOptions =
                                                KeyboardOptions(imeAction = ImeAction.Search),
                                        keyboardActions =
                                                KeyboardActions(
                                                        onSearch = { focusManager.clearFocus() }
                                                )
                                )
                        } else {
                                Text(
                                        text = if (query.isEmpty()) placeholder else query,
                                        modifier =
                                                Modifier.weight(1f)
                                                        .clickable { onClick() }
                                                        .padding(vertical = 12.dp),
                                        color =
                                                if (query.isEmpty())
                                                        MaterialTheme.colorScheme.onSurfaceVariant
                                                else MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.bodyLarge
                                )
                        }

                        // Clear button
                        if (query.isNotEmpty()) {
                                IconButton(onClick = onClear) { Icon(Icons.Default.Clear, "Clear") }
                        }
                }
        }
}

// ============================================
// SEARCH RESULTS CARD
// ============================================

@Composable
fun SearchResultsCard(query: String, onResultClick: (GeoSearchResult) -> Unit) {
        var suggestions by remember { mutableStateOf<List<GeoSearchResult>>(emptyList()) }
        var isLoading by remember { mutableStateOf(false) }

        LaunchedEffect(query) {
                if (query.isBlank()) {
                        suggestions = getQuickCitySuggestions()
                } else if (query.length >= 2) {
                        isLoading = true
                        suggestions =
                                withContext(kotlinx.coroutines.Dispatchers.Default) {
                                        OfflineGeocoder.search(query)
                                }
                        isLoading = false
                } else {
                        suggestions = emptyList()
                }
        }

        Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors =
                        CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
        ) {
                Column {
                        // Quick actions row
                        if (query.isBlank()) {
                                Row(
                                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceEvenly
                                ) {
                                        QuickActionChip(
                                                Icons.Default.LocalGasStation,
                                                "Petrol",
                                                Color(0xFFFF9800)
                                        )
                                        QuickActionChip(
                                                Icons.Default.Restaurant,
                                                "Food",
                                                Color(0xFF4CAF50)
                                        )
                                        QuickActionChip(
                                                Icons.Default.LocalHospital,
                                                "Hospital",
                                                DangerRed
                                        )
                                        QuickActionChip(
                                                Icons.Default.LocalPharmacy,
                                                "Pharmacy",
                                                Color(0xFF2196F3)
                                        )
                                }
                                HorizontalDivider()
                        }

                        if (isLoading) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth().padding(vertical = 24.dp),
                                        contentAlignment = Alignment.Center
                                ) {
                                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                        } else if (suggestions.isEmpty() && query.isNotBlank()) {
                                Box(
                                        modifier =
                                                Modifier.fillMaxWidth().padding(
                                                        horizontal = 16.dp,
                                                        vertical = 24.dp
                                                )
                                ) {
                                        Text(
                                                text = "No offline matches found",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                style = MaterialTheme.typography.bodyMedium
                                        )
                                }
                        }

                        // Results
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                                items(suggestions) { result ->
                                        Row(
                                                modifier =
                                                        Modifier.fillMaxWidth()
                                                                .clickable { onResultClick(result) }
                                                                .padding(
                                                                        horizontal = 16.dp,
                                                                        vertical = 12.dp
                                                                ),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                                        ) {
                                                Icon(
                                                        imageVector =
                                                                when (result.type) {
                                                                        "hospital" ->
                                                                                Icons.Default
                                                                                        .LocalHospital
                                                                        "emergency" ->
                                                                                Icons.Default
                                                                                        .Emergency
                                                                        "city" ->
                                                                                Icons.Default
                                                                                        .LocationCity
                                                                        else -> Icons.Default.Place
                                                                },
                                                        contentDescription = null,
                                                        tint = MaterialTheme.colorScheme.primary
                                                )
                                                Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                                result.name,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodyLarge
                                                        )
                                                        Text(
                                                                result.address,
                                                                style =
                                                                        MaterialTheme.typography
                                                                                .bodySmall,
                                                                color =
                                                                        MaterialTheme.colorScheme
                                                                                .onSurfaceVariant
                                                        )
                                                }
                                                Icon(
                                                        Icons.Default.NorthEast,
                                                        null,
                                                        tint =
                                                                MaterialTheme.colorScheme
                                                                        .onSurfaceVariant,
                                                        modifier = Modifier.size(20.dp)
                                                )
                                        }
                                }
                        }
                }
        }
}

@Composable
fun QuickActionChip(icon: ImageVector, label: String, color: Color) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.clickable {}
        ) {
                Box(
                        modifier =
                                Modifier.size(48.dp)
                                        .clip(CircleShape)
                                        .background(color.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                ) { Icon(icon, null, tint = color, modifier = Modifier.size(24.dp)) }
                Spacer(modifier = Modifier.height(4.dp))
                Text(label, style = MaterialTheme.typography.labelSmall)
        }
}

// ============================================
// ROUTE PREVIEW CARD - with working Walk/Drive
// ============================================
