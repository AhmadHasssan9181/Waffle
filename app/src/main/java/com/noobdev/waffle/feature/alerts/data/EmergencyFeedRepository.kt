package com.noobdev.waffle.feature.alerts.data

import android.util.Log
import com.noobdev.waffle.data.local.AlertDao
import com.noobdev.waffle.data.local.toEntity
import com.noobdev.waffle.data.model.StatusLevel
import com.noobdev.waffle.data.model.DisasterAlert
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

/**
 * Pulls live disaster data from public feeds that do not need an API key:
 *
 *  - USGS earthquakes (GeoJSON, past 24h, magnitude 2.5+)
 *  - GDACS RSS (earthquakes, floods, cyclones, wildfires, volcanoes, droughts)
 *
 * The backend (Firestore) stays the primary source when it is reachable. This exists so a phone
 * with a data connection but no backend, or a user outside the curated feed, still receives
 * warnings. Everything is cached in Room, so the alert list keeps working offline.
 */
class EmergencyFeedRepository {

    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(20, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .callTimeout(45, TimeUnit.SECONDS)
                    .build()

    data class SyncResult(
            val fetched: List<DisasterAlert>,
            val newAlerts: List<DisasterAlert>,
            val errors: List<String>
    )

    suspend fun sync(alertDao: AlertDao): SyncResult = withContext(Dispatchers.IO) {
        val errors = mutableListOf<String>()
        val fetched = mutableListOf<DisasterAlert>()

        runCatching { fetchUsgsEarthquakes() }
                .onSuccess { fetched.addAll(it) }
                .onFailure {
                    Log.w(TAG, "USGS feed failed", it)
                    errors.add("USGS: ${it.message ?: "unavailable"}")
                }

        runCatching { fetchGdacs() }
                .onSuccess { fetched.addAll(it) }
                .onFailure {
                    Log.w(TAG, "GDACS feed failed", it)
                    errors.add("GDACS: ${it.message ?: "unavailable"}")
                }

        val unique = fetched.distinctBy { it.id }
        val newAlerts = mutableListOf<DisasterAlert>()
        for (alert in unique) {
            val id = alert.id ?: continue
            if (alertDao.getAlertById(id) == null) {
                newAlerts.add(alert)
            }
        }

        val entities = unique.mapNotNull { it.toEntity() }
        if (entities.isNotEmpty()) {
            alertDao.insertAll(entities)
        }

        // Keep the cache to a two-week window: feeds are snapshots, and old rows (including
        // legacy demo data) would otherwise sit in "near you" forever.
        val cutoff =
                isoFormat().format(Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(14)))
        val pruned = alertDao.deleteOlderThan(cutoff)
        if (pruned > 0) Log.d(TAG, "Pruned $pruned alerts older than $cutoff")

        Log.d(TAG, "Feed sync: ${unique.size} alerts, ${newAlerts.size} new")
        SyncResult(fetched = unique, newAlerts = newAlerts, errors = errors)
    }

