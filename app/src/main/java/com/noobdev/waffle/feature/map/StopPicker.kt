package com.noobdev.waffle.feature.map

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.noobdev.waffle.core.navigation.GeoGeometry
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.data.remote.RouteResult
import com.noobdev.waffle.design.theme.SafeGreen
import com.noobdev.waffle.feature.shops.data.Shop
import com.noobdev.waffle.feature.shops.data.ShopCategory
import java.util.Locale
import kotlin.math.roundToInt

private data class StopCandidate(
        val shop: Shop,
        val detourMeters: Double,
        val routeIndex: Int
)

private val stopCategories =
        listOf(
                ShopCategory.FUEL,
                ShopCategory.FOOD,
                ShopCategory.STAY,
                ShopCategory.HEALTH,
                ShopCategory.GROCERY
        )

/**
 * A picker for stops that are actually near the planned route. Unlike the old one-tap shortcut,
 * this lets the driver choose the exact fuel station, restaurant or rest stop instead of silently
 * taking the closest cached shop.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StopPickerSheet(
        visible: Boolean,
        shops: List<Shop>,
        route: RouteResult?,
        initialCategory: ShopCategory?,
        onSelect: (Shop) -> Unit,
        onSearch: () -> Unit,
        onDismiss: () -> Unit
) {
    if (!visible) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var selectedCategory by remember(initialCategory) { mutableStateOf(initialCategory) }
    val routePoints =
            remember(route) {
                route?.coordinates?.map { GeoPoint(it.first, it.second) }
            }
    val candidates =
            remember(shops, selectedCategory, routePoints) {
                shops.asSequence()
                        .filter { selectedCategory == null || it.category == selectedCategory }
                        .mapNotNull { shop ->
                            val point = GeoPoint(shop.lat, shop.lon)
                            val nearestIndex =
                                    routePoints
                                            ?.indices
                                            ?.minByOrNull { index ->
                                                GeoGeometry.haversineMeters(
                                                        point,
                                                        routePoints[index]
                                                )
                                            }
                            val detour =
                                    if (nearestIndex != null) {
                                        GeoGeometry.haversineMeters(
                                                point,
                                                routePoints[nearestIndex]
                                        )
                                    } else {
                                        shop.distanceMeters.toDouble()
                                    }
                            if (detour > 2_500.0) return@mapNotNull null
                            StopCandidate(
                                    shop = shop,
                                    detourMeters = detour,
                                    routeIndex = nearestIndex ?: Int.MAX_VALUE
                            )
                        }
                        .distinctBy { it.shop.id }
                        .sortedWith(compareBy({ it.detourMeters }, { it.routeIndex }))
                        .take(30)
                        .toList()
            }

    ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                            "Add a stop",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                    )
                    Text(
                            "Pick a place on your route",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(onClick = onSearch) {
                    Icon(Icons.Default.Search, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Search")
                }
            }

            LazyRow(
                    contentPadding = PaddingValues(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    FilterChip(
                            selected = selectedCategory == null,
                            onClick = { selectedCategory = null },
                            label = { Text("All") },
                            leadingIcon = {
                                Icon(
                                        Icons.Default.Place,
                                        null,
                                        modifier = Modifier.size(16.dp)
                                )
                            }
                    )
                }
                items(stopCategories) { category ->
                    FilterChip(
                            selected = selectedCategory == category,
                            onClick = {
                                selectedCategory =
                                        if (selectedCategory == category) null else category
                            },
                            label = { Text(category.shortLabel) },
                            leadingIcon = {
                                Icon(
                                        category.icon,
                                        null,
                                        modifier = Modifier.size(16.dp),
                                        tint = category.tint
                                )
                            }
                    )
                }
            }

            if (candidates.isEmpty()) {
                EmptyStopState(selectedCategory = selectedCategory, onSearch = onSearch)
            } else {
                LazyColumn(
                        modifier = Modifier.heightIn(max = 460.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(candidates, key = { it.shop.id }) { candidate ->
                        StopCandidateRow(candidate = candidate, onSelect = onSelect)
                    }
                }
            }
            Spacer(Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun StopCandidateRow(
        candidate: StopCandidate,
        onSelect: (Shop) -> Unit
) {
    val shop = candidate.shop
    val category = shop.category
    Surface(
            onClick = { onSelect(shop) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
                modifier = Modifier.padding(10.dp),
                verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                    modifier =
                            Modifier.size(58.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(category.tint.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
            ) {
                if (!shop.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                            model = shop.photoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                            category.icon,
                            null,
                            modifier = Modifier.size(28.dp),
                            tint = category.tint
                    )
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                        shop.name,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                            category.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = category.tint,
                            fontWeight = FontWeight.SemiBold
                    )
                    Text(
                            " · ${detourLabel(candidate.detourMeters)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                shop.address?.takeIf { it.isNotBlank() }?.let { address ->
                    Text(
                            address,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                    )
                }
                if (shop.isOpen24h) {
                    Text(
                            "Open 24/7",
                            style = MaterialTheme.typography.labelSmall,
                            color = SafeGreen,
                            fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                    Icons.Default.Add,
                    contentDescription = "Add ${shop.name}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun EmptyStopState(
        selectedCategory: ShopCategory?,
        onSearch: () -> Unit
) {
    Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
                selectedCategory?.icon ?: Icons.Default.Place,
                null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
                if (selectedCategory != null) {
                    "No ${selectedCategory.shortLabel.lowercase()} found near this route"
                } else {
                    "No stops found near this route"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
        )
        Text(
                "Try another category or search for a specific place.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        TextButton(onClick = onSearch) { Text("Search for a place") }
    }
}

private fun detourLabel(meters: Double): String =
        when {
            meters < 40.0 -> "On route"
            meters < 1_000.0 -> "${meters.roundToInt()} m off route"
            else -> String.format(Locale.US, "%.1f km off route", meters / 1000.0)
        }
