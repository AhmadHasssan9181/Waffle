package com.noobdev.waffle.core.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.noobdev.waffle.design.theme.AppTheme
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

// DataStore instance
private val Context.dataStore: DataStore<Preferences> by
        preferencesDataStore(name = "waffle_settings")

class PreferencesManager(private val context: Context) {

    companion object {
        // Preference Keys
        private val THEME_KEY = stringPreferencesKey("app_theme")
        private val MAP_STYLE_INDEX_KEY = intPreferencesKey("map_style_index")
        private val SPEED_UNIT_KEY = stringPreferencesKey("speed_unit")
        private val VEHICLE_ICON_KEY = stringPreferencesKey("vehicle_icon")
        private val NAV_VOICE_ENABLED_KEY = booleanPreferencesKey("nav_voice_enabled")
        private val NOTIFICATIONS_ENABLED_KEY = booleanPreferencesKey("notifications_enabled")
        private val FLOOD_ALERTS_KEY = booleanPreferencesKey("flood_alerts")
        private val EARTHQUAKE_ALERTS_KEY = booleanPreferencesKey("earthquake_alerts")
        private val WILDFIRE_ALERTS_KEY = booleanPreferencesKey("wildfire_alerts")
        private val USER_LAT_KEY = doublePreferencesKey("user_lat")
        private val USER_LNG_KEY = doublePreferencesKey("user_lng")
        private val LAST_LOCATION_NAME_KEY = stringPreferencesKey("last_location_name")
    }

    // ============================================
    // THEME PREFERENCES
    // ============================================

    val appTheme: Flow<AppTheme> =
            context.dataStore.data.map { preferences ->
                val themeName = preferences[THEME_KEY] ?: AppTheme.SYSTEM.name
                // Older builds stored theme names that no longer exist (OCEAN, SUNSET, ...).
                // Unknown values fall back to SYSTEM instead of crashing.
                runCatching { AppTheme.valueOf(themeName) }.getOrDefault(AppTheme.SYSTEM)
            }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { preferences -> preferences[THEME_KEY] = theme.name }
    }

    // ============================================
    // MAP STYLE PREFERENCES
    // ============================================

    val mapStyleIndex: Flow<Int> =
            context.dataStore.data.map { preferences -> preferences[MAP_STYLE_INDEX_KEY] ?: -1 }

    suspend fun setMapStyleIndex(index: Int) {
        context.dataStore.edit { preferences -> preferences[MAP_STYLE_INDEX_KEY] = index }
    }

    // ============================================
    // SPEED UNIT
    // ============================================

    val speedUnit: Flow<SpeedUnit> =
            context.dataStore.data.map { preferences ->
                val name = preferences[SPEED_UNIT_KEY] ?: SpeedUnit.KMH.name
                runCatching { SpeedUnit.valueOf(name) }.getOrDefault(SpeedUnit.KMH)
            }

    suspend fun setSpeedUnit(unit: SpeedUnit) {
        context.dataStore.edit { preferences -> preferences[SPEED_UNIT_KEY] = unit.name }
    }

    // ============================================
    // VEHICLE ICON
    // ============================================

    val vehicleIcon: Flow<String> =
            context.dataStore.data.map { preferences ->
                preferences[VEHICLE_ICON_KEY] ?: "SEDAN"
            }

    suspend fun setVehicleIcon(key: String) {
        context.dataStore.edit { preferences -> preferences[VEHICLE_ICON_KEY] = key }
    }

    // ============================================
    // NAVIGATION VOICE
    // ============================================

    val voiceEnabled: Flow<Boolean> =
            context.dataStore.data.map { preferences ->
                preferences[NAV_VOICE_ENABLED_KEY] ?: true
            }

    suspend fun setVoiceEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[NAV_VOICE_ENABLED_KEY] = enabled }
    }

    // ============================================
    // NOTIFICATION PREFERENCES
    // ============================================

    val notificationsEnabled: Flow<Boolean> =
            context.dataStore.data.map { preferences ->
                preferences[NOTIFICATIONS_ENABLED_KEY] ?: true
            }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[NOTIFICATIONS_ENABLED_KEY] = enabled }
    }

    val floodAlertsEnabled: Flow<Boolean> =
            context.dataStore.data.map { preferences -> preferences[FLOOD_ALERTS_KEY] ?: true }

    suspend fun setFloodAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[FLOOD_ALERTS_KEY] = enabled }
    }

    val earthquakeAlertsEnabled: Flow<Boolean> =
            context.dataStore.data.map { preferences -> preferences[EARTHQUAKE_ALERTS_KEY] ?: true }

    suspend fun setEarthquakeAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[EARTHQUAKE_ALERTS_KEY] = enabled }
    }

    val wildfireAlertsEnabled: Flow<Boolean> =
            context.dataStore.data.map { preferences -> preferences[WILDFIRE_ALERTS_KEY] ?: true }

    suspend fun setWildfireAlertsEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences -> preferences[WILDFIRE_ALERTS_KEY] = enabled }
    }

    // ============================================
    // LOCATION PREFERENCES
    // ============================================

    val userLocation: Flow<Pair<Double?, Double?>> =
            context.dataStore.data.map { preferences ->
                val lat = preferences[USER_LAT_KEY]
                val lng = preferences[USER_LNG_KEY]
                Pair(lat, lng)
            }

    suspend fun setUserLocation(lat: Double, lng: Double) {
        context.dataStore.edit { preferences ->
            preferences[USER_LAT_KEY] = lat
            preferences[USER_LNG_KEY] = lng
        }
    }

    val lastLocationName: Flow<String> =
            context.dataStore.data.map { preferences ->
                preferences[LAST_LOCATION_NAME_KEY] ?: "Unknown Location"
            }

    suspend fun setLastLocationName(name: String) {
        context.dataStore.edit { preferences -> preferences[LAST_LOCATION_NAME_KEY] = name }
    }
}
