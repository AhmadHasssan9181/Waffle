package com.noobdev.waffle.data.remote

import com.noobdev.waffle.BuildConfig
import java.net.HttpURLConnection
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * A TomTom flow sample at one point. Speeds are km/h.
 *
 * `currentSpeed / freeFlowSpeed` is the useful number: 1.0 means traffic is moving at the road's
 * normal speed, 0.4 means it is crawling.
 */
data class TrafficFlow(
        val currentSpeedKmh: Int,
        val freeFlowSpeedKmh: Int,
        val confidence: Double,
        val roadClosure: Boolean
) {
    val ratio: Float
        get() =
                if (freeFlowSpeedKmh <= 0) 1f
                else (currentSpeedKmh.toFloat() / freeFlowSpeedKmh).coerceIn(0f, 1.5f)

    val isHeavy: Boolean
        get() = roadClosure || ratio < 0.5f

    val isSlow: Boolean
        get() = ratio < 0.8f
}

/**
 * TomTom Traffic Flow client. The key is optional: when it is absent the app simply has no live
 * traffic and falls back to GPS/route speed for the ETA. Never log the URL — it contains the key.
 */
object TomTomTrafficRepository {

    suspend fun flowAt(lat: Double, lon: Double): TrafficFlow? =
            withContext(Dispatchers.IO) {
                val key = BuildConfig.TOMTOM_API_KEY.trim()
                if (key.isBlank()) return@withContext null

                val url =
                        "https://api.tomtom.com/traffic/services/4/flowSegmentData/" +
                                "absolute/10/json?key=$key&point=$lat,$lon&unit=KMPH"
                val connection =
                        (URL(url).openConnection() as HttpURLConnection).apply {
                            connectTimeout = 6_000
                            readTimeout = 6_000
                            requestMethod = "GET"
                        }

                try {
                    if (connection.responseCode !in 200..299) return@withContext null
                    val body =
                            connection.inputStream.bufferedReader().use { it.readText() }
                    val flow =
                            JSONObject(body).optJSONObject("flowSegmentData")
                                    ?: return@withContext null
                    TrafficFlow(
                                    currentSpeedKmh = flow.optInt("currentSpeed", 0),
                                    freeFlowSpeedKmh = flow.optInt("freeFlowSpeed", 0),
                                    confidence = flow.optDouble("confidence", 0.0),
                                    roadClosure = flow.optBoolean("roadClosure", false)
                            )
                            .takeIf { it.currentSpeedKmh > 0 && it.freeFlowSpeedKmh > 0 }
                } catch (_: Exception) {
                    null
                } finally {
                    connection.disconnect()
                }
            }
}
