package com.noobdev.waffle.feature.shops

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Directions
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Storefront
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil.compose.AsyncImage
import com.google.android.gms.location.LocationServices
import com.noobdev.waffle.Screen
import com.noobdev.waffle.design.brand.WaffleMark
import com.noobdev.waffle.design.components.Eyebrow
import com.noobdev.waffle.design.components.WaffleSpace
import com.noobdev.waffle.design.theme.SafeGreen
import com.noobdev.waffle.feature.map.MapFocus
import com.noobdev.waffle.feature.map.MapTarget
import com.noobdev.waffle.feature.shops.data.Shop
import com.noobdev.waffle.feature.shops.data.ShopCategory
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * "What is open near me right now" — shops, pharmacies, fuel and banks from OpenStreetMap with
 * freely licensed photos from Wikimedia Commons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ShopsScreen(
        navController: NavController,
        viewModel: ShopsViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var selected by remember { mutableStateOf<Shop?>(null) }

    LaunchedEffect(Unit) {
        val fix = lastKnownLocation(context)
        if (fix != null) {
            viewModel.load(fix.first, fix.second)
        } else {
            // No permission or no fix yet: start from the capital rather than showing an empty app.
            viewModel.load(33.6844, 73.0479, "Islamabad")
        }
    }

    val visible = state.visible

    PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding =
                        PaddingValues(
                                start = WaffleSpace.lg,
                                end = WaffleSpace.lg,
                                top = WaffleSpace.lg,
                                bottom = 96.dp
                        ),
                verticalArrangement = Arrangement.spacedBy(WaffleSpace.md)
        ) {
            item {
                ShopsHeader(
                        locationLabel = state.locationLabel,
                        count = state.shops.size,
                        onSettings = { navController.navigate(Screen.Settings.route) }
                )
            }

            item {
                SearchField(
                        value = state.query,
                        onValueChange = viewModel::setQuery,
                        modifier = Modifier.padding(top = WaffleSpace.sm)
                )
            }

            item {
                CategoryRow(
                        selected = state.category,
                        onSelect = viewModel::setCategory
                )
            }

            item {
                Row(
                        modifier = Modifier.fillMaxWidth().padding(top = WaffleSpace.xs),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                            text =
                                    when {
                                        state.loading -> "Looking around…"
                                        visible.isEmpty() -> "Nothing in this category"
                                        else -> "${visible.size} places nearby"
                                    },
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.weight(1f))
                    RadiusPicker(
                            current = state.radiusMeters,
                            onSelect = { viewModel.setRadius(it) }
                    )
                }
            }

            if (state.loading && state.shops.isEmpty()) {
                items(5) { ShopSkeleton() }
            }

            if (state.error != null && state.shops.isEmpty()) {
                item {
                    Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Column(Modifier.padding(WaffleSpace.lg)) {
                            Text(
                                    text = state.error ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            Spacer(Modifier.height(WaffleSpace.md))
                            Button(
                                    onClick = { viewModel.refresh() },
                                    colors =
                                            ButtonDefaults.buttonColors(
                                                    containerColor =
                                                            MaterialTheme.colorScheme.error
                                            )
                            ) { Text("Try again") }
                        }
                    }
                }
            }

            if (!state.loading && visible.isEmpty() && state.error == null) {
                item {
                    EmptyShops(
                            filtered = state.query.isNotBlank() || state.category != null,
                            onClear = {
                                viewModel.setQuery("")
                                viewModel.setCategory(null)
                            }
                    )
                }
            }

            items(visible, key = { it.id }) { shop ->
                ShopCard(shop = shop, onClick = { selected = shop })
            }

            item {
                Text(
                        text = "Places © OpenStreetMap contributors · photos from Wikimedia Commons",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = WaffleSpace.md)
                )
            }
        }
    }

    selected?.let { shop ->
        ShopDetailSheet(
                shop = shop,
                onDismiss = { selected = null },
                onDirections = {
                    MapFocus.request(MapTarget(shop.lat, shop.lon, shop.name))
                    selected = null
                    navController.navigate(Screen.Map.route) {
                        popUpTo(Screen.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
        )
    }
}

// ============================================
// HEADER
// ============================================

@Composable
private fun ShopsHeader(locationLabel: String, count: Int, onSettings: () -> Unit) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            WaffleMark(size = 30.dp)
            Spacer(Modifier.width(WaffleSpace.sm))
            Eyebrow("Near you")
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onSettings) {
                Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(WaffleSpace.xs))
        Text(
                text = "Nearby",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(Modifier.height(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                    Icons.Rounded.LocationOn,
                    null,
                    Modifier.size(13.dp),
                    tint = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(4.dp))
            Text(
                    text = if (count > 0) "$locationLabel · $count places cached" else locationLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ============================================
// SEARCH + FILTERS
// ============================================

@Composable
private fun SearchField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    Icons.Rounded.Search,
                    null,
                    Modifier.size(19.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(WaffleSpace.md))
            BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle =
                            MaterialTheme.typography.bodyMedium.copy(
                                    color = MaterialTheme.colorScheme.onSurface
                            ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (value.isEmpty()) {
                            Text(
                                    text = "Search food, pharmacy, fuel…",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        inner()
                    }
            )
            if (value.isNotEmpty()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(20.dp)) {
                    Icon(
                            Icons.Rounded.Close,
                            "Clear",
                            Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(selected: ShopCategory?, onSelect: (ShopCategory?) -> Unit) {
    LazyRow(
            horizontalArrangement = Arrangement.spacedBy(WaffleSpace.sm),
            contentPadding = PaddingValues(vertical = WaffleSpace.xs)
    ) {
        item {
            CategoryChip(
                    label = "All",
                    icon = null,
                    tint = MaterialTheme.colorScheme.primary,
                    selected = selected == null,
                    onClick = { onSelect(null) }
            )
        }
        items(ShopCategory.entries) { category ->
            CategoryChip(
                    label = category.shortLabel,
                    icon = category.icon,
                    tint = category.tint,
                    selected = selected == category,
                    onClick = { onSelect(if (selected == category) null else category) }
            )
        }
    }
}

@Composable
private fun CategoryChip(
        label: String,
        icon: androidx.compose.ui.graphics.vector.ImageVector?,
        tint: Color,
        selected: Boolean,
        onClick: () -> Unit
) {
    Surface(
            modifier = Modifier.clip(RoundedCornerShape(50)).clickable { onClick() },
            shape = RoundedCornerShape(50),
            color = if (selected) tint.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surface,
            border =
                    BorderStroke(
                            1.dp,
                            if (selected) tint.copy(alpha = 0.45f)
                            else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                    )
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (icon != null) {
                Icon(
                        icon,
                        null,
                        Modifier.size(15.dp),
                        tint = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) tint else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RadiusPicker(current: Int, onSelect: (ShopRadius) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Surface(
                modifier = Modifier.clip(RoundedCornerShape(50)).clickable { expanded = true },
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
        ) {
            Row(
                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                        Icons.Rounded.MyLocation,
                        null,
                        Modifier.size(13.dp),
                        tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(5.dp))
                Text(
                        text = ShopRadius.entries.firstOrNull { it.meters == current }?.label ?: "1.5 km",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        androidx.compose.material3.DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
        ) {
            ShopRadius.entries.forEach { radius ->
                androidx.compose.material3.DropdownMenuItem(
                        text = { Text(radius.label) },
                        onClick = {
                            expanded = false
                            onSelect(radius)
                        }
                )
            }
        }
    }
}

// ============================================
// SHOP CARD
// ============================================

@Composable
private fun ShopCard(shop: Shop, onClick: () -> Unit) {
    Surface(
            modifier =
                    Modifier.fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { onClick() },
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Row(
                modifier = Modifier.padding(WaffleSpace.md),
                verticalAlignment = Alignment.CenterVertically
        ) {
            ShopPhoto(shop = shop, size = 78.dp)
            Spacer(Modifier.width(WaffleSpace.md))
            Column(Modifier.weight(1f)) {
                Text(
                        text = shop.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                            shop.category.icon,
                            null,
                            Modifier.size(13.dp),
                            tint = shop.category.tint
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                            text = shop.category.label,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                    )
                    Text(
                            text = " · ${shop.distanceLabel}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!shop.address.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                            text = shop.address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
                if (!shop.openingHours.isNullOrBlank()) {
                    Spacer(Modifier.height(5.dp))
                    HoursPill(shop.openingHours)
                }
            }
            Spacer(Modifier.width(WaffleSpace.xs))
            Icon(
                    Icons.Rounded.ChevronRight,
                    null,
                    Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun HoursPill(hours: String) {
    val open24 = hours.trim() == "24/7"
    Surface(
            shape = RoundedCornerShape(50),
            color =
                    if (open24) SafeGreen.copy(alpha = 0.13f)
                    else MaterialTheme.colorScheme.surfaceVariant,
            border =
                    if (open24) null
                    else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Row(
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                    Icons.Rounded.Schedule,
                    null,
                    Modifier.size(10.dp),
                    tint = if (open24) SafeGreen else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(3.dp))
            Text(
                    text = if (open24) "Open 24/7" else hours,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (open24) SafeGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ShopPhoto(shop: Shop, size: Dp) {
    Box(
            modifier =
                    Modifier.size(size)
                            .clip(RoundedCornerShape(16.dp))
                            .background(shop.category.tint.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
    ) {
        if (!shop.photoUrl.isNullOrBlank()) {
            AsyncImage(
                    model = shop.photoUrl,
                    contentDescription = shop.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                    shop.category.icon,
                    null,
                    Modifier.size(size * 0.36f),
                    tint = shop.category.tint.copy(alpha = 0.8f)
            )
        }
    }
}

@Composable
private fun ShopSkeleton() {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.35f))
    ) {
        Row(Modifier.padding(WaffleSpace.md), verticalAlignment = Alignment.CenterVertically) {
            Box(
                    Modifier.size(78.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
            )
            Spacer(Modifier.width(WaffleSpace.md))
            Column(Modifier.weight(1f)) {
                Box(
                        Modifier.fillMaxWidth(0.6f)
                                .height(13.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                )
                Spacer(Modifier.height(8.dp))
                Box(
                        Modifier.fillMaxWidth(0.4f)
                                .height(10.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        }
    }
}

@Composable
private fun EmptyShops(filtered: Boolean, onClear: () -> Unit) {
    Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f))
    ) {
        Column(
                modifier = Modifier.padding(WaffleSpace.xxl),
                horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                    Modifier.size(52.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
            ) {
                Icon(
                        Icons.Rounded.Storefront,
                        null,
                        Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(WaffleSpace.md))
            Text(
                    text = if (filtered) "Nothing matches" else "No places mapped here yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(4.dp))
            Text(
                    text =
                            if (filtered) "Try another category or clear the search."
                            else "OpenStreetMap coverage is thin in this area. Try a wider radius.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (filtered) {
                Spacer(Modifier.height(WaffleSpace.md))
                OutlinedButton(onClick = onClear) { Text("Clear filters") }
            }
        }
    }
}

// ============================================
// DETAIL SHEET
// ============================================

// ============================================
// LOCATION
// ============================================

private suspend fun lastKnownLocation(context: Context): Pair<Double, Double>? {
    val granted =
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
                    PackageManager.PERMISSION_GRANTED
    if (!granted) return null

    return suspendCancellableCoroutine { continuation ->
        try {
            LocationServices.getFusedLocationProviderClient(context)
                    .lastLocation
                    .addOnSuccessListener { location ->
                        continuation.resume(
                                location?.let { it.latitude to it.longitude }
                        )
                    }
                    .addOnFailureListener { continuation.resume(null) }
        } catch (security: SecurityException) {
            continuation.resume(null)
        }
    }
}
