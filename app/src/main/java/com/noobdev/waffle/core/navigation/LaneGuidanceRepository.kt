package com.noobdev.waffle.core.navigation

import android.content.Context
import java.lang.Math.abs
import kotlin.math.max
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lane hints for the bundled Islamabad–Rawalpindi graph.
 *
 * GraphHopper 1.0 does not carry `turn:lanes`, and the metro's OSM data currently has no
 * `turn:lanes` tags at all. It does have `lanes` tags, so this sidecar stores the way geometry and
 * lane counts. The hint highlights the side of the road the next maneuver needs. It is explicitly
 * a lane-count hint, not an authoritative turn-lane assignment.
 */
data class LaneHint(
        val laneCount: Int,
        val arrows: List<String>,
        val activeLanes: Set<Int>,
        val estimated: Boolean = true
)

private data class LaneWay(
        val lanes: Int,
        val forward: Int?,
        val backward: Int?,
        val oneway: Boolean,
        val points: List<GeoPoint>
)

object LaneGuidanceRepository {

    private const val ASSET = "lane-data-islamabad.json"
    private const val MAX_DISTANCE_METERS = 35.0

    @Volatile private var ways: List<LaneWay> = emptyList()
    @Volatile private var loaded = false

    @Synchronized
    fun ensureLoaded(context: Context) {
        if (loaded) return
        loaded = true
        runCatching {
                    val text =
                            context.assets.open(ASSET).bufferedReader().use { it.readText() }
                    val array = JSONObject(text).optJSONArray("ways") ?: JSONArray()
                    val parsed = ArrayList<LaneWay>(array.length())
                    for (index in 0 until array.length()) {
                        val way = array.optJSONObject(index) ?: continue
                        val geometry = way.optJSONArray("geometry") ?: continue
                        val points = ArrayList<GeoPoint>(geometry.length())
                        for (pointIndex in 0 until geometry.length()) {
                            val point = geometry.optJSONArray(pointIndex) ?: continue
                            if (point.length() < 2) continue
                            points.add(GeoPoint(point.getDouble(0), point.getDouble(1)))
                        }
                        if (points.size < 2) continue
                        parsed.add(
                                LaneWay(
                                        lanes = way.optInt("lanes", 0),
                                        forward =
                                                if (way.has("forward") &&
                                                                !way.isNull("forward")
                                                )
                                                        way.optInt("forward")
                                                else null,
                                        backward =
                                                if (way.has("backward") &&
                                                                !way.isNull("backward")
                                                )
                                                        way.optInt("backward")
                                                else null,
                                        oneway = way.optBoolean("oneway", false),
                                        points = points
                                )
                        )
                    }
                    ways = parsed
                }
                .onFailure { loaded = false }
    }

    fun hintAt(
            latitude: Double,
            longitude: Double,
            bearingDegrees: Double,
            maneuver: ManeuverType
    ): LaneHint? {
        if (ways.isEmpty()) return null
        var bestDistance = MAX_DISTANCE_METERS
        var bestWay: LaneWay? = null
        var bestA: GeoPoint? = null
        var bestB: GeoPoint? = null

        for (way in ways) {
            for (index in 0 until way.points.size - 1) {
                val a = way.points[index]
                val b = way.points[index + 1]
                val distance = distanceToSegmentMeters(latitude, longitude, a, b)
                if (distance < bestDistance) {
                    bestDistance = distance
                    bestWay = way
                    bestA = a
                    bestB = b
                }
            }
        }

        val way = bestWay ?: return null
        val a = bestA ?: return null
        val b = bestB ?: return null
        val wayBearing = GeoGeometry.bearingDegrees(a, b)
        val delta = ((bearingDegrees - wayBearing + 540.0) % 360.0) - 180.0
        val reversed = abs(delta) > 90.0

        val laneCount =
                when {
                            reversed && way.backward != null -> way.backward
                            !reversed && way.forward != null -> way.forward
                            way.oneway -> way.lanes
                            else -> max(1, (way.lanes + 1) / 2)
                        }
                        ?.coerceIn(1, 6)
                        ?: return null

        val active =
                when (maneuver) {
                    ManeuverType.TURN_LEFT,
                    ManeuverType.TURN_SHARP_LEFT,
                    ManeuverType.TURN_SLIGHT_LEFT,
                    ManeuverType.KEEP_LEFT,
                    ManeuverType.RAMP_LEFT,
                    ManeuverType.U_TURN_LEFT -> setOf(0)
                    ManeuverType.TURN_RIGHT,
                    ManeuverType.TURN_SHARP_RIGHT,
                    ManeuverType.TURN_SLIGHT_RIGHT,
                    ManeuverType.KEEP_RIGHT,
                    ManeuverType.RAMP_RIGHT,
                    ManeuverType.U_TURN_RIGHT -> setOf(laneCount - 1)
                    ManeuverType.ARRIVE, ManeuverType.WAYPOINT -> emptySet()
                    else -> (0 until laneCount).toSet()
                }

        val arrows =
                (0 until laneCount).map { index ->
                    when {
                        index !in active -> "\u2191"
                        maneuver == ManeuverType.U_TURN_LEFT ||
                                maneuver == ManeuverType.U_TURN_RIGHT -> "\u21B0"
                        maneuver == ManeuverType.ROUNDABOUT_LEFT ||
                                maneuver == ManeuverType.ROUNDABOUT_RIGHT -> "\u21BB"
                        index == 0 &&
                                maneuver in
                                        setOf(
                                                ManeuverType.TURN_LEFT,
                                                ManeuverType.TURN_SHARP_LEFT,
                                                ManeuverType.TURN_SLIGHT_LEFT,
                                                ManeuverType.KEEP_LEFT,
                                                ManeuverType.RAMP_LEFT
                                        ) -> "\u2190"
                        index == laneCount - 1 &&
                                maneuver in
                                        setOf(
                                                ManeuverType.TURN_RIGHT,
                                                ManeuverType.TURN_SHARP_RIGHT,
                                                ManeuverType.TURN_SLIGHT_RIGHT,
                                                ManeuverType.KEEP_RIGHT,
                                                ManeuverType.RAMP_RIGHT
                                        ) -> "\u2192"
                        else -> "\u2191"
                    }
                }

        return LaneHint(laneCount = laneCount, arrows = arrows, activeLanes = active)
    }

    private fun distanceToSegmentMeters(
            latitude: Double,
            longitude: Double,
            a: GeoPoint,
            b: GeoPoint
    ): Double {
        // Local equirectangular projection is accurate enough for a 35 m snap.
        val metersPerDegreeLat = 111_320.0
        val metersPerDegreeLng = 111_320.0 * kotlin.math.cos(Math.toRadians(latitude))
        val px = (longitude - a.longitude) * metersPerDegreeLng
        val py = (latitude - a.latitude) * metersPerDegreeLat
        val bx = (b.longitude - a.longitude) * metersPerDegreeLng
        val by = (b.latitude - a.latitude) * metersPerDegreeLat
        val lengthSquared = bx * bx + by * by
        if (lengthSquared <= 0.0001) return kotlin.math.hypot(px, py)
        val t = ((px * bx + py * by) / lengthSquared).coerceIn(0.0, 1.0)
        return kotlin.math.hypot(px - bx * t, py - by * t)
    }
}
