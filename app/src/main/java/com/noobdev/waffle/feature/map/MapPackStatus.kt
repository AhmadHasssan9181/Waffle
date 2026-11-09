package com.noobdev.waffle.feature.map

import android.content.Context
import java.io.File

/**
 * Which pieces of the side-loaded Pakistan map pack are on this phone.
 *
 * The pack is far too big to ship in the APK (~700 MB), so it lives in the app's storage and the
 * map silently falls back to the online basemap when any piece is missing. That silence is hard to
 * debug, so Settings reports exactly what was found and what is missing.
 *
 * Layout the app expects, under `getExternalFilesDir(null)` or `filesDir`:
 *
 * ```
 * pakistan_vector.pmtiles   OpenMapTiles basemap
 * pakistan_places.pmtiles   Overture POI layer
 * places_search.db          FTS5 place index
 * fonts/Noto Sans Regular/  glyph ranges for labels
 * style/sprite.json         POI icons
 * ```
 */
data class MapPackStatus(
        val vectorBytes: Long = 0,
        val placesBytes: Long = 0,
        val searchBytes: Long = 0,
        val hasFonts: Boolean = false,
        val hasSprite: Boolean = false
) {
    val hasVector: Boolean get() = vectorBytes > 0
    val hasPlaces: Boolean get() = placesBytes > 0
    val hasSearchIndex: Boolean get() = searchBytes > 0

    /** Everything the local vector stack needs to render with labels and POI icons. */
    val basemapReady: Boolean
        get() = hasVector && hasPlaces && hasFonts && hasSprite

    /** Search works as soon as the index is there, independent of the basemap. */
    val searchReady: Boolean get() = hasSearchIndex

    val complete: Boolean get() = basemapReady && searchReady

    val summary: String
        get() =
                when {
                    complete -> "Basemap, places and search · installed"
                    basemapReady && !searchReady -> "Basemap installed · search index missing"
                    searchReady && !basemapReady -> "Search index installed · basemap missing"
                    hasVector || hasPlaces || hasSearchIndex -> "Partially installed"
                    else -> "Not installed · using the online basemap"
                }

    companion object {
        fun read(context: Context): MapPackStatus {
            val dirs = listOfNotNull(context.getExternalFilesDir(null), context.filesDir)

            fun file(name: String): File? =
                    dirs.map { File(it, name) }.firstOrNull { it.isFile }

            fun dir(name: String): File? =
                    dirs.map { File(it, name) }.firstOrNull { it.isDirectory }

            val fonts =
                    dir("fonts")?.let { root ->
                        root.listFiles()?.any { child ->
                            child.isDirectory &&
                                    (child.listFiles()?.any { it.name.endsWith(".pbf") } == true)
                        } == true
                    } ?: false

            val sprite =
                    dir("style")?.let { File(it, "sprite.json").isFile } ?: false

            return MapPackStatus(
                    vectorBytes = file("pakistan_vector.pmtiles")?.length() ?: 0,
                    placesBytes = file("pakistan_places.pmtiles")?.length() ?: 0,
                    searchBytes = file("places_search.db")?.length() ?: 0,
                    hasFonts = fonts,
                    hasSprite = sprite
            )
        }

        fun formatSize(bytes: Long): String =
                when {
                    bytes <= 0 -> "—"
                    bytes >= 1024L * 1024L * 1024L ->
                            String.format(java.util.Locale.US, "%.1f GB", bytes / 1073741824.0)
                    bytes >= 1024L * 1024L ->
                            String.format(java.util.Locale.US, "%.0f MB", bytes / 1048576.0)
                    else -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1024.0)
                }
    }
}
