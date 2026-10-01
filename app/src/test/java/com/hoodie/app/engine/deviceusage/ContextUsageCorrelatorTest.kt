package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.engine.timeline.ContextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ContextUsageCorrelatorTest {
    private val label: (String) -> String = { FakeAppMetadata().label(it) }
    private val contexts = listOf(
        ContextSpan(UserContextType.HOME, DAY_START, mon(8)),
        ContextSpan(UserContextType.COMMUTING, mon(8), mon(8, 49)),
        ContextSpan(UserContextType.WORK, mon(8, 49), mon(12, 16)),
        ContextSpan(UserContextType.LUNCH, mon(12, 16), null),
    )

    @Test
    fun `cada uso cai no contexto em que aconteceu`() {
        val sessions = listOf(
            AppSession(WHATSAPP, mon(7), mon(7, 10)),
            AppSession(YOUTUBE, mon(8, 10), mon(8, 30)),
            AppSession(TEAMS, mon(9, 15), mon(9, 42)),
        )
        val r = ContextUsageCorrelator.correlate(sessions, contexts, label, DAY_END)
        assertEquals(10 * MINUTE_MS, r.first { it.context == UserContextType.HOME }.foregroundMs)
        assertEquals("YouTube", r.first { it.context == UserContextType.COMMUTING }.apps.single().appLabel)
        assertEquals(27 * MINUTE_MS, r.first { it.context == UserContextType.WORK }.apps.single().foregroundMs)
    }

    @Test
    fun `sessao que atravessa troca de contexto e dividida e conta nos dois`() {
        val r = ContextUsageCorrelator.correlate(listOf(AppSession(SPOTIFY, mon(7, 50), mon(8, 10))), contexts, label, DAY_END)
        assertEquals(10 * MINUTE_MS, r.first { it.context == UserContextType.HOME }.foregroundMs)
        assertEquals(10 * MINUTE_MS, r.first { it.context == UserContextType.COMMUTING }.foregroundMs)
        assertTrue(r.all { it.sessionCount == 1 })
    }

    @Test
    fun `contexto em aberto vai ate o fim da janela`() {
        val r = ContextUsageCorrelator.correlate(listOf(AppSession(YOUTUBE, mon(12, 20), mon(12, 28))), contexts, label, mon(13))
        assertEquals(8 * MINUTE_MS, r.single { it.context == UserContextType.LUNCH }.foregroundMs)
    }

    @Test
    fun `uso fora de qualquer contexto nao e atribuido`() {
        val r = ContextUsageCorrelator.correlate(listOf(AppSession(YOUTUBE, mon(7), mon(7, 5))), emptyList(), label, DAY_END)
        assertTrue(r.isEmpty())
    }

    @Test
    fun `top apps do contexto ordenados e limitados`() {
        val sessions = (0 until 8).map { i -> AppSession("app.n$i", mon(9, i * 5), mon(9, i * 5 + i + 1)) }
        val work = ContextUsageCorrelator.correlate(sessions, contexts, label, DAY_END, topAppsPerContext = 3).single()
        assertEquals(3, work.apps.size)
        assertEquals("app.n7", work.apps.first().packageName)
        assertEquals(8, work.sessionCount)
        assertNull(work.apps.firstOrNull { it.packageName == "app.n0" })
    }
}
