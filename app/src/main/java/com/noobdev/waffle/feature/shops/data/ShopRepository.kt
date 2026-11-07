package com.noobdev.waffle.feature.shops.data

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import com.noobdev.waffle.data.remote.OfflineGeocoder
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Nearby shops and essential services from OpenStreetMap.
 *
 * Offline-first: results are cached in Room and served from the cache instantly. A network fetch
 * only happens when the box around the user has never been fetched, the cache is older than
 * [CACHE_TTL_MS], or the caller explicitly asks to refresh. If Overpass is down we still return the
 * cached rows rather than an error — during a disaster that is the whole point.
 */
class ShopRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = ShopDatabase.get(context).shopDao()

    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(35, TimeUnit.SECONDS)
                    .callTimeout(45, TimeUnit.SECONDS)
                    .build()

    suspend fun nearby(
            lat: Double,
            lon: Double,
            radiusMeters: Int = DEFAULT_RADIUS_METERS,
            forceRefresh: Boolean = false
    ): Result<List<Shop>> =
            withContext(Dispatchers.IO) {
                val box = boundingBox(lat, lon, radiusMeters)
                val cached = runCatching { dao.inBox(box.minLat, box.maxLat, box.minLon, box.maxLon) }
                        .getOrDefault(emptyList())

                if (!forceRefresh && cached.isNotEmpty() && isFresh(cached)) {
                    return@withContext Result.success(toSortedModels(cached, lat, lon, radiusMeters))
                }

                // The bundled places database is the offline safety net. Load it only when the
                // Room cache is empty; online requests still use Overpass and its richer tags.
                val offline =
                        if (cached.isEmpty()) {
                            runCatching { offlineShops(lat, lon, radiusMeters) }.getOrNull()
                        } else null

                val fetched =
                        runCatching { fetchFromOverpass(lat, lon, radiusMeters) }
                                .onFailure { Log.w(TAG, "Overpass fetch failed", it) }

                fetched
                        .map { entities ->
                            val now = System.currentTimeMillis()
                            val cachedById = cached.associateBy { it.id }
                            // Upsert replaces the whole row, so carry the downloaded photo fields
                            // forward explicitly; re-reading after the upsert is too late.
                            val stamped =
                                    entities.map { entity ->
                                        val previous = cachedById[entity.id]
                                        entity.copy(
                                                fetchedAt = now,
                                                photoUrl = entity.photoUrl ?: previous?.photoUrl,
                                                photoCredit =
                                                        entity.photoCredit ?: previous?.photoCredit
                                        )
                                    }
                            runCatching { dao.upsertAll(stamped) }
                            runCatching { dao.deleteOlderThan(now - RETAIN_MS) }
                            val merged =
                                    runCatching {
                                                dao.inBox(
                                                        box.minLat,
                                                        box.maxLat,
                                                        box.minLon,
                                                        box.maxLon
                                                )
                                            }
                                            .getOrDefault(stamped)
                            toSortedModels(merged, lat, lon, radiusMeters)
                        }
                        .recoverCatching { error ->
                            when {
                                cached.isNotEmpty() ->
                                        toSortedModels(cached, lat, lon, radiusMeters)
                                offline != null -> offline
                                else -> throw error
                            }
                        }
            }

    private fun isFresh(rows: List<ShopEntity>): Boolean {
        val newest = rows.maxOfOrNull { it.fetchedAt } ?: return false
        return System.currentTimeMillis() - newest < CACHE_TTL_MS
    }

    private fun toSortedModels(
            rows: List<ShopEntity>,
            lat: Double,
            lon: Double,
            radiusMeters: Int
    ): List<Shop> =
            rows.map { entity ->
                        entity.toModel(
                                distanceMeters =
                                        haversineMeters(
                                                lat,
                                                lon,
                                                entity.lat,
                                                entity.lon
                                        )
                                        .toInt()
                        )
                    }
                    .filter { it.distanceMeters <= radiusMeters * 1.2 }
                    .sortedBy { it.distanceMeters }

    private suspend fun offlineShops(
            lat: Double,
            lon: Double,
            radiusMeters: Int
    ): List<Shop> {
        OfflineGeocoder.load(appContext)
        return OfflineGeocoder.nearbyAmenities(lat, lon, radiusMeters, limit = 200)
                .map { result ->
                    Shop(
                            id = "offline:${result.type}:${result.lat}:${result.lng}",
                            name = result.name,
                            category = ShopCategory.fromOfflineType(result.type),
                            lat = result.lat,
                            lon = result.lng,
                            address = result.address.ifBlank { null },
                            distanceMeters =
                                    haversineMeters(lat, lon, result.lat, result.lng).toInt()
                    )
                }
                .filter { it.distanceMeters <= radiusMeters * 1.2 }
                .sortedBy { it.distanceMeters }
    }

    private fun fetchFromOverpass(
            lat: Double,
            lon: Double,
            radiusMeters: Int
    ): List<ShopEntity> {
        val query =
                String.format(
                        Locale.US,
                        """
                [out:json][timeout:25];
                (
                  nwr["shop"](around:%d,%.5f,%.5f);
                  nwr["amenity"~"^(restaurant|cafe|fast_food|food_court|pharmacy|hospital|clinic|doctors|dentist|bank|atm|fuel|marketplace|police|fire_station|post_office|bus_station)$"](around:%d,%.5f,%.5f);
                  nwr["tourism"~"^(hotel|guest_house|hostel)$"](around:%d,%.5f,%.5f);
                );
                out center tags 150;
                """
                                .trimIndent(),
                        radiusMeters,
                        lat,
                        lon,
                        radiusMeters,
                        lat,
                        lon,
                        radiusMeters,
                        lat,
                        lon
                )

        var lastError: Throwable? = null
        for (endpoint in ENDPOINTS) {
            try {
                val request =
                        Request.Builder()
                                .url(endpoint)
                                .header("User-Agent", USER_AGENT)
                                .post(
                                        query.toRequestBody(
                                                "application/x-www-form-urlencoded".toMediaType()
                                        )
                                )
                                .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        lastError = IllegalStateException("Overpass ${response.code}")
                        return@use
                    }
                    val body = response.body?.string().orEmpty()
                    return parse(body)
                }
            } catch (t: Throwable) {
                lastError = t
            }
        }
        throw lastError ?: IllegalStateException("No Overpass endpoint reachable")
    }

    private fun parse(body: String): List<ShopEntity> {
        if (body.isBlank()) return emptyList()
        val root = JsonParser.parseString(body).asJsonObject
        val elements = root.getAsJsonArray("elements") ?: return emptyList()
        val now = System.currentTimeMillis()

        return elements.mapNotNull { element ->
            val obj = element.asJsonObject
            val tags = mutableMapOf<String, String>()
            obj.getAsJsonObject("tags")?.entrySet()?.forEach { (key, value) ->
                if (!value.isJsonNull) tags[key] = value.asString
            }
            // Unnamed points are noise on a list; the category already tells us what they are.
            val name = tags["name"] ?: tags["brand"] ?: return@mapNotNull null

            val type = obj.get("type")?.asString ?: "node"
            val id = obj.get("id")?.asLong ?: return@mapNotNull null
            val lat =
                    obj.get("lat")?.takeIf { !it.isJsonNull }?.asDouble
                            ?: obj.getAsJsonObject("center")?.get("lat")?.asDouble
                            ?: return@mapNotNull null
            val lon =
                    obj.get("lon")?.takeIf { !it.isJsonNull }?.asDouble
                            ?: obj.getAsJsonObject("center")?.get("lon")?.asDouble
                            ?: return@mapNotNull null

            val category = ShopCategory.fromTags(tags)
            ShopEntity(
                    id = "$type/$id",
                    name = name,
                    category = category.name,
                    lat = lat,
                    lon = lon,
                    address = formatAddress(tags),
                    openingHours = tags["opening_hours"],
                    phone = tags["phone"] ?: tags["contact:phone"],
                    website = tags["website"] ?: tags["contact:website"],
                    brand = tags["brand"],
                    cuisine = tags["cuisine"],
                    fetchedAt = now
            )
        }
    }

    private fun formatAddress(tags: Map<String, String>): String? {
        val street = tags["addr:street"]
        val number = tags["addr:housenumber"]
        val suburb = tags["addr:suburb"] ?: tags["addr:district"]
        val city = tags["addr:city"]

        val line =
                listOfNotNull(
                                listOfNotNull(street, number).joinToString(" ").ifBlank { null },
                                suburb ?: city
                        )
                        .joinToString(", ")
        return line.ifBlank { null }
    }

    private data class Box(
            val minLat: Double,
            val maxLat: Double,
            val minLon: Double,
            val maxLon: Double
    )

    private fun boundingBox(lat: Double, lon: Double, radiusMeters: Int): Box {
        val latDelta = radiusMeters / 111_320.0
        val lonDelta = radiusMeters / (111_320.0 * cos(Math.toRadians(lat)).coerceAtLeast(0.05))
        return Box(lat - latDelta, lat + latDelta, lon - lonDelta, lon + lonDelta)
    }

    private fun haversineMeters(
            lat1: Double,
            lon1: Double,
            lat2: Double,
            lon2: Double
    ): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
                sin(dLat / 2).pow(2) +
                        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
    }

    companion object {
        const val DEFAULT_RADIUS_METERS = 1500

        private const val TAG = "ShopRepository"
        private const val EARTH_RADIUS_M = 6_371_000.0
        private const val CACHE_TTL_MS = 14L * 24 * 60 * 60 * 1000 // 14 days
        private const val RETAIN_MS = 60L * 24 * 60 * 60 * 1000 // 60 days
        private const val USER_AGENT = "Waffle/1.0 (offline-first disaster response app)"

        private val ENDPOINTS =
                listOf(
                        "https://overpass-api.de/api/interpreter",
                        "https://overpass.kumi.systems/api/interpreter"
                )
    }
}


