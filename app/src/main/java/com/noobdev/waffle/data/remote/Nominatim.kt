package com.noobdev.waffle.data.remote

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONArray

/**
 * Keyless online geocoder used when OpenCage is unavailable or its key is rejected.
 * Nominatim is OpenStreetMap's public geocoder; keep the request rate low and identify
 * the app with a real User-Agent as their usage policy requires.
 */
suspend fun searchLocationsOnline(query: String): List<GeoSearchResult> {
    if (query.isBlank()) return emptyList()
    // OpenCage can take its full 30 s timeout when the network is flaky. Give it a short
    // chance, then let the keyless Nominatim fallback run inside the caller's search window.
    val openCage = withTimeoutOrNull(3_000) { searchLocations(query) }.orEmpty()
    if (openCage.isNotEmpty()) return openCage
    return searchNominatim(query)
}

private suspend fun searchNominatim(query: String): List<GeoSearchResult> =
        withContext(Dispatchers.IO) {
            val encoded = URLEncoder.encode(query, "UTF-8")
            val url =
                    "https://nominatim.openstreetmap.org/search?format=jsonv2" +
                            "&q=$encoded&limit=8&countrycodes=pk&addressdetails=1&accept-language=en"
            val connection =
                    (URL(url).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 8_000
                        readTimeout = 8_000
                        requestMethod = "GET"
                        setRequestProperty(
                                "User-Agent",
                                "Waffle/1.0 (offline-first navigation; " +
                                        "https://github.com/AhmadHassan9181/Waffle)"
                        )
                        setRequestProperty("Accept", "application/json")
                    }
            try {
                if (connection.responseCode !in 200..299) return@withContext emptyList()
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(body)
                buildList {
                    for (index in 0 until array.length()) {
                        val item = array.optJSONObject(index) ?: continue
                        val lat = item.optString("lat").toDoubleOrNull() ?: continue
                        val lon = item.optString("lon").toDoubleOrNull() ?: continue
                        val display = item.optString("display_name")
                        val name =
                                item.optString("name").ifBlank {
                                    display.substringBefore(",").ifBlank { query }
                                }
                        add(
                                GeoSearchResult(
                                        name = name,
                                        address = display,
                                        lat = lat,
                                        lng = lon,
                                        type = item.optString("type").ifBlank { "place" }
                                )
                        )
                    }
                }
            } catch (error: Exception) {
                Log.w("Nominatim", "Online search failed: ${error.message}")
                emptyList()
            } finally {
                connection.disconnect()
            }
        }
