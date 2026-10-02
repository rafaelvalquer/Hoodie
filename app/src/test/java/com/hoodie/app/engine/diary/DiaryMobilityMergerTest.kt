package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.MobilitySegmentEntity
import com.hoodie.app.core.database.MobilitySessionEntity
import com.hoodie.app.core.mobility.MobilitySource
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.data.repository.MobilityTrip
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryMapData
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType
import com.hoodie.app.domain.diary.model.ReplaySequence
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.ZONE
import com.hoodie.app.engine.at
import com.hoodie.app.engine.ms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DiaryMobilityMergerTest {
    private fun t(h: Int, m: Int) = at(MONDAY, h, m).ms()
    private val day0 = at(MONDAY, 0).ms()
    private val day1 = at(MONDAY + 1, 0).ms()
    private val left = DiaryTimelineItem("ctx-1", t(7, 47), DiaryTimelineType.LEFT, DiaryActor.USER, "Saiu de Casa", emoji = "🏠")
    private val arrived = DiaryTimelineItem("ctx-2", t(8, 31), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Chegou ao Trabalho", emoji = "🏢")
    private val diary = DailyDiary(DailySummary(LocalDate.of(2026, 1, 5)), listOf(left, arrived), emptyList(), DiaryMapData(), ReplaySequence.EMPTY)

    private fun seg(id: Long, mode: MovementMode, from: Long, to: Long?) = MobilitySegmentEntity(id, 1, mode, from, to, 1f, true, MobilitySource.CONFIRMATION)
    private val session = MobilitySessionEntity(1, t(7, 47), t(8, 31), 1, 2, MovementMode.WALKING, MovementMode.WALKING, MobilityState.ARRIVED, 1f, true, MobilitySource.CONFIRMATION)

    @Test
    fun `trechos entram na linha do tempo na ordem com horario e duracao`() {
        val trip = MobilityTrip(session, listOf(seg(1, MovementMode.WALKING, t(7, 47), t(7, 54)), seg(2, MovementMode.BUS, t(7, 54), t(8, 25)), seg(3, MovementMode.WALKING, t(8, 25), t(8, 31))))
        val merged = DiaryMobilityMerger.merge(diary, listOf(trip), day0, day1, t(23, 0), ZONE)
        assertEquals(
            listOf("🏠 Saiu de Casa", "🚶 Caminhou", "🚌 Pegou ônibus", "🚶 Caminhou", "🏢 Chegou ao Trabalho"),
            merged.timeline.map { "${it.emoji} ${it.title}" },
        )
        assertEquals("07:54–08:25 · 31 min", merged.timeline[2].subtitle)
        assertEquals(mapOf(MovementMode.WALKING to 13 * 60_000L, MovementMode.BUS to 31 * 60_000L), merged.mobilityTotals)
        assertEquals("🚌 31 min ônibus · 🚶 13 min caminhada", DiaryMobilityMerger.summary(merged.mobilityTotals))
        assertTrue("nada de rua ou coordenada", merged.timeline.none { "," in (it.subtitle ?: "") && "-23" in (it.subtitle ?: "") })
    }

    @Test
    fun `trecho que atravessa a meia-noite e cortado no dia e trecho em andamento vai ate agora`() {
        val night = MobilityTrip(session, listOf(seg(1, MovementMode.CAR, day0 - 20 * 60_000, day0 + 10 * 60_000)))
        val open = MobilityTrip(session.copy(endedAt = null), listOf(seg(2, MovementMode.WALKING, t(9, 0), null)))
        val merged = DiaryMobilityMerger.merge(diary, listOf(night, open), day0, day1, t(9, 5), ZONE)
        assertEquals(10 * 60_000L, merged.mobilityTotals[MovementMode.CAR])
        assertEquals(5 * 60_000L, merged.mobilityTotals[MovementMode.WALKING])
    }

    @Test
    fun `sem deslocamentos o diario nao muda`() {
        assertSame(diary, DiaryMobilityMerger.merge(diary, emptyList(), day0, day1, t(23, 0), ZONE))
    }
}
