package com.noobdev.waffle.core.navigation

import com.noobdev.waffle.data.model.StatusLevel
import com.noobdev.waffle.data.model.DisasterAlert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GeoGeometryTest {

    private val islamabad = GeoPoint(33.6844, 73.0479)
    private val rawalpindi = GeoPoint(33.5651, 73.0169)

    @Test
    fun `haversine measures the Islamabad Rawalpindi gap`() {
        val distance = GeoGeometry.haversineMeters(islamabad, rawalpindi)
        assertTrue("expected ~13.5km but was $distance", distance in 12_500.0..14_500.0)
    }

    @Test
    fun `point to segment uses perpendicular distance`() {
        val a = GeoPoint(33.6800, 73.0400)
        val b = GeoPoint(33.6800, 73.0600)
        val pointOneKmNorth = GeoPoint(33.6890, 73.0500)

        val distance = GeoGeometry.pointToSegmentMeters(pointOneKmNorth, a, b)
        assertTrue("expected ~1km but was $distance", distance in 900.0..1_100.0)
    }

    @Test
    fun `point past the end clamps to the endpoint`() {
        val a = GeoPoint(33.6800, 73.0400)
        val b = GeoPoint(33.6800, 73.0500)
        val beyond = GeoPoint(33.6800, 73.0600)

        val distance = GeoGeometry.pointToSegmentMeters(beyond, a, b)
        val direct = GeoGeometry.haversineMeters(b, beyond)
        assertEquals(direct, distance, 5.0)
    }

    @Test
    fun `route crossing a hazard produces one ordered hit`() {
        val route = listOf(GeoPoint(33.60, 73.00), GeoPoint(33.70, 73.10))
        val onRoute = HazardZone(
                id = "flood-1",
                title = "Flood",
                type = "flood",
                severity = "CRITICAL",
                center = GeoPoint(33.65, 73.05),
                radiusMeters = 1_000.0
        )
        val farAway = HazardZone(
                id = "flood-2",
                title = "Distant flood",
                type = "flood",
                severity = "WARNING",
                center = GeoPoint(33.60, 73.30),
                radiusMeters = 500.0
        )

        val hits = GeoGeometry.findHazardHits(route, listOf(onRoute, farAway))
        assertEquals(1, hits.size)
        assertEquals("flood-1", hits.first().hazard.id)
        assertTrue(hits.first().closestDistanceMeters <= 1_000.0)
    }

    @Test
    fun `detour waypoints sit outside the hazard on both sides`() {
        val start = GeoPoint(33.60, 73.00)
        val end = GeoPoint(33.70, 73.10)
        val hazard = HazardZone(
                id = "storm-1",
                title = "Storm",
                type = "storm",
                severity = "WARNING",
                center = GeoPoint(33.65, 73.05),
                radiusMeters = 1_500.0
        )

        val left = GeoGeometry.detourWaypoints(start, end, hazard, sideSign = 1)
        val right = GeoGeometry.detourWaypoints(start, end, hazard, sideSign = -1)

        assertEquals(2, left.size)
        assertEquals(2, right.size)
        for (point in left + right) {
            val distance = GeoGeometry.haversineMeters(hazard.center, point)
            assertTrue("waypoint inside hazard: $distance", distance > hazard.radiusMeters)
        }
        assertTrue(
                "sides should differ",
                GeoGeometry.haversineMeters(left[0], right[0]) > 100.0
        )
        assertTrue(
                "a detour must be longer than the direct line",
                GeoGeometry.detourLengthMeters(start, end, left) >
                        GeoGeometry.haversineMeters(start, end)
        )
    }

    @Test
    fun `circle polygon is closed and radius accurate`() {
        val center = GeoPoint(33.6844, 73.0479)
        val polygon = GeoGeometry.circlePolygon(center, 2_000.0, segments = 48)

        assertEquals(49, polygon.size)
        assertEquals(polygon.first().latitude, polygon.last().latitude, 1e-6)
        assertEquals(polygon.first().longitude, polygon.last().longitude, 1e-6)
        for (point in polygon) {
            val distance = GeoGeometry.haversineMeters(center, point)
            assertTrue("radius drifted: $distance", distance in 1_980.0..2_020.0)
        }
    }

    @Test
    fun `blast radius scales with earthquake magnitude and severity`() {
        val magnitudeSix = DisasterAlert(
                id = "eq-1",
                title = "M6.0",
                type = "earthquake",
                status = StatusLevel.CRITICAL,
                latitude = 33.7,
                longitude = 73.0,
                details = mapOf("magnitude" to 6.0)
        )
        val magnitudeFour = magnitudeSix.copy(id = "eq-2", details = mapOf("magnitude" to 4.0))

        val strong = BlastRadius.displayRadiusMeters(magnitudeSix)
        val light = BlastRadius.displayRadiusMeters(magnitudeFour)
        val routing = BlastRadius.routingRadiusMeters(magnitudeSix)

        assertTrue("M6 should be large: $strong", strong in 50_000.0..90_000.0)
        assertTrue("M4 should be smaller: $light", light < strong / 2)
        assertTrue("routing radius must be tighter", routing < strong)
    }

    @Test
    fun `hazard zones are built only for alerts with coordinates`() {
        val located = DisasterAlert(
                id = "flood-9",
                title = "Flood",
                type = "flood",
                status = StatusLevel.WARNING,
                latitude = 33.7,
                longitude = 73.0,
                details = mapOf("radius_km" to 4.0)
        )
        val unlocated = located.copy(id = "flood-10", latitude = null, longitude = null)

        val zone = BlastRadius.fromAlert(located)
        assertNotNull(zone)
        assertEquals(4_000.0, zone!!.radiusMeters, 1.0)
        assertEquals(null, BlastRadius.fromAlert(unlocated))
    }
}
