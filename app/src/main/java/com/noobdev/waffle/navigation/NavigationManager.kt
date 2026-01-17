package com.noobdev.waffle.navigation

import android.util.Log
import com.noobdev.waffle.Sketching.sectors
import com.noobdev.waffle.api.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Advanced Navigation Manager Handles smart routing with flood avoidance, road closures, and
 * re-routing
 */
object NavigationManager {

    // Road closures data (will be populated from backend/user reports)
    private val roadClosures = mutableListOf<RoadClosure>()

    // Active flood zones extracted from EMSR838 data
    private val activeFloodZones = mutableListOf<FloodZone>()

    // Current route
    private var currentRoute: RouteResult? = null
    private var destinationLat: Double = 0.0
    private var destinationLng: Double = 0.0

    /** Road closure data class */
    data class RoadClosure(
            val id: String,
            val name: String,
            val lat: Double,
            val lng: Double,
            val radius: Double, // meters
            val reason: String, // "flood", "accident", "construction"
            val isActive: Boolean = true
    )

    /** Flood zone extracted from EMSR data */
    data class FloodZone(
            val sectorName: String,
            val centerLat: Double,
            val centerLng: Double,
            val boundingBox: List<List<Double>>, // Polygon coordinates for avoidance
            val severity: String // "low", "medium", "high", "critical"
    )

    /** Navigation state for UI consumption */
    data class NavigationState(
            val isNavigating: Boolean = false,
            val currentRoute: RouteResult? = null,
            val currentInstructionIndex: Int = 0,
            val distanceRemaining: Double = 0.0,
            val durationRemaining: Double = 0.0,
            val nextInstruction: String = "",
            val nextTurnDistance: String = "",
            val isRerouting: Boolean = false,
            val avoidingFloodZones: Int = 0,
            val avoidingClosures: Int = 0
    )

    /** Initialize flood zones from EMSR sector data */
    fun initializeFloodZones() {
        activeFloodZones.clear()
        sectors.forEach { sector ->
            // Create bounding box around sector center
            // This is approximate - in production, extract actual polygons from GeoJSON
            val buffer = 0.05 // ~5km buffer
            val boundingBox =
                    listOf(
                            listOf(sector.lon - buffer, sector.lat - buffer),
                            listOf(sector.lon + buffer, sector.lat - buffer),
                            listOf(sector.lon + buffer, sector.lat + buffer),
                            listOf(sector.lon - buffer, sector.lat + buffer),
                            listOf(sector.lon - buffer, sector.lat - buffer) // Close polygon
                    )

            activeFloodZones.add(
                    FloodZone(
                            sectorName = sector.name,
                            centerLat = sector.lat,
                            centerLng = sector.lon,
                            boundingBox = boundingBox,
                            severity = "high"
                    )
            )
        }
        Log.d("NavigationManager", "Initialized ${activeFloodZones.size} flood zones")
    }

    /** Add a road closure (called when user reports or data received) */
    fun addRoadClosure(closure: RoadClosure) {
        roadClosures.add(closure)
        Log.d("NavigationManager", "Added road closure: ${closure.name}")
    }

    /** Remove a road closure */
    fun removeRoadClosure(id: String) {
        roadClosures.removeAll { it.id == id }
    }

    /** Get all active closures */
    fun getActiveClosures(): List<RoadClosure> = roadClosures.filter { it.isActive }

    /** Get all flood zones */
    fun getActiveFloodZones(): List<FloodZone> = activeFloodZones.toList()

    /** Calculate smart route avoiding flood zones and closures */
    suspend fun calculateSmartRoute(
            startLat: Double,
            startLng: Double,
            endLat: Double,
            endLng: Double
    ): RouteResult {
        return withContext(Dispatchers.IO) {
            // Build avoidance polygons from flood zones
            val avoidPolygons = mutableListOf<List<List<Double>>>()

            // Add flood zone polygons
            activeFloodZones.forEach { zone -> avoidPolygons.add(zone.boundingBox) }

            // Add road closure buffers as circular polygons
            roadClosures.filter { it.isActive }.forEach { closure ->
                val buffer = closure.radius / 111000 // Convert meters to degrees (approx)
                val closurePolygon = createCirclePolygon(closure.lng, closure.lat, buffer)
                avoidPolygons.add(closurePolygon)
            }

            // Store destination for re-routing
            destinationLat = endLat
            destinationLng = endLng

            val result =
                    if (avoidPolygons.isNotEmpty()) {
                        Log.d("NavigationManager", "Routing with ${avoidPolygons.size} avoid zones")
                        getRouteAvoidingFloodZones(
                                startLat = startLat,
                                startLng = startLng,
                                endLat = endLat,
                                endLng = endLng,
                                avoidAreas = avoidPolygons
                        )
                    } else {
                        getRoute(startLat, startLng, endLat, endLng)
                    }

            if (result.success) {
                currentRoute = result
            }

            result
        }
    }

    /** Re-route from current position (called when user deviates or new closure added) */
    suspend fun reroute(currentLat: Double, currentLng: Double): RouteResult {
        Log.d("NavigationManager", "Re-routing from $currentLat, $currentLng")
        return calculateSmartRoute(
                startLat = currentLat,
                startLng = currentLng,
                endLat = destinationLat,
                endLng = destinationLng
        )
    }

