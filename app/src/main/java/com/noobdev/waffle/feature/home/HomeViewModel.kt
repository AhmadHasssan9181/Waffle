package com.noobdev.waffle.feature.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.noobdev.waffle.core.navigation.GeoGeometry
import com.noobdev.waffle.core.prefs.PreferencesManager
import com.noobdev.waffle.data.local.AppDatabase
import com.noobdev.waffle.data.model.DisasterAlert
import com.noobdev.waffle.data.model.StatusLevel
import com.noobdev.waffle.data.remote.fetchAreaName
import com.noobdev.waffle.data.remote.fetchWeather
import com.noobdev.waffle.data.remote.getQuickCitySuggestions
import com.noobdev.waffle.feature.alerts.data.EmergencySyncScheduler
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
        val locationName: String = "Locating…",
        val latitude: Double? = null,
        val longitude: Double? = null,
        val temperature: String = "--°",
        val weatherCondition: String = "—",
        val precipitation: String = "0 mm",
        val activeAlerts: List<DisasterAlert> = emptyList(),
        val greeting: String = "Welcome",
        val weatherLoaded: Boolean = false
) {
    /** Distance from the user to an alert, or null when either side has no coordinates. */
    fun distanceKm(alert: DisasterAlert): Double? {
        val lat = latitude ?: return null
        val lon = longitude ?: return null
        val alertLat = alert.latitude ?: return null
        val alertLon = alert.longitude ?: return null
        return GeoGeometry.metersBetweenLatLng(lat, lon, alertLat, alertLon) / 1000.0
    }

    /**
     * Alerts close enough to actually matter. The feed is global (USGS), so showing every
     * earthquake on the hero card would be alarming and useless.
     */
    val nearbyAlerts: List<DisasterAlert>
        get() {
            if (latitude == null || longitude == null) return emptyList()
            return activeAlerts
                    .mapNotNull { alert -> distanceKm(alert)?.let { km -> alert to km } }
                    .filter { (_, km) -> km <= NEARBY_RADIUS_KM }
                    .sortedBy { (_, km) -> km }
                    .map { (alert, _) -> alert }
        }

    /** Closest alert anywhere in the world — used for the honest "all clear nearby" subtitle. */
    val nearestAlert: Pair<DisasterAlert, Double>?
        get() =
                activeAlerts
                        .mapNotNull { alert -> distanceKm(alert)?.let { km -> alert to km } }
                        .minByOrNull { (_, km) -> km }

    val worstAlert: DisasterAlert?
        get() =
                nearbyAlerts.minByOrNull { alert ->
                    when (alert.status) {
                        StatusLevel.CRITICAL -> 0
                        StatusLevel.WARNING -> 1
                        StatusLevel.INFO -> 2
                        StatusLevel.SAFE -> 3
                        null -> 4
                    }
                }

    val criticalCount: Int
        get() = nearbyAlerts.count { it.status == StatusLevel.CRITICAL }

    val warningCount: Int
        get() = nearbyAlerts.count { it.status == StatusLevel.WARNING }

    companion object {
        /** Anything inside this radius is "near you" for the hero and the preview list. */
        const val NEARBY_RADIUS_KM = 300.0
    }
}

/**
 * Home is the "is my family safe right now" screen. Everything that can be cached is read from
 * Room first, so opening the app in airplane mode still shows the last known alerts and place.
 */
class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val preferencesManager = PreferencesManager(application)
    private val dao = AppDatabase.getDatabase(application).alertDao()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(greeting = greetingForHour()) }
        observeAlerts()
        EmergencySyncScheduler.syncNow(application)
    }

    private fun observeAlerts() {
        viewModelScope.launch {
            dao.getAllAlertsFlow().collect { entities ->
                _uiState.update {
                    it.copy(activeAlerts = entities.map { entity -> entity.toDomainModel() })
                }
            }
        }
    }

    /** Called once a fix is available. Safe to call repeatedly. */
    fun updateLocation(lat: Double, lng: Double) {
        val state = _uiState.value
        if (state.latitude == lat && state.longitude == lng) return
        _uiState.update { it.copy(latitude = lat, longitude = lng) }

        viewModelScope.launch {
            // Persist the fix so the alerts feed and the notification worker can measure distance.
            runCatching { preferencesManager.setUserLocation(lat, lng) }

            val name =
                    runCatching { fetchAreaName(lat, lng) }
                            .getOrNull()
                            ?.takeUnless { it.isBlank() || it == "Unknown Area" }
                            ?: nearestKnownPlace(lat, lng)
                            ?: String.format(Locale.US, "%.3f, %.3f", lat, lng)

            _uiState.update { it.copy(locationName = name) }
            runCatching { preferencesManager.setLastLocationName(name) }

            runCatching { fetchWeather(lat, lng) }
                    .getOrNull()
                    ?.let { weather ->
                        val temp = weather.hourly.temperature.firstOrNull() ?: 0f
                        val rain = weather.hourly.precipitation.firstOrNull() ?: 0f
                        _uiState.update {
                            it.copy(
                                    temperature = "${temp.toInt()}°",
                                    precipitation = "${rain.toInt()} mm",
                                    weatherLoaded = true,
                                    weatherCondition =
                                            when {
                                                rain > 5 -> "Heavy rain"
                                                rain > 1 -> "Light rain"
                                                temp > 38 -> "Very hot"
                                                temp > 30 -> "Warm"
                                                temp < 8 -> "Cold"
                                                else -> "Clear"
                                            }
                            )
                        }
                    }
        }
    }

    fun refresh() {
        EmergencySyncScheduler.syncNow(getApplication())
        val state = _uiState.value
        val lat = state.latitude
        val lon = state.longitude
        if (lat != null && lon != null) {
            // Force a weather re-read even if the coordinates did not change.
            _uiState.update { it.copy(latitude = null, longitude = null) }
            updateLocation(lat, lon)
        }
    }

    /**
     * When OpenCage is unavailable (no key, offline, rate-limited), name the place after the
     * closest city we ship in the offline suggestion list. Returns null when nothing is within
     * 60 km, in which case the caller shows coordinates instead of inventing a place.
     */
    private fun nearestKnownPlace(lat: Double, lng: Double): String? {
        val city =
                getQuickCitySuggestions().minByOrNull { candidate ->
                    GeoGeometry.metersBetweenLatLng(lat, lng, candidate.lat, candidate.lng)
                } ?: return null
        val metres = GeoGeometry.metersBetweenLatLng(lat, lng, city.lat, city.lng)
        return if (metres <= 60_000) "Near ${city.name}" else null
    }

    private fun greetingForHour(): String =
            when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
                in 0..4 -> "Good night"
                in 5..11 -> "Good morning"
                in 12..16 -> "Good afternoon"
                else -> "Good evening"
            }
}
