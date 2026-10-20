package com.noobdev.waffle.feature.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobdev.waffle.core.navigation.GeoGeometry
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
    private var loading = false

    /**
     * Loads shops around a fix. Re-fetches only when the camera/user has moved more than 800 m, so
     * panning around the neighbourhood does not hammer Overpass.
     */
    fun loadAround(lat: Double, lon: Double, radiusMeters: Int = DEFAULT_RADIUS_METERS) {
        if (loading) return
        val moved =
                lastLat.isNaN() ||
                        GeoGeometry.metersBetweenLatLng(lastLat, lastLon, lat, lon) > 800.0
        if (!moved && _shops.value.isNotEmpty()) return

        loading = true
        viewModelScope.launch {
            _isLoading.value = true
            runCatching { repository.nearby(lat, lon, radiusMeters) }
                    .onSuccess { result ->
                        result.getOrNull()?.let { shops ->
                            _shops.value = shops
                            lastLat = lat
                            lastLon = lon
                        }
                    }
            _isLoading.value = false
            loading = false
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
