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
import android.util.Log
import com.google.gson.annotations.SerializedName
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestoreSettings
import com.google.firebase.firestore.DocumentChange
import com.noobdev.waffle.notifications.NotificationHelper

// ============================================
// DATA MODELS
// ============================================

data class DisasterAlert(
        val id: String? = null,
        val title: String? = null,
        @SerializedName("location_name")
        @PropertyName("location_name")
        var locationName: String? = null,
        val type: String? = null, // earthquake, flood, wildfire, storm
        val status: StatusLevel? = null,
        val timestamp: String? = null,
        val latitude: Double? = null,
        val longitude: Double? = null,
        val details: Map<String, Any>? = emptyMap()
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
        val isPaginating: Boolean = false,
        val hasMore: Boolean = true,
        val alerts: List<DisasterAlert> = emptyList(),
        val filterType: String? = null, // null = all
        val sortOrder: AlertSortOrder = AlertSortOrder.DATE_DESC,
        val error: String? = null
)

enum class AlertSortOrder(val displayName: String) {
    DATE_DESC("Newest First"),
    DATE_ASC("Oldest First"),
    SEVERITY_DESC("Highest Severity")
}

// ============================================
// HOME VIEW MODEL
// ============================================

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val openCageApiKey = "YOUR_OPENCAGE_API_KEY" // Replace with actual key
    private val notificationHelper = NotificationHelper(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()
    private var alertsListener: ListenerRegistration? = null
    private var isFirstLoad = true

    init {
        loadInitialData()
        listenToAlerts()
    }

    private fun listenToAlerts() {
        alertsListener = db.collection("disaster_alerts")
            .addSnapshotListener { snapshots, e ->
                if (e != null) {
                    Log.w("HomeViewModel", "Listen failed.", e)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = "Failed to fetch alerts: ${e.message}"
                    )
                    return@addSnapshotListener
                }

                if (snapshots != null) {
                    val alerts = ArrayList<DisasterAlert>()
                    for (doc in snapshots.documents) {
                        try {
                            val alert = doc.toObject(DisasterAlert::class.java)
                            if (alert != null) alerts.add(alert)
                        } catch (ex: Exception) {
                            Log.e("HomeViewModel", "Error parsing doc: ${doc.id}", ex)
                        }
                    }

                    if (!isFirstLoad) {
                        // Notifications are now handled centrally in AlertRepository via WaffleApplication
                    }

                    isFirstLoad = false

                    _uiState.value = _uiState.value.copy(
                        activeAlerts = alerts,
                        isLoading = false
                    )
                    Log.d("HomeViewModel", "Loaded ${alerts.size} alerts from Firestore.")
                }
            }
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

    override fun onCleared() {
        super.onCleared()
        alertsListener?.remove()
    }
}

// ============================================
// ALERTS VIEW MODEL
// ============================================

class AlertsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    private val db = FirebaseFirestore.getInstance()

    // We will use standard pagination fetching over a real-time listener for infinite scroll
    private var lastVisibleDoc: DocumentSnapshot? = null
    private val pageSize: Long = 10

    init {
        loadPage(isRefresh = true)
    }

    fun loadPage(isRefresh: Boolean = false) {
        if (isRefresh) {
            _uiState.value = _uiState.value.copy(isLoading = true, hasMore = true)
            lastVisibleDoc = null
        } else {
            if (!_uiState.value.hasMore || _uiState.value.isPaginating) return
            _uiState.value = _uiState.value.copy(isPaginating = true)
        }

        var query = db.collection("disaster_alerts")
            .orderBy("timestamp", Query.Direction.DESCENDING) // Sort by date first to avoid missing composite index
            .limit(pageSize)

        lastVisibleDoc?.let {
            query = query.startAfter(it)
        }

        query.get()
            .addOnSuccessListener { snapshots ->
                val newAlerts = ArrayList<DisasterAlert>()
                for (doc in snapshots) {
                    try {
                        val alert = doc.toObject(DisasterAlert::class.java)
                        newAlerts.add(alert)
                    } catch (ex: Exception) {
                        Log.e("AlertsViewModel", "Error parsing doc: ${doc.id}", ex)
                    }
                }

                if (snapshots.documents.isNotEmpty()) {
                    lastVisibleDoc = snapshots.documents[snapshots.size() - 1]
                }

                val hasMore = snapshots.size() >= pageSize

                if (isRefresh) {
                    _uiState.value = _uiState.value.copy(
                        alerts = newAlerts,
                        isLoading = false,
                        isPaginating = false,
                        hasMore = hasMore
                    )
                } else {
                    val currentAlerts = _uiState.value.alerts.toMutableList()
                    currentAlerts.addAll(newAlerts)
                    _uiState.value = _uiState.value.copy(
                        alerts = currentAlerts,
                        isPaginating = false,
                        hasMore = hasMore
                    )
                }
            }
            .addOnFailureListener { e ->
                Log.w("AlertsViewModel", "Fetch failed.", e)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isPaginating = false,
                    error = "Failed to fetch alerts: ${e.message}"
                )
            }
    }

    fun setFilter(type: String?) {
        _uiState.value = _uiState.value.copy(filterType = type)
    }

    fun setSortOrder(order: AlertSortOrder) {
        _uiState.value = _uiState.value.copy(sortOrder = order)
    }

    fun getFilteredAlerts(): List<DisasterAlert> {
        val filter = _uiState.value.filterType

        // Filter
        val filtered = if (filter == null) {
            _uiState.value.alerts
        } else {
            _uiState.value.alerts.filter { it.type == filter }
        }

        // Sort locally
        return when (_uiState.value.sortOrder) {
            AlertSortOrder.DATE_DESC -> filtered.sortedByDescending { it.timestamp ?: "" }
            AlertSortOrder.DATE_ASC -> filtered.sortedBy { it.timestamp ?: "" }
            AlertSortOrder.SEVERITY_DESC -> {
                // Critical (highest priority) -> Safe (lowest)
                filtered.sortedBy { alert ->
                    when (alert.status) {
                        StatusLevel.CRITICAL -> 1
                        StatusLevel.WARNING -> 2
                        StatusLevel.INFO -> 3
                        StatusLevel.SAFE -> 4
                        null -> 5
                    }
                }
            }
        }
    }

    fun refresh() {
        loadPage(isRefresh = true)
    }

    override fun onCleared() {
        super.onCleared()
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
