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
    private const val PLACES_ASSET = "offline_places.tsv"
    private const val ROADS_ASSET = "offline_roads.json"

    private data class Entry(val result: GeoSearchResult, val name: String, val blob: String)

    private val entries = mutableListOf<Entry>()
    private val loadMutex = Mutex()
    private val _loaded = MutableStateFlow(false)
    val loaded: StateFlow<Boolean> = _loaded.asStateFlow()
    val isLoaded: Boolean
        get() = _loaded.value

    suspend fun load(context: Context) {
        if (isLoaded) return
        loadMutex.withLock {
            if (isLoaded) return
            withContext(Dispatchers.IO) {
                if (isLoaded) return@withContext
                try {
                Log.d(TAG, "Loading offline places and roads...")
                val loaded = ArrayList<Entry>(140_000)

                runCatching {
                            context.assets.open(PLACES_ASSET).bufferedReader().useLines { lines ->
                                lines.forEach { line ->
                                    if (line.isBlank()) return@forEach
                                    val parts = line.split('\t', limit = 5)
                                    if (parts.size < 4) return@forEach
                                    val name = parts[0].trim()
                                    if (name.isEmpty()) return@forEach
                                    val lat = parts[2].toDoubleOrNull() ?: return@forEach
                                    val lng = parts[3].toDoubleOrNull() ?: return@forEach
                                    val result =
                                            GeoSearchResult(
                                                    name = name,
                                                    address = parts[1],
                                                    lat = lat,
                                                    lng = lng,
                                                    type =
                                                            parts.getOrNull(4)?.ifBlank { null }
                                                                    ?: "offline"
                                            )
                                    loaded.add(
                                            Entry(
                                                    result,
                                                    name.lowercase(Locale.ROOT),
                                                    blob(result)
                                            )
                                    )
                                }
                            }
                        }

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

    fun search(query: String, limit: Int = 15): List<GeoSearchResult> {
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
        if (BuildConfig.DEBUG) {
            Log.d(TAG, "search='$queryNormalized' scored=${scored.size} returned=${result.size}")
        }
        return result
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
        if (!isLoaded) return emptyList()
        val radius = radiusMeters.toDouble()
        return entries
                .asSequence()
                .filter { it.result.type.lowercase(Locale.ROOT) in AMENITY_TYPES }
                .mapNotNull { entry ->
                    val result = entry.result
                    val distance =
                            haversineMeters(latitude, longitude, result.lat, result.lng)
                    if (distance <= radius) distance to result else null
                }
                .sortedBy { it.first }
                .take(limit)
                .map { it.second }
                .toList()
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
                kotlin.math.sin(dLat / 2).let { it * it } +
                        kotlin.math.cos(Math.toRadians(lat1)) *
                                kotlin.math.cos(Math.toRadians(lat2)) *
                                kotlin.math.sin(dLon / 2).let { it * it }
        return earth * 2 * kotlin.math.asin(kotlin.math.sqrt(a).coerceIn(0.0, 1.0))
    }

    private val AMENITY_TYPES =
            setOf(
                    "hospital", "clinic", "pharmacy", "doctors", "dentist",
                    "fuel", "charging_station", "car_repair", "car_wash",
                    "bank", "atm", "bureau_de_change",
                    "restaurant", "cafe", "fast_food", "food_court", "bakery",
                    "police", "fire_station", "post_office", "library",
                    "marketplace", "supermarket", "convenience", "mall",
                    "bus_station", "parking",
                    "school", "college", "university",
                    "place_of_worship", "community_centre",
                    "hotel", "guest_house", "hostel",
                    "hairdresser", "beauty", "laundry", "tailor",
                    "shop"
            )

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
