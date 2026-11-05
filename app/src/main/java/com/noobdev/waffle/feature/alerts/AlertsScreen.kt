package com.noobdev.waffle.feature.alerts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.NotificationsNone
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobdev.waffle.Screen
import com.noobdev.waffle.data.model.DisasterAlert
import com.noobdev.waffle.data.model.StatusLevel
import com.noobdev.waffle.data.remote.chatWithGemini
import com.noobdev.waffle.design.components.DisasterAlertCard
import com.noobdev.waffle.design.components.Eyebrow
import com.noobdev.waffle.design.components.WaffleLoadingSpinner
import com.noobdev.waffle.design.components.WaffleSpace
import com.noobdev.waffle.design.components.disasterColor
import com.noobdev.waffle.design.components.disasterIcon
import com.noobdev.waffle.design.components.statusColor
import com.noobdev.waffle.design.components.formatDistanceKm
import com.noobdev.waffle.feature.map.MapFocus
import com.noobdev.waffle.feature.map.MapTarget
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The live feed. Everything here is already in Room, so a cold start without signal still lists
 * the alerts the last sync saw.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlertsScreen(navController: NavController, viewModel: AlertsViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var showSortMenu by remember { mutableStateOf(false) }
    var selectedAlert by remember { mutableStateOf<DisasterAlert?>(null) }
    var isRefreshing by remember { mutableStateOf(false) }

    var showAiSheet by remember { mutableStateOf(false) }
    var aiResponse by remember { mutableStateOf<String?>(null) }
    var isAiLoading by remember { mutableStateOf(false) }

    val filterOptions =
            listOf(
                    null to "All",
                    "flood" to "Floods",
                    "earthquake" to "Quakes",
                    "wildfire" to "Fires",
                    "storm" to "Storms"
            )

    LaunchedEffect(Unit) {
        if (uiState.alerts.isEmpty()) viewModel.refresh()
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
    ) {
        // === HEADER ===
        Column(Modifier.padding(horizontal = WaffleSpace.lg)) {
            Spacer(Modifier.height(WaffleSpace.lg))
            Eyebrow("Live feed")
            Spacer(Modifier.height(4.dp))
            Text(
                    text = "Alerts",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                    text =
                            if (uiState.alerts.isEmpty()) "Nothing cached yet"
                            else "${uiState.alerts.size} alerts · kept on this phone",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(WaffleSpace.lg))

            LazyRow(horizontalArrangement = Arrangement.spacedBy(WaffleSpace.sm)) {
                items(filterOptions) { (type, label) ->
                    val selected = uiState.filterType == type
                    Surface(
                            modifier =
                                    Modifier.clip(RoundedCornerShape(50))
                                            .clickable { viewModel.setFilter(type) },
                            shape = RoundedCornerShape(50),
                            color =
                                    if (selected)
                                            MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)
                                    else MaterialTheme.colorScheme.surface,
                            border =
                                    BorderStroke(
                                            1.dp,
                                            if (selected)
                                                    MaterialTheme.colorScheme.primary.copy(
                                                            alpha = 0.45f
                                                    )
                                            else MaterialTheme.colorScheme.outline.copy(
                                                    alpha = 0.5f
                                            )
                                    )
                    ) {
                        Text(
                                text = label,
                                style = MaterialTheme.typography.labelMedium,
                                color =
                                        if (selected) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier =
                                        Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(WaffleSpace.sm))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                        text = "Sort · ${uiState.sortOrder.displayName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box {
                    IconButton(onClick = { showSortMenu = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                                Icons.Rounded.Sort,
                                contentDescription = "Sort",
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                            expanded = showSortMenu,
                            onDismissRequest = { showSortMenu = false }
                    ) {
                        AlertSortOrder.entries.forEach { order ->
                            DropdownMenuItem(
                                    text = { Text(order.displayName) },
                                    onClick = {
                                        viewModel.setSortOrder(order)
                                        showSortMenu = false
                                    },
                                    trailingIcon = {
                                        if (uiState.sortOrder == order) {
                                            Icon(
                                                    Icons.Rounded.Check,
                                                    null,
                                                    Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                            )
                        }
                    }
                }

                Spacer(Modifier.weight(1f))

                Surface(
                        modifier =
                                Modifier.clip(RoundedCornerShape(50)).clickable {
                                    showAiSheet = true
                                },
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.surface,
                        border =
                                BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                ) {
                    Row(
                            modifier =
                                    Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                                Icons.Rounded.SmartToy,
                                null,
                                Modifier.size(13.dp),
                                tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                                text = "AI brief",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(WaffleSpace.sm))

        // === LIST ===
        PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    scope.launch {
                        isRefreshing = true
                        viewModel.refresh()
                        delay(600)
                        isRefreshing = false
                    }
                },
                modifier = Modifier.fillMaxSize()
        ) {
            if (uiState.isLoading && uiState.alerts.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    WaffleLoadingSpinner()
                }
            } else {
                val filtered = viewModel.getFilteredAlerts()
                if (filtered.isEmpty()) {
                    EmptyAlerts(
                            filterLabel =
                                    filterOptions.firstOrNull { it.first == uiState.filterType }
                                            ?.second,
                            onClear = { viewModel.setFilter(null) }
                    )
                } else {
                    LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding =
                                    PaddingValues(
                                            start = WaffleSpace.lg,
                                            end = WaffleSpace.lg,
                                            top = WaffleSpace.sm,
                                            bottom = 100.dp
                                    ),
                            verticalArrangement = Arrangement.spacedBy(WaffleSpace.md)
                    ) {
                        items(filtered, key = { it.id ?: it.hashCode().toString() }) { alert ->
                            DisasterAlertCard(
                                    title = alert.title,
                                    location = alert.location_name,
                                    status = alert.status,
                                    disasterType = alert.type,
                                    timestamp = prettyTime(alert.timestamp),
                                    onClick = { selectedAlert = alert },
                                    distanceKm = uiState.distanceKm(alert)
                            )
                        }
                        if (viewModel.hasMoreFilteredAlerts()) {
                            item {
                                TextButton(
                                        onClick = { viewModel.loadMore() },
                                        modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Show more alerts")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    selectedAlert?.let { alert ->
        AlertDetailSheet(
                alert = alert,
                distanceKm = uiState.distanceKm(alert),
                onDismiss = { selectedAlert = null },
                onShowOnMap = {
                    val lat = alert.latitude
                    val lon = alert.longitude
                    if (lat != null && lon != null) {
                        MapFocus.request(MapTarget(lat, lon, alert.title ?: "Alert"))
                        selectedAlert = null
                        navController.navigate(Screen.Map.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                }
        )
    }

    if (showAiSheet) {
        ModalBottomSheet(
                onDismissRequest = { showAiSheet = false },
                containerColor = MaterialTheme.colorScheme.surface,
                dragHandle = { BottomSheetDefaults.DragHandle() }
        ) {
            Column(
                    Modifier.fillMaxWidth()
                            .padding(horizontal = WaffleSpace.xl)
                            .padding(bottom = WaffleSpace.xxl)
                            .verticalScroll(rememberScrollState())
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            Icons.Rounded.SmartToy,
                            null,
                            Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(Modifier.width(WaffleSpace.sm))
                    Text(
                            "Waffle AI brief",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                        text =
                                "A plain-language summary of the alerts currently cached on this phone. Generated on demand.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(WaffleSpace.lg))

                if (isAiLoading) {
                    Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                        WaffleLoadingSpinner(size = 30.dp)
                    }
                } else if (aiResponse != null) {
                    Surface(
                            shape = MaterialTheme.shapes.medium,
                            color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                                text = aiResponse ?: "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(WaffleSpace.lg)
                        )
                    }
                }

                Spacer(Modifier.height(WaffleSpace.lg))
                Button(
                        onClick = {
                            isAiLoading = true
                            val alertsText =
                                    uiState.alerts.take(10).joinToString("\n") {
                                        "${it.title} at ${it.location_name ?: "unknown"}: ${it.type} (${it.status})"
                                    }
                            val prompt =
                                    "You are Waffle, a calm emergency-preparedness assistant. Summarise these active alerts for a person on the ground in under 120 words. Lead with the most dangerous one, say where it is, and give one practical next step. Do not use markdown headings.\n\n$alertsText"
                            scope.launch {
                                aiResponse = chatWithGemini(prompt)
                                isAiLoading = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        enabled = !isAiLoading && uiState.alerts.isNotEmpty(),
                        shape = RoundedCornerShape(15.dp)
                ) {
                    Text(if (aiResponse == null) "Generate brief" else "Regenerate")
                }
            }
        }
    }
}

// ============================================
// DETAIL
// ============================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlertDetailSheet(
        alert: DisasterAlert,
        distanceKm: Double?,
        onDismiss: () -> Unit,
        onShowOnMap: () -> Unit
) {
    val level = alert.status ?: StatusLevel.INFO
    val accent = statusColor(level)
    val typeColor = disasterColor(alert.type)

    ModalBottomSheet(
            onDismissRequest = onDismiss,
            containerColor = MaterialTheme.colorScheme.surface,
            dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
                Modifier.fillMaxWidth()
                        .padding(horizontal = WaffleSpace.xl)
                        .padding(bottom = WaffleSpace.xxl)
                        .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                        Modifier.size(44.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(typeColor.copy(alpha = 0.13f)),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            disasterIcon(alert.type),
                            null,
                            Modifier.size(22.dp),
                            tint = typeColor
                    )
                }
                Spacer(Modifier.width(WaffleSpace.md))
                Column {
                    Text(
                            text = alert.title ?: "Alert",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                            text = prettyTime(alert.timestamp),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(
                            Icons.Rounded.Close,
                            "Close",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(WaffleSpace.md))

            Surface(
                    shape = RoundedCornerShape(50),
                    color = accent.copy(alpha = 0.13f)
            ) {
                Row(
                        Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(accent))
                    Spacer(Modifier.width(5.dp))
                    Text(
                            text =
                                    when (level) {
                                        StatusLevel.CRITICAL -> "Critical"
                                        StatusLevel.WARNING -> "Warning"
                                        StatusLevel.INFO -> "Update"
                                        StatusLevel.SAFE -> "Resolved"
                                    },
                            style = MaterialTheme.typography.labelSmall,
                            color = accent
                    )
                }
            }

            val location = alert.location_name
            val locationLine =
                    buildString {
                        if (!location.isNullOrBlank()) append(location.trim())
                        if (distanceKm != null) {
                            if (isNotEmpty()) append(" · ")
                            append(formatDistanceKm(distanceKm))
                            append(" away")
                        }
                    }
            if (locationLine.isNotEmpty()) {
                Spacer(Modifier.height(WaffleSpace.lg))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            Icons.Rounded.LocationOn,
                            null,
                            Modifier.size(17.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                            text = locationLine,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            val details = alert.details.orEmpty()
            if (details.isNotEmpty()) {
                Spacer(Modifier.height(WaffleSpace.lg))
                Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Column(Modifier.padding(WaffleSpace.lg)) {
                        details.entries.take(12).forEach { (key, value) ->
                            Row(
                                    Modifier.fillMaxWidth().padding(vertical = 3.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                        text = key.replace("_", " ").replaceFirstChar {
                                            it.uppercase()
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(WaffleSpace.md))
                                Text(
                                        text = value?.toString() ?: "—",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.End
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(WaffleSpace.lg))

            if (alert.latitude != null && alert.longitude != null) {
                Button(
                        onClick = onShowOnMap,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(15.dp)
                ) {
                    Icon(Icons.Rounded.Directions, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(WaffleSpace.sm))
                    Text("Show on map")
                }
            } else {
                OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        shape = RoundedCornerShape(15.dp)
                ) { Text("No coordinates for this alert") }
            }
        }
    }
}

// ============================================
// EMPTY
// ============================================

@Composable
private fun EmptyAlerts(filterLabel: String?, onClear: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(WaffleSpace.xxxl)
        ) {
            Box(
                    Modifier.size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        Icons.Rounded.NotificationsNone,
                        null,
                        Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(WaffleSpace.lg))
            Text(
                    text = if (filterLabel == null) "No alerts cached" else "No $filterLabel",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                    text = "Pull down to sync, or check your connection.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
            )
            if (filterLabel != null) {
                Spacer(Modifier.height(WaffleSpace.md))
                TextButton(onClick = onClear) { Text("Show all alerts") }
            }
        }
    }
}

private fun prettyTime(timestamp: String?): String =
        timestamp?.replace("T", " ")?.take(16)?.trim().orEmpty()
