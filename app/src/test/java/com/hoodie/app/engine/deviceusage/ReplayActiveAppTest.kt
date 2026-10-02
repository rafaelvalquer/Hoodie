package com.hoodie.app.engine.deviceusage

import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayActiveAppTest {
    private val sessions = listOf(AppSession("spotify", 1_025, 1_046), AppSession("teams", 900, 1_000), AppSession("chrome", 1_046, 1_100))

    @Test
    fun `app ativo e a sessao que contem o instante`() {
        assertEquals("spotify", VisitPhoneUsageCalculator.activeAppAt(1_037, sessions)!!.packageName)
        assertEquals("teams", VisitPhoneUsageCalculator.activeAppAt(900, sessions)!!.packageName)
        assertNull("fim é exclusivo", VisitPhoneUsageCalculator.activeAppAt(1_000, sessions))
        assertNull(VisitPhoneUsageCalculator.activeAppAt(1_010, sessions))
        // Na emenda, vale o app que acabou de abrir.
        assertEquals("chrome", VisitPhoneUsageCalculator.activeAppAt(1_046, sessions)!!.packageName)
        assertEquals("spotify", VisitPhoneUsageCalculator.activeAppAt(1_030, sessions + AppSession("old", 1_000, 1_040))!!.packageName)
    }

    @Test
    fun `nome do app sem registro vem do pacote`() {
        assertEquals("Spotify", DailyPhoneInsights.fallbackLabel("com.spotify.music"))
        assertEquals("Whatsapp", DailyPhoneInsights.fallbackLabel("com.whatsapp"))
        // Heurística simples (primeiro segmento não genérico): só vale quando o app não tem nome salvo.
        assertEquals("Microsoft", DailyPhoneInsights.fallbackLabel("com.microsoft.teams"))
    }
}
