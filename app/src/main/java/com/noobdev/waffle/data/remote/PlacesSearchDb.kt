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
                                                SELECT p.name, p.cat, p.bcat, p.lon, p.lat, p.addr
                                                FROM places_fts f JOIN places p ON p.id = f.rowid
                                                WHERE places_fts MATCH ?
                                                ORDER BY rank
                                                LIMIT ?
                                                """.trimIndent(),
                                                arrayOf(match, limit.toString())
                                        )
                                        .use(::readResults)
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
                                                    SELECT name, cat, bcat, lon, lat, addr
                                                    FROM places
                                                    WHERE name LIKE ?
                                                    ORDER BY name
                                                    LIMIT 300
                                                    """.trimIndent(),
                                            arrayOf("$safe%")
                                    )
                                            .use(::readResults)
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
                                    SELECT name, cat, bcat, lon, lat, addr
                                    FROM places
                                    WHERE $where
                                    ORDER BY length(name)
                                    LIMIT 300
                                    """.trimIndent(),
                                    args
                            )
                            .use(::readResults)
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
                                            SELECT name, cat, bcat, lon, lat, addr
                                            FROM places
                                            WHERE lat BETWEEN ? AND ?
                                              AND lon BETWEEN ? AND ?
                                            LIMIT 4000
                                            """.trimIndent(),
                                            arrayOf(
                                                    (latitude - latDelta).toString(),
                                                    (latitude + latDelta).toString(),
                                                    (longitude - lonDelta).toString(),
                                                    (longitude + lonDelta).toString()
                                            )
                                    )
                                    .use(::readResults)
                        }
                        .getOrDefault(emptyList())
        val manual = ManualPlacesStore.nearby(context, latitude, longitude, radiusMeters, limit)
        return (manual + candidates)
                .distinctBy { "${it.name.lowercase()}|${it.lat}|${it.lng}" }
                .map { it to haversineMeters(latitude, longitude, it.lat, it.lng) }
                .filter { it.second <= radiusMeters * 1.2 }
                .sortedBy { it.second }
                .take(limit)
                .map { it.first }
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

    private fun readResults(cursor: android.database.Cursor): List<GeoSearchResult> =
            buildList {
                while (cursor.moveToNext()) {
                    val name = cursor.getString(0) ?: continue
                    val cat = cursor.getString(1).orEmpty()
                    val bcat = cursor.getString(2).orEmpty()
                    val addr = cursor.getString(5).orEmpty()
                    add(
                            GeoSearchResult(
                                    name = name,
                                    address =
                                            addr.ifBlank {
                                                        bcat.replace('_', ' ')
                                                                .ifBlank {
                                                                    cat.replace('_', ' ')
                                                                }
                                                                .ifBlank { "Pakistan" }
                                                    },
                                    lat = cursor.getDouble(4),
                                    lng = cursor.getDouble(3),
                                    type = cat.ifBlank { bcat }.ifBlank { "place" }
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
