package com.noobdev.waffle.core.routing

import com.graphhopper.GHRequest
import com.graphhopper.GHResponse
import com.graphhopper.GraphHopper
import com.graphhopper.config.CHProfile
import com.graphhopper.config.Profile
import com.graphhopper.routing.ev.MaxSpeed
import com.graphhopper.routing.util.EdgeFilter
import com.graphhopper.util.shapes.GHPoint
import com.noobdev.waffle.core.navigation.GeoPoint
import kotlin.math.roundToInt

/**
 * Offline routing engine backed by GraphHopper 1.0.
 *
 * GraphHopper dropped offline support after 1.0, so this deliberately stays on 1.0 and the routing
 * graph in assets must be generated with the same version (see tools/graph-builder). `forMobile()`
 * switches GraphHopper into its in-memory, read-only mode which is what an Android app wants.
 */
class LocalRoutingEngine(graphFolderAbsolutePath: String) {

    private val hopper: GraphHopper =
            GraphHopper().apply {
                forMobile()
                setGraphHopperLocation(graphFolderAbsolutePath)
                setProfiles(
                        Profile("car").setVehicle("car").setWeighting("fastest"),
                        Profile("foot").setVehicle("foot").setWeighting("shortest"),
                        Profile("bike").setVehicle("bike").setWeighting("fastest")
                )
                // GraphHopper's mobile mode does not allow disabling CH per request, so every
                // profile needs a CH preparation. The bundled graph is built the same way.
                getCHPreparationHandler()
                        .setCHProfiles(
                                CHProfile("car"),
                                CHProfile("foot"),
                                CHProfile("bike")
                        )
                setAllowWrites(false)
                importOrLoad()
            }

    val isReady: Boolean
        get() = hopper.graphHopperStorage != null

    fun calculateRoute(
            start: GeoPoint,
            end: GeoPoint,
            profile: String = "car",
            waypoints: List<GeoPoint> = emptyList()
    ): GHResponse = calculateRoutes(start, end, profile, waypoints, maxPaths = 1)

    /**
     * Plans the best route plus optional alternatives. GraphHopper 1.0's `alternative_route`
     * algorithm works with the CH graph that ships in the APK.
     */
    fun calculateRoutes(
            start: GeoPoint,
            end: GeoPoint,
            profile: String = "car",
            waypoints: List<GeoPoint> = emptyList(),
            maxPaths: Int = 3
    ): GHResponse {
        val points = ArrayList<GHPoint>(waypoints.size + 2)
        points.add(GHPoint(start.latitude, start.longitude))
        waypoints.forEach { points.add(GHPoint(it.latitude, it.longitude)) }
        points.add(GHPoint(end.latitude, end.longitude))

        val normalizedProfile = normalizeProfile(profile)
        val request =
                GHRequest(points).apply {
                    setProfile(normalizedProfile)
                    setLocale("en")
                    if (maxPaths > 1 && waypoints.isEmpty()) {
                        setAlgorithm("alternative_route")
                        putHint("alternative_route.max_paths", maxPaths)
                        putHint("alternative_route.max_weight_factor", 1.65)
                        putHint("alternative_route.max_share_factor", 0.55)
                        putHint("alternative_route.min_plateau_factor", 0.15)
                    }
                }
        var response = hopper.route(request)
        if (maxPaths > 1 && response.hasErrors()) {
            // A graph without alternative-route support must still return the best route.
            val fallback =
                    GHRequest(points).apply {
                        setProfile(normalizedProfile)
                        setLocale("en")
                    }
            response = hopper.route(fallback)
        }
        if (maxPaths > 1 && !response.hasErrors() && response.all.isNotEmpty() && response.all.size < maxPaths) {
            appendCorridorAlternatives(
                    baseResponse = response,
                    start = start,
                    end = end,
                    waypoints = waypoints,
                    normalizedProfile = normalizedProfile,
                    maxPaths = maxPaths
            )
        }
        return response
    }

