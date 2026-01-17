package com.noobdev.waffle.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobdev.waffle.api.fetchAreaName
import com.noobdev.waffle.api.fetchWeather
import com.noobdev.waffle.ui.components.StatusLevel
import com.noobdev.waffle.utils.PreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ============================================
// DATA MODELS
// ============================================

data class DisasterAlert(
        val id: String,
        val title: String,
        val location: String,
        val type: String, // earthquake, flood, wildfire, storm
        val status: StatusLevel,
        val timestamp: String,
        val latitude: Double,
        val longitude: Double,
        val details: Map<String, String> = emptyMap()
)

data class HomeUiState(
        val isLoading: Boolean = true,
        val locationName: String = "Loading...",
        val latitude: Double? = null,
        val longitude: Double? = null,
        val temperature: String = "--°C",
        val weatherCondition: String = "Loading...",
        val precipitation: String = "0mm",
        val activeAlerts: List<DisasterAlert> = emptyList(),
        val greeting: String = "Welcome",
        val error: String? = null
)

data class MapUiState(
        val isLoading: Boolean = false,
        val currentStyleIndex: Int = 0,
        val showFloodOverlay: Boolean = true,
        val selectedSector: String? = null,
        val routeCoordinates: List<Pair<Double, Double>> = emptyList()
)

data class AlertsUiState(
        val isLoading: Boolean = true,
        val alerts: List<DisasterAlert> = emptyList(),
        val filterType: String? = null, // null = all
        val error: String? = null
)

// ============================================
// HOME VIEW MODEL
// ============================================

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val openCageApiKey = "YOUR_OPENCAGE_API_KEY" // Replace with actual key

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadInitialData()
    }

    fun loadInitialData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Set greeting based on time
            val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
            val greeting =
                    when {
                        hour < 12 -> "Good Morning"
                        hour < 17 -> "Good Afternoon"
                        else -> "Good Evening"
                    }
            _uiState.value = _uiState.value.copy(greeting = greeting)

            // Load mock alerts for now
            val mockAlerts =
                    listOf(
                            DisasterAlert(
                                    id = "1",
                                    title = "Flash Flood Warning",
                                    location = "Valencia, Spain",
                                    type = "flood",
                                    status = StatusLevel.CRITICAL,
                                    timestamp = "2 hours ago",
                                    latitude = 39.4699,
                                    longitude = -0.3763
                            ),
                            DisasterAlert(
                                    id = "2",
                                    title = "Earthquake Detected",
                                    location = "Kobe Region, Japan",
                                    type = "earthquake",
                                    status = StatusLevel.WARNING,
                                    timestamp = "5 hours ago",
                                    latitude = 34.6901,
                                    longitude = 135.1956
                            )
                    )

            _uiState.value = _uiState.value.copy(activeAlerts = mockAlerts, isLoading = false)
        }
    }

    fun updateLocation(lat: Double, lng: Double) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(latitude = lat, longitude = lng)

            // Fetch location name
            try {
                val areaName = fetchAreaName(lat, lng, openCageApiKey)
                _uiState.value = _uiState.value.copy(locationName = areaName ?: "Unknown Location")
                preferencesManager.setLastLocationName(areaName ?: "Unknown")
            } catch (e: Exception) {
                // Keep previous location name
            }

            // Fetch weather
            try {
                val weather = fetchWeather(lat, lng)
                weather?.let {
                    val currentTemp = it.hourly.temperature.firstOrNull() ?: 0f
                    val currentPrecip = it.hourly.precipitation.firstOrNull() ?: 0f
                    _uiState.value =
                            _uiState.value.copy(
                                    temperature = "${currentTemp.toInt()}°C",
                                    precipitation = "${currentPrecip}mm",
                                    weatherCondition = if (currentPrecip > 0) "Rainy" else "Clear"
                            )
                }
            } catch (e: Exception) {
                // Keep previous weather data
            }
        }
    }

    fun refresh() {
        loadInitialData()
        _uiState.value.latitude?.let { lat ->
            _uiState.value.longitude?.let { lng -> updateLocation(lat, lng) }
        }
    }
}

// ============================================
// ALERTS VIEW MODEL
// ============================================

class AlertsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    init {
        loadAlerts()
    }

    fun loadAlerts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)

            // Mock data - in production would fetch from API/database
            val alerts =
                    listOf(
                            DisasterAlert(
                                    id = "1",
                                    title = "Severe Flash Flood Warning",
                                    location = "Valencia Region, Spain",
                                    type = "flood",
                                    status = StatusLevel.CRITICAL,
                                    timestamp = "2024-01-17 14:30",
                                    latitude = 39.4699,
                                    longitude = -0.3763,
                                    details =
                                            mapOf(
                                                    "Water Level" to "2.5m",
                                                    "Affected Area" to "450 km²",
                                                    "Duration" to "Ongoing"
                                            )
                            ),
                            DisasterAlert(
                                    id = "2",
                                    title = "Earthquake M5.2",
                                    location = "Kobe Region, Japan",
                                    type = "earthquake",
                                    status = StatusLevel.WARNING,
                                    timestamp = "2024-01-17 09:15",
                                    latitude = 34.6901,
                                    longitude = 135.1956,
                                    details =
                                            mapOf(
                                                    "Magnitude" to "5.2",
                                                    "Depth" to "12 km",
                                                    "Intensity" to "Moderate"
                                            )
                            ),
                            DisasterAlert(
                                    id = "3",
                                    title = "Wildfire Risk Elevated",
                                    location = "California, USA",
                                    type = "wildfire",
                                    status = StatusLevel.MONITORING,
                                    timestamp = "2024-01-17 08:00",
                                    latitude = 36.7783,
                                    longitude = -119.4179,
                                    details =
                                            mapOf(
                                                    "Risk Level" to "High",
                                                    "Wind Speed" to "45 km/h",
                                                    "Humidity" to "15%"
                                            )
                            ),
                            DisasterAlert(
                                    id = "4",
                                    title = "Tropical Storm Watch",
                                    location = "Manila Bay, Philippines",
                                    type = "storm",
                                    status = StatusLevel.WARNING,
                                    timestamp = "2024-01-16 22:00",
                                    latitude = 14.5995,
                                    longitude = 120.9842,
                                    details =
                                            mapOf(
                                                    "Wind Speed" to "95 km/h",
                                                    "Pressure" to "990 hPa",
                                                    "Movement" to "NW at 20 km/h"
                                            )
                            )
                    )

            _uiState.value = _uiState.value.copy(alerts = alerts, isLoading = false)
        }
    }

    fun setFilter(type: String?) {
        _uiState.value = _uiState.value.copy(filterType = type)
    }

    fun getFilteredAlerts(): List<DisasterAlert> {
        val filter = _uiState.value.filterType
        return if (filter == null) {
            _uiState.value.alerts
        } else {
            _uiState.value.alerts.filter { it.type == filter }
        }
    }

    fun refresh() {
        loadAlerts()
    }
}

// ============================================
// MAP VIEW MODEL
// ============================================

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
