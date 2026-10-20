package com.noobdev.waffle.feature.map

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobdev.waffle.core.prefs.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class MapUiState(
        val isLoading: Boolean = false,
        val currentStyleIndex: Int = 0,
        val showFloodOverlay: Boolean = true,
        val selectedSector: String? = null,
        val routeCoordinates: List<Pair<Double, Double>> = emptyList()
)

class MapViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)

    private val _uiState = MutableStateFlow(MapUiState())
    val uiState: StateFlow<MapUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesManager.mapStyleIndex.collect { index ->
                _uiState.value = _uiState.value.copy(currentStyleIndex = index)
            }
        }
    }

    fun setMapStyle(index: Int) {
        viewModelScope.launch {
            preferencesManager.setMapStyleIndex(index)
            _uiState.value = _uiState.value.copy(currentStyleIndex = index)
        }
    }

    fun toggleFloodOverlay() {
        _uiState.value = _uiState.value.copy(showFloodOverlay = !_uiState.value.showFloodOverlay)
    }

    fun selectSector(sectorId: String?) {
        _uiState.value = _uiState.value.copy(selectedSector = sectorId)
    }

    fun setRoute(coordinates: List<Pair<Double, Double>>) {
        _uiState.value = _uiState.value.copy(routeCoordinates = coordinates)
    }

    fun clearRoute() {
        _uiState.value = _uiState.value.copy(routeCoordinates = emptyList())
    }
}
