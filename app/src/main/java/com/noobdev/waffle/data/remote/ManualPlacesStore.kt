package com.noobdev.waffle.data.remote

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Locale

/**
 * Small user-owned layer for places that are missing from Overture/OSM. It lives in app-private
 * storage and is merged into search/nearby immediately; it is never overwritten by a map pack
 * refresh.
 */
object ManualPlacesStore {
    private const val TAG = "ManualPlacesStore"
    private val gson = Gson()
    private val type = object : TypeToken<List<ManualPlace>>() {}.type

    data class ManualPlace(
            val name: String,
            val address: String,
            val lat: Double,
            val lng: Double,
            val type: String = "place"
    )

    fun add(context: Context, place: ManualPlace): Boolean =
            runCatching {
                        val all = load(context).toMutableList()
                        all.removeAll {
                            it.name.equals(place.name, ignoreCase = true) &&
                                    kotlin.math.abs(it.lat - place.lat) < 0.0001 &&
                                    kotlin.math.abs(it.lng - place.lng) < 0.0001
                        }
                        all.add(place)
                        file(context).writeText(gson.toJson(all))
                    }
                    .onFailure { Log.w(TAG, "Could not save manual place", it) }
                    .isSuccess

    fun search(context: Context, query: String): List<GeoSearchResult> {
        val tokens =
                query.lowercase(Locale.ROOT)
                        .split(Regex("[^\\p{L}\\p{N}]+"))
                        .filter { it.length >= 2 }
        if (tokens.isEmpty()) return emptyList()
        return load(context)
                .filter { place ->
                    val blob = "${place.name} ${place.address} ${place.type}".lowercase(Locale.ROOT)
                    tokens.all { blob.contains(it) }
                }
                .map {
                    GeoSearchResult(
                            name = it.name,
                            address = it.address,
                            lat = it.lat,
                            lng = it.lng,
                            type = it.type
                    )
                }
    }

    fun nearby(
            context: Context,
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
            limit: Int = 160
    ): List<GeoSearchResult> =
            load(context)
                    .map { place ->
                        place to
                                haversineMeters(
                                        latitude,
                                        longitude,
                                        place.lat,
                                        place.lng
                                )
                    }
                    .filter { it.second <= radiusMeters * 1.2 }
                    .sortedBy { it.second }
                    .take(limit)
                    .map {
                        GeoSearchResult(
                                name = it.first.name,
                                address = it.first.address,
                                lat = it.first.lat,
                                lng = it.first.lng,
                                type = it.first.type
                        )
                    }

    private fun load(context: Context): List<ManualPlace> =
            runCatching {
                        val f = file(context)
                        if (!f.isFile) emptyList()
                        else gson.fromJson<List<ManualPlace>>(f.readText(), type).orEmpty()
                    }
                    .getOrDefault(emptyList())

    private fun file(context: Context) = File(context.filesDir, "manual_places.json")

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
}
