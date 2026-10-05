package com.hoodie.app.engine

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.Needs
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.Routine
import com.hoodie.app.core.model.SleepSchedule
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.engine.dialogue.DialogueEngine
import com.hoodie.app.engine.dialogue.DialogueInput
import com.hoodie.app.engine.hoodie.DecisionInput
import com.hoodie.app.engine.hoodie.HoodieDecisionEngine
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.random.Random

/** Escola, Compras, Família e Passeio: tipo de local → contexto → atividade própria. */
class PlaceActivityCoverageTest {

    private val zone = ZoneId.of("America/Sao_Paulo")
    // Terça, 15h: fora do sono e do horário de almoço.
    private val tuesday = ZonedDateTime.of(2026, 9, 29, 15, 0, 0, 0, zone)

    private fun input(ctx: UserContextType, needs: Needs = Needs()) =
        DecisionInput(tuesday, ctx, needs, previous = null, routine = Routine(), sleep = SleepSchedule(), dayOff = false)

    private fun weights(ctx: UserContextType, needs: Needs = Needs()) = HoodieDecisionEngine.weights(input(ctx, needs))

    private fun share(w: Map<HoodieActivity, Int>, a: HoodieActivity) = (w[a] ?: 0).toDouble() / w.values.sum()

    private val primary = mapOf(
        UserContextType.STUDY to HoodieActivity.STUDYING,
        UserContextType.SHOPPING to HoodieActivity.SHOPPING,
        UserContextType.VISITING to HoodieActivity.SOCIALIZING,
        UserContextType.LEISURE to HoodieActivity.SIGHTSEEING,
    )

    @Test
    fun `cada tipo de local mapeia para o contexto esperado`() {
        assertEquals(UserContextType.STUDY, PlaceType.SCHOOL.toContext())
        assertEquals(UserContextType.DINING, PlaceType.RESTAURANT.toContext())
        assertEquals(UserContextType.SHOPPING, PlaceType.MARKET.toContext())
        assertEquals(UserContextType.VISITING, PlaceType.FAMILY.toContext())
        assertEquals(UserContextType.LEISURE, PlaceType.LEISURE.toContext())
        // "Outro" não vira passeio: é um lugar desconhecido.
        assertEquals(UserContextType.UNKNOWN, PlaceType.OTHER.toContext())
    }

    @Test
    fun `atividade principal domina cada contexto novo`() {
        primary.forEach { (ctx, activity) ->
            val w = weights(ctx)
            assertEquals("$ctx -> $w", activity, w.maxBy { it.value }.key)
            // Mas não é 100%: o Hoodie mantém vida própria.
            assertTrue("$ctx -> $w", w.size >= 4)
        }
    }

    @Test
    fun `DINING prioriza refeicao sem comportamento de passeio`() {
        val w = weights(UserContextType.DINING)
        assertEquals(HoodieActivity.EATING, w.maxBy { it.value }.key)
        assertFalse(HoodieActivity.SIGHTSEEING in w)
        assertFalse(HoodieActivity.WALKING in w)
        assertTrue(HoodieActivity.PHONE in w && HoodieActivity.COFFEE in w && HoodieActivity.RESTING in w)
    }

    @Test
    fun `LUNCH e DINING compartilham a cena de restaurante`() {
        assertEquals(SceneId.RESTAURANT, VisualDirector.resolve(HoodieActivity.EATING, UserContextType.LUNCH).scene)
        assertEquals(SceneId.RESTAURANT, VisualDirector.resolve(HoodieActivity.EATING, UserContextType.DINING).scene)
    }

    @Test
    fun `atividades novas so aparecem no proprio contexto`() {
        UserContextType.entries.forEach { ctx ->
            val w = weights(ctx)
            primary.filterKeys { it != ctx }.values.forEach { other -> assertFalse("$other em $ctx", other in w) }
        }
    }

