package com.hoodie.app.engine.diary.journey

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.journey.ClockArc
import com.hoodie.app.domain.diary.journey.DayChapter
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.engine.diary.StopPhase
import com.hoodie.app.engine.diary.journey.JourneyV3Fixtures.V
import com.hoodie.app.engine.diary.journey.JourneyV3Fixtures.at
import com.hoodie.app.engine.diary.journey.JourneyV3Fixtures.day
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Replay sobre o overworld (plano §4.7): capítulo certo, portais e a mesma hora nas duas vistas. */
class JourneyReplayOverworldTest {
    private val zone = JourneyV3Fixtures.UTC

    /** 10 paradas; o trecho 11:30 → 13:00 atravessa o meio-dia. */
    private val data = day(
        *(6..10).map { h -> V("Manhã $h", PlaceType.OTHER, at(h), at(h, 30)) }.toTypedArray(),
        V("Mercado", PlaceType.MARKET, at(11), at(11, 30)),
        V("Trabalho", PlaceType.WORK, at(13), at(14)),
        V("Academia", PlaceType.GYM, at(15), at(16)),
        V("Família", PlaceType.FAMILY, at(19), at(20)),
        V("Casa", PlaceType.HOME, at(21), null),
    )
    private val model = JourneyOverworldModel.build(data, zone)
    private val morning get() = model.chapters.getValue(DayChapter.MORNING)
    private val afternoon get() = model.chapters.getValue(DayChapter.AFTERNOON)

    @Test fun hoodieOnlyAppearsInTheChapterOfTheReplayTime() {
        val t = at(8, 10)
        val m = JourneyReplayAssembler.overworld(model.plan, morning, t, replaying = true)
        assertEquals("journey-2", m.hoodie?.stopId)
        assertEquals(StopPhase.CURRENT, m.phases["journey-2"])
        assertEquals(StopPhase.FUTURE, m.phases["journey-5"])
        assertNull(JourneyReplayAssembler.overworld(model.plan, afternoon, t, replaying = true).hoodie)
        assertEquals(DayChapter.MORNING, JourneyChapterPlanner.activeChapter(model.plan as com.hoodie.app.domain.diary.journey.JourneyPlan.Chapters, t, playing = true, manual = DayChapter.NIGHT, isToday = false, now = 0))
    }

    @Test fun crossingLegLeavesByTheBottomAndEntersByTheTop() {
        val out = morning.links.single { it.kind == LinkKind.PORTAL_OUT }
        val inn = afternoon.links.single { it.kind == LinkKind.PORTAL_IN }
        assertEquals(out.legId, inn.legId)

        // 11:48 → 20% do trecho: metade de cima (saída) com 40% andado.
        val early = at(11, 48)
        val m = JourneyReplayAssembler.overworld(model.plan, morning, early, true)
        assertEquals(out.id, m.hoodie?.linkId)
        assertEquals(0.4f, m.hoodie!!.linkProgress, 0.001f)
        assertEquals(0.4f, m.linkProgress.getValue(out.id), 0.001f)
        assertNull(JourneyReplayAssembler.overworld(model.plan, afternoon, early, true).hoodie)
        assertEquals(DayChapter.MORNING, JourneyChapterPlanner.chapterOf(early, JourneyChapterPlanner.windows(JourneyV3Fixtures.DATE, zone)))

        // 12:45 → 83%: já entrou na Tarde pelo topo.
        val late = at(12, 45)
        val a = JourneyReplayAssembler.overworld(model.plan, afternoon, late, true)
        assertEquals(inn.id, a.hoodie?.linkId)
        assertEquals(2f / 3f, a.hoodie!!.linkProgress, 0.001f)
        assertNull(JourneyReplayAssembler.overworld(model.plan, morning, late, true).hoodie)
        assertEquals(1f, JourneyReplayAssembler.overworld(model.plan, morning, late, true).linkProgress.getValue(out.id), 0.001f)
    }

    @Test fun sameTimestampMeansTheSamePlaceInBothViews() {
        val clock = DayClockLegacyAssembler.build(data, zone)
        val clockDay = DayClockLegacyAssembler.day(JourneyV3Fixtures.DATE, zone)
        listOf(at(6, 15), at(9, 20), at(13, 30), at(15, 45), at(19, 10), at(22)).forEach { t ->
            val arc = DayClockLegacyAssembler.hit(clock, clockDay.deg(t), tickToleranceDeg = 0f) as? ClockArc
            assertNotNull("arco às $t", arc)
            assertEquals(ClockArc.Kind.STAY, arc!!.kind)
            val chapter = JourneyChapterPlanner.chapterOf(t, JourneyChapterPlanner.windows(JourneyV3Fixtures.DATE, zone))
            val layout = model.chapters.getValue(chapter)
            val state = JourneyReplayAssembler.overworld(model.plan, layout, t, true)
            assertEquals(arc.stopId, state.hoodie?.stopId)
            assertTrue(state.phases[arc.stopId] == StopPhase.CURRENT)
        }
    }
}
