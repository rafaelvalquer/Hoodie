package com.hoodie.app.engine.personality

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.Needs
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.daystate.DayState
import com.hoodie.app.domain.personality.HoodiePersonalityProfile
import com.hoodie.app.domain.personality.PersonalityWeightPolicy
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.ReactionDirector
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.PersonalityVisuals
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HoodiePersonalityTest {
    private val catalog = PersonalityDialogueCatalog.parse(File("src/main/assets/metadata/personality-dialogues.json").readText())
    private val engine = HoodiePersonalityEngine(catalog)
    private val minute = 60_000L
    private val hour = 60 * minute

    private fun ctx(
        trigger: PersonalityTrigger, at: Long = 10 * hour, day: Int = 100, visit: Long = 1L,
        activity: HoodieActivity = HoodieActivity.IDLE, context: UserContextType = UserContextType.HOME, hourOfDay: Int = 9,
    ) = PersonalityContext(trigger, SceneId.HOME, activity, Needs(), null, at, day, visit, context, hourOfDay)

    // ───── Perfil e pesos ─────

    @Test
    fun `perfil padrao tem os tracos do plano e recusa valores fora de 0 a 100`() {
        val p = HoodiePersonalityProfile.DEFAULT
        assertEquals(listOf(80, 85, 65, 70, 50), listOf(p.curiosity, p.coffeeAffinity, p.sociability, p.restAffinity, p.spontaneity))
        assertTrue(runCatching { HoodiePersonalityProfile(curiosity = 101) }.isFailure)
        assertTrue(runCatching { HoodiePersonalityProfile(restAffinity = -1) }.isFailure)
    }

    @Test
    fun `politica de pesos so ajusta elegiveis com peso positivo e nunca desbloqueia`() {
        val p = HoodiePersonalityProfile.DEFAULT
        assertEquals(1f + 85 / 300f, PersonalityWeightPolicy.coffeeMultiplier(p), 1e-6f)
        assertEquals(1f + 70 / 300f, PersonalityWeightPolicy.restMultiplier(p), 1e-6f)
        assertEquals(0, PersonalityWeightPolicy.adjust(0, 2f, eligible = true))
        assertEquals(10, PersonalityWeightPolicy.adjust(10, 2f, eligible = false))
        assertEquals(1, PersonalityWeightPolicy.adjust(1, 1.01f, eligible = true))
        assertEquals(10, PersonalityWeightPolicy.adjust(8, PersonalityWeightPolicy.coffeeMultiplier(p), eligible = true))
    }

    @Test
    fun `cafe e descanso ganham peso sem criar nem remover microacoes`() {
        val neutral = VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK, personality = null)
        val personal = VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK, personality = HoodiePersonalityProfile.DEFAULT)
        assertEquals(neutral.actions.map { it.anim }, personal.actions.map { it.anim })
        fun w(v: List<MicroAction>, a: AnimationId) = v.filter { it.anim == a }.sumOf { it.weight }
        assertTrue(w(personal.actions, AnimationId.DRINK) > w(neutral.actions, AnimationId.DRINK))
        assertTrue(w(personal.actions, AnimationId.STRETCH_SIT) > w(neutral.actions, AnimationId.STRETCH_SIT))
        assertEquals(w(neutral.actions, AnimationId.WORK_TYPING), w(personal.actions, AnimationId.WORK_TYPING))
        // Uma ação só (caminhar) não muda: não há o que escolher.
        val single = listOf(MicroAction(AnimationId.DRINK, 5))
        assertEquals(single, PersonalityVisuals.apply(single, HoodiePersonalityProfile.DEFAULT))
    }

    @Test
    fun `gestos ao abrir o app mudam de jeito mas nao de frequencia`() {
        val weights = ReactionDirector.weights(HoodiePersonalityProfile.DEFAULT)
        assertEquals(100, weights.sum())
        assertEquals(ReactionDirector.WEIGHTS[0], weights[0])
        assertTrue(weights[1] > ReactionDirector.WEIGHTS[1]) // curioso: percebe mais
        assertTrue(weights.all { it >= 1 })
        assertEquals(ReactionDirector.WEIGHTS, ReactionDirector.weights(null))
    }

    // ───── Motor ─────

    @Test
    fun `decisao e deterministica e nao altera a memoria`() {
        val ledger = PersonalityLedger()
        val c = ctx(PersonalityTrigger.NEW_PLACE_CONFIRMED, visit = 77)
        val a = engine.decide(c, ledger)
        val b = HoodiePersonalityEngine(catalog).decide(c, ledger)
        assertEquals(a, b)
        assertNotNull(a)
        assertEquals(PersonalityLedger(), ledger)
    }

    @Test
    fun `cumprimento de abertura no maximo uma vez por dia`() {
        val c = ctx(PersonalityTrigger.FIRST_OPEN_OF_DAY)
        val first = engine.decide(c, PersonalityLedger())!!
        assertNull(first.animation) // o gesto de abertura é do ReactionDirector
        val ledger = PersonalityLedger().record(first, c)
        assertNull(engine.decide(c.copy(occurredAt = c.occurredAt + 5 * hour), ledger))
        assertNotNull(engine.decide(c.copy(daySeed = 101, occurredAt = c.occurredAt + 24 * hour), ledger))
    }

    @Test
    fun `pausa no maximo uma vez a cada 60 min e fim do dia uma vez por dia`() {
        val pause = ctx(PersonalityTrigger.WORK_BREAK, activity = HoodieActivity.COFFEE, context = UserContextType.WORK)
        var ledger = PersonalityLedger().record(engine.decide(pause, PersonalityLedger())!!, pause)
        assertNull(engine.decide(pause.copy(occurredAt = pause.occurredAt + 59 * minute), ledger))
        assertNotNull(engine.decide(pause.copy(occurredAt = pause.occurredAt + 60 * minute), ledger))

        val night = ctx(PersonalityTrigger.DAY_WINDING_DOWN, hourOfDay = 22)
        ledger = ledger.record(engine.decide(night, ledger)!!, night)
        assertNull(engine.decide(night.copy(occurredAt = night.occurredAt + 3 * hour), ledger))
    }

    @Test
    fun `lugar novo uma vez por visita e comentarios com 2 min de distancia`() {
        val place = ctx(PersonalityTrigger.NEW_PLACE_CONFIRMED, visit = 5)
        val first = engine.decide(place, PersonalityLedger())!!
        assertNotNull(first.text)
        val ledger = PersonalityLedger().record(first, place)
        assertNull(engine.decide(place.copy(occurredAt = place.occurredAt + hour), ledger))
        // Outra visita 1 min depois: o gesto acontece, mas sem fala (comentário cedo demais).
        val other = engine.decide(place.copy(visitSeed = 6, occurredAt = place.occurredAt + minute), ledger)!!
        assertNull(other.text)
        assertNotNull(other.animation)
        assertNotNull(engine.decide(place.copy(visitSeed = 6, occurredAt = place.occurredAt + 2 * minute), ledger)!!.text)
    }

    @Test
    fun `nao interrompe caminhada nem troca de cena e no trabalho so fala`() {
        val c = ctx(PersonalityTrigger.ARRIVED_AT_WORK, context = UserContextType.WORK)
        assertNull(engine.decide(c, PersonalityLedger(), PersonalityGate(walking = true)))
        assertNull(engine.decide(c, PersonalityLedger(), PersonalityGate(sceneTransition = true)))
        assertNull(engine.decide(c, PersonalityLedger(), PersonalityGate(npcSpeaking = true)))
        assertNull(engine.decide(c, PersonalityLedger(), PersonalityGate(busyWith = ReactionPriority.SOCIAL_EVENT)))
        val atDesk = engine.decide(c, PersonalityLedger(), PersonalityGate(workAction = true))!!
        assertNull(atDesk.animation)
        assertNotNull(atDesk.text)
        assertNotNull(engine.decide(c, PersonalityLedger(), PersonalityGate(busyWith = ReactionPriority.AMBIENT_SPEECH)))
    }

    @Test
    fun `mesma fala nao se repete de perto`() {
        val pause = ctx(PersonalityTrigger.WORK_BREAK, activity = HoodieActivity.COFFEE, context = UserContextType.WORK)
        val first = engine.decide(pause, PersonalityLedger())!!
        val ledger = PersonalityLedger().record(first, pause)
        val later = pause.copy(occurredAt = pause.occurredAt + 61 * minute)
        assertTrue(engine.decide(later, ledger)!!.dialogueId != first.dialogueId)
    }

    @Test
    fun `tracos liberam falas proprias`() {
        val shy = HoodiePersonalityEngine(catalog, HoodiePersonalityProfile(coffeeAffinity = 10))
        val pause = ctx(PersonalityTrigger.WORK_BREAK, activity = HoodieActivity.COFFEE, context = UserContextType.WORK)
        assertEquals("break_coffee_01", engine.decide(pause, PersonalityLedger())!!.dialogueId)
        assertTrue(shy.decide(pause, PersonalityLedger())!!.dialogueId != "break_coffee_01")
    }

    // ───── Catálogo ─────

    @Test
    fun `falas curtas, unicas, sem culpa e com uma opcao para cada gatilho`() {
        assertEquals(catalog.size, catalog.map { it.id }.toSet().size)
        assertTrue(catalog.all { it.text.length <= PersonalityDialogueCatalog.MAX_TEXT_LENGTH })
        val guilt = listOf("sumiu", "abandon", "esquec", "demorou", "finalmente", "saudade", "cadê", "onde você", "deveria", "culpa", "tarde demais")
        catalog.forEach { rule -> guilt.forEach { word -> assertFalse("${rule.id}: $word", rule.text.lowercase().contains(word)) } }
        PersonalityTrigger.entries.forEach { trigger ->
            assertTrue("$trigger sem fala genérica", catalog.any { it.trigger == trigger && it.trait == null && it.activity == null && it.context == null })
        }
        val back = catalog.filter { it.trigger == PersonalityTrigger.APP_RETURN }.map { it.text }
        assertTrue(back.contains("Olá! Que bom te ver por aqui."))
    }

    @Test
    fun `memoria sobrevive ao codec e memoria corrompida volta vazia`() {
        val c = ctx(PersonalityTrigger.BACK_HOME, visit = 9)
        val ledger = PersonalityLedger().record(engine.decide(c, PersonalityLedger())!!, c)
        assertEquals(ledger, PersonalityLedgerCodec.decode(PersonalityLedgerCodec.encode(ledger)))
        assertEquals(PersonalityLedger(), PersonalityLedgerCodec.decode("{oops"))
        assertEquals(PersonalityLedger(), PersonalityLedgerCodec.decode(null))
    }

    // ───── Detector ─────

    private fun obs(
        now: Long, contextId: Long? = 1, type: UserContextType? = UserContextType.HOME, startedAt: Long? = now - hour,
        activity: HoodieActivity = HoodieActivity.IDLE, day: DayState? = DayState.ACTIVE, dayStarted: Long? = now - hour,
    ) = PersonalityObservation(now, 100, 9, contextId, type, startedAt, null, activity, day, dayStarted)

    @Test
    fun `detector so ve bordas e nao inventa volta para casa`() {
        val d = PersonalityTriggerDetector()
        val t0 = 10 * hour
        assertEquals(emptyList<DetectedTrigger>(), d.detect(obs(t0)))
        // Primeira observação em casa: nada de "voltou para casa" sem saber de onde veio.
        val fresh = PersonalityTriggerDetector()
        assertTrue(fresh.detect(obs(t0)).none { it.trigger == PersonalityTrigger.BACK_HOME })
        // Trabalho recém-começado → chegada; depois volta para casa → casa + atividade longa.
        val work = d.detect(obs(t0 + minute, contextId = 2, type = UserContextType.WORK, startedAt = t0 + minute))
        assertEquals(listOf(PersonalityTrigger.ARRIVED_AT_WORK), work.map { it.trigger })
        assertTrue(d.detect(obs(t0 + 2 * minute, contextId = 2, type = UserContextType.WORK, startedAt = t0 + minute)).isEmpty())
        val pause = d.detect(obs(t0 + 3 * minute, contextId = 2, type = UserContextType.WORK, startedAt = t0 + minute, activity = HoodieActivity.COFFEE))
        assertEquals(listOf(PersonalityTrigger.WORK_BREAK), pause.map { it.trigger })
        val home = d.detect(obs(t0 + 3 * hour, contextId = 3, type = UserContextType.HOME, startedAt = t0 + 3 * hour))
        assertEquals(listOf(PersonalityTrigger.BACK_HOME, PersonalityTrigger.LONG_ACTIVITY_FINISHED), home.map { it.trigger })
        val night = d.detect(obs(t0 + 4 * hour, contextId = 3, startedAt = t0 + 3 * hour, day = DayState.WINDING_DOWN, dayStarted = t0 + 4 * hour))
        assertEquals(listOf(PersonalityTrigger.DAY_WINDING_DOWN), night.map { it.trigger })
    }

    @Test
    fun `abrir o app traz cumprimento e volta so depois de um tempo fora`() {
        val d = PersonalityTriggerDetector()
        d.onForeground(0)
        assertEquals(listOf(PersonalityTrigger.FIRST_OPEN_OF_DAY, PersonalityTrigger.APP_RETURN), d.detect(obs(10 * hour)).map { it.trigger })
        d.onBackground(10 * hour)
        d.onForeground(10 * hour + 5 * minute)
        assertEquals(listOf(PersonalityTrigger.FIRST_OPEN_OF_DAY), d.detect(obs(10 * hour + 5 * minute)).map { it.trigger })
        d.onNewPlaceConfirmed(42)
        assertEquals(DetectedTrigger(PersonalityTrigger.NEW_PLACE_CONFIRMED, 42), d.detect(obs(11 * hour)).single())
    }
}