    /** Check if current position is near a flood zone (for warnings) */
    fun checkFloodProximity(lat: Double, lng: Double): FloodZone? {
        val warningDistance = 0.02 // ~2km
        return activeFloodZones.find { zone ->
            val distance = haversineDistance(lat, lng, zone.centerLat, zone.centerLng)
            distance < warningDistance
        }
    }

    /** Check if current position is near a road closure */
    fun checkClosureProximity(lat: Double, lng: Double): RoadClosure? {
        return roadClosures.find { closure ->
            val distance = haversineDistance(lat, lng, closure.lat, closure.lng)
            distance < (closure.radius / 111000) // Convert meters to degrees
        }
    }

    /** Get formatted instruction with distance */
    fun getFormattedInstruction(instruction: String, distanceMeters: Double): String {
        val distanceStr =
                when {
                    distanceMeters < 100 -> "${distanceMeters.toInt()} m"
                    distanceMeters < 1000 -> "${(distanceMeters / 10).toInt() * 10} m"
                    else -> "%.1f km".format(distanceMeters / 1000)
                }
        return "In $distanceStr, $instruction"
    }

    /** Create a circular polygon for avoidance (approximation) */
    private fun createCirclePolygon(
            lng: Double,
            lat: Double,
            radiusDegrees: Double
    ): List<List<Double>> {
        val points = mutableListOf<List<Double>>()
        val segments = 12
        for (i in 0..segments) {
            val angle = 2 * Math.PI * i / segments
            val x = lng + radiusDegrees * Math.cos(angle)
            val y = lat + radiusDegrees * Math.sin(angle)
            points.add(listOf(x, y))
        }
        return points
    }

    /** Haversine distance in degrees (approximate) */
    private fun haversineDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = lat2 - lat1
        val dLng = lng2 - lng1
        return Math.sqrt(dLat * dLat + dLng * dLng)
    }

    /** Reset navigation state */
    fun clearNavigation() {
        currentRoute = null
        destinationLat = 0.0
        destinationLng = 0.0
    }
}

/** Search result from geocoding */
data class SearchResult(
        val name: String,
        val address: String,
        val lat: Double,
        val lng: Double,
        val type: String = "place" // "place", "city", "road", etc.
)

/**
 * Mock search suggestions for Pakistan locations In production, integrate with OpenCage
 * autocomplete
 */
object SearchSuggestions {

    private val pakistanCities =
            listOf(
                    SearchResult(
                            "Islamabad",
                            "Federal Capital, Pakistan",
                            33.6844,
                            73.0479,
                            "city"
                    ),
                    SearchResult("Lahore", "Punjab, Pakistan", 31.5204, 74.3587, "city"),
                    SearchResult("Karachi", "Sindh, Pakistan", 24.8607, 67.0011, "city"),
                    SearchResult("Peshawar", "KPK, Pakistan", 34.0151, 71.5249, "city"),
                    SearchResult("Quetta", "Balochistan, Pakistan", 30.1798, 66.9750, "city"),
                    SearchResult("Multan", "Punjab, Pakistan", 30.1575, 71.5249, "city"),
                    SearchResult("Faisalabad", "Punjab, Pakistan", 31.4504, 73.1350, "city"),
                    SearchResult("Rawalpindi", "Punjab, Pakistan", 33.5651, 73.0169, "city"),
                    SearchResult("Hyderabad", "Sindh, Pakistan", 25.3960, 68.3578, "city"),
                    SearchResult("Gujranwala", "Punjab, Pakistan", 32.1877, 74.1945, "city")
            )

    private val hospitals =
            listOf(
                    SearchResult("PIMS Hospital", "G-8, Islamabad", 33.6950, 73.0450, "hospital"),
                    SearchResult("Shifa Hospital", "H-8, Islamabad", 33.6800, 73.0700, "hospital"),
                    SearchResult(
                            "CMH Rawalpindi",
                            "Saddar, Rawalpindi",
                            33.5700,
                            73.0600,
                            "hospital"
                    ),
                    SearchResult(
                            "Mayo Hospital",
                            "Hall Road, Lahore",
                            31.5700,
                            74.3200,
                            "hospital"
                    ),
                    SearchResult(
                            "Jinnah Hospital",
                            "Gulistan Colony, Lahore",
                            31.5100,
                            74.3400,
                            "hospital"
                    )
            )

    private val emergencyPoints =
            listOf(
                    SearchResult(
                            "Rescue 1122 HQ",
                            "Mall Road, Lahore",
                            31.5500,
                            74.3400,
                            "emergency"
                    ),
                    SearchResult("PDMA Office", "Lahore", 31.5600, 74.3500, "emergency"),
                    SearchResult("NDMA HQ", "Islamabad", 33.7000, 73.0500, "emergency")
            )

    /** Search locations by query */
    fun search(query: String): List<SearchResult> {
        if (query.isBlank()) return emptyList()

        val allLocations = pakistanCities + hospitals + emergencyPoints
        val lowerQuery = query.lowercase()

        return allLocations
                .filter {
                    it.name.lowercase().contains(lowerQuery) ||
                            it.address.lowercase().contains(lowerQuery)
                }
                .take(8)
    }

    /** Get quick suggestions (cities) */
    fun getQuickSuggestions(): List<SearchResult> = pakistanCities.take(6)

    /** Get nearby hospitals (mock - would use actual location) */
    fun getNearbyHospitals(): List<SearchResult> = hospitals

    /** Get emergency points */
    fun getEmergencyPoints(): List<SearchResult> = emergencyPoints
}
