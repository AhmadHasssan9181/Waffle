package com.noobdev.waffle.core.routing

/** User choices from the route preview card. */
data class RouteOptions(
        val avoidTolls: Boolean = false,
        val avoidHighways: Boolean = false,
        val avoidFerries: Boolean = false,
        val alternatives: Boolean = true
) {
    val needsOnline: Boolean
        get() = avoidTolls || avoidHighways || avoidFerries

    fun avoidFeatures(): List<String> =
            buildList {
                if (avoidTolls) add("tollways")
                if (avoidHighways) add("highways")
                if (avoidFerries) add("ferries")
            }

    fun label(): String =
            buildList {
                        if (avoidTolls) add("no tolls")
                        if (avoidHighways) add("no highways")
                        if (avoidFerries) add("no ferries")
                    }
                    .joinToString(", ")
}
