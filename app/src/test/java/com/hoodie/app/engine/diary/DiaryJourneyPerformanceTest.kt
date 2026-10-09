package com.hoodie.app.engine.diary

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.pixel.diary.journey.JourneyTestFixtures
import org.junit.Assert.assertEquals
import org.junit.Test

/** Lightweight reproducible scaling probe; timings are recorded, not used as a flaky wall-clock gate. */
class DiaryJourneyPerformanceTest {
    @Test
    fun `journey assembly scales across representative visit counts`() {
        val samples = listOf(0, 5, 20, 50).associateWith { count ->
            val visits = (0 until count).map { index ->
                val start = index * 15L * JourneyTestFixtures.MIN
                JourneyTestFixtures.visit(
                    id = 1L + index % 7,
                    name = "Place ${index % 7}",
                    type = PlaceType.entries[index % PlaceType.entries.size],
                    from = start,
                    to = start + 10L * JourneyTestFixtures.MIN,
                )
            }
            val diary = JourneyTestFixtures.diary(visits, emptyList())
            JourneyMapAssembler.build(diary, JourneyTestFixtures.NOW) // warm-up
            val durations = List(7) {
                val started = System.nanoTime()
                val result = JourneyMapAssembler.build(diary, JourneyTestFixtures.NOW)
                assertEquals(count, result.nodes.size)
                System.nanoTime() - started
            }.sorted()
            durations[durations.lastIndex / 2] / 1_000
        }

        println("DIARY_JOURNEY_BENCHMARK median_us_by_visits=$samples")
        assertEquals(setOf(0, 5, 20, 50), samples.keys)

        val revisitComparisons = listOf(5, 20, 50).associateWith { count ->
            val keys = (0 until count).map { "place-${it % 7}" }
            val previous = List(7) {
                val oldStarted = System.nanoTime()
                val old = keys.indices.map { index -> keys.subList(0, index).count { it == keys[index] } }
                val oldNanos = System.nanoTime() - oldStarted

                val newStarted = System.nanoTime()
                val counts = mutableMapOf<String, Int>()
                val optimized = keys.map { key -> counts.getOrDefault(key, 0).also { counts[key] = it + 1 } }
                val newNanos = System.nanoTime() - newStarted
                assertEquals(old, optimized)
                oldNanos to newNanos
            }.sortedBy { it.first }
            previous[previous.lastIndex / 2].let { (old, new) -> old / 1_000 to new / 1_000 }
        }
        println("DIARY_REVISIT_COUNTS_BENCHMARK median_us_by_visits=$revisitComparisons")
    }
}
