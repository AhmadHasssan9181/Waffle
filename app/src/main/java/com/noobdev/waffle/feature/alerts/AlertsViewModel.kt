package com.noobdev.waffle.feature.alerts

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobdev.waffle.core.navigation.GeoGeometry
import com.noobdev.waffle.core.prefs.PreferencesManager
import com.noobdev.waffle.data.local.AppDatabase
import com.noobdev.waffle.data.model.DisasterAlert
import com.noobdev.waffle.data.model.StatusLevel
import com.noobdev.waffle.feature.alerts.data.EmergencySyncScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class AlertsUiState(
        val isLoading: Boolean = true,
        val isPaginating: Boolean = false,
        val hasMore: Boolean = false,
        val alerts: List<DisasterAlert> = emptyList(),
        val filterType: String? = null, // null = all
        val sortOrder: AlertSortOrder = AlertSortOrder.DATE_DESC,
        val visibleLimit: Int = PAGE_SIZE,
        val error: String? = null,
        val userLat: Double? = null,
        val userLng: Double? = null
) {
    /** Distance from the last known fix, or null when either side lacks coordinates. */
    fun distanceKm(alert: DisasterAlert): Double? {
        val lat = userLat ?: return null
        val lon = userLng ?: return null
        val alertLat = alert.latitude ?: return null
        val alertLon = alert.longitude ?: return null
        return GeoGeometry.metersBetweenLatLng(lat, lon, alertLat, alertLon) / 1000.0
    }

    companion object {
        const val PAGE_SIZE = 50
    }
}

enum class AlertSortOrder(val displayName: String) {
    DATE_DESC("Newest first"),
    DATE_ASC("Oldest first"),
    SEVERITY_DESC("Highest severity"),
    DISTANCE("Nearest first")
}

/**
 * Alerts are offline-first: the list is driven by Room, which is filled by both the Firebase feed
 * and the public USGS/GDACS feeds. A cold start with no signal still shows the last known alerts.
 */
class AlertsViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(AlertsUiState())
    val uiState: StateFlow<AlertsUiState> = _uiState.asStateFlow()

    private val dao = AppDatabase.getDatabase(application).alertDao()
    private val preferencesManager = PreferencesManager(application)

    init {
        viewModelScope.launch {
            preferencesManager.userLocation.collect { (lat, lng) ->
                _uiState.value = _uiState.value.copy(userLat = lat, userLng = lng)
            }
        }
        viewModelScope.launch {
            dao.getAllAlertsFlow().collect { entities ->
                _uiState.value =
                        _uiState.value.copy(
                                alerts = entities.map { it.toDomainModel() },
                                isLoading = false,
                                isPaginating = false,
                                hasMore = entities.size > _uiState.value.visibleLimit
                        )
            }
        }
        EmergencySyncScheduler.syncNow(application)
    }

    fun loadPage(isRefresh: Boolean = false) {
        if (!isRefresh) return
        _uiState.value = _uiState.value.copy(isLoading = _uiState.value.alerts.isEmpty())
        EmergencySyncScheduler.syncNow(getApplication())
    }

    fun setFilter(type: String?) {
        _uiState.value =
                _uiState.value.copy(
                        filterType = type,
                        visibleLimit = AlertsUiState.PAGE_SIZE,
                        hasMore = _uiState.value.alerts.size > AlertsUiState.PAGE_SIZE
                )
    }

    fun setSortOrder(order: AlertSortOrder) {
        _uiState.value =
                _uiState.value.copy(
                        sortOrder = order,
                        visibleLimit = AlertsUiState.PAGE_SIZE,
                        hasMore = _uiState.value.alerts.size > AlertsUiState.PAGE_SIZE
                )
    }

    fun loadMore() {
        val next = _uiState.value.visibleLimit + AlertsUiState.PAGE_SIZE
        _uiState.value = _uiState.value.copy(visibleLimit = next, hasMore = false)
    }

    fun hasMoreFilteredAlerts(): Boolean {
        val filter = _uiState.value.filterType
        val total =
                if (filter == null) _uiState.value.alerts.size
                else _uiState.value.alerts.count { it.type == filter }
        return total > _uiState.value.visibleLimit
    }

    fun getFilteredAlerts(): List<DisasterAlert> {
        val filter = _uiState.value.filterType
        val filtered =
                if (filter == null) _uiState.value.alerts
                else _uiState.value.alerts.filter { it.type == filter }

        val sorted = when (_uiState.value.sortOrder) {
            AlertSortOrder.DATE_DESC -> filtered.sortedByDescending { it.timestamp ?: "" }
            AlertSortOrder.DATE_ASC -> filtered.sortedBy { it.timestamp ?: "" }
            AlertSortOrder.SEVERITY_DESC ->
                    filtered.sortedBy { alert ->
                        when (alert.status) {
                            StatusLevel.CRITICAL -> 1
                            StatusLevel.WARNING -> 2
                            StatusLevel.INFO -> 3
                            StatusLevel.SAFE -> 4
                            null -> 5
                        }
                    }
            AlertSortOrder.DISTANCE ->
                    filtered.sortedBy { alert ->
                        _uiState.value.distanceKm(alert) ?: Double.MAX_VALUE
                    }
        }
        return sorted.take(_uiState.value.visibleLimit)
    }

    fun refresh() {
        loadPage(isRefresh = true)
    }
}
