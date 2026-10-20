package com.noobdev.waffle.data.remote

import android.util.Log
import com.google.gson.annotations.SerializedName
import com.noobdev.waffle.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import retrofit2.http.GET
import retrofit2.http.Query

/*
 * OpenCage Geocoding API integration
 * Documentation: https://opencagedata.com/api
 * Supports both forward (search) and reverse (coords to name) geocoding
 */

// API Key
private val OPENCAGE_API_KEY: String = BuildConfig.OPENCAGE_API_KEY

// Response models
data class OpenCageResult(val results: List<OpenCageResponseItem>?)

data class OpenCageResponseItem(
        val formatted: String,
        val components: OpenCageComponents?,
        val geometry: OpenCageGeometry?
)

data class OpenCageComponents(
        val city: String?,
        val town: String?,
        val village: String?,
        val state: String?,
        val country: String?,
        @SerializedName("_type") val type: String?
)

data class OpenCageGeometry(val lat: Double, val lng: Double)

// Search result for UI
data class GeoSearchResult(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val type: String
)

interface OpenCageApiService {
    @GET("/geocode/v1/json")
    suspend fun getAreaName(
            @Query("q") coordinates: String,
            @Query("key") apiKey: String
    ): OpenCageResult

    @GET("/geocode/v1/json")
    suspend fun searchLocation(
            @Query("q") query: String,
            @Query("key") apiKey: String,
            @Query("countrycode") countryCode: String = "pk",
            @Query("limit") limit: Int = 8
    ): OpenCageResult
}

object OpenCageClient {
    private const val BASE_URL = "https://api.opencagedata.com/"
    val api: OpenCageApiService by lazy {
        retrofit2.Retrofit.Builder()
                .baseUrl(BASE_URL)
                .addConverterFactory(retrofit2.converter.gson.GsonConverterFactory.create())
                .build()
                .create(OpenCageApiService::class.java)
    }
}

/** Reverse geocode - coordinates to location name */
suspend fun fetchAreaName(lat: Double, lng: Double, apiKey: String = OPENCAGE_API_KEY): String? {
    return withContext(Dispatchers.IO) {
        try {
            val response = OpenCageClient.api.getAreaName("$lat+$lng", apiKey)
            val area = response.results?.firstOrNull()?.components
            area?.city ?: area?.town ?: area?.village ?: "Unknown Area"
        } catch (e: Exception) {
            Log.e("OpenCageAPI", "Error fetching area: ${e.message}")
            null
        }
    }
}

/** Forward geocode - search text to locations (Pakistan-focused) */
suspend fun searchLocations(query: String): List<GeoSearchResult> {
    return withContext(Dispatchers.IO) {
        try {
            if (query.isBlank()) return@withContext emptyList()

            val response =
                    OpenCageClient.api.searchLocation(
                            query = query,
                            apiKey = OPENCAGE_API_KEY,
                            countryCode = "pk",
                            limit = 8
                    )

            response.results?.mapNotNull { item ->
                val geometry = item.geometry ?: return@mapNotNull null
                val components = item.components

                GeoSearchResult(
                        name = components?.city
                                        ?: components?.town ?: components?.village
                                                ?: item.formatted.split(",").firstOrNull() ?: query,
                        address = item.formatted,
                        lat = geometry.lat,
                        lng = geometry.lng,
                        type = components?.type ?: "place"
                )
            }
                    ?: emptyList()
        } catch (e: Exception) {
            Log.e("OpenCageAPI", "Search error: ${e.message}")
            emptyList()
        }
    }
}

/** Get quick suggestions for Pakistan cities (fallback/offline) */
fun getQuickCitySuggestions(): List<GeoSearchResult> =
        listOf(
                GeoSearchResult("Islamabad", "Federal Capital, Pakistan", 33.6844, 73.0479, "city"),
                GeoSearchResult("Lahore", "Punjab, Pakistan", 31.5204, 74.3587, "city"),
                GeoSearchResult("Karachi", "Sindh, Pakistan", 24.8607, 67.0011, "city"),
                GeoSearchResult("Peshawar", "KPK, Pakistan", 34.0151, 71.5249, "city"),
                GeoSearchResult("Quetta", "Balochistan, Pakistan", 30.1798, 66.9750, "city"),
                GeoSearchResult("Multan", "Punjab, Pakistan", 30.1575, 71.5249, "city"),
                GeoSearchResult("Faisalabad", "Punjab, Pakistan", 31.4504, 73.1350, "city"),
                GeoSearchResult("Rawalpindi", "Punjab, Pakistan", 33.5651, 73.0169, "city")
        )
