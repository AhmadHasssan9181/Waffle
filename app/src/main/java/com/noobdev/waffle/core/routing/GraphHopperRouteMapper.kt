package com.noobdev.waffle.core.routing

import com.graphhopper.GHResponse
import com.graphhopper.util.Instruction
import com.noobdev.waffle.data.remote.NavigationStep
import com.noobdev.waffle.data.remote.RouteResult
import kotlin.math.abs

/**
 * Converts GraphHopper's route model into the same [RouteResult] the rest of the app already
 * understands, so offline and online routes render through one code path.
 */
object GraphHopperRouteMapper {

    fun toRouteResult(response: GHResponse): RouteResult {
        if (response.hasErrors()) {
            val message =
                    response.errors
                            .mapNotNull { it.message }
                            .filter { it.isNotBlank() }
                            .joinToString("; ")
                            .ifBlank { "Offline route failed" }
            return failure(message)
        }

        val best = runCatching { response.best }.getOrNull() ?: return failure("No route found")
        val pointList = best.points
        if (pointList.size < 2) return failure("Offline route has no geometry")

        val coordinates = ArrayList<Pair<Double, Double>>(pointList.size)
        for (index in 0 until pointList.size) {
            coordinates.add(Pair(pointList.getLatitude(index), pointList.getLongitude(index)))
        }

        val instructions = mutableListOf<String>()
        val steps = mutableListOf<NavigationStep>()
        val instructionList = best.instructions
        for (index in 0 until instructionList.size) {
            val instruction = instructionList.get(index)
            val text = describe(instruction)
            val wayPointIndex =
                    if (instruction.points.size > 0) {
                        nearestIndex(
                                coordinates,
                                instruction.points.getLatitude(0),
                                instruction.points.getLongitude(0)
                        )
                    } else 0

            instructions.add(text)
            steps.add(
                    NavigationStep(
                            instruction = text,
                            distance = instruction.distance,
                            duration = 0.0,
                            type = instruction.sign,
                            wayPointIndex = wayPointIndex
                    )
            )
        }

        return RouteResult(
                distanceMeters = best.distance,
                durationSeconds = best.time / 1000.0,
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
        return if (road.isEmpty()) action else "$action onto $road"
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
