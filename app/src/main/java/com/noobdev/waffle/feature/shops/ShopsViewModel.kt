package com.noobdev.waffle.feature.shops

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobdev.waffle.feature.shops.data.Shop
import com.noobdev.waffle.feature.shops.data.ShopCategory
import com.noobdev.waffle.feature.shops.data.ShopPhotoRepository
import com.noobdev.waffle.feature.shops.data.ShopRepository
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ShopsUiState(
        val loading: Boolean = false,
        val refreshing: Boolean = false,
        val shops: List<Shop> = emptyList(),
        val query: String = "",
        val category: ShopCategory? = null,
        val radiusMeters: Int = ShopRepository.DEFAULT_RADIUS_METERS,
        val centerLat: Double? = null,
        val centerLon: Double? = null,
        val locationLabel: String = "Near you",
        val photosLoading: Boolean = false,
        val error: String? = null
) {
    val visible: List<Shop>
        get() =
                shops.filter { shop ->
                    (category == null || shop.category == category) &&
                            (query.isBlank() ||
                                    shop.name.contains(query, ignoreCase = true) ||
                                    shop.brand?.contains(query, ignoreCase = true) == true ||
                                    shop.cuisine?.contains(query, ignoreCase = true) == true ||
                                    shop.address?.contains(query, ignoreCase = true) == true)
                }
}

enum class ShopRadius(val meters: Int, val label: String) {
    WALK(600, "600 m"),
    NEAR(1500, "1.5 km"),
    AREA(3500, "3.5 km")
}

/**
 * Nearby places the user can walk or drive to. Loads from cache instantly, refreshes in the
 * background, then resolves photos from Wikimedia Commons.
 */
class ShopsViewModel(application: Application) : AndroidViewModel(application) {

    private val shopRepository = ShopRepository(application)
    private val photoRepository = ShopPhotoRepository(application)

    private val _uiState = MutableStateFlow(ShopsUiState())
    val uiState: StateFlow<ShopsUiState> = _uiState.asStateFlow()

    /** Called by the screen once a fix is available. */
    fun load(lat: Double, lon: Double, label: String? = null) {
        val state = _uiState.value
        val moved =
                state.centerLat?.let { metres(it, state.centerLon ?: lon, lat, lon) > 250 } ?: true

        if (!moved && state.shops.isNotEmpty()) {
            if (label != null && label != state.locationLabel) {
                _uiState.update { it.copy(locationLabel = label) }
            }
            return
        }

        _uiState.update {
            it.copy(
                    centerLat = lat,
                    centerLon = lon,
                    locationLabel = label ?: it.locationLabel,
                    error = null
            )
        }
        fetch(forceRefresh = false)
    }

    fun refresh() {
        if (_uiState.value.centerLat == null) return
        fetch(forceRefresh = true)
    }

    fun setRadius(radius: ShopRadius) {
        val grew = radius.meters > _uiState.value.radiusMeters
        _uiState.update { it.copy(radiusMeters = radius.meters) }
        fetch(forceRefresh = grew)
    }

    fun setQuery(query: String) {
        _uiState.update { it.copy(query = query) }
    }

    fun setCategory(category: ShopCategory?) {
        _uiState.update { it.copy(category = category) }
    }

    private fun fetch(forceRefresh: Boolean) {
        val state = _uiState.value
        val lat = state.centerLat ?: return
        val lon = state.centerLon ?: return

        _uiState.update {
            it.copy(
                    loading = it.shops.isEmpty(),
                    refreshing = forceRefresh && it.shops.isNotEmpty(),
                    error = null
            )
        }

        viewModelScope.launch {
            val result =
                    shopRepository.nearby(
                            lat = lat,
                            lon = lon,
                            radiusMeters = state.radiusMeters,
                            forceRefresh = forceRefresh
                    )

            result
                    .onSuccess { shops ->
                        _uiState.update {
                            it.copy(shops = shops, loading = false, refreshing = false)
                        }
                        loadPhotos(lat, lon)
                    }
                    .onFailure { error ->
                        _uiState.update {
                            it.copy(
                                    loading = false,
                                    refreshing = false,
                                    error =
                                            if (it.shops.isEmpty())
                                                    "Couldn't reach OpenStreetMap. Check your connection and try again."
                                            else null
                            )
                        }
                    }
        }
    }

    private fun loadPhotos(lat: Double, lon: Double) {
        viewModelScope.launch {
            _uiState.update { it.copy(photosLoading = true) }
            val current = _uiState.value.shops
            val withPhotos = photoRepository.attachPhotos(current, lat, lon)
            // Ignore the result if the user moved on to another area meanwhile.
            if (_uiState.value.centerLat == lat && _uiState.value.centerLon == lon) {
                _uiState.update { it.copy(shops = withPhotos, photosLoading = false) }
            } else {
                _uiState.update { it.copy(photosLoading = false) }
            }
        }
    }

    private fun metres(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
                sin(dLat / 2).pow(2) +
                        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6_371_000.0 * asin(min(1.0, sqrt(a)))
    }
}
