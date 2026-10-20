package com.noobdev.waffle.core.navigation

/**
 * Decides which way-points to insert so a route bends around active hazards.
 *
 * Kept engine-agnostic on purpose: the same list works for GraphHopper (offline) and ORS (online).
 * For each hazard that cuts the current path we try both sides of the circle and keep the shorter
 * detour that actually leaves the hazard.
 */
data class DetourPlan(
        val waypoints: List<GeoPoint>,
        val avoidedHazards: List<HazardZone>
)

object HazardDetourPlanner {

    fun plan(
            start: GeoPoint,
            end: GeoPoint,
            hazards: List<HazardZone>,
            clearanceMeters: Double = 250.0,
            maxHazards: Int = 3
    ): DetourPlan {
        if (hazards.isEmpty()) return DetourPlan(emptyList(), emptyList())

        val direct = listOf(start, end)
        val hits = GeoGeometry.findHazardHits(direct, hazards, clearanceMeters)
        if (hits.isEmpty()) return DetourPlan(emptyList(), emptyList())

        val waypoints = mutableListOf<GeoPoint>()
        val avoided = mutableListOf<HazardZone>()

        for (hit in hits.take(maxHazards)) {
            val hazard = hit.hazard
            val currentPath = listOf(start) + waypoints + listOf(end)
            if (GeoGeometry.findHazardHits(currentPath, listOf(hazard), clearanceMeters).isEmpty()) {
                continue
            }

            val left = GeoGeometry.detourWaypoints(start, end, hazard, sideSign = 1, clearanceMeters)
            val right = GeoGeometry.detourWaypoints(start, end, hazard, sideSign = -1, clearanceMeters)

            val leftPath = listOf(start) + waypoints + left + listOf(end)
            val rightPath = listOf(start) + waypoints + right + listOf(end)

            val leftHits = GeoGeometry.findHazardHits(leftPath, listOf(hazard), 0.0).isEmpty()
            val rightHits = GeoGeometry.findHazardHits(rightPath, listOf(hazard), 0.0).isEmpty()

            val chosen =
                    when {
                        leftHits && !rightHits -> left
                        rightHits && !leftHits -> right
                        else ->
                                if (GeoGeometry.detourLengthMeters(start, end, waypoints + left) <=
                                                GeoGeometry.detourLengthMeters(
                                                        start,
                                                        end,
                                                        waypoints + right
                                                )
                                ) left
                                else right
                    }

            if (chosen.isEmpty()) continue
            waypoints.addAll(chosen)
            avoided.add(hazard)
        }

        return DetourPlan(waypoints.toList(), avoided.toList())
    }
}
