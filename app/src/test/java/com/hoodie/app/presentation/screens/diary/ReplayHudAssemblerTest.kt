package com.hoodie.app.presentation.screens.diary

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.core.time.startOfDay
import com.hoodie.app.domain.diary.model.ReplayVisualState
import com.hoodie.app.engine.diary.DiaryRegressionScenario
import com.hoodie.app.engine.diary.ReplayHudAssembler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReplayHudAssemblerTest {
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val date = LocalDate.of(2026, 10, 5)
    private val diary = DiaryRegressionScenario.create(date, zone)
    private fun at(h: Int, m: Int = 0) = startOfDay(date, zone) + (h * 60L + m) * 60_000L

    @Test
    fun `estado visual reune contexto, atividade, app, mapa e periodo`() {
        val v = ReplayHudAssembler.assemble(diary.replay, diary.phoneInsights, at(18, 30), zone)
        assertEquals(UserContextType.GYM, v.context)
        assertEquals(HoodieActivity.TRAINING, v.hoodieActivity)
        assertEquals("Spotify", v.activePhoneApp!!.appLabel)
        assertEquals("visit-4", v.activeNodeId)
        assertNull(v.activeTripId)
        assertEquals(DayPeriod.EVENING, v.dayPeriod)
        assertEquals(ReplayVisualState.IDLE, ReplayHudAssembler.assemble(diary.replay, diary.phoneInsights, null, zone))
    }

    @Test
    fun `linhas do HUD`() {
        val v = ReplayHudAssembler.assemble(diary.replay, diary.phoneInsights, at(10, 37), zone)
        val lines = ReplayHudText.lines(v, zone)!!
        assertEquals("10:37", lines.clock)
        assertEquals("🏢 Trabalho", lines.context)
        assertEquals("🐱 Hoodie: trabalhando", lines.hoodie)
        assertNull("10:37 ninguém no celular", lines.app)
        val music = ReplayHudText.lines(ReplayHudAssembler.assemble(diary.replay, diary.phoneInsights, at(18, 25), zone), zone)!!
        assertEquals("Spotify · música", music.app)
        assertNull(ReplayHudText.lines(ReplayVisualState.IDLE, zone))
    }

    @Test
    fun `sem dados do celular o HUD so nao mostra app`() {
        val v = ReplayHudAssembler.assemble(diary.replay, null, at(18, 25), zone)
        assertNull(v.activePhoneApp)
        assertEquals(UserContextType.GYM, v.context)
    }
}
