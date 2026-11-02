package com.noobdev.waffle.feature.map

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.noobdev.waffle.feature.shops.data.Shop
import com.noobdev.waffle.feature.shops.data.ShopCategory
import org.json.JSONArray
import org.json.JSONObject
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.Property
import org.maplibre.android.style.layers.PropertyFactory.circleColor
import org.maplibre.android.style.layers.PropertyFactory.circleOpacity
import org.maplibre.android.style.layers.PropertyFactory.circleRadius
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeColor
import org.maplibre.android.style.layers.PropertyFactory.circleStrokeWidth
import org.maplibre.android.style.layers.PropertyFactory.iconAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.iconAnchor
import org.maplibre.android.style.layers.PropertyFactory.iconIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.iconImage
import org.maplibre.android.style.layers.PropertyFactory.iconSize
import org.maplibre.android.style.layers.PropertyFactory.textAllowOverlap
import org.maplibre.android.style.layers.PropertyFactory.textColor
import org.maplibre.android.style.layers.PropertyFactory.textField
import org.maplibre.android.style.layers.PropertyFactory.textFont
import org.maplibre.android.style.layers.PropertyFactory.textIgnorePlacement
import org.maplibre.android.style.layers.PropertyFactory.textSize
import org.maplibre.android.style.layers.PropertyFactory.visibility
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonOptions
import org.maplibre.android.style.sources.GeoJsonSource

/**
 * The "amenities on the map" layer.
 *
 * Nothing is drawn until the user picks a category in [ShopFilterRow]: a hundred pins over a city
 * is noise, and the map should stay calm until someone asks "where is fuel?". Each marker is a
 * small white badge with the category's icon, not a plain dot.
 */
@Composable
internal fun MapShopMarkers(map: MapLibreMap?, styleIndex: Int, shops: List<Shop>) {
    val icons = rememberShopIconBitmaps()

    LaunchedEffect(map, styleIndex, shops, icons) {
        val mapLibre = map ?: return@LaunchedEffect
        mapLibre.getStyle { style ->
            runCatching {
                icons.forEach { (category, bitmap) ->
                    val name = category.mapIconName()
                    runCatching { style.removeImage(name) }
                    style.addImage(name, bitmap)
                }

                val geoJson = shopsGeoJson(shops)
                val existing = style.getSourceAs<GeoJsonSource>("shops-source")
                if (existing != null) {
                    existing.setGeoJson(geoJson)
                    val show = if (shops.isEmpty()) Property.NONE else Property.VISIBLE
                    style.getLayer("shops-symbol")?.setProperties(visibility(show))
                    style.getLayer("shops-cluster")?.setProperties(visibility(show))
                    style.getLayer("shops-cluster-count")?.setProperties(visibility(show))
                    return@getStyle
                }
                if (shops.isEmpty()) return@getStyle

                style.addSource(
                        GeoJsonSource(
                                "shops-source",
                                geoJson,
                                GeoJsonOptions()
                                        .withCluster(true)
                                        .withClusterRadius(50)
                                        .withClusterMaxZoom(14)
                        )
                )
                style.addLayer(
                        CircleLayer("shops-cluster", "shops-source")
                                .withProperties(
                                        circleColor(AndroidColor.parseColor("#F2A33C")),
                                        circleRadius(18f),
                                        circleStrokeWidth(2.5f),
                                        circleStrokeColor(AndroidColor.WHITE),
                                        circleOpacity(0.95f)
                                )
                                .withFilter(Expression.has("point_count"))
                                .apply { minZoom = 6f }
                )
                style.addLayer(
                        SymbolLayer("shops-cluster-count", "shops-source")
                                .withProperties(
                                        textField(
                                                Expression.get("point_count_abbreviated")
                                        ),
                                        textFont(arrayOf("Noto Sans Regular")),
                                        textSize(12f),
                                        textColor(AndroidColor.WHITE),
                                        textAllowOverlap(true),
                                        textIgnorePlacement(true)
                                )
                                .withFilter(Expression.has("point_count"))
                                .apply { minZoom = 6f }
                )
                style.addLayer(
                        SymbolLayer("shops-symbol", "shops-source")
                                .withProperties(
                                        iconImage(Expression.get("icon")),
                                        iconSize(0.9f),
                                        iconAllowOverlap(true),
                                        iconIgnorePlacement(true),
                                        iconAnchor(Property.ICON_ANCHOR_CENTER)
                                )
                                .withFilter(Expression.not(Expression.has("point_count")))
                                .apply { minZoom = 8.0f }
                )
            }
        }
    }

    DisposableEffect(map) {
        onDispose {
            map?.getStyle { style ->
                runCatching {
                    style.removeLayer("shops-symbol")
                    style.removeLayer("shops-cluster-count")
                    style.removeLayer("shops-cluster")
                    style.removeSource("shops-source")
                }
            }
        }
    }
}

