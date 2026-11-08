package com.noobdev.waffle.core.routing

import com.graphhopper.GHResponse
import com.graphhopper.ResponsePath
import com.graphhopper.util.Instruction
import com.noobdev.waffle.core.navigation.ManeuverType
import com.noobdev.waffle.core.navigation.hasNonLatinLetters
import com.noobdev.waffle.data.remote.NavigationStep
import com.noobdev.waffle.data.remote.RouteResult
import kotlin.math.abs

/**
 * Converts GraphHopper's route model into the same [RouteResult] the rest of the app already
 * understands, so offline and online routes render through one code path.
 */
object GraphHopperRouteMapper {

    fun toRouteResult(response: GHResponse): RouteResult =
            toRouteResults(response).firstOrNull() ?: failure("No route found")

    /** Best route first, followed by any alternatives GraphHopper produced. */
    fun toRouteResults(response: GHResponse): List<RouteResult> {
        if (response.hasErrors()) {
            val message =
                    response.errors
                            .mapNotNull { it.message }
                            .filter { it.isNotBlank() }
                            .joinToString("; ")
                            .ifBlank { "Offline route failed" }
            return listOf(failure(message))
        }
        val paths = response.all
        if (paths.isEmpty()) return listOf(failure("No route found"))
        return paths.mapNotNull { path -> toRouteResult(path) }
    }

    private fun toRouteResult(path: ResponsePath): RouteResult? {
        val pointList = path.points
        if (pointList.size < 2) return null

        val coordinates = ArrayList<Pair<Double, Double>>(pointList.size)
        for (index in 0 until pointList.size) {
            coordinates.add(Pair(pointList.getLatitude(index), pointList.getLongitude(index)))
        }

        val instructions = mutableListOf<String>()
        val steps = mutableListOf<NavigationStep>()
        val instructionList = path.instructions
        var totalStepSeconds = 0.0
        for (index in 0 until instructionList.size) {
            val instruction = instructionList.get(index)
            if (instruction.sign == Instruction.REACHED_VIA) continue
            val text = describe(instruction)
            val wayPointIndex =
                    if (instruction.points.size > 0) {
                        nearestIndex(
                                coordinates,
                                instruction.points.getLatitude(0),
                                instruction.points.getLongitude(0)
                        )
                    } else 0

            val maneuver = maneuverFor(instruction.sign)
            val rawStepSec = (instruction.time / 1000.0).coerceAtLeast(0.0)
            // GraphHopper 1.0's free-flow edge weights ignore intersection/signal deceleration.
            // Add a realistic maneuver penalty and slight urban friction so step & total durations
            // match real-world driving pace.
            val turnPenaltySec =
                    when (maneuver) {
                        ManeuverType.U_TURN_LEFT,
                        ManeuverType.U_TURN_RIGHT -> 14.0
                        ManeuverType.ROUNDABOUT_LEFT,
                        ManeuverType.ROUNDABOUT_RIGHT -> 10.0
                        ManeuverType.TURN_LEFT,
                        ManeuverType.TURN_RIGHT,
                        ManeuverType.TURN_SHARP_LEFT,
                        ManeuverType.TURN_SHARP_RIGHT -> 8.0
                        ManeuverType.TURN_SLIGHT_LEFT,
                        ManeuverType.TURN_SLIGHT_RIGHT,
                        ManeuverType.KEEP_LEFT,
                        ManeuverType.KEEP_RIGHT -> 3.0
                        else -> 0.0
                    }
            val avgSpeedMps =
                    if (rawStepSec > 0.5) instruction.distance / rawStepSec else 11.0
            // Urban/local streets (< 50 km/h free-flow) experience signal/traffic friction (~14%),
            // whereas expressways (> 70 km/h) flow closer to nominal speed (~5%).
            val frictionFactor =
                    when {
                        avgSpeedMps < 2.2 -> 1.0 // walking
                        avgSpeedMps < 6.0 -> 1.05 // cycling
                        avgSpeedMps < 14.0 -> 1.14 // city street
                        avgSpeedMps < 19.5 -> 1.09 // arterial
                        else -> 1.05 // highway
                    }
            val stepDurationSec = rawStepSec * frictionFactor + turnPenaltySec
            totalStepSeconds += stepDurationSec

            instructions.add(text)
            steps.add(
                    NavigationStep(
                            instruction = text,
                            distance = instruction.distance,
                            duration = stepDurationSec,
                            type = instruction.sign,
                            wayPointIndex = wayPointIndex,
                            maneuver = maneuver
                    )
            )
        }

        val rawTotalSec = (path.time / 1000.0).coerceAtLeast(1.0)
        val calibratedDurationSec =
                if (totalStepSeconds >= rawTotalSec) totalStepSeconds else rawTotalSec

        return RouteResult(
                distanceMeters = path.distance,
                durationSeconds = calibratedDurationSec,
                coordinates = coordinates,
                instructions = instructions,
                steps = steps,
                success = true
        )
    }