    private fun fetchUsgsEarthquakes(): List<DisasterAlert> {
        val request =
                Request.Builder()
                        .url(USGS_URL)
                        .header("User-Agent", "Waffle/1.0 (offline disaster resilience app)")
                        .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("USGS HTTP ${response.code}")
            }
            val body = response.body?.string() ?: error("USGS returned an empty body")
            return parseUsgs(body)
        }
    }

    private fun parseUsgs(json: String): List<DisasterAlert> {
        val root = JSONObject(json)
        val features = root.optJSONArray("features") ?: return emptyList()
        val alerts = ArrayList<DisasterAlert>(features.length())

        for (index in 0 until features.length()) {
            val feature = features.optJSONObject(index) ?: continue
            val properties = feature.optJSONObject("properties") ?: continue
            val geometry = feature.optJSONObject("geometry") ?: continue
            val coordinates = geometry.optJSONArray("coordinates") ?: continue
            if (coordinates.length() < 2) continue

            val magnitude = properties.optDouble("mag", Double.NaN)
            if (!magnitude.isFinite()) continue

            val longitude = coordinates.optDouble(0, Double.NaN)
            val latitude = coordinates.optDouble(1, Double.NaN)
            if (!latitude.isFinite() || !longitude.isFinite()) continue

            val place = properties.optString("place", "Unknown location")
            val eventId = feature.optString("id", "").ifBlank { "$latitude,$longitude" }
            val alertLevel = properties.optString("alert", "")
            val tsunami = properties.optInt("tsunami", 0)
            val timeMillis = properties.optLong("time", 0L)

            val status =
                    when (alertLevel.lowercase(Locale.US)) {
                        "red", "orange" -> StatusLevel.CRITICAL
                        "yellow" -> StatusLevel.WARNING
                        else ->
                                when {
                                    magnitude >= 6.0 -> StatusLevel.CRITICAL
                                    magnitude >= 4.5 -> StatusLevel.WARNING
                                    else -> StatusLevel.INFO
                                }
                    }

            val details =
                    mutableMapOf<String, Any>(
                            "source" to "USGS",
                            "magnitude" to magnitude,
                            "depth_km" to coordinates.optDouble(2, 0.0),
                            "url" to properties.optString("url", ""),
                            "raw_alert_level" to alertLevel,
                            "tsunami" to tsunami
                    )
            if (tsunami == 1) details["tsunami_warning"] = true

            alerts.add(
                    DisasterAlert(
                            id = "usgs:$eventId",
                            title = properties.optString(
                                            "title",
                                            "M%.1f earthquake".format(Locale.US, magnitude)
                                    ),
                            location_name = place,
                            type = "earthquake",
                            status = status,
                            timestamp = epochMillisToIso(timeMillis),
                            latitude = latitude,
                            longitude = longitude,
                            details = details
                    )
            )
        }
        return alerts
    }

    private fun fetchGdacs(): List<DisasterAlert> {
        val request =
                Request.Builder()
                        .url(GDACS_URL)
                        .header("User-Agent", "Waffle/1.0 (offline disaster resilience app)")
                        .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                error("GDACS HTTP ${response.code}")
            }
            val body = response.body?.string() ?: error("GDACS returned an empty body")
            return parseGdacs(body)
        }
    }

    private fun parseGdacs(xml: String): List<DisasterAlert> {
        val factory = XmlPullParserFactory.newInstance().apply { isNamespaceAware = false }
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xml))

        val alerts = mutableListOf<DisasterAlert>()
        var inItem = false
        var activeField: String? = null
        val text = StringBuilder()

        var title = ""
        var description = ""
        var link = ""
        var pubDate = ""
        var point = ""
        var eventType = ""
        var alertLevel = ""
        var eventId = ""
        var country = ""

        fun resetItem() {
            title = ""
            description = ""
            link = ""
            pubDate = ""
            point = ""
            eventType = ""
            alertLevel = ""
            eventId = ""
            country = ""
            activeField = null
            text.setLength(0)
        }

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    val local = parser.name.substringAfter(':').lowercase(Locale.US)
                    if (local == "item") {
                        resetItem()
                        inItem = true
                    } else if (inItem) {
                        when (local) {
                            "title",
                            "description",
                            "link",
                            "pubdate",
                            "point",
                            "eventtype",
                            "alertlevel",
                            "eventid",
                            "country" -> {
                                activeField = local
                                text.setLength(0)
                            }
                        }
                    }
                }
                XmlPullParser.TEXT -> {
                    if (inItem && activeField != null) text.append(parser.text)
                }
                XmlPullParser.END_TAG -> {
                    val local = parser.name.substringAfter(':').lowercase(Locale.US)
                    if (activeField == local) {
                        val value = text.toString().trim()
                        when (local) {
                            "title" -> title = value
                            "description" -> description = value
                            "link" -> link = value
                            "pubdate" -> pubDate = value
                            "point" -> point = value
                            "eventtype" -> eventType = value
                            "alertlevel" -> alertLevel = value
                            "eventid" -> eventId = value
                            "country" -> country = value
                        }
                        activeField = null
                    } else if (local == "item" && inItem) {
                        buildGdacsAlert(
                                        title,
                                        description,
                                        link,
                                        pubDate,
                                        point,
                                        eventType,
                                        alertLevel,
                                        eventId,
                                        country
                                )
                                ?.let { alerts.add(it) }
                        inItem = false
                    }
                }
            }
            event = parser.next()
        }

        return alerts
    }

    private fun buildGdacsAlert(
            title: String,
            description: String,
            link: String,
            pubDate: String,
            point: String,
            eventType: String,
            alertLevel: String,
            eventId: String,
            country: String
    ): DisasterAlert? {
        if (title.isBlank() || point.isBlank()) return null

        val coordinates = point.split(Regex("\\s+"))
        val latitude = coordinates.getOrNull(0)?.toDoubleOrNull() ?: return null
        val longitude = coordinates.getOrNull(1)?.toDoubleOrNull() ?: return null

        val normalizedType = mapGdacsType(eventType)
        val status =
                when (alertLevel.lowercase(Locale.US)) {
                    "red" -> StatusLevel.CRITICAL
                    "orange" -> StatusLevel.WARNING
                    "green" -> StatusLevel.INFO
                    else -> StatusLevel.INFO
                }

        // GDACS publishes hundreds of "green" wildfire and drought notifications — small fires and
        // seasonal dryness that would bury real emergencies in the list. Keep only assessed events.
        if (status == StatusLevel.INFO && (normalizedType == "wildfire" || normalizedType == "drought")) {
            return null
        }

        val magnitude =
                Regex("M\\s*([0-9]+(?:\\.[0-9]+)?)")
                        .find(title)
                        ?.groupValues
                        ?.getOrNull(1)
                        ?.toDoubleOrNull()

        val details =
                mutableMapOf<String, Any>(
                        "source" to "GDACS",
                        "raw_event_type" to eventType,
                        "raw_alert_level" to alertLevel,
                        "country" to country,
                        "description" to cleanText(description).take(700),
                        "url" to link
                )
        if (magnitude != null) details["magnitude"] = magnitude

        val id =
                if (eventId.isNotBlank()) "gdacs:${eventType.uppercase(Locale.US)}:$eventId"
                else "gdacs:${title.hashCode()}:${pubDate.hashCode()}"

        return DisasterAlert(
                id = id,
                title = cleanText(title),
                location_name = country.ifBlank { cleanText(title) },
                type = normalizedType,
                status = status,
                timestamp = parseRfc822ToIso(pubDate),
                latitude = latitude,
                longitude = longitude,
                details = details
        )
    }

    private fun mapGdacsType(code: String): String =
            when (code.uppercase(Locale.US)) {
                "EQ" -> "earthquake"
                "TC", "TS" -> "storm"
                "FL" -> "flood"
                "WF" -> "wildfire"
                "VO" -> "volcano"
                "DR" -> "drought"
                "LS" -> "landslide"
                else -> code.lowercase(Locale.US).ifBlank { "alert" }
            }

    private fun cleanText(value: String): String =
            value.replace(Regex("<[^>]+>"), " ")
                    .replace(Regex("\\s+"), " ")
                    .trim()

    private fun epochMillisToIso(millis: Long): String? {
        if (millis <= 0L) return null
        return isoFormat().format(Date(millis))
    }

    private fun parseRfc822ToIso(value: String): String? {
        if (value.isBlank()) return null
        val patterns =
                listOf(
                        "EEE, dd MMM yyyy HH:mm:ss zzz",
                        "EEE, dd MMM yyyy HH:mm:ss Z",
                        "yyyy-MM-dd'T'HH:mm:ss"
                )
        for (pattern in patterns) {
            val parsed =
                    runCatching {
                                SimpleDateFormat(pattern, Locale.US)
                                        .apply { timeZone = TimeZone.getTimeZone("UTC") }
                                        .parse(value)
                            }
                            .getOrNull()
            if (parsed != null) return isoFormat().format(parsed)
        }
        return null
    }

    private fun isoFormat(): SimpleDateFormat =
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }

    companion object {
        private const val TAG = "EmergencyFeed"
        private const val USGS_URL =
                "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/2.5_day.geojson"
        private const val GDACS_URL = "https://www.gdacs.org/xml/rss.xml"
    }
}
