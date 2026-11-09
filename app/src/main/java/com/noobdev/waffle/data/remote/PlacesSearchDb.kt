package com.noobdev.waffle.data.remote

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import java.io.File
import java.util.Locale

/**
 * Read-only FTS5 index generated from the Overture places dataset. The file is kept in app
 * external storage because it is ~100 MB; it is never loaded into RAM.
 */
object PlacesSearchDb {
    private const val TAG = "PlacesSearchDb"
    private var database: SQLiteDatabase? = null
    private var checked = false
    private var ftsAvailable: Boolean? = null

    fun search(
            context: Context,
            query: String,
            limit: Int = 15,
            latitude: Double? = null,
            longitude: Double? = null
    ): List<GeoSearchResult> {
        val db = open(context) ?: return emptyList()
        val manual = ManualPlacesStore.search(context, query)
        val tokens =
                query.lowercase(Locale.ROOT)
                        .split(Regex("[^\\p{L}\\p{N}]+"))
                        .filter { it.length >= 2 }
        if (tokens.isEmpty()) return emptyList()
        if (ftsAvailable != false) {
            val match = tokens.joinToString(" ") { "${it.replace("\"", "")}*" }
            val fts =
                    runCatching {
                                db.rawQuery(
                                                """
                                                SELECT p.name, p.cat, p.bcat, p.lon, p.lat, p.addr, p.phone, p.website
                                                FROM places_fts f JOIN places p ON p.id = f.rowid
                                                WHERE places_fts MATCH ?
                                                ORDER BY rank
                                                LIMIT ?
                                                """.trimIndent(),
                                                arrayOf(match, limit.toString())
                                        )
                                        .use { readResults(it, fallbackAddress = true) }
                            }
                            .onFailure {
                                // Android's bundled SQLite does not always include FTS5.
                                // Fall back to indexed/contains search below.
                                ftsAvailable = false
                            }
                            .getOrNull()
            if (fts != null) {
                ftsAvailable = true
                if (fts.isNotEmpty()) {
                    return (manual + sortedByDistance(fts, latitude, longitude))
                            .distinctBy { "${it.name.lowercase()}|${it.lat}|${it.lng}" }
                            .take(limit)
                }
            }
        }

        // Prefix search is fast and covers normal place names. Query one token group at a time
        // (and its common aliases), filter the small candidate set in memory, and stop as soon
        // as it contains a full match. This avoids both the full 800k-row LIKE '%token%' scan
        // and five prefix queries for a three-word query on Android's non-FTS5 SQLite.
        val groups = tokens.map(::synonyms)
        val prefixCandidates = mutableListOf<GeoSearchResult>()
        val queriedPrefixes = mutableSetOf<String>()
        for (group in groups) {
            // Two aliases are enough to cover the useful cases (Rawalpindi/Pindi, KFC/
            // Kentucky Fried Chicken) without paying for the third redundant prefix.
            for (prefix in group.take(2)) {
                val safe = prefix.replace("%", "").replace("_", "")
                if (!queriedPrefixes.add(safe)) continue
                prefixCandidates +=
                        runCatching {
                                    db.rawQuery(
                                                    """
                                                    SELECT name, cat, bcat, lon, lat, addr, phone, website
                                                    FROM places
                                                    WHERE name LIKE ?
                                                    ORDER BY name
                                                    LIMIT 300
                                                    """.trimIndent(),
                                            arrayOf("$safe%")
                                    )
                                            .use { readResults(it, fallbackAddress = true) }
                                }
                                .getOrDefault(emptyList())
            }
            // Filter after the group so "pindi cricket stadium" includes both
            // Pindi Cricket Stadium and Rawalpindi Cricket Stadium.
            val prefixMatches =
                    prefixCandidates
                            .distinctBy { "${it.name.lowercase()}|${it.lat}|${it.lng}" }
                            .filter { result ->
                                val blob =
                                        "${result.name} ${result.address} ${result.type}"
                                                .lowercase(Locale.ROOT)
                                groups.all { tokenGroup ->
                                    tokenGroup.any { blob.contains(it) }
                                }
                            }
            if (prefixMatches.isNotEmpty()) {
                return (manual + sortedByDistance(prefixMatches, latitude, longitude))
                        .distinctBy { "${it.name.lowercase()}|${it.lat}|${it.lng}" }
                        .take(limit)
            }
        }

