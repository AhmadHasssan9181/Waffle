package com.noobdev.waffle.feature.shops.data

import android.content.Context
import android.util.Log
import com.google.gson.JsonParser
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * Shop photos from Wikimedia Commons.
 *
 * OSM has almost no photos of its own. Instead of scraping Google (which the Maps terms forbid),
 * we ask Commons for geotagged images around the user and attach each one to the nearest shop
 * within ~70 m. Commons content is freely licensed; the author and licence are stored alongside
 * the URL and shown on the detail sheet, which is what the licence requires.
 */
class ShopPhotoRepository(context: Context) {

    private val dao = ShopDatabase.get(context).shopDao()

    private val client =
            OkHttpClient.Builder()
                    .connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(25, TimeUnit.SECONDS)
                    .build()

    private data class GeoPhoto(
            val title: String,
            val url: String,
            val credit: String?,
            val lat: Double,
            val lon: Double
    )

    /**
     * Returns [shops] with `photoUrl` filled where a nearby Commons image exists. Never throws:
     * a missing photo is a cosmetic loss, not a reason to fail the whole list.
     */
    suspend fun attachPhotos(
            shops: List<Shop>,
            centerLat: Double,
            centerLon: Double
    ): List<Shop> =
            withContext(Dispatchers.IO) {
                if (shops.isEmpty()) return@withContext shops
                if (shops.all { !it.photoUrl.isNullOrBlank() }) return@withContext shops

                val photos =
                        runCatching { fetchNearby(centerLat, centerLon) }
                                .onFailure { Log.w(TAG, "Commons lookup failed", it) }
                                .getOrDefault(emptyList())
                if (photos.isEmpty()) return@withContext shops

                val used = mutableSetOf<String>()
                val updates = mutableMapOf<String, Pair<String, String?>>()

                val updated =
                        shops.map { shop ->
                            if (!shop.photoUrl.isNullOrBlank()) return@map shop

                            val match =
                                    photos.asSequence()
                                            .filter { it.url !in used }
                                            .filter { photoMatchesShop(it.title, shop.name) }
                                            .map { photo ->
                                                photo to
                                                        distanceMeters(
                                                                shop.lat,
                                                                shop.lon,
                                                                photo.lat,
                                                                photo.lon
                                                        )
                                            }
                                            .filter { it.second <= MATCH_RADIUS_METERS }
                                            .minByOrNull { it.second }
                                            ?: return@map shop

                            used += match.first.url
                            updates[shop.id] = match.first.url to match.first.credit
                            shop.copy(
                                    photoUrl = match.first.url,
                                    photoCredit = match.first.credit
                            )
                        }

                updates.forEach { (id, value) -> runCatching { dao.setPhoto(id, value.first, value.second) } }
                updated
            }

