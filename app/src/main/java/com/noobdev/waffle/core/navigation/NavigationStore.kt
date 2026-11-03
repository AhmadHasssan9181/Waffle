package com.noobdev.waffle.core.navigation

import android.content.Context
import com.noobdev.waffle.data.remote.GeoSearchResult
import com.noobdev.waffle.data.remote.NavigationStep
import com.noobdev.waffle.data.remote.RouteResult
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists the route, destination, stops and mute state so a process restart can resume the trip.
 * Live position/ETA are intentionally not saved; the controller gets a fresh GPS fix.
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
                            .put("muted", snapshot.muted)
                            .put("destination", destination.toJson())
                            .put("stops", JSONArray(snapshot.stops.map { it.toJson() }))
                            .put("route", route.toJson())
            file(context).writeText(json.toString())
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
                        stops = stops,
                        muted = json.optBoolean("muted", false),
                        remainingDistance = route.distanceMeters,
                        remainingDuration = route.durationSeconds
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
