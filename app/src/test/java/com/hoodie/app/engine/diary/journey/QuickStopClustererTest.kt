package com.hoodie.app.engine.diary.journey

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.journey.BiomeType
import com.hoodie.app.domain.diary.journey.JourneyStop
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QuickStopClustererTest {
    private val min = 60_000L
    private fun visit(i: Int, minutes: Long, ongoing: Boolean = false) = JourneyStop.Visit(
        "s$i", "s$i", i, BiomeType.CAMP, PlaceType.OTHER, "L$i", i * 100 * min, if (ongoing) null else i * 100 * min + minutes * min, minutes * min, 1,
    )
    private fun day(vararg minutes: Long) = minutes.mapIndexed { i, m -> visit(i, m) }

    @Test fun onlyConsecutiveQuickStopsAboveTheLimitAreGrouped() {
        val stops = day(30, 5, 6, 7, 30, 4, 30, 8, 9, 30, 30, 30, 30)
        val r = QuickStopClusterer.cluster(stops, maxStops = 12, quickMs = 10 * min)
        val clusters = r.stops.filterIsInstance<JourneyStop.QuickCluster>()
        assertEquals(listOf(listOf("s1", "s2", "s3"), listOf("s7", "s8")), clusters.map { it.stopIds })
        assertTrue("s5 sozinha não vira grupo", r.stops.any { it.id == "s5" })
        assertFalse(r.overflow)
    }

    @Test fun belowTheLimitNothingIsGrouped() {
        val stops = day(5, 5, 5, 5)
        assertEquals(stops, QuickStopClusterer.cluster(stops, 12, 10 * min).stops)
    }

    @Test fun protectedStopBreaksTheGroupAroundIt() {
        val stops = day(30, 5, 5, 5, 5, 5, 30, 30, 30, 30, 30, 30, 30)
        val r = QuickStopClusterer.cluster(stops, 12, 10 * min, protectedIds = setOf("s3"))
        val clusters = r.stops.filterIsInstance<JourneyStop.QuickCluster>()
        assertEquals(listOf(listOf("s1", "s2"), listOf("s4", "s5")), clusters.map { it.stopIds })
        assertTrue(r.stops.any { it.id == "s3" })
    }

    @Test fun clusterKeepsIdsAndTimesForTheDetail() {
        val stops = day(30, 5, 6, 30, 30, 30, 30, 30, 30, 30, 30, 30, 30)
        val c = QuickStopClusterer.cluster(stops, 12, 10 * min).stops.filterIsInstance<JourneyStop.QuickCluster>().single()
        assertEquals(listOf("s1", "s2"), c.stopIds)
        assertEquals(stops[1].arrivalAt, c.startAt)
        assertEquals(stops[2].departureAt, c.endAt)
    }

    @Test fun worstCaseStaysAboveTheLimitAndReportsIt() {
        val stops = day(*LongArray(16) { 30 })
        val r = QuickStopClusterer.cluster(stops, 12, 10 * min)
        assertEquals(16, r.stops.size)
        assertTrue(r.overflow)
    }
}