    private fun fetchNearby(lat: Double, lon: Double): List<GeoPhoto> {
        val coord = URLEncoder.encode(String.format(Locale.US, "%.5f|%.5f", lat, lon), "UTF-8")
        val url =
                "https://commons.wikimedia.org/w/api.php" +
                        "?action=query&format=json&formatversion=2" +
                        "&generator=geosearch&ggscoord=$coord" +
                        "&ggsradius=$SEARCH_RADIUS_METERS&ggslimit=60&ggsnamespace=6" +
                        "&prop=coordinates%7Cimageinfo" +
                        "&iiprop=url%7Cmime%7Cextmetadata&iiurlwidth=520"

        val request =
                Request.Builder()
                        .url(url)
                        .header("User-Agent", USER_AGENT)
                        .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            val body = response.body?.string().orEmpty()
            if (body.isBlank()) return emptyList()

            val root = JsonParser.parseString(body).asJsonObject
            val pages = root.getAsJsonObject("query")?.getAsJsonArray("pages") ?: return emptyList()

            return pages.mapNotNull { page ->
                val obj = page.asJsonObject
                val title = obj.get("title")?.asString.orEmpty()
                if (!isPhotoTitle(title)) return@mapNotNull null

                val info =
                        obj.getAsJsonArray("imageinfo")?.firstOrNull()?.asJsonObject
                                ?: return@mapNotNull null
                val mime = info.get("mime")?.asString.orEmpty()
                if (!mime.startsWith("image/jpeg") &&
                                !mime.startsWith("image/png") &&
                                !mime.startsWith("image/webp")
                )
                        return@mapNotNull null

                val url =
                        info.get("thumburl")?.takeIf { !it.isJsonNull }?.asString
                                ?: info.get("url")?.asString
                                ?: return@mapNotNull null

                val coords =
                        obj.getAsJsonArray("coordinates")?.firstOrNull()?.asJsonObject
                                ?: return@mapNotNull null
                val latValue = coords.get("lat")?.asDouble ?: return@mapNotNull null
                val lonValue = coords.get("lon")?.asDouble ?: return@mapNotNull null

                GeoPhoto(
                        title = title,
                        url = url,
                        credit = credit(info.getAsJsonObject("extmetadata")),
                        lat = latValue,
                        lon = lonValue
                )
            }
        }
    }

    /** Commons titles that are maps, diagrams or logos make terrible shop photos. */
    private fun isPhotoTitle(title: String): Boolean =
            !NOISY_TITLE.containsMatchIn(title) && !title.endsWith(".svg", ignoreCase = true)

    /**
     * Prevents random street/monument photos from Wikimedia Commons from being attached to an
     * unrelated nearby shop purely because they are within 70 m.
     */
    private fun photoMatchesShop(fileTitle: String, shopName: String): Boolean {
        val cleanTitle =
                fileTitle
                        .removePrefix("File:")
                        .substringBeforeLast('.')
                        .lowercase(Locale.ROOT)
        val tokens =
                shopName.lowercase(Locale.ROOT)
                        .split(Regex("[^\\p{L}\\p{N}]+"))
                        .filter { it.length >= 4 && it !in PHOTO_STOPWORDS }
        if (tokens.isEmpty()) return false
        return tokens.any { cleanTitle.contains(it) }
    }

    private fun credit(metadata: com.google.gson.JsonObject?): String? {
        if (metadata == null) return "Wikimedia Commons"
        val artist =
                metadata.getAsJsonObject("Artist")
                        ?.get("value")
                        ?.asString
                        ?.replace(HTML_TAGS, "")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }
        val license =
                metadata.getAsJsonObject("LicenseShortName")
                        ?.get("value")
                        ?.asString
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

        return listOfNotNull(artist, license).joinToString(" · ").ifBlank { "Wikimedia Commons" }
    }

    private fun distanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
                sin(dLat / 2).pow(2) +
                        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2)
        return 2 * 6_371_000.0 * asin(min(1.0, sqrt(a)))
    }

    private companion object {
        const val TAG = "ShopPhotos"
        const val SEARCH_RADIUS_METERS = 1600
        const val MATCH_RADIUS_METERS = 70.0
        const val USER_AGENT = "Waffle/1.0 (offline-first disaster response app)"

        val HTML_TAGS = Regex("<[^>]*>")
        val NOISY_TITLE =
                Regex(
                        """(?i)\b(map|plan|diagram|chart|logo|flag|coat[ _]of[ _]arms|sign|screenshot|drawing|sketch)\b"""
                )
        val PHOTO_STOPWORDS =
                setOf(
                        "rawalpindi",
                        "islamabad",
                        "pakistan",
                        "lahore",
                        "karachi",
                        "peshawar",
                        "road",
                        "street",
                        "lane",
                        "main",
                        "shop",
                        "store",
                        "mart",
                        "mall",
                        "plaza",
                        "center",
                        "centre",
                        "market",
                        "branch",
                        "cantt",
                        "colony",
                        "town"
                )
    }
}
