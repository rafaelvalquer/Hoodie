package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.time.MINUTE_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppSessionBuilderTest {

    @Test
    fun `foreground e background do mesmo app viram uma sessao`() {
        val s = AppSessionBuilder.build(listOf(fg(YOUTUBE, mon(12, 18)), bg(YOUTUBE, mon(12, 26))), DAY_START, DAY_END)
        assertEquals(1, s.size)
        assertEquals(8 * MINUTE_MS, s.single().durationMs)
    }

    @Test
    fun `outro app assumindo a frente encerra a sessao anterior`() {
        val s = AppSessionBuilder.build(listOf(fg(WHATSAPP, mon(9, 2)), fg(TEAMS, mon(9, 7)), bg(TEAMS, mon(9, 34))), DAY_START, DAY_END)
        assertEquals(listOf(WHATSAPP, TEAMS), s.map { it.packageName })
        assertEquals(listOf(5 * MINUTE_MS, 27 * MINUTE_MS), s.map { it.durationMs })
    }

    @Test
    fun `background atrasado de um app que ja saiu e ignorado`() {
        // ACTIVITY_STOPPED do WhatsApp chega depois do Teams já estar na frente.
        val s = AppSessionBuilder.build(listOf(fg(WHATSAPP, mon(9)), fg(TEAMS, mon(9, 5)), bg(WHATSAPP, mon(9, 5) + 300), bg(TEAMS, mon(9, 20))), DAY_START, DAY_END)
        assertEquals(15 * MINUTE_MS, s.last().durationMs)
    }

    @Test
    fun `troca de tela dentro do mesmo app nao abre sessao nova`() {
        val s = AppSessionBuilder.build(
            listOf(fg(SPOTIFY, mon(8)), bg(SPOTIFY, mon(8, 3)), fg(SPOTIFY, mon(8, 3) + 800), bg(SPOTIFY, mon(8, 10))),
            DAY_START, DAY_END,
        )
        assertEquals(1, s.size)
        assertEquals(10 * MINUTE_MS, s.single().durationMs)
    }

    @Test
    fun `voltar ao app minutos depois e uma sessao nova`() {
        val s = AppSessionBuilder.build(listOf(fg(SPOTIFY, mon(8)), bg(SPOTIFY, mon(8, 3)), fg(SPOTIFY, mon(8, 30)), bg(SPOTIFY, mon(8, 31))), DAY_START, DAY_END)
        assertEquals(2, s.size)
    }

    @Test
    fun `tela apagando encerra o app em uso`() {
        val s = AppSessionBuilder.build(listOf(fg(YOUTUBE, mon(22)), screenOff(mon(22, 40))), DAY_START, DAY_END)
        assertEquals(40 * MINUTE_MS, s.single().durationMs)
    }

    @Test
    fun `app aberto antes da meia-noite conta so a parte do dia`() {
        val s = AppSessionBuilder.build(listOf(fg(YOUTUBE, DAY_START - 30 * MINUTE_MS), bg(YOUTUBE, DAY_START + 10 * MINUTE_MS)), DAY_START, DAY_END)
        assertEquals(DAY_START, s.single().startedAt)
        assertEquals(10 * MINUTE_MS, s.single().durationMs)
    }

    @Test
    fun `app ainda aberto fecha no fim da janela`() {
        val s = AppSessionBuilder.build(listOf(fg(YOUTUBE, mon(10))), DAY_START, mon(10, 12))
        assertEquals(12 * MINUTE_MS, s.single().durationMs)
    }

    @Test
    fun `sessoes inteiramente fora da janela somem`() {
        assertTrue(AppSessionBuilder.build(listOf(fg(YOUTUBE, DAY_START - 60 * MINUTE_MS), bg(YOUTUBE, DAY_START - 50 * MINUTE_MS)), DAY_START, DAY_END).isEmpty())
    }
}