    @Test
    fun `necessidades mudam os pesos`() {
        // Foco baixo: estuda menos e se distrai no celular.
        assertTrue(share(weights(UserContextType.STUDY, Needs(focus = 20)), HoodieActivity.STUDYING) < share(weights(UserContextType.STUDY), HoodieActivity.STUDYING))
        assertTrue(share(weights(UserContextType.STUDY, Needs(focus = 20)), HoodieActivity.PHONE) > share(weights(UserContextType.STUDY), HoodieActivity.PHONE))
        // Fome alta nas compras: lanche.
        assertTrue(share(weights(UserContextType.SHOPPING, Needs(hunger = 80)), HoodieActivity.EATING) > share(weights(UserContextType.SHOPPING), HoodieActivity.EATING))
        // Social baixo na família: mais conversa.
        assertTrue(share(weights(UserContextType.VISITING, Needs(social = 20)), HoodieActivity.SOCIALIZING) > share(weights(UserContextType.VISITING), HoodieActivity.SOCIALIZING))
        // Energia baixa no passeio: descansa mais e caminha menos.
        val tired = weights(UserContextType.LEISURE, Needs(energy = 20))
        assertTrue(share(tired, HoodieActivity.RESTING) > share(weights(UserContextType.LEISURE), HoodieActivity.RESTING))
        assertTrue(share(tired, HoodieActivity.WALKING) < share(weights(UserContextType.LEISURE), HoodieActivity.WALKING))
    }

    @Test
    fun `energia minima nao inicia atividade longa nos lugares novos`() {
        primary.keys.forEach { ctx ->
            val w = weights(ctx, Needs(energy = 5))
            assertFalse("$ctx -> $w", w.keys.any { it.isLong })
        }
    }

    @Test
    fun `duracoes das atividades novas`() {
        val ranges = mapOf(
            HoodieActivity.STUDYING to 20L..60L, HoodieActivity.SHOPPING to 15L..50L,
            HoodieActivity.SOCIALIZING to 15L..45L, HoodieActivity.SIGHTSEEING to 15L..45L,
        )
        ranges.forEach { (a, range) ->
            repeat(30) { seed ->
                val minutes = HoodieDecisionEngine.durationFor(a, input(UserContextType.STUDY), Random(seed)) / MINUTE_MS
                assertTrue("$a $minutes", minutes in range)
            }
        }
    }

    @Test
    fun `timeline e diario tem texto proprio para cada atividade nova`() {
        assertEquals("começou a estudar", HoodieActivity.STUDYING.pastTense)
        assertEquals("foi às compras", HoodieActivity.SHOPPING.pastTense)
        assertEquals("foi visitar a família", HoodieActivity.SOCIALIZING.pastTense)
        assertEquals("saiu para passear", HoodieActivity.SIGHTSEEING.pastTense)
        // Textos e emojis únicos: nada de "jogando" para estudar.
        assertEquals(HoodieActivity.entries.size, HoodieActivity.entries.map { it.label }.toSet().size)
        assertEquals(HoodieActivity.entries.size, HoodieActivity.entries.map { it.pastTense }.toSet().size)
    }

    @Test
    fun `atividades novas ficam no fim do enum para nao mudar as seeds antigas`() {
        val tail = HoodieActivity.entries.takeLast(4)
        assertEquals(listOf(HoodieActivity.STUDYING, HoodieActivity.SHOPPING, HoodieActivity.SOCIALIZING, HoodieActivity.SIGHTSEEING), tail)
    }

    @Test
    fun `falas proprias para cada atividade principal nova`() {
        val dialogues = DialogueEngine(DialogueEngine.parse(File("src/main/assets/metadata/dialogues.json").readText()))
        primary.forEach { (ctx, activity) ->
            val input = DialogueInput(ctx, activity, contextMinutes = 20, hour = 15, weekend = false, overtime = false, energy = 70, arrivedEarly = false)
            assertTrue("$ctx/$activity sem fala própria", dialogues.matches(input).any { it.activity == activity })
            assertNotNull(dialogues.pick(input, Random(1)))
        }
    }
}