    private fun failure(message: String): RouteResult =
            RouteResult(
                    distanceMeters = 0.0,
                    durationSeconds = 0.0,
                    coordinates = emptyList(),
                    instructions = emptyList(),
                    success = false,
                    errorMessage = message
            )

    private fun maneuverFor(sign: Int): ManeuverType =
            when (sign) {
                Instruction.KEEP_LEFT -> ManeuverType.KEEP_LEFT
                Instruction.TURN_SHARP_LEFT -> ManeuverType.TURN_SHARP_LEFT
                Instruction.TURN_LEFT -> ManeuverType.TURN_LEFT
                Instruction.TURN_SLIGHT_LEFT -> ManeuverType.TURN_SLIGHT_LEFT
                Instruction.CONTINUE_ON_STREET -> ManeuverType.CONTINUE
                Instruction.TURN_SLIGHT_RIGHT -> ManeuverType.TURN_SLIGHT_RIGHT
                Instruction.TURN_RIGHT -> ManeuverType.TURN_RIGHT
                Instruction.TURN_SHARP_RIGHT -> ManeuverType.TURN_SHARP_RIGHT
                Instruction.FINISH -> ManeuverType.ARRIVE
                Instruction.REACHED_VIA -> ManeuverType.WAYPOINT
                Instruction.USE_ROUNDABOUT -> ManeuverType.ROUNDABOUT_RIGHT
                Instruction.KEEP_RIGHT -> ManeuverType.KEEP_RIGHT
                else -> ManeuverType.UNKNOWN
            }

    private fun describe(instruction: Instruction): String {
        val action =
                when (instruction.sign) {
                    Instruction.KEEP_LEFT -> "Keep left"
                    Instruction.TURN_SHARP_LEFT -> "Turn sharp left"
                    Instruction.TURN_LEFT -> "Turn left"
                    Instruction.TURN_SLIGHT_LEFT -> "Bear left"
                    Instruction.CONTINUE_ON_STREET -> "Continue"
                    Instruction.TURN_SLIGHT_RIGHT -> "Bear right"
                    Instruction.TURN_RIGHT -> "Turn right"
                    Instruction.TURN_SHARP_RIGHT -> "Turn sharp right"
                    Instruction.FINISH -> "Arrive at destination"
                    Instruction.REACHED_VIA -> "Reach the via point"
                    Instruction.USE_ROUNDABOUT -> "Take the roundabout"
                    Instruction.KEEP_RIGHT -> "Keep right"
                    else -> "Continue"
                }

        val road = instruction.name?.trim().orEmpty()
        val latinRoad = road.takeUnless { it.hasNonLatinLetters() }.orEmpty()
        return if (latinRoad.isEmpty()) action else "$action onto $latinRoad"
    }

    private fun nearestIndex(
            coordinates: List<Pair<Double, Double>>,
            latitude: Double,
            longitude: Double
    ): Int {
        var bestIndex = 0
        var bestDistance = Double.MAX_VALUE
        for (index in coordinates.indices) {
            val point = coordinates[index]
            val delta = abs(point.first - latitude) + abs(point.second - longitude)
            if (delta < bestDistance) {
                bestDistance = delta
                bestIndex = index
            }
        }
        return bestIndex
    }
}
