package com.noobdev.waffle.core.navigation

import com.noobdev.waffle.data.model.DisasterAlert
import com.noobdev.waffle.data.model.StatusLevel
import java.time.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Hazards are routing input only. These tests pin the two filters that keep stale data out of the
 * router: resolved alerts and alerts older than a week.
 */
class HazardRegistryTest {

    private val now = Instant.parse("2026-11-09T12:00:00Z").toEpochMilli()

    private fun alert(
            id: String,
            status: StatusLevel? = StatusLevel.WARNING,
            timestamp: String? = "2026-11-09T11:00:00Z",
            type: String = "flood"
    ) = DisasterAlert(
            id = id,
            title = "Test $id",
            type = type,
            status = status,
            timestamp = timestamp,
            latitude = 33.6844,
            longitude = 73.0479,
            details = emptyMap()
    )

    @Before
    fun clear() = HazardRegistry.clear()

    @After
    fun tearDown() = HazardRegistry.clear()

    @Test
    fun `fresh alerts become hazards`() {
        HazardRegistry.updateFromAlerts(listOf(alert("fresh")), now)
        assertEquals(1, HazardRegistry.active().size)
    }

    @Test
    fun `resolved alerts are dropped`() {
        HazardRegistry.updateFromAlerts(
                listOf(alert("done", status = StatusLevel.SAFE)),
                now
        )
        assertTrue(HazardRegistry.active().isEmpty())
    }

    @Test
    fun `alerts older than a week are dropped`() {
        HazardRegistry.updateFromAlerts(
                listOf(alert("old", timestamp = "2022-08-30T09:00:00Z")),
                now
        )
        assertTrue(HazardRegistry.active().isEmpty())
    }

    @Test
    fun `six day old alert is still kept`() {
        HazardRegistry.updateFromAlerts(
                listOf(alert("recent", timestamp = "2026-11-03T12:00:00Z")),
                now
        )
        assertEquals(1, HazardRegistry.active().size)
    }

    @Test
    fun `unparseable timestamp is treated as current`() {
        HazardRegistry.updateFromAlerts(listOf(alert("odd", timestamp = "yesterday")), now)
        assertEquals(1, HazardRegistry.active().size)
    }

    @Test
    fun `missing timestamp is treated as current`() {
        HazardRegistry.updateFromAlerts(listOf(alert("null", timestamp = null)), now)
        assertEquals(1, HazardRegistry.active().size)
    }

    @Test
    fun `local date time without a zone is parsed as UTC`() {
        HazardRegistry.updateFromAlerts(
                listOf(alert("local", timestamp = "2026-11-09T11:00:00")),
                now
        )
        assertEquals(1, HazardRegistry.active().size)
    }

    @Test
    fun `mixed feed keeps only the live alerts`() {
        HazardRegistry.updateFromAlerts(
                listOf(
                        alert("live"),
                        alert("old", timestamp = "2022-08-30T09:00:00Z"),
                        alert("done", status = StatusLevel.SAFE),
                        alert("odd", timestamp = "not a date")
                ),
                now
        )
        val ids = HazardRegistry.active().map { it.id }.toSet()
        assertEquals(setOf("live", "odd"), ids)
    }
}
