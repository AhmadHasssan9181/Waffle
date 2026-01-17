package com.noobdev.waffle.api

import android.util.Log
import com.google.gson.annotations.SerializedName
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Query

/*
 * OpenRouteService (ORS) Routing API Integration
 * Using GET endpoint for simplicity and reliability
 * API Docs: https://openrouteservice.org/dev/#/api-docs/v2/directions/{profile}/get
 */

// === API KEY ===
private const val ORS_API_KEY = "5b3ce3597851110001cf6248099f56e121c64067b5762a109e70ee9b"
private const val ORS_BASE_URL = "https://api.openrouteservice.org/"

// === RESPONSE MODELS ===

data class DirectionsResponse(
        val type: String?,
        val features: List<RouteFeature>?,
        val bbox: List<Double>?,
        val metadata: RouteMetadata?
)

data class RouteFeature(
        val type: String?,
        val properties: RouteProperties?,
        val geometry: RouteGeometry?
)

data class RouteProperties(
        @SerializedName("summary") val summary: RouteSummary?,
        @SerializedName("segments") val segments: List<RouteSegment>?
)

data class RouteSummary(
        val distance: Double, // meters
        val duration: Double // seconds
)

data class RouteSegment(val distance: Double, val duration: Double, val steps: List<RouteStep>?)

data class RouteStep(
        val distance: Double,
        val duration: Double,
        val type: Int,
        val instruction: String,
        val name: String,
        @SerializedName("way_points") val wayPoints: List<Int>?
)

data class RouteGeometry(
        val type: String?,
        val coordinates: List<List<Double>>? // [[lng, lat], [lng, lat], ...]
)

data class RouteMetadata(
        val attribution: String?,
        val timestamp: Long?,
        val query: Map<String, Any>?
)

// === SIMPLIFIED RESULT FOR UI ===

data class NavigationStep(
        val instruction: String,
        val distance: Double,
        val duration: Double,
        val type: Int,
        val wayPointIndex: Int // Index in the coordinates list where this step starts
)

data class RouteResult(
        val distanceMeters: Double,
        val durationSeconds: Double,
        val coordinates: List<Pair<Double, Double>>, // List of (lat, lng) pairs
        val instructions: List<String>,
        val steps: List<NavigationStep> = emptyList(),
        val success: Boolean,
        val errorMessage: String? = null
)

// === RETROFIT SERVICE - Using GET endpoint ===

interface OpenRouteServiceApi {
        /** GET /v2/directions/{profile} Coordinates format: "lng,lat|lng,lat" */
        @GET("v2/directions/{profile}")
        suspend fun getDirections(
                @Path("profile") profile: String,
                @Header("Authorization") apiKey: String,
                @Query("start") start: String,
                @Query("end") end: String
        ): Response<DirectionsResponse>
}

// === RETROFIT INSTANCE ===

object ORSRetrofitInstance {
        private val loggingInterceptor =
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BODY }

        private val client =
                OkHttpClient.Builder()
                        .addInterceptor(loggingInterceptor)
                        .connectTimeout(30, TimeUnit.SECONDS)
                        .readTimeout(30, TimeUnit.SECONDS)
                        .build()

        val api: OpenRouteServiceApi by lazy {
                Retrofit.Builder()
                        .baseUrl(ORS_BASE_URL)
                        .client(client)
                        .addConverterFactory(GsonConverterFactory.create())
                        .build()
                        .create(OpenRouteServiceApi::class.java)
        }
}

// === ROUTING FUNCTIONS ===

/**
 * Get driving route between two points using ORS GET endpoint
 * @param startLat Starting latitude
 * @param startLng Starting longitude
 * @param endLat Destination latitude
 * @param endLng Destination longitude
 * @param profile Route profile: "driving-car", "cycling-regular", "foot-walking"
 * @return RouteResult with coordinates, distance, duration, and instructions
 */
