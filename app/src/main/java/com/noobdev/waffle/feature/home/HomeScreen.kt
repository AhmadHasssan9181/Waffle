package com.noobdev.waffle.feature.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.LocalGasStation
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.noobdev.waffle.Screen
import com.noobdev.waffle.core.location.currentOrLastLocation
import com.noobdev.waffle.data.model.DisasterAlert
import com.noobdev.waffle.data.model.StatusLevel
import com.noobdev.waffle.design.brand.WaffleWordmark
import com.noobdev.waffle.design.components.ActionTile
import com.noobdev.waffle.design.components.AllClearCard
import com.noobdev.waffle.design.components.DisasterAlertCard
import com.noobdev.waffle.design.components.Eyebrow
import com.noobdev.waffle.design.components.SectionHeader
import com.noobdev.waffle.design.components.WaffleSpace
import com.noobdev.waffle.design.components.formatDistanceKm
import com.noobdev.waffle.design.components.statusColor
import com.noobdev.waffle.design.theme.DangerRed
import com.noobdev.waffle.design.theme.SafeGreen
import com.noobdev.waffle.design.theme.Sky

/**
 * Home: the one screen you open in a hurry. It answers three questions in order — am I safe, what
 * is the weather doing, and where do I go next.
 */
@Composable
fun HomeScreen(navController: NavController, viewModel: HomeViewModel = viewModel()) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        currentOrLastLocation(context)?.let { (lat, lon) -> viewModel.updateLocation(lat, lon) }
    }

    Column(
            modifier =
                    Modifier.fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = WaffleSpace.lg)
    ) {
        Spacer(Modifier.height(WaffleSpace.md))

        // === BRAND + SETTINGS ===
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaffleWordmark(markSize = 32.dp, subtitle = "Stay found")
            Spacer(Modifier.weight(1f))
            Surface(
                    onClick = { navController.navigate(Screen.Settings.route) },
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surface,
                    border =
                            BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            )
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(
                            Icons.Rounded.Settings,
                            contentDescription = "Settings",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        Spacer(Modifier.height(WaffleSpace.xl))

        // === GREETING + LOCATION PILL ===
        Text(
                text = "${state.greeting}.",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(8.dp))
        Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface,
                    border =
                            BorderStroke(
                                    1.dp,
                                    MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                            )
            ) {
                Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            Icons.Rounded.LocationOn,
                            null,
                            Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                            text = state.locationName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Surface(
                    shape = RoundedCornerShape(50),
                    color = SafeGreen.copy(alpha = 0.14f)
            ) {
                Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(SafeGreen))
                    Spacer(Modifier.width(5.dp))
                    Text(
                            text = "Offline ready",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = SafeGreen
                    )
                }
            }
        }

        Spacer(Modifier.height(WaffleSpace.lg))

        // === QUICK "WHERE TO?" SEARCH & NAVIGATION LAUNCHER ===
        QuickNavigateCard(
                onOpenMap = { navController.navigate(Screen.Map.route) },
                onOpenNearby = { navController.navigate(Screen.Nearby.route) },
                onOpenOffline = { navController.navigate(Screen.OfflineMaps.route) }
        )

        Spacer(Modifier.height(WaffleSpace.md))

        // === SAFETY HERO ===
        SafetyHero(
                state = state,
                onViewAlerts = { navController.navigate(Screen.Alerts.route) }
        )

        Spacer(Modifier.height(WaffleSpace.md))

        // === WEATHER ===
        Row(horizontalArrangement = Arrangement.spacedBy(WaffleSpace.md)) {
            WeatherTile(
                    value = state.temperature,
                    label = if (state.weatherLoaded) state.weatherCondition else "Weather",
                    icon = Icons.Rounded.Thermostat,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f)
            )
            WeatherTile(
                    value = state.precipitation,
                    label = "Rain today",
                    icon = Icons.Rounded.WaterDrop,
                    tint = Sky,
                    modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(WaffleSpace.xxl))

        // === ACTIONS ===
        SectionHeader(title = "What do you need?")
        Spacer(Modifier.height(WaffleSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(WaffleSpace.md)) {
            ActionTile(
                    icon = Icons.Rounded.Map,
                    title = "Map & routes",
                    subtitle = "Works with no signal",
                    tint = MaterialTheme.colorScheme.primary,
                    onClick = { navController.navigate(Screen.Map.route) },
                    modifier = Modifier.weight(1f).height(136.dp)
            )
            ActionTile(
                    icon = Icons.Rounded.Storefront,
                    title = "Nearby",
                    subtitle = "Food, pharmacy, fuel",
                    tint = SafeGreen,
                    onClick = { navController.navigate(Screen.Nearby.route) },
                    modifier = Modifier.weight(1f).height(136.dp)
            )
        }
        Spacer(Modifier.height(WaffleSpace.md))
        Row(horizontalArrangement = Arrangement.spacedBy(WaffleSpace.md)) {
            ActionTile(
                    icon = Icons.Rounded.WarningAmber,
                    title = "Alerts",
                    subtitle = "USGS · GDACS · local",
                    tint = DangerRed,
                    onClick = { navController.navigate(Screen.Alerts.route) },
                    modifier = Modifier.weight(1f).height(136.dp)
            )
            ActionTile(
                    icon = Icons.Rounded.CloudDownload,
                    title = "Offline maps",
                    subtitle = "Saved for offline",
                    tint = Sky,
                    onClick = { navController.navigate(Screen.OfflineMaps.route) },
                    modifier = Modifier.weight(1f).height(136.dp)
            )
        }

        Spacer(Modifier.height(WaffleSpace.xxl))

        // === ALERTS PREVIEW ===
        val preview = previewAlerts(state)
        SectionHeader(
                title = "Near you",
                action = {
                    if (state.activeAlerts.isNotEmpty()) {
                        Surface(
                                modifier =
                                        Modifier.clip(RoundedCornerShape(50))
                                                .clickable {
                                                    navController.navigate(Screen.Alerts.route)
                                                },
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.surface,
                                border =
                                        BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                                        )
                        ) {
                            Row(
                                    modifier =
                                            Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                        text = "See all",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.width(3.dp))
                                Icon(
                                        Icons.Rounded.ArrowForward,
                                        null,
                                        Modifier.size(12.dp),
                                        tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
        )
        Spacer(Modifier.height(WaffleSpace.md))

        if (preview.isEmpty()) {
            AllClearCard()
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(WaffleSpace.md)) {
                preview.forEach { alert ->
                    DisasterAlertCard(
                            title = alert.title,
                            location = alert.location_name,
                            status = alert.status,
                            disasterType = alert.type,
                            timestamp = alert.timestamp?.take(16)?.replace("T", " "),
                            onClick = { navController.navigate(Screen.Alerts.route) },
                            distanceKm = state.distanceKm(alert)
                    )
                }
            }
        }

        Spacer(Modifier.height(WaffleSpace.xxl))

        // === EMERGENCY NUMBERS ===
        Eyebrow("Emergency")
        Spacer(Modifier.height(WaffleSpace.sm))
        Row(horizontalArrangement = Arrangement.spacedBy(WaffleSpace.sm)) {
            EmergencyNumber("1122", "Rescue", DangerRed, Modifier.weight(1f))
            EmergencyNumber("1166", "NDMA", MaterialTheme.colorScheme.primary, Modifier.weight(1f))
            EmergencyNumber("15", "Police", Sky, Modifier.weight(1f))
        }

        Spacer(Modifier.height(110.dp))
    }
}

// ============================================
// QUICK NAVIGATE LAUNCHER
// ============================================

@Composable
private fun QuickNavigateCard(
        onOpenMap: () -> Unit,
        onOpenNearby: () -> Unit,
        onOpenOffline: () -> Unit
) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            border =
                    BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
                    )
    ) {
        Column(modifier = Modifier.padding(WaffleSpace.md)) {
            Surface(
                    onClick = onOpenMap,
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
            ) {
                Row(
                        modifier =
                                Modifier.fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                            text = "Where to? Search places & routes…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                    Box(
                            modifier =
                                    Modifier.size(30.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Rounded.Directions,
                                contentDescription = "Open map",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickShortcutChip(
                        icon = Icons.Rounded.LocalGasStation,
                        label = "Fuel",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = onOpenNearby,
                        modifier = Modifier.weight(1f)
                )
                QuickShortcutChip(
                        icon = Icons.Rounded.Restaurant,
                        label = "Food",
                        tint = SafeGreen,
                        onClick = onOpenNearby,
                        modifier = Modifier.weight(1f)
                )
                QuickShortcutChip(
                        icon = Icons.Rounded.LocalHospital,
                        label = "Health",
                        tint = DangerRed,
                        onClick = onOpenNearby,
                        modifier = Modifier.weight(1f)
                )
                QuickShortcutChip(
                        icon = Icons.Rounded.CloudDownload,
                        label = "Offline",
                        tint = Sky,
                        onClick = onOpenOffline,
                        modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun QuickShortcutChip(
        icon: ImageVector,
        label: String,
        tint: Color,
        onClick: () -> Unit,
        modifier: Modifier = Modifier
) {
    Surface(
            onClick = onClick,
            modifier = modifier,
            shape = RoundedCornerShape(14.dp),
            color = tint.copy(alpha = 0.10f)
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
        ) {
            Icon(icon, null, Modifier.size(14.dp), tint = tint)
            Spacer(Modifier.width(4.dp))
            Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    softWrap = false
            )
        }
    }
}

// ============================================
// HERO
// ============================================

@Composable
private fun SafetyHero(state: HomeUiState, onViewAlerts: () -> Unit) {
    val nearby = state.nearbyAlerts
    val worst = state.worstAlert
    val level = if (nearby.isEmpty()) StatusLevel.SAFE else worst?.status ?: StatusLevel.SAFE
    val accent = statusColor(level)

    val title =
            when (level) {
                StatusLevel.CRITICAL ->
                        if (state.criticalCount == 1) "1 critical nearby"
                        else "${state.criticalCount} critical nearby"
                StatusLevel.WARNING ->
                        if (state.warningCount == 1) "1 warning nearby"
                        else "${state.warningCount} warnings nearby"
                StatusLevel.INFO ->
                        if (nearby.size == 1) "1 update nearby" else "${nearby.size} updates nearby"
                StatusLevel.SAFE -> "All clear nearby"
            }

    val subtitle =
            when {
                nearby.isNotEmpty() -> worst?.title ?: "Active alerts near you."
                state.latitude == null -> "Waiting for a location fix."
                state.nearestAlert != null ->
                        state.nearestAlert?.let { (alert, km) ->
                            "Nearest alert: ${alert.title ?: "unknown"} · ${formatDistanceKm(km)} away"
                        } ?: "No active disasters reported."
                else -> "No active disasters reported."
            }

    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(26.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, accent.copy(alpha = 0.28f))
    ) {
        Box(
                modifier =
                        Modifier.background(
                                Brush.linearGradient(
                                        listOf(accent.copy(alpha = 0.16f), Color.Transparent)
                                )
                        )
        ) {
            Row(
                    modifier = Modifier.padding(WaffleSpace.xl),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                        modifier =
                                Modifier.size(52.dp)
                                        .clip(CircleShape)
                                        .background(accent.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                ) {
                    Icon(
                            if (level == StatusLevel.SAFE) Icons.Rounded.CheckCircle
                            else Icons.Rounded.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                            tint = accent
                    )
                }
                Spacer(Modifier.width(WaffleSpace.lg))
                Column(Modifier.weight(1f)) {
                    Text(
                            text = title,
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                    )
                }
                if (level != StatusLevel.SAFE) {
                    IconButton(onClick = onViewAlerts) {
                        Icon(
                                Icons.Rounded.ArrowForward,
                                contentDescription = "View alerts",
                                tint = accent
                        )
                    }
                }
            }
        }
    }
}

// ============================================
// WEATHER
// ============================================

@Composable
private fun WeatherTile(
        value: String,
        label: String,
        icon: ImageVector,
        tint: Color,
        modifier: Modifier = Modifier
) {
    Surface(
            modifier = modifier,
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
                modifier = Modifier.padding(WaffleSpace.lg),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                    modifier =
                            Modifier.size(42.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, Modifier.size(21.dp), tint = tint)
            }
            Spacer(Modifier.width(WaffleSpace.md))
            Column {
                Text(
                        text = value,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                        text = label,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ============================================
// EMERGENCY NUMBERS
// ============================================

@Composable
private fun EmergencyNumber(
        number: String,
        label: String,
        tint: Color,
        modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Surface(
            modifier =
                    modifier.clip(RoundedCornerShape(18.dp)).clickable {
                        runCatching {
                            context.startActivity(
                                    Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number"))
                            )
                        }
                    },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
                modifier = Modifier.padding(vertical = WaffleSpace.md, horizontal = WaffleSpace.sm),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                    modifier =
                            Modifier.size(30.dp)
                                    .clip(CircleShape)
                                    .background(tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        Icons.Rounded.Phone,
                        null,
                        Modifier.size(15.dp),
                        tint = tint
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                    text = number,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================
// HELPERS
// ============================================

private fun previewAlerts(state: HomeUiState): List<DisasterAlert> =
        state.nearbyAlerts
                .filter { it.status == StatusLevel.CRITICAL || it.status == StatusLevel.WARNING }
                .sortedBy { alert ->
                    when (alert.status) {
                        StatusLevel.CRITICAL -> 0
                        StatusLevel.WARNING -> 1
                        else -> 2
                    }
                }
                .take(2)
                .ifEmpty {
                    state.nearbyAlerts
                            .sortedByDescending { it.timestamp ?: "" }
                            .take(1)
                            .filter { it.status != StatusLevel.SAFE }
                }
