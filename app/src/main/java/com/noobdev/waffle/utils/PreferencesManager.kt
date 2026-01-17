package com.noobdev.waffle.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.noobdev.waffle.ui.theme.AppTheme
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
                val themeName = preferences[THEME_KEY] ?: AppTheme.DEFAULT.name
                try {
                    AppTheme.valueOf(themeName)
                } catch (e: Exception) {
                    AppTheme.DEFAULT
                }
            }

    suspend fun setAppTheme(theme: AppTheme) {
        context.dataStore.edit { preferences -> preferences[THEME_KEY] = theme.name }
    }

    // ============================================
    // MAP STYLE PREFERENCES
    // ============================================

    val mapStyleIndex: Flow<Int> =
            context.dataStore.data.map { preferences -> preferences[MAP_STYLE_INDEX_KEY] ?: 0 }

    suspend fun setMapStyleIndex(index: Int) {
        context.dataStore.edit { preferences -> preferences[MAP_STYLE_INDEX_KEY] = index }
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
