package com.hoodie.app.engine.diary

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.engine.diary.DiaryRegressionScenario.CHROME
import com.hoodie.app.engine.diary.DiaryRegressionScenario.MAPS
import com.hoodie.app.engine.diary.DiaryRegressionScenario.SPOTIFY
import com.hoodie.app.engine.diary.DiaryRegressionScenario.TEAMS
import com.hoodie.app.engine.diary.DiaryRegressionScenario.WHATSAPP
import com.hoodie.app.engine.diary.DiaryRegressionScenario.YOUTUBE
import com.hoodie.app.pixel.diary.DiaryMapLayoutEngine
import com.hoodie.app.presentation.screens.diary.visitsOfNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** Bottom sheet por visita: o Trabalho tem duas visitas, cada uma com o próprio celular. */
class PlaceVisitPhoneIntegrationTest {
    private val zone: ZoneId = ZoneId.of("America/Sao_Paulo")
    private val diary = DiaryRegressionScenario.create(LocalDate.of(2026, 10, 5), zone)
    private val details = ReplayHudAssembler.visitDetails(diary, diary.replay.endAt)
    private val layout = DiaryMapLayoutEngine.layout(diary.visits)

    private fun appsOf(i: Int) = details[i].phoneUsage?.apps?.map { it.packageName }

    @Test
    fun `cada visita tem o proprio uso do celular`() {
        assertEquals(listOf(WHATSAPP), appsOf(0))   // Casa de manhã
        assertEquals(listOf(TEAMS), appsOf(1))      // Trabalho, visita 1
        assertEquals(listOf(YOUTUBE), appsOf(2))    // Restaurante
        assertEquals(listOf(CHROME), appsOf(3))     // Trabalho, visita 2
        assertEquals(listOf(SPOTIFY), appsOf(4))    // Academia
        assertEquals(listOf(YOUTUBE), appsOf(5))    // Casa à noite
        // Maps foi usado no transporte: não pertence a nenhuma visita.
        assertTrue(details.none { d -> d.phoneUsage?.apps?.any { it.packageName == MAPS } == true })
        assertEquals(50 * 60_000L, details[1].phoneUsage!!.foregroundMs)
        assertEquals("Teams", details[1].phoneUsage!!.apps.single().appLabel)
    }

    @Test
    fun `o no do trabalho junta as duas visitas na ordem, sem misturar o celular`() {
        val work = layout.nodes.single { it.type == PlaceType.WORK }
        val mine = visitsOfNode(work, details)
        assertEquals(listOf(1, 3), mine.map { it.index })
        assertEquals(listOf(listOf(TEAMS), listOf(CHROME)), mine.map { d -> d.phoneUsage!!.apps.map { it.packageName } })
        assertEquals(listOf(HoodieActivity.WORKING), mine[0].hoodieActivities)
        assertTrue(mine[0].events.any { it.title == "Chegou ao trabalho" })
    }

    @Test
    fun `sem dados do celular as visitas continuam e o celular fica vazio`() {
        val noPhone = ReplayHudAssembler.visitDetails(diary.copy(phoneInsights = null), diary.replay.endAt)
        assertEquals(6, noPhone.size)
        assertNull(noPhone[1].phoneUsage)
    }
}
