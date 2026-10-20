package com.noobdev.waffle.feature.map

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.feature.shops.data.Shop
import com.noobdev.waffle.feature.shops.data.ShopCategory
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The "shops on the map" layer. Dots, not pins: at city zoom a hundred pin heads become a wall of
 * noise. Category colour carries the meaning, the tap opens the same detail sheet as Nearby.
 */
@Composable
internal fun MapShopMarkers(map: MapLibreMap?, styleIndex: Int, shops: List<Shop>) {
    LaunchedEffect(map, styleIndex, shops) {
        val mapLibre = map ?: return@LaunchedEffect
        mapLibre.getStyle { style ->
            runCatching {
                val geoJson = shopsGeoJson(shops)
                val existing = style.getSourceAs<GeoJsonSource>("shops-source")
                if (existing != null) {
                    existing.setGeoJson(geoJson)
                    val show = if (shops.isEmpty()) Property.NONE else Property.VISIBLE
                    style.getLayer("shops-dot")?.setProperties(visibility(show))
                    style.getLayer("shops-halo")?.setProperties(visibility(show))
                    return@getStyle
                }
                if (shops.isEmpty()) return@getStyle

                style.addSource(GeoJsonSource("shops-source", geoJson))

                style.addLayer(
                        CircleLayer("shops-halo", "shops-source")
                                .withProperties(
                                        circleColor(Expression.toColor(Expression.get("color"))),
                                        circleRadius(
                                                Expression.interpolate(
                                                        Expression.exponential(1.3f),
                                                        Expression.zoom(),
                                                        Expression.stop(11f, 7f),
                                                        Expression.stop(16f, 13f)
                                                )
                                        ),
                                        circleOpacity(0.18f)
                                )
                                .apply { minZoom = 10.5f }
                )

                style.addLayer(
                        CircleLayer("shops-dot", "shops-source")
                                .withProperties(
                                        circleColor(Expression.toColor(Expression.get("color"))),
                                        circleRadius(
                                                Expression.interpolate(
                                                        Expression.exponential(1.3f),
                                                        Expression.zoom(),
                                                        Expression.stop(11f, 3.4f),
                                                        Expression.stop(16f, 6.2f)
                                                )
                                        ),
                                        circleStrokeWidth(1.6f),
                                        circleStrokeColor(AndroidColor.WHITE),
                                        circleOpacity(0.96f)
                                )
                                .apply { minZoom = 10.5f }
                )
            }
        }
    }

    DisposableEffect(map) {
        onDispose {
            map?.getStyle { style ->
                runCatching {
                    style.removeLayer("shops-dot")
                    style.removeLayer("shops-halo")
                    style.removeSource("shops-source")
                }
            }
        }
    }
}

internal fun shopsGeoJson(shops: List<Shop>): String {
    val features = JSONArray()
    for (shop in shops) {
        features.put(
                JSONObject()
                        .put("type", "Feature")
                        .put(
                                "geometry",
                                JSONObject()
                                        .put("type", "Point")
                                        .put(
                                                "coordinates",
                                                JSONArray().put(shop.lon).put(shop.lat)
                                        )
                        )
                        .put(
                                "properties",
                                JSONObject()
                                        .put("shopId", shop.id)
                                        .put("name", shop.name)
                                        .put("category", shop.category.name)
                                        .put("color", shop.category.markerHex())
                        )
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

/** Brand-palette hex for a category — mirrors `ShopCategory.tint` without pulling Compose into the map. */
internal fun ShopCategory.markerHex(): String =
        when (this) {
            ShopCategory.FOOD -> "#F2A33C"
            ShopCategory.GROCERY -> "#3FA37A"
            ShopCategory.HEALTH -> "#4FB3D9"
            ShopCategory.FUEL -> "#C9761B"
            ShopCategory.MONEY -> "#8C97A8"
            ShopCategory.SHOPPING -> "#F2A33C"
            ShopCategory.SERVICES -> "#3FA37A"
            ShopCategory.STAY -> "#4FB3D9"
            ShopCategory.OTHER -> "#8C97A8"
        }

/**
 * Google-Maps-style category chips under the search bar. Counts make the map feel alive and let
 * people answer "is there a pharmacy near me" without reading a list.
 */
@Composable
internal fun ShopFilterRow(
        shops: List<Shop>,
        selected: ShopCategory?,
        onSelect: (ShopCategory?) -> Unit
) {
    val counts = shops.groupingBy { it.category }.eachCount()
    val categories = counts.entries.sortedByDescending { it.value }.map { it.key }
    val options = listOf<ShopCategory?>(null) + categories

    LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(options) { category ->
            val isSelected = category == selected
            val tint = category?.tint ?: MaterialTheme.colorScheme.primary
            Surface(
                    modifier =
                            Modifier.clip(RoundedCornerShape(50))
                                    .clickable { onSelect(category) },
                    shape = RoundedCornerShape(50),
                    color =
                            if (isSelected) tint.copy(alpha = 0.9f)
                            else MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                    border =
                            BorderStroke(
                                    1.dp,
                                    if (isSelected) tint
                                    else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                            )
            ) {
                Row(
                        modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically
                ) {
                    if (category != null) {
                        Icon(
                                category.icon,
                                null,
                                Modifier.size(13.dp),
                                tint = if (isSelected) Color(0xFF231607) else tint
                        )
                        Spacer(Modifier.width(5.dp))
                    }
                    Text(
                            text = category?.shortLabel ?: "All",
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                    if (isSelected) Color(0xFF231607)
                                    else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                            text = "${counts[category] ?: shops.size}",
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                    if (isSelected) Color(0xFF231607).copy(alpha = 0.7f)
                                    else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
