package com.noobdev.waffle.api

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray

object OfflineGeocoder {
    private const val TAG = "OfflineGeocoder"
    private val places = mutableListOf<GeoSearchResult>()

    @Volatile
    var isLoaded = false
        private set

    suspend fun load(context: Context) {
        if (isLoaded) return

        withContext(Dispatchers.IO) {
            if (isLoaded) return@withContext

            try {
                Log.d(TAG, "Loading offline places into RAM...")
                val jsonString =
                        context.assets.open("offline_places.json").bufferedReader().use {
                            it.readText()
                        }
                val jsonArray = JSONArray(jsonString)
                val loadedPlaces = ArrayList<GeoSearchResult>(jsonArray.length())

                for (index in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(index)
                    loadedPlaces.add(
                            GeoSearchResult(
                                    name = item.getString("name"),
                                    address = item.optString("address", "Pakistan"),
                                    lat = item.getDouble("lat"),
                                    lng = item.getDouble("lng"),
                                    type = item.optString("type", "offline")
                            )
                    )
                }

                places.clear()
                places.addAll(loadedPlaces)
                isLoaded = true
                Log.d(TAG, "Loaded ${places.size} offline places")
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to load offline places", throwable)
            }
        }
    }

    fun search(query: String, limit: Int = 15): List<GeoSearchResult> {
        val trimmedQuery = query.trim()
        if (trimmedQuery.isBlank() || !isLoaded) return emptyList()

        return places.asSequence()
                .filter { place -> place.name.contains(trimmedQuery, ignoreCase = true) }
                .take(limit)
                .toList()
    }
}
