package com.noobdev.waffle.feature.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobdev.waffle.core.navigation.GeoGeometry
import com.noobdev.waffle.core.navigation.GeoPoint
import com.noobdev.waffle.feature.shops.data.Shop
import com.noobdev.waffle.feature.shops.data.ShopPhotoRepository
import com.noobdev.waffle.feature.shops.data.ShopRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Owns the shops that are drawn on the map. The Nearby tab and the map share the same repository,
 * so a list fetched in either place is instantly available in the other (same Room cache).
 */
class MapShopsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ShopRepository(application)
    private val photos = ShopPhotoRepository(application)

    private val _shops = MutableStateFlow<List<Shop>>(emptyList())
    val shops: StateFlow<List<Shop>> = _shops.asStateFlow()

    private val _selected = MutableStateFlow<Shop?>(null)
    val selected: StateFlow<Shop?> = _selected.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private var lastLat = Double.NaN
    private var lastLon = Double.NaN
    /** Latest shop request; an older request may finish but must not replace its results. */
    private var loadGeneration = 0

    /**
     * Loads shops around a fix. Re-fetches only when the camera/user has moved more than 800 m, so
     * panning around the neighbourhood does not hammer Overpass.
     */
    fun loadAround(lat: Double, lon: Double, radiusMeters: Int = DEFAULT_RADIUS_METERS) {
        val moved =
                lastLat.isNaN() ||
                        GeoGeometry.metersBetweenLatLng(lastLat, lastLon, lat, lon) > 800.0
        if (!moved && _shops.value.isNotEmpty()) return

        val generation = ++loadGeneration
        viewModelScope.launch {
            _isLoading.value = true
            val result = runCatching { repository.nearby(lat, lon, radiusMeters) }
            if (generation != loadGeneration) return@launch
            result.onSuccess { shops ->
                shops.getOrNull()?.let {
                    _shops.value = it
                    lastLat = lat
                    lastLon = lon
                }
            }
            _isLoading.value = false
        }
    }

    /**
     * Loads shops around a few points along a planned route, merging them into one list. This is
     * what makes "fuel on the way" work for a 30 km trip instead of only the current block.
     */
    fun loadAlongRoute(points: List<GeoPoint>) {
        if (points.isEmpty()) return
        val generation = ++loadGeneration
        viewModelScope.launch {
            _isLoading.value = true
            val merged = linkedMapOf<String, Shop>()
            points.distinctBy { "${it.latitude},${it.longitude}" }.take(3).forEach { point ->
                runCatching { repository.nearby(point.latitude, point.longitude, 2_500) }
                        .getOrNull()
                        ?.getOrNull()
                        ?.forEach { merged[it.id] = it }
            }
            if (generation != loadGeneration) return@launch
            if (merged.isNotEmpty()) {
                _shops.value = merged.values.toList()
                // Let the next local load refresh even if the user is near the route start.
                lastLat = Double.NaN
                lastLon = Double.NaN
            }
            _isLoading.value = false
        }
    }

    fun select(shop: Shop) {
        _selected.value = shop
        if (!shop.photoUrl.isNullOrBlank()) return

        // Photos are only needed for the open sheet, so fetch one lazily instead of for 150 pins.
        viewModelScope.launch {
            val enriched =
                    runCatching { photos.attachPhotos(listOf(shop), shop.lat, shop.lon) }
                            .getOrNull()
                            ?.firstOrNull()
                            ?: return@launch
            if (_selected.value?.id == enriched.id) _selected.value = enriched
            _shops.value = _shops.value.map { if (it.id == enriched.id) enriched else it }
        }
    }

    fun clearSelection() {
        _selected.value = null
    }

    companion object {
        /** Wide enough to fill a city block view without pulling half a city. */
        const val DEFAULT_RADIUS_METERS = 3000
    }
}
