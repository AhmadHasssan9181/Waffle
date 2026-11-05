package com.noobdev.waffle.core.navigation

import android.content.Context
import com.noobdev.waffle.data.remote.GeoSearchResult
import com.noobdev.waffle.data.remote.NavigationStep
import com.noobdev.waffle.data.remote.RouteResult
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the route, destination, stops, mute state and a throttled snapshot of the live
 * position/ETA so a process restart resumes with useful progress instead of only the route start.
 * The controller still prefers a fresh GPS fix when one arrives.
 */
object NavigationStore {

    private const val FILE_NAME = "navigation_session.json"
    private const val MAX_AGE_MS = 24 * 60 * 60 * 1000L

    fun save(context: Context, snapshot: NavigationSnapshot) {
        val route = snapshot.route ?: return
        val destination = snapshot.destination ?: return
        runCatching {
            val json =
                    JSONObject()
                            .put("savedAt", System.currentTimeMillis())
                            .put("profile", snapshot.profile)
                            .put("styleIndex", snapshot.styleIndex)
                            .put("vehicleIconKey", snapshot.vehicleIconKey)
                            .put("muted", snapshot.muted)
                            .put("lat", snapshot.lat)
                            .put("lng", snapshot.lng)
                            .put("bearing", snapshot.bearing)
                            .put("speedMps", snapshot.speedMps)
                            .put("instructionIndex", snapshot.instructionIndex)
                            .put("remainingDistance", snapshot.remainingDistance)
                            .put("remainingDuration", snapshot.remainingDuration)
                            .put("distanceToNextTurn", snapshot.distanceToNextTurn)
                            .put("matchedIndex", snapshot.matchedIndex)
                            .put("destination", destination.toJson())
                            .put("stops", JSONArray(snapshot.stops.map { it.toJson() }))
                            .put("route", route.toJson())
            // Write through a temp file so a process death mid-save cannot corrupt the trip.
            val target = file(context)
            val temp = File(target.parentFile, "${target.name}.tmp")
            temp.writeText(json.toString())
            if (!temp.renameTo(target)) {
                target.writeText(json.toString())
                temp.delete()
            }
        }
    }

    fun load(context: Context): NavigationSnapshot? =
            runCatching {
                val file = file(context)
                if (!file.exists()) return null
                val json = JSONObject(file.readText())
                val savedAt = json.optLong("savedAt", 0L)
                if (System.currentTimeMillis() - savedAt > MAX_AGE_MS) {
                    file.delete()
                    return null
                }
                val route = json.getJSONObject("route").toRouteResult()
                val destination = json.getJSONObject("destination").toGeoSearchResult()
                val stopsJson = json.optJSONArray("stops") ?: JSONArray()
                val stops =
                        (0 until stopsJson.length()).map {
                            stopsJson.getJSONObject(it).toGeoSearchResult()
                        }
                NavigationSnapshot(
                        active = true,
                        route = route,
                        destination = destination,
                        profile = json.optString("profile", "driving-car"),
                        styleIndex = json.optInt("styleIndex", 1),
                        vehicleIconKey = json.optString("vehicleIconKey", "SEDAN"),
                        stops = stops,
                        lat = json.optDouble("lat", 0.0),
                        lng = json.optDouble("lng", 0.0),
                        bearing = json.optDouble("bearing", 0.0),
                        speedMps = json.optDouble("speedMps", 0.0).toFloat(),
                        instructionIndex = json.optInt("instructionIndex", 0),
                        remainingDistance =
                                json.optDouble("remainingDistance", route.distanceMeters),
                        remainingDuration =
                                json.optDouble("remainingDuration", route.durationSeconds),
                        distanceToNextTurn = json.optDouble("distanceToNextTurn", 0.0),
                        matchedIndex = json.optInt("matchedIndex", -1),
                        muted = json.optBoolean("muted", false)
                )
            }.getOrNull()

    fun clear(context: Context) {
        runCatching { file(context).delete() }
    }

    private fun file(context: Context) = File(context.filesDir, FILE_NAME)
}

private fun GeoSearchResult.toJson(): JSONObject =
        JSONObject()
                .put("name", name)
                .put("address", address)
                .put("lat", lat)
                .put("lng", lng)
                .put("type", type)

private fun JSONObject.toGeoSearchResult(): GeoSearchResult =
        GeoSearchResult(
                name = getString("name"),
                address = optString("address", ""),
                lat = getDouble("lat"),
                lng = getDouble("lng"),
                type = optString("type", "offline")
        )

private fun RouteResult.toJson(): JSONObject {
    val coords = JSONArray()
    coordinates.forEach { coords.put(JSONArray().put(it.first).put(it.second)) }
    val stepsJson = JSONArray()
    steps.forEach { step ->
        stepsJson.put(
                JSONObject()
                        .put("instruction", step.instruction)
                        .put("distance", step.distance)
                        .put("duration", step.duration)
                        .put("type", step.type)
                        .put("wayPointIndex", step.wayPointIndex)
                        .put("maneuver", step.maneuver.name)
        )
    }
    return JSONObject()
            .put("distanceMeters", distanceMeters)
            .put("durationSeconds", durationSeconds)
            .put("coordinates", coords)
            .put("instructions", JSONArray(instructions))
            .put("steps", stepsJson)
            .put("success", success)
}

private fun JSONObject.toRouteResult(): RouteResult {
    val coordsJson = getJSONArray("coordinates")
    val coordinates =
            (0 until coordsJson.length()).map {
                val pair = coordsJson.getJSONArray(it)
                Pair(pair.getDouble(0), pair.getDouble(1))
            }
    val instructionsJson = optJSONArray("instructions") ?: JSONArray()
    val instructions =
            (0 until instructionsJson.length()).map { instructionsJson.getString(it) }
    val stepsJson = optJSONArray("steps") ?: JSONArray()
    val steps =
            (0 until stepsJson.length()).map {
                val step = stepsJson.getJSONObject(it)
                NavigationStep(
                        instruction = step.optString("instruction", ""),
                        distance = step.optDouble("distance", 0.0),
                        duration = step.optDouble("duration", 0.0),
                        type = step.optInt("type", 0),
                        wayPointIndex = step.optInt("wayPointIndex", 0),
                        maneuver =
                                runCatching {
                                            ManeuverType.valueOf(
                                                    step.optString(
                                                            "maneuver",
                                                            ManeuverType.UNKNOWN.name
                                                    )
                                            )
                                        }
                                        .getOrDefault(ManeuverType.UNKNOWN)
                )
            }
    return RouteResult(
            distanceMeters = getDouble("distanceMeters"),
            durationSeconds = getDouble("durationSeconds"),
            coordinates = coordinates,
            instructions = instructions,
            steps = steps,
            success = optBoolean("success", true)
    )
}
