package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.domain.phoneinsights.model.AppSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenSessionBuilderTest {

    @Test
    fun `tela ligada e desligada vira uma sessao`() {
        val events = listOf(screenOn(mon(8)), unlock(mon(8)), screenOff(mon(8, 10)))
        val r = ScreenSessionBuilder.build(events, emptyList(), DAY_START, DAY_END)
        assertEquals(1, r.sessions.size)
        assertEquals(10 * MINUTE_MS, r.sessions.single().durationMs)
        assertEquals(1, r.unlockCount)
        assertFalse(r.isEstimated)
    }

    @Test
    fun `sessao que cruza a meia-noite e recortada no inicio do dia`() {
        val events = listOf(screenOn(DAY_START - 20 * MINUTE_MS), screenOff(DAY_START + 15 * MINUTE_MS))
        val r = ScreenSessionBuilder.build(events, emptyList(), DAY_START, DAY_END)
        assertEquals(DAY_START, r.sessions.single().startedAt)
        assertEquals(15 * MINUTE_MS, r.sessions.single().durationMs)
    }

    @Test
    fun `tela ainda ligada fecha no agora`() {
        val now = mon(10, 30)
        val r = ScreenSessionBuilder.build(listOf(screenOn(mon(10))), emptyList(), DAY_START, now)
        assertEquals(30 * MINUTE_MS, r.sessions.single().durationMs)
    }

    @Test
    fun `primeiro evento desligando sem ter ligado assume tela ligada desde o inicio da janela`() {
        val r = ScreenSessionBuilder.build(listOf(screenOff(DAY_START + 5 * MINUTE_MS)), emptyList(), DAY_START, DAY_END)
        assertEquals(5 * MINUTE_MS, r.sessions.single().durationMs)
    }

    @Test
    fun `desbloqueios contam so keyguard hidden dentro do dia`() {
        val events = listOf(unlock(DAY_START - 1), screenOn(mon(7)), unlock(mon(7)), screenOff(mon(7, 1)), screenOn(mon(9)), unlock(mon(9)), screenOff(mon(9, 2)))
        val r = ScreenSessionBuilder.build(events, emptyList(), DAY_START, DAY_END)
        assertEquals(2, r.unlockCount)
        assertEquals(2, r.sessions.size)
    }

    @Test
    fun `sem eventos de bloqueio cada vez que a tela acende e um desbloqueio estimado`() {
        val events = listOf(screenOn(mon(7)), screenOff(mon(7, 1)), screenOn(mon(9)), screenOff(mon(9, 2)), screenOn(mon(11)), screenOff(mon(11, 3)))
        val r = ScreenSessionBuilder.build(events, emptyList(), DAY_START, DAY_END)
        assertEquals(3, r.unlockCount)
        assertTrue(r.isEstimated)
    }

    @Test
    fun `sem eventos de tela (API 26-27) usa sessoes de app proximas`() {
        val apps = listOf(
            AppSession(YOUTUBE, mon(8), mon(8, 10)),
            AppSession(WHATSAPP, mon(8, 10) + 10_000, mon(8, 15)), // 10 s depois: mesma sessão de tela
            AppSession(SPOTIFY, mon(12), mon(12, 5)),
        )
        val r = ScreenSessionBuilder.build(emptyList(), apps, DAY_START, DAY_END)
        assertEquals(2, r.sessions.size)
        assertEquals(15 * MINUTE_MS, r.sessions[0].durationMs)
        assertEquals(2, r.unlockCount)
        assertTrue(r.isEstimated)
    }

    @Test
    fun `desligar o aparelho fecha a sessao e religar descarta sessao aberta`() {
        val events = listOf(
            screenOn(mon(8)),
            com.hoodie.app.core.deviceusage.RawUsageEvent(com.hoodie.app.core.deviceusage.RawUsageEventType.DEVICE_SHUTDOWN, mon(8, 5)),
            com.hoodie.app.core.deviceusage.RawUsageEvent(com.hoodie.app.core.deviceusage.RawUsageEventType.DEVICE_STARTUP, mon(9)),
            screenOn(mon(9, 1)), screenOff(mon(9, 3)),
        )
        val r = ScreenSessionBuilder.build(events, emptyList(), DAY_START, DAY_END)
        assertEquals(listOf(5 * MINUTE_MS, 2 * MINUTE_MS), r.sessions.map { it.durationMs })
    }

    @Test
    fun `dia vazio`() {
        val r = ScreenSessionBuilder.build(emptyList(), emptyList(), DAY_START, DAY_END)
        assertTrue(r.sessions.isEmpty())
        assertEquals(0, r.unlockCount)
        assertFalse(r.isEstimated)
    }
}