    /**
     * Supplements `alternative_route` when CH returns fewer than [maxPaths] routes (or when
     * [waypoints] is non-empty, where GraphHopper 1.0's `alternative_route` is unsupported).
     * Probes lateral corridor via-points perpendicular to the primary route, snaps them to the
     * road network, and accepts only clean forward-flowing paths that do not backtrack.
     */
    private fun appendCorridorAlternatives(
            baseResponse: GHResponse,
            start: GeoPoint,
            end: GeoPoint,
            waypoints: List<GeoPoint>,
            normalizedProfile: String,
            maxPaths: Int
    ) {
        val primary = baseResponse.best ?: return
        val primaryPts = primary.points
        if (primaryPts.size < 6) return
        val dLat = end.latitude - start.latitude
        val dLng = end.longitude - start.longitude
        val chordDeg = kotlin.math.hypot(dLat, dLng)
        if (chordDeg < 0.004) return // < ~450m: too short for meaningful corridor alternatives

        // Unit vector perpendicular to start->end chord
        val perpLat = -dLng / chordDeg
        val perpLng = dLat / chordDeg

        val candidateSpecs =
                listOf(
                        0.50 to 0.20,
                        0.50 to -0.20,
                        0.38 to 0.30,
                        0.62 to -0.30
                )

        for ((alongFrac, lateralFrac) in candidateSpecs) {
            if (baseResponse.all.size >= maxPaths) break
            val anchorIdx =
                    ((primaryPts.size - 1) * alongFrac)
                            .toInt()
                            .coerceIn(1, primaryPts.size - 2)
            val anchorLat = primaryPts.getLatitude(anchorIdx)
            val anchorLng = primaryPts.getLongitude(anchorIdx)
            val offsetDeg = (chordDeg * lateralFrac).coerceIn(-0.045, 0.045)
            val probeLat = anchorLat + perpLat * offsetDeg
            val probeLng = anchorLng + perpLng * offsetDeg

            val snap = hopper.locationIndex.findClosest(probeLat, probeLng, EdgeFilter.ALL_EDGES)
            if (!snap.isValid) continue
            val snappedPt = snap.snappedPoint ?: continue

            val viaPoints = ArrayList<GHPoint>(waypoints.size + 3)
            viaPoints.add(GHPoint(start.latitude, start.longitude))
            waypoints.forEach { viaPoints.add(GHPoint(it.latitude, it.longitude)) }
            viaPoints.add(GHPoint(snappedPt.lat, snappedPt.lon))
            viaPoints.add(GHPoint(end.latitude, end.longitude))

            val altReq =
                    GHRequest(viaPoints).apply {
                        setProfile(normalizedProfile)
                        setLocale("en")
                    }
            val altRes = runCatching { hopper.route(altReq) }.getOrNull() ?: continue
            if (altRes.hasErrors() || altRes.all.isEmpty()) continue
            val candidate = altRes.best ?: continue

            // Reject excessively long detours (> 1.45x time or > 1.50x distance of primary).
            if (candidate.time > primary.time * 1.45 ||
                            candidate.distance > primary.distance * 1.50
            ) {
                continue
            }
            // Reject paths that U-turn or spur into the via-point and backtrack out.
            if (hasSpurAtVia(candidate, snappedPt.lat, snappedPt.lon)) continue
            // Reject paths that are nearly identical to an already-included path.
            val isDuplicate =
                    baseResponse.all.any { existing ->
                        val distDiff =
                                kotlin.math.abs(existing.distance - candidate.distance) /
                                        existing.distance.coerceAtLeast(1.0)
                        distDiff < 0.035 && sharesMostGeometry(existing, candidate)
                    }
            if (!isDuplicate) {
                baseResponse.add(candidate)
            }
        }
    }

    private fun hasSpurAtVia(
            path: com.graphhopper.ResponsePath,
            viaLat: Double,
            viaLng: Double
    ): Boolean {
        val pts = path.points
        if (pts.size < 5) return false
        var bestIdx = -1
        var bestDist = Double.MAX_VALUE
        for (i in 2 until pts.size - 2) {
            val d =
                    kotlin.math.hypot(
                            pts.getLatitude(i) - viaLat,
                            pts.getLongitude(i) - viaLng
                    )
            if (d < bestDist) {
                bestDist = d
                bestIdx = i
            }
        }
        if (bestIdx < 2 || bestIdx >= pts.size - 2) return false
        val inLat = pts.getLatitude(bestIdx) - pts.getLatitude(bestIdx - 2)
        val inLng = pts.getLongitude(bestIdx) - pts.getLongitude(bestIdx - 2)
        val outLat = pts.getLatitude(bestIdx + 2) - pts.getLatitude(bestIdx)
        val outLng = pts.getLongitude(bestIdx + 2) - pts.getLongitude(bestIdx)
        val inLen = kotlin.math.hypot(inLat, inLng)
        val outLen = kotlin.math.hypot(outLat, outLng)
        if (inLen < 1e-6 || outLen < 1e-6) return false
        val cosAngle = (inLat * outLat + inLng * outLng) / (inLen * outLen)
        // cosAngle < 0.25 means the route turns sharper than ~75° (or U-turns) right at the via point.
        return cosAngle < 0.25
    }

    private fun sharesMostGeometry(
            a: com.graphhopper.ResponsePath,
            b: com.graphhopper.ResponsePath
    ): Boolean {
        val ptsA = a.points
        val ptsB = b.points
        if (ptsA.size < 4 || ptsB.size < 4) return true
        val samples = 12
        var closeCount = 0
        for (s in 1 until samples) {
            val idxA = (s * (ptsA.size - 1)) / samples
            val latA = ptsA.getLatitude(idxA)
            val lngA = ptsA.getLongitude(idxA)
            var minD = Double.MAX_VALUE
            for (j in 0 until ptsB.size step maxOf(1, ptsB.size / 30)) {
                val d =
                        kotlin.math.hypot(
                                latA - ptsB.getLatitude(j),
                                lngA - ptsB.getLongitude(j)
                        )
                if (d < minD) minD = d
            }
            if (minD < 0.0012) closeCount++ // within ~130m
        }
        return closeCount >= (samples - 1) * 0.78
    }

    /**
     * The OSM `maxspeed` value for the road under [lat]/[lon], in km/h, or null when the way has no
     * signed limit. The graph already encodes this value; no extra data file is needed.
     */
    fun speedLimitKmhAt(lat: Double, lon: Double): Int? {
        val maxSpeed =
                hopper.encodingManager.getDecimalEncodedValue(MaxSpeed.KEY) ?: return null
        val result = hopper.locationIndex.findClosest(lat, lon, EdgeFilter.ALL_EDGES)
        if (!result.isValid) return null
        val edge = result.closestEdge ?: return null
        val limit = edge.get(maxSpeed)
        if (!limit.isFinite() ||
                        limit <= 0.0 ||
                        limit >= MaxSpeed.UNLIMITED_SIGN_SPEED
        ) {
            return null
        }
        return limit.roundToInt()
    }

    private fun normalizeProfile(profile: String): String =
            when (profile.lowercase()) {
                "foot", "foot-walking", "walking" -> "foot"
                "bike", "cycling-regular", "cycling", "bicycle" -> "bike"
                "motorcycle", "driving-motorcycle", "motorbike" -> "car"
                else -> "car"
            }
}