suspend fun getRoute(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        profile: String = "driving-car"
): RouteResult {
        return withContext(Dispatchers.IO) {
                try {
                        Log.d(
                                "ORS",
                                "Getting route from ($startLat,$startLng) to ($endLat,$endLng)"
                        )

                        // Format: "lng,lat" for start and end
                        val start = "$startLng,$startLat"
                        val end = "$endLng,$endLat"

                        Log.d("ORS", "Start: $start, End: $end")

                        val response =
                                ORSRetrofitInstance.api.getDirections(
                                        profile = profile,
                                        apiKey = ORS_API_KEY,
                                        start = start,
                                        end = end
                                )

                        Log.d("ORS", "Response code: ${response.code()}")

                        if (response.isSuccessful && response.body() != null) {
                                val body = response.body()!!
                                val feature = body.features?.firstOrNull()

                                if (feature != null && feature.geometry?.coordinates != null) {
                                        // Convert coordinates from [lng, lat] to (lat, lng)
                                        val coordinates =
                                                feature.geometry.coordinates.mapNotNull { coord ->
                                                        if (coord.size >= 2) {
                                                                Pair(
                                                                        coord[1],
                                                                        coord[0]
                                                                ) // (lat, lng)
                                                        } else null
                                                }

                                        // Extract detailed steps
                                        val navigationSteps = ArrayList<NavigationStep>()
                                        val instructions = ArrayList<String>()

                                        feature.properties?.segments?.forEach { segment ->
                                                segment.steps?.forEach { step ->
                                                        instructions.add(step.instruction)
                                                        // ORS returns way_points as [startIndex,
                                                        // endIndex]
                                                        val startIndex =
                                                                step.wayPoints?.firstOrNull() ?: 0
                                                        navigationSteps.add(
                                                                NavigationStep(
                                                                        instruction =
                                                                                step.instruction,
                                                                        distance = step.distance,
                                                                        duration = step.duration,
                                                                        type = step.type,
                                                                        wayPointIndex = startIndex
                                                                )
                                                        )
                                                }
                                        }

                                        val distance = feature.properties?.summary?.distance ?: 0.0
                                        val duration = feature.properties?.summary?.duration ?: 0.0

                                        Log.d(
                                                "ORS",
                                                "Route found: ${coordinates.size} points, ${formatDistance(distance)}"
                                        )

                                        RouteResult(
                                                distanceMeters = distance,
                                                durationSeconds = duration,
                                                coordinates = coordinates,
                                                instructions = instructions,
                                                steps = navigationSteps,
                                                success = true
                                        )
                                } else {
                                        Log.e("ORS", "No route feature found in response")
                                        RouteResult(
                                                distanceMeters = 0.0,
                                                durationSeconds = 0.0,
                                                coordinates = emptyList(),
                                                instructions = emptyList(),
                                                success = false,
                                                errorMessage = "No route found"
                                        )
                                }
                        } else {
                                val errorBody = response.errorBody()?.string()
                                Log.e("ORS", "Route error: ${response.code()} - $errorBody")
                                RouteResult(
                                        distanceMeters = 0.0,
                                        durationSeconds = 0.0,
                                        coordinates = emptyList(),
                                        instructions = emptyList(),
                                        success = false,
                                        errorMessage = "API error: ${response.code()} - $errorBody"
                                )
                        }
                } catch (e: Exception) {
                        Log.e("ORS", "Route exception: ${e.message}", e)
                        RouteResult(
                                distanceMeters = 0.0,
                                durationSeconds = 0.0,
                                coordinates = emptyList(),
                                instructions = emptyList(),
                                success = false,
                                errorMessage = e.message
                        )
                }
        }
}

/**
 * Simpler route function without flood zone avoidance (ORS free tier has limited avoid_polygon
 * support)
 */
suspend fun getRouteAvoidingFloodZones(
        startLat: Double,
        startLng: Double,
        endLat: Double,
        endLng: Double,
        avoidAreas: List<List<List<Double>>> = emptyList()
): RouteResult {
        // For now, just get the regular route
        // Avoid polygons require POST endpoint which may have issues
        return getRoute(startLat, startLng, endLat, endLng)
}

// === HELPER FUNCTIONS ===

/** Format distance for display */
fun formatDistance(meters: Double): String {
        return when {
                meters < 1000 -> "${meters.toInt()} m"
                else -> "%.1f km".format(meters / 1000)
        }
}

/** Format duration for display */
fun formatDuration(seconds: Double): String {
        val totalMinutes = (seconds / 60).toInt()
        return when {
                totalMinutes < 60 -> "$totalMinutes min"
                else -> {
                        val hours = totalMinutes / 60
                        val mins = totalMinutes % 60
                        if (mins > 0) "$hours h $mins min" else "$hours h"
                }
        }
}