        val where = groups.joinToString(" AND ") { group ->
            "(" +
                    group.joinToString(" OR ") {
                        "(name LIKE ? OR addr LIKE ?)"
                    } +
                    ")"
        }
        val args =
                groups
                        .flatMap { group ->
                            group.flatMap { token -> listOf("%$token%", "%$token%") }
                        }
                        .toTypedArray()
        return runCatching {
                    db.rawQuery(
                                    """
                                    SELECT name, cat, bcat, lon, lat, addr, phone, website
                                    FROM places
                                    WHERE $where
                                    ORDER BY length(name)
                                    LIMIT 300
                                    """.trimIndent(),
                                    args
                            )
                            .use { readResults(it, fallbackAddress = true) }
                }
                .onFailure { Log.w(TAG, "Contains search failed", it) }
                .getOrDefault(emptyList())
                .let {
                    (manual + sortedByDistance(it, latitude, longitude))
                            .distinctBy { result -> "${result.name.lowercase()}|${result.lat}|${result.lng}" }
                            .take(limit)
                }
    }

    private fun synonyms(token: String): List<String> =
            when (token) {
                "rawalpindi", "rwp", "pindi" ->
                        listOf("rawalpindi", "pindi", "rwp")
                "islamabad", "isb" ->
                        listOf("islamabad", "isb")
                "kfc" ->
                        listOf("kfc", "kentucky fried chicken")
                else -> listOf(token)
            }

    private fun sortedByDistance(
            results: List<GeoSearchResult>,
            latitude: Double?,
            longitude: Double?
    ): List<GeoSearchResult> =
            if (latitude != null && longitude != null) {
                results.sortedBy { haversineMeters(latitude, longitude, it.lat, it.lng) }
            } else results

    fun nearby(
            context: Context,
            latitude: Double,
            longitude: Double,
            radiusMeters: Int,
            limit: Int = 160
    ): List<GeoSearchResult> {
        val db = open(context) ?: return emptyList()
        val latDelta = radiusMeters / 111_320.0
        val lonDelta =
                radiusMeters /
                        (111_320.0 * kotlin.math.cos(Math.toRadians(latitude)).coerceAtLeast(0.05))
        val candidates =
                runCatching {
                            db.rawQuery(
                                            """
                                            SELECT name, cat, bcat, lon, lat, addr, phone, website
                                            FROM places
                                            WHERE lat BETWEEN ? AND ?
                                              AND lon BETWEEN ? AND ?
                                            ORDER BY ((lat - ?) * (lat - ?) + (lon - ?) * (lon - ?)) ASC
                                            LIMIT 2500
                                            """.trimIndent(),
                                            arrayOf(
                                                    (latitude - latDelta).toString(),
                                                    (latitude + latDelta).toString(),
                                                    (longitude - lonDelta).toString(),
                                                    (longitude + lonDelta).toString(),
                                                    latitude.toString(),
                                                    latitude.toString(),
                                                    longitude.toString(),
                                                    longitude.toString()
                                            )
                                    )
                                    .use { readResults(it, fallbackAddress = false) }
                        }
                        .getOrDefault(emptyList())
                        .filter { place ->
                            !isExcludedNearbyPlace(place.name, place.type) &&
                                    !isMismatchedAddress(place.address, place.lat, place.lng)
                        }
        val manual = ManualPlacesStore.nearby(context, latitude, longitude, radiusMeters, limit)
        val sorted =
                (manual + candidates)
                        .distinctBy { "${it.name.lowercase()}|${it.lat}|${it.lng}" }
                        .map { it to haversineMeters(latitude, longitude, it.lat, it.lng) }
                        .filter { it.second <= radiusMeters * 1.2 }
                        .sortedBy { it.second }

        val deduped = mutableListOf<Pair<GeoSearchResult, Double>>()
        for ((place, dist) in sorted) {
            val norm = normalizeForDedup(place.name)
            val existingIdx =
                    deduped.indexOfFirst { (other, _) ->
                        val otherNorm = normalizeForDedup(other.name)
                        val sameName =
                                norm == otherNorm ||
                                        norm == "${otherNorm}s" ||
                                        otherNorm == "${norm}s" ||
                                        (norm.length >= 9 &&
                                                otherNorm.length >= 9 &&
                                                (norm.startsWith(otherNorm) ||
                                                        otherNorm.startsWith(norm)))
                        sameName && haversineMeters(place.lat, place.lng, other.lat, other.lng) < 90.0
                    }
            if (existingIdx >= 0) {
                val (existing, existingDist) = deduped[existingIdx]
                if (existing.address.isBlank() && place.address.isNotBlank()) {
                    deduped[existingIdx] = place.copy(lat = existing.lat, lng = existing.lng) to existingDist
                }
                continue
            }
            deduped.add(place to dist)
            if (deduped.size >= limit) break
        }
        return deduped.map { it.first }
    }

    private fun normalizeForDedup(name: String): String =
            name.lowercase(Locale.ROOT)
                    .replace("شیل", "shell")
                    .replace(Regex("[^a-z0-9\\u0600-\\u06FF]+"), "")

    private val EXCLUDED_NEARBY_TYPES =
            setOf(
                    "place",
                    "locality",
                    "road",
                    "building",
                    "apartment",
                    "military_site",
                    "historic_site",
                    "public_plaza",
                    "grave_yard",
                    "cemetery",
                    "park",
                    "pitch",
                    "playground",
                    "fishing",
                    "viewpoint",
                    "attraction",
                    "mountain",
                    "school",
                    "elementary_school",
                    "high_school",
                    "middle_school",
                    "preschool",
                    "day_care_preschool",
                    "religious_school",
                    "test_preparation",
                    "college",
                    "college_university",
                    "university",
                    "education",
                    "educational_service",
                    "educational_facility",
                    "educational_camp",
                    "educational_institution",
                    "place_of_learning",
                    "tutoring_service",
                    "specialty_school",
                    "place_of_worship",
                    "muslim_place_of_worship",
                    "hindu_place_of_worship",
                    "christian_place_of_worship",
                    "sikh_place_of_worship",
                    "religious_organization",
                    "government_office",
                    "government",
                    "police",
                    "fire_station",
                    "community_centre",
                    "social_or_community_service",
                    "senior_citizen_service",
                    "non_governmental_association",
                    "political_organization",
                    "non_profit_organization",
                    "corporate_or_business_office",
                    "company",
                    "b2b_service",
                    "real_estate_service",
                    "real_estate_agent",
                    "estate_agent",
                    "property_management",
                    "building_or_construction_service",
                    "supplier_or_distributor",
                    "manufacturer",
                    "industrial_equipment_manufacturer",
                    "software_development",
                    "information_technology_company",
                    "party_and_event_planning",
                    "event_or_party_service",
                    "events_venue",
                    "sport_league",
                    "sports_and_recreation",
                    "martial_arts_club",
                    "bus_station",
                    "public_transit_facility_or_service"
            )

    private val COLONY_OR_LOCALITY_NAME =
            Regex(
                    """(?i)\b(colony|abad|town|phase\s*\d|sector\s*[a-z0-9]|block\s*[a-z0-9]|mohalla|dhok|dhoke|cantt|enclave|housing|society|village|road|street|lane)(\s+rawalpindi|\s+islamabad)?$"""
            )
    private val NON_SHOP_NAME =
            Regex(
                    """(?i)\b(banquet|marriage\s*hall|shadi\s*hall|marquee|wedding\s*hall|tent\s*service|high\s*school|public\s*school|grammar\s*school|school\s*system|school\s+and\s+college|school\s+and\s+daycare|college|university|masjid|mosque|imam\s*bargah|madrassa|madrasa|church|mandir|temple|army|airbase|aviation\s*base|graveyard|qabristan|real\s*estate|properties|property\s*consultant|developers|builders|construction|advocates|barristers|law\s*associates|welfare\s*trust|welfare\s*organization)\b"""
            )
    private val REAL_STAY_NAME =
            Regex(
                    """(?i)\b(hotel|guest\s*house|inn|motel|resort|hostel|lodge|suites|rooms|continental|serena|marriott|ramada|avari|shelton|residency)\b"""
            )

    private fun isExcludedNearbyPlace(name: String, type: String): Boolean {
        val cleanName = name.trim()
        if (cleanName.length < 2) return true
        val parts = type.lowercase(Locale.ROOT).split('|').map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.any { it in EXCLUDED_NEARBY_TYPES || it.startsWith("b2b_") }) return true
        if (COLONY_OR_LOCALITY_NAME.containsMatchIn(cleanName)) return true
        if (NON_SHOP_NAME.containsMatchIn(cleanName) &&
                        !cleanName.contains("salon", ignoreCase = true) &&
                        !cleanName.contains("saloon", ignoreCase = true) &&
                        !cleanName.contains("parlor", ignoreCase = true) &&
                        !cleanName.contains("hospital", ignoreCase = true) &&
                        !cleanName.contains("pharmacy", ignoreCase = true)
        ) {
            return true
        }
        if (parts.any { it == "lodging" } && !REAL_STAY_NAME.containsMatchIn(cleanName)) {
            return true
        }
        return false
    }

    private data class AreaAnchor(
            val pattern: Regex,
            val lat: Double,
            val lon: Double,
            val maxMeters: Double
    )

    private val KNOWN_AREAS =
            listOf(
                    // Major cities across Pakistan
                    AreaAnchor(
                            Regex("""(?i)\b(clifton|karachi|gulshan-e-iqbal\s+karachi|north\s+nazimabad|korangi|malir|pechs|tariq\s+road|saddar\s+karachi|dha\s+karachi)\b"""),
                            24.8607,
                            67.0011,
                            45_000.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(lahore|gulberg\s+iii|johar\s+town|model\s+town\s+lahore|sabzazar|raiwind|allama\s+iqbal\s+town|wapda\s+town\s+lahore|dha\s+lahore|cantt\s+lahore)\b"""),
                            31.5204,
                            74.3587,
                            45_000.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(faisalabad|d\s+ground|peoples\s+colony\s+faisalabad|madina\s+town\s+faisalabad|samanabad\s+faisalabad)\b"""),
                            31.4187,
                            73.0791,
                            35_000.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(multan|gulgasht|cantt\s+multan|bosan\s+road)\b"""),
                            30.1575,
                            71.5249,
                            35_000.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(peshawar\s+cantt|hayatabad|university\s+road\s+peshawar|warsak\s+road|ring\s+road\s+peshawar)\b"""),
                            34.0151,
                            71.5249,
                            30_000.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(quetta|jinnah\s+road\s+quetta|sariab\s+road)\b"""),
                            30.1798,
                            66.9750,
                            30_000.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(gujranwala|sialkot|hyderabad\s+sindh|latifabad|qasimabad)\b"""),
                            25.3960,
                            68.3578,
                            900_000.0 // Handled individually below
                    ),
                    AreaAnchor(Regex("""(?i)\b(gujranwala)\b"""), 32.1877, 74.1945, 30_000.0),
                    AreaAnchor(Regex("""(?i)\b(sialkot)\b"""), 32.4945, 74.5229, 30_000.0),
                    AreaAnchor(Regex("""(?i)\b(latifabad|qasimabad|hyderabad)\b"""), 25.3960, 68.3578, 30_000.0),
                    AreaAnchor(Regex("""(?i)\b(sukkur)\b"""), 27.7052, 68.8574, 30_000.0),
                    AreaAnchor(Regex("""(?i)\b(bahawalpur)\b"""), 29.3544, 71.6911, 30_000.0),
                    AreaAnchor(Regex("""(?i)\b(sargodha)\b"""), 32.0836, 72.6711, 30_000.0),
                    AreaAnchor(Regex("""(?i)\b(sahiwal)\b"""), 30.6682, 73.1114, 30_000.0),
                    AreaAnchor(Regex("""(?i)\b(abbottabad)\b"""), 34.1688, 73.2215, 25_000.0),
                    AreaAnchor(Regex("""(?i)\b(mardan)\b"""), 34.1986, 72.0404, 25_000.0),
                    AreaAnchor(Regex("""(?i)\b(kohat)\b"""), 33.5889, 71.4429, 30_000.0),
                    AreaAnchor(Regex("""(?i)\b(gujrat)\b"""), 32.5731, 74.0750, 25_000.0),
                    AreaAnchor(Regex("""(?i)\b(jhelum)\b"""), 32.9425, 73.7257, 25_000.0),
                    AreaAnchor(Regex("""(?i)\b(muzaffarabad)\b"""), 34.3700, 73.4711, 25_000.0),
                    AreaAnchor(Regex("""(?i)\b(gilgit)\b"""), 35.9208, 74.3080, 30_000.0),
                    AreaAnchor(Regex("""(?i)\b(skardu)\b"""), 35.2971, 75.6333, 30_000.0),
                    // Intra-city sectors (Rawalpindi / Islamabad / Lahore / Karachi)
                    AreaAnchor(
                            Regex("""(?i)\b(bahria\s+town\s+phase\s*[78]|bahria\s+phase\s*[78]|bahria\s+hamlet)\b"""),
                            33.5220,
                            73.0950,
                            3_500.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(bahria\s+town.*rawalpindi|bahria\s+town.*islamabad|bahria\s+phase\s*[1-6])\b"""),
                            33.5350,
                            73.1150,
                            5_000.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(dha\s*0?2|dha\s+phase\s*2.*islamabad)\b"""),
                            33.5320,
                            73.1620,
                            4_500.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(pwd\s+double\s+road|pwd\s+housing|korang\s+town|pakistan\s+town)\b"""),
                            33.5710,
                            73.1450,
                            4_000.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(gulberg\s+green|gulberg\s+expressway)\b"""),
                            33.6060,
                            73.1560,
                            4_500.0
                    ),
                    AreaAnchor(Regex("""(?i)\b(g[\s-]?15)\b"""), 33.6350, 72.9220, 4_000.0),
                    AreaAnchor(Regex("""(?i)\b(g[\s-]?11)\b"""), 33.6680, 72.9980, 3_500.0),
                    AreaAnchor(Regex("""(?i)\b(f[\s-]?10|f[\s-]?11)\b"""), 33.6880, 73.0050, 4_000.0),
                    AreaAnchor(Regex("""(?i)\b(f[\s-]?6|f[\s-]?7|blue\s+area)\b"""), 33.7200, 73.0650, 4_000.0),
                    AreaAnchor(
                            Regex("""(?i)\b(commercial\s+market.*murree\s+road|satt?ellite\s+town|6th\s+road|rabi\s+center|noor\s+mobile\s+mall)\b"""),
                            33.6350,
                            73.0725,
                            3_800.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(kashmir\s+road\s+saddar|adam\s*jee|bank\s+road.*saddar)\b"""),
                            33.5960,
                            73.0540,
                            2_500.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(peshawar\s+road)\b"""),
                            33.6060,
                            73.0150,
                            3_000.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(bostan\s+khan\s+road|scheme\s*3)\b"""),
                            33.5850,
                            73.0950,
                            3_500.0
                    ),
                    AreaAnchor(
                            Regex("""(?i)\b(chakri\s+road)\b"""),
                            33.5350,
                            72.9750,
                            4_500.0
                    )
            )

    private fun isMismatchedAddress(address: String, lat: Double, lon: Double): Boolean {
        if (address.isBlank()) return false
        for (anchor in KNOWN_AREAS) {
            if (anchor.pattern.containsMatchIn(address)) {
                if (haversineMeters(lat, lon, anchor.lat, anchor.lon) > anchor.maxMeters) {
                    return true
                }
            }
        }
        return false
    }

    private fun haversineMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val earth = 6_371_000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a =
                kotlin.math.sin(dLat / 2).let { it * it } +
                        kotlin.math.cos(Math.toRadians(lat1)) *
                                kotlin.math.cos(Math.toRadians(lat2)) *
                                kotlin.math.sin(dLon / 2).let { it * it }
        return earth * 2 * kotlin.math.asin(kotlin.math.sqrt(a).coerceIn(0.0, 1.0))
    }

    private fun readResults(
            cursor: android.database.Cursor,
            fallbackAddress: Boolean = true
    ): List<GeoSearchResult> =
            buildList {
                val colCount = cursor.columnCount
                while (cursor.moveToNext()) {
                    val rawName = cursor.getString(0) ?: continue
                    val name = rawName.trim().trimStart('\u200b', '\u200e', '\u200f')
                    if (name.isEmpty()) continue
                    val cat = cursor.getString(1).orEmpty().trim()
                    val bcat = cursor.getString(2).orEmpty().trim()
                    val rawAddr =
                            cursor.getString(5)
                                    .orEmpty()
                                    .replace("\\n", ", ")
                                    .replace("\n", ", ")
                                    .replace("\r", " ")
                                    .replace(Regex("\\s+"), " ")
                                    .trim()
                                    .trim(',', ' ')
                    val addr =
                            if (rawAddr.equals("Pakistan", ignoreCase = true)) "" else rawAddr
                    val phone =
                            if (colCount > 6) cursor.getString(6)?.trim()?.ifBlank { null }
                            else null
                    val website =
                            if (colCount > 7) cursor.getString(7)?.trim()?.ifBlank { null }
                            else null
                    val combinedType =
                            when {
                                cat.isNotBlank() && bcat.isNotBlank() && cat != bcat -> "$cat|$bcat"
                                cat.isNotBlank() -> cat
                                bcat.isNotBlank() -> bcat
                                else -> "place"
                            }
                    add(
                            GeoSearchResult(
                                    name = name,
                                    address =
                                            if (fallbackAddress) {
                                                addr.ifBlank {
                                                    bcat.replace('_', ' ')
                                                            .ifBlank { cat.replace('_', ' ') }
                                                            .ifBlank { "Pakistan" }
                                                }
                                            } else {
                                                addr
                                            },
                                    lat = cursor.getDouble(4),
                                    lng = cursor.getDouble(3),
                                    type = combinedType,
                                    phone = phone,
                                    website = website
                            )
                    )
                }
            }

    private fun open(context: Context): SQLiteDatabase? {
        database?.let { if (it.isOpen) return it }
        if (checked && database == null) return null
        checked = true
        val dirs = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)
        val file = dirs.map { File(it, "places_search.db") }.firstOrNull { it.isFile } ?: return null
        return runCatching {
                    SQLiteDatabase.openDatabase(
                            file.absolutePath,
                            null,
                            SQLiteDatabase.OPEN_READONLY
                    )
                }
                .onSuccess {
                    database = it
                    Log.d(TAG, "Opened places search index ${file.absolutePath}")
                }
                .onFailure { Log.w(TAG, "Could not open places search index", it) }
                .getOrNull()
    }
}
