package com.hoodie.app.engine.deviceusage

import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VisitPhoneUsageCalculatorTest {
    private val label = { pkg: String -> pkg.uppercase() }
    private val cat = { _: String -> HoodieAppCategory.OTHER }
    private fun calc(arrival: Long, departure: Long?, sessions: List<AppSession>, now: Long = 1_000) =
        VisitPhoneUsageCalculator.calculate(arrival, departure, now, sessions, label, cat)

    @Test
    fun `intersecao corta as sessoes nas bordas da visita`() {
        val sessions = listOf(
            AppSession("a", 50, 150),   // começa antes: conta 100..150
            AppSession("b", 120, 180),  // inteira dentro
            AppSession("a", 190, 260),  // termina depois: conta 190..200
            AppSession("c", 300, 400),  // fora
        )
        val u = calc(100, 200, sessions)!!
        assertEquals(50L + 60L + 10L, u.foregroundMs)
        // Empate de 60 ms: desempata pelo pacote.
        assertEquals(listOf("a" to 60L, "b" to 60L), u.apps.map { it.packageName to it.foregroundMs })
        assertEquals("A", u.apps.first { it.packageName == "a" }.appLabel)
    }

    @Test
    fun `ordena por tempo e desempata pelo pacote`() {
        val u = calc(0, 100, listOf(AppSession("z", 0, 10), AppSession("y", 10, 40), AppSession("x", 40, 50)))!!
        assertEquals(listOf("y", "x", "z"), u.apps.map { it.packageName })
    }

    @Test
    fun `visita em andamento vai ate agora e visita sem uso e null`() {
        assertEquals(30L, calc(100, null, listOf(AppSession("a", 110, 400)), now = 140)!!.foregroundMs)
        assertNull(calc(100, 200, listOf(AppSession("a", 0, 100), AppSession("b", 200, 300))))
        assertNull(calc(200, 100, listOf(AppSession("a", 0, 1_000))))
    }

    @Test
    fun `overlap e zero sem intersecao`() {
        assertEquals(0L, VisitPhoneUsageCalculator.overlap(0, 10, 20, 30))
        assertEquals(5L, VisitPhoneUsageCalculator.overlap(0, 25, 20, 30))
    }
}
