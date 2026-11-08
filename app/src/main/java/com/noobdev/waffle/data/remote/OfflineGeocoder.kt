package com.noobdev.waffle.data.remote

import android.content.Context
import android.util.Log
import com.noobdev.waffle.BuildConfig
import java.text.Normalizer
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray

/**
 * Offline search over the bundled POI list and the named-road index.
 *
 * The old implementation only looked for the entire query inside `name`, so "Mall Road
 * Rawalpindi" never matched a road called "Mall Road". The index now tokenises the query and
 * scores name, address, road refs and alternate names, while keeping the first result fast.
 */
object OfflineGeocoder {
    private const val TAG = "OfflineGeocoder"
    private const val ROADS_ASSET = "offline_roads.json"

    private data class Entry(val result: GeoSearchResult, val name: String, val blob: String)

    private val entries = mutableListOf<Entry>()
    private var appContext: Context? = null
    private val loadMutex = Mutex()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    val isLoaded: Boolean
        get() = _loaded.value

    suspend fun load(context: Context) {
        appContext = context.applicationContext
        if (isLoaded) return
        loadMutex.withLock {
            if (isLoaded) return
            withContext(Dispatchers.IO) {
                if (isLoaded) return@withContext
                try {
                Log.d(TAG, "Loading offline roads; places come from the Overture FTS index...")
                val loaded = ArrayList<Entry>(4_000)

                runCatching {
                            context.assets.open(ROADS_ASSET).bufferedReader().use {
                                it.readText()
                            }
                        }
                        .getOrNull()
                        ?.let { text ->
                            val array = JSONArray(text)
                            for (index in 0 until array.length()) {
                                val item = array.optJSONObject(index) ?: continue
                                val name = item.optString("name").trim()
                                if (name.isEmpty()) continue
                                val alt = item.optString("alt", "")
                                val address = item.optString("address", "Road")
                                val result =
                                        GeoSearchResult(
                                                name = name,
                                                address = address,
                                                lat = item.optDouble("lat"),
                                                lng =
                                                        if (item.has("lng")) item.optDouble("lng")
                                                        else item.optDouble("lon"),
                                                type = "road",
                                                searchText = alt
                                        )
                                loaded.add(Entry(result, normalize(name), blob(result)))
                            }
                        }

                entries.clear()
                entries.addAll(loaded)
                _loaded.value = true
                Log.d(TAG, "Loaded ${entries.size} offline search entries")
            } catch (throwable: Throwable) {
                Log.e(TAG, "Failed to load offline search data", throwable)
            }
            }
        }
    }

    fun search(
            query: String,
            limit: Int = 15,
            latitude: Double? = null,
            longitude: Double? = null
    ): List<GeoSearchResult> {
        val trimmed = query.trim()
        if (trimmed.isBlank() || !isLoaded) return emptyList()

        val queryNormalized = normalize(trimmed)
        val tokens = queryNormalized.split(' ').filter { it.length >= 2 }
        if (tokens.isEmpty()) return emptyList()
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "search='$queryNormalized' tokens=$tokens entries=${entries.size}")
        }
        val asksForRoad =
                tokens.any {
                    it == "road" || it == "street" || it == "rd" || it == "st" || it == "avenue"
                }

        val scored = ArrayList<Pair<Int, GeoSearchResult>>(64)
        for (entry in entries) {
            val name = entry.name
            var score =
                    when {
                        name == queryNormalized -> 1200
                        name.startsWith(queryNormalized) -> 1000
                        name.contains(queryNormalized) -> 820
                        else -> {
                            val matched = tokens.count { entry.blob.contains(it) }
                            if (matched == 0) continue
                            180 + matched * 65
                        }
                    }

            if (entry.result.type == "road") score += if (asksForRoad) 120 else 40
            if (name.contains(tokens.first())) score += 60
            score -= (name.length / 24).coerceAtMost(40)
            scored.add(score to entry.result)
        }

        val result =
                scored
                        .sortedWith(
                                compareByDescending<Pair<Int, GeoSearchResult>> { it.first }
                                        .thenBy { it.second.name.length }
                                        .thenBy { it.second.name }
                        )
                        .distinctBy {
                            "${it.second.name.lowercase()}|${it.second.lat}|${it.second.lng}"
                        }
                        .take(limit)
                        .map { it.second }
        val fts =
                appContext?.let {
                    PlacesSearchDb.search(it, trimmed, limit, latitude, longitude)
                } ?: emptyList()
        val merged =
                (fts + result)
                        .distinctBy {
                            "${it.name.lowercase()}|${it.lat}|${it.lng}"
                        }
                        .take(limit)
        if (BuildConfig.DEBUG) {
            Log.d(
                    TAG,
                    "search='$queryNormalized' scored=${scored.size} fts=${fts.size} returned=${merged.size}"
            )
        }
        return merged
    }

    /**
     * Offline nearby feed for the map and Nearby tab. The bundled places database contains
     * hundreds of thousands of named OSM features; this exposes only useful service categories
     * and filters them by great-circle distance.
     */
    fun nearbyAmenities(
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
            limit: Int = 160
    ): List<GeoSearchResult> {
        val context = appContext ?: return emptyList()
        return PlacesSearchDb.nearby(context, latitude, longitude, radiusMeters, limit)
    }

    private fun blob(result: GeoSearchResult): String =
            buildString {
                        append(result.name)
                        append(' ')
                        append(result.address)
                        append(' ')
                        append(result.type)
                        if (result.searchText.isNotBlank()) {
                            append(' ')
                            append(result.searchText)
                        }
                    }
                    .lowercase(Locale.ROOT)

    private fun normalize(value: String): String {
        val decomposed = Normalizer.normalize(value, Normalizer.Form.NFD)
        return decomposed
                .replace(Regex("\\p{M}+"), "")
                .lowercase(Locale.ROOT)
                .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
                .trim()
    }
}
