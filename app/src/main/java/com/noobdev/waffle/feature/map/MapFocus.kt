package com.noobdev.waffle.feature.map

/**
 * One-shot handoff from another screen to the map ("show me this place").
 *
 * The map screen consumes and clears the request when it is next created, which avoids plumbing
 * navigation arguments through every caller.
 */
data class MapTarget(
        val lat: Double,
        val lon: Double,
        val label: String,
        val zoom: Double = 16.0
)

object MapFocus {
    @Volatile private var pending: MapTarget? = null

    fun request(target: MapTarget) {
        pending = target
    }

    /** Returns the pending target and clears it. */
    fun consume(): MapTarget? {
        val target = pending ?: return null
        pending = null
        return target
    }
}