/**
 * Draws each category icon into a small white badge bitmap. MapLibre only accepts bitmaps for
 * symbol icons, so the Compose vector is rasterised once per category instead of shipping a second
 * set of PNGs.
 */
@Composable
private fun rememberShopIconBitmaps(size: Dp = 34.dp): Map<ShopCategory, Bitmap> {
    val density = LocalDensity.current
    val painters = ShopCategory.entries.map { it to rememberVectorPainter(it.icon) }.toMap()

    return remember(density, painters, size) {
        val pixels = with(density) { size.roundToPx() }
        val centre = pixels / 2f
        ShopCategory.entries.associateWith { category ->
            val bitmap = Bitmap.createBitmap(pixels, pixels, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap.asImageBitmap())
            CanvasDrawScope().draw(
                    density = density,
                    layoutDirection = LayoutDirection.Ltr,
                    canvas = canvas,
                    size = Size(pixels.toFloat(), pixels.toFloat())
            ) {
                drawCircle(
                        color = Color(0x30000000),
                        radius = centre - 1f,
                        center = Offset(centre, centre + 1.5f)
                )
                drawCircle(color = Color.White, radius = centre - 2f, center = Offset(centre, centre))
                drawCircle(
                        color = category.tint,
                        radius = centre - 2f,
                        center = Offset(centre, centre),
                        style = Stroke(width = pixels * 0.075f)
                )
                val iconSize = pixels * 0.56f
                translate(left = (pixels - iconSize) / 2f, top = (pixels - iconSize) / 2f) {
                    with(painters.getValue(category)) {
                        draw(
                                size = Size(iconSize, iconSize),
                                colorFilter = ColorFilter.tint(category.tint)
                        )
                    }
                }
            }
            bitmap
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
                                        .put("icon", shop.category.mapIconName())
                                        .put("color", shop.category.markerHex())
                        )
        )
    }
    return JSONObject().put("type", "FeatureCollection").put("features", features).toString()
}

internal fun ShopCategory.mapIconName(): String = "shop-${name.lowercase()}"

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
 * Category chips under the search bar. Nothing is selected by default; choosing a chip shows only
 * that category on the map, and tapping it again (or the Hide chip) clears the layer.
 */
@Composable
internal fun ShopFilterRow(
        shops: List<Shop>,
        selected: ShopCategory?,
        onSelect: (ShopCategory?) -> Unit
) {
    val counts = shops.groupingBy { it.category }.eachCount()
    val categories = counts.entries.sortedByDescending { it.value }.map { it.key }

    LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            Row(
                    modifier = Modifier.padding(end = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                        Icons.Default.Place,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                        "Places",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (selected != null) {
                    Spacer(Modifier.width(2.dp))
                    Box(
                            modifier = Modifier.size(28.dp).clickable { onSelect(null) },
                            contentAlignment = Alignment.Center
                    ) {
                        Icon(
                                Icons.Default.Close,
                                "Hide places",
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        items(categories) { category ->
            val isSelected = category == selected
            val tint = category.tint
            Surface(
                    modifier =
                            Modifier.clip(RoundedCornerShape(50))
                                    .clickable { onSelect(if (isSelected) null else category) },
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
                    Icon(
                            category.icon,
                            null,
                            Modifier.size(13.dp),
                            tint = if (isSelected) Color(0xFF231607) else tint
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                            text = category.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color =
                                    if (isSelected) Color(0xFF231607)
                                    else MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                            text = "${counts[category] ?: 0}",
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
