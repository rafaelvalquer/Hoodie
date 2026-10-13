package com.hoodie.app.pixel.npc.events

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.office.OfficeDocumentDeliveries
import com.hoodie.app.pixel.npc.office.OfficeExecutiveTimeline
import com.hoodie.app.pixel.npc.office.OfficeNavigationGraph
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.npc.office.OfficeNpcSpot
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class SceneStoryEventsTest {
    private fun event(id: String, start: Long, participants: List<String>, type: SceneStoryEventType = SceneStoryEventType.OFFICE_DOCUMENT_DELIVERY, priority: Int = 1) =
        SceneStoryEvent(id, type, type.scene, participants, emptyList(), start, 10_000, priority, 30_000)

    @Test
    fun `diretor aceita no maximo 2 eventos a cada 10 min sem participante em dois eventos`() {
        val candidates = listOf(
            event("a", 0, listOf("x")),
            event("b", 5_000, listOf("x"), SceneStoryEventType.OFFICE_COFFEE_CHAT), // x ocupado
            event("c", 60_000, listOf("y"), SceneStoryEventType.RESTAURANT_SPILL_HELP),
            event("d", 120_000, listOf("z"), SceneStoryEventType.SHOPPING_DROPPED_BAG), // estoura o orçamento
            event("e", 620_000, listOf("z"), SceneStoryEventType.SHOPPING_DROPPED_BAG),
        )
        val accepted = SceneEventDirector.admit(candidates).map { it.id }
        assertEquals(listOf("a", "c", "e"), accepted)
        assertEquals(accepted, SceneEventDirector.admit(candidates.reversed()).map { it.id })
    }

    @Test
    fun `fases seguem os marcos do evento e cancelar e terminal`() {
        val e = SceneStoryEvent("x", SceneStoryEventType.OFFICE_DOCUMENT_DELIVERY, SceneId.OFFICE, listOf("a"), emptyList(),
            1_000, 9_000, 1, 0, interactAt = 3_000, resolveAt = 6_000)
        assertEquals(StoryEventPhase.PLANNED, SceneEventDirector.phaseAt(e, 0))
        assertEquals(StoryEventPhase.APPROACHING, SceneEventDirector.phaseAt(e, 1_000))
        assertEquals(StoryEventPhase.INTERACTING, SceneEventDirector.phaseAt(e, 3_000))
        assertEquals(StoryEventPhase.RESOLVING, SceneEventDirector.phaseAt(e, 6_000))
        assertEquals(StoryEventPhase.COMPLETED, SceneEventDirector.phaseAt(e, 10_000))
        assertEquals(StoryEventPhase.CANCELLED, SceneEventDirector.phaseAt(e, 4_000, cancelledAt = 3_500))
    }

    // ───── Entrega de documento no escritório ─────

    private fun session(seed: Int) = OfficeNpcDirector.createSession(SceneEnv(DayPeriod.DAY, 9 * 60, variant = seed, daySeed = seed))

    private fun deliveries(seed: Int): Pair<OfficeDocumentDeliveries, List<com.hoodie.app.pixel.npc.office.OfficeDocumentDelivery>> {
        val s = session(seed)
        val d = s.social.deliveries!!
        val found = (0 until 20L).mapNotNull { cycle -> d.deliveryAt(cycle * OfficeExecutiveTimeline.CYCLE_MS) }
        return d to found
    }

    @Test
    fun `entregas acontecem nas cenas atuais, dentro do orcamento e de forma deterministica`() {
        val all = (1..12).flatMap { seed -> deliveries(seed).second.map { seed to it } }
        assertTrue("nenhuma entrega em 12 dias de 80 min", all.size >= 6)
        (1..12).forEach { seed ->
            val events = deliveries(seed).second.map { it.event }
            events.forEach { e -> assertTrue(events.count { it.startAt > e.startAt - 600_000 && it.startAt <= e.startAt } <= 2) }
        }
        val seed = all.first().first
        assertEquals(deliveries(seed).second, deliveries(seed).second)
    }

    @Test
    fun `o documento e um objeto unico que passa de mao em mao em ordem`() {
        val (deliveries, found) = (1..12).map { deliveries(it) }.first { it.second.isNotEmpty() }
        val d = found.first()
        val expected = listOf(StoryObjectHolder.ON_DESK, StoryObjectHolder.HELD_BY_RABBIT, StoryObjectHolder.HELD_BY_BULLDOG, StoryObjectHolder.CARRIED_OUT)
        val seen = mutableListOf<StoryObjectHolder>()
        var t = d.cycleStart
        while (t < d.cycleStart + OfficeExecutiveTimeline.CYCLE_MS) {
            val snap = deliveries.snapshotAt(t)!!
            assertEquals(1, snap.objects.size)
            assertEquals(d.documentId, snap.objects.single().objectId)
            val holder = snap.objects.single().holder
            if (seen.lastOrNull() != holder) seen += holder
            // Nunca em duas mãos: só quem está com a folha a desenha.
            val inHands = listOf("rabbit_analyst", "bulldog_exec").count { deliveries.heldItemAt(it, t) == Item.DOCUMENT }
            assertEquals(if (holder == StoryObjectHolder.HELD_BY_RABBIT || holder == StoryObjectHolder.HELD_BY_BULLDOG) 1 else 0, inHands)
            t += 50
        }
        assertEquals(expected, seen)
    }

    @Test
    fun `coelho sai sentado, anda sem teleporte ate o chefe e volta a sentar na propria mesa`() {
        val s = (1..12).map { session(it) }.first { sess -> (0 until 20L).any { sess.social.deliveries!!.deliveryAt(it * OfficeExecutiveTimeline.CYCLE_MS) != null } }
        val d = (0 until 20L).firstNotNullOf { s.social.deliveries!!.deliveryAt(it * OfficeExecutiveTimeline.CYCLE_MS) }
        val rabbit = s.npcSlots(9 * 60).first { it.definition.id == "rabbit_analyst" }.officeBrain!!
        val bulldog = s.npcSlots(9 * 60).first { it.definition.id == "bulldog_exec" }.officeBrain!!
        val desk = OfficeNavigationGraph.spots.getValue(OfficeNpcSpot.DESK_LEFT)
        val before = rabbit.movementAt(d.startAt - 1)
        assertTrue(before.seated)
        assertEquals(desk.x, before.x)
        var previous = before
        var t = d.startAt
        while (t <= d.endsAt + 2_000) {
            val m = rabbit.movementAt(t)
            assertTrue("salto em $t: ${previous.x}→${m.x}", abs(m.x - previous.x) <= 2 && abs(m.floorY - previous.floorY) <= 2)
            previous = m
            t += 20
        }
        val handoff = rabbit.movementAt(d.arriveAt + 100)
        val boss = bulldog.movementAt(d.arriveAt + 100)
        assertEquals(boss.x - OfficeDocumentDeliveries.HANDOFF_GAP_PX, handoff.x)
        assertTrue(handoff.facingRight)
        assertFalse(boss.facingRight)
        assertEquals(NpcAnimation.READ_DOCUMENT, bulldog.movementAt(d.handoffAt + 50).animation)
        val after = rabbit.movementAt(d.endsAt + 100)
        assertTrue(after.seated)
        assertEquals(desk.x, after.x)
        // O bulldog sai do escritório com a folha, e na próxima visita sem entrega a mesa fica vazia.
        assertEquals(Item.DOCUMENT, bulldog.movementAt(d.bulldogExitAt - 3_000).heldItem)
        assertEquals(StoryObjectHolder.CARRIED_OUT, s.social.deliveries!!.snapshotAt(d.bulldogExitAt + 1)!!.objects.single().holder)
    }

    @Test
    fun `colegas ficam sentados nas proprias mesas durante toda a entrega`() {
        val catDesk = OfficeNavigationGraph.spots.getValue(OfficeNpcSpot.DESK_RIGHT)
        (1..12).forEach { seed ->
            val s = session(seed)
            val cat = s.npcSlots(9 * 60).first { it.definition.id == "cat_colleague" }.officeBrain!!
            (0 until 20L).mapNotNull { s.social.deliveries!!.deliveryAt(it * OfficeExecutiveTimeline.CYCLE_MS) }.forEach { d ->
                var t = d.startAt
                while (t < d.endsAt) {
                    val m = cat.movementAt(t)
                    assertTrue("seed $seed: gato fora da mesa em $t (x=${m.x})", m.seated && m.x == catDesk.x)
                    t += 250
                }
            }
        }
    }

    @Test
    fun `entrega nunca cruza conversa da rotina nem fala do chefe`() {
        (1..12).forEach { seed ->
            val s = session(seed)
            (0 until 20L).mapNotNull { s.social.deliveries!!.deliveryAt(it * OfficeExecutiveTimeline.CYCLE_MS) }.forEach { d ->
                var t = d.startAt
                while (t < d.endsAt) { assertNull(s.social.activeEventAt(t)); t += 500 }
                val visitStart = d.cycleStart + OfficeExecutiveTimeline.ENTRY_MS
                OfficeDocumentDeliveries.SPEECH_WINDOWS.forEach { w ->
                    assertFalse(visitStart + w.first < d.returnAt && d.arriveAt < visitStart + w.last)
                }
            }
        }
    }

    @Test
    fun `chefe puxa conversa com o Hoodie no cafe, sem cruzar a entrega nem as falas dele`() {
        var chats = 0
        (1..12).forEach { seed ->
            val s = session(seed)
            val d = s.social.deliveries!!
            val bulldog = s.npcSlots(9 * 60).first { it.definition.id == "bulldog_exec" }.officeBrain!!
            (0 until 20L).forEach { cycle ->
                val t0 = cycle * OfficeExecutiveTimeline.CYCLE_MS
                val chat = d.chatAt(t0) ?: return@forEach
                chats++
                assertNull("entrega e conversa no mesmo ciclo", d.deliveryAt(t0))
                val visitStart = t0 + OfficeExecutiveTimeline.ENTRY_MS
                OfficeDocumentDeliveries.SPEECH_WINDOWS.forEach { w ->
                    assertFalse(visitStart + w.first < chat.event.endsAt && chat.event.startAt < visitStart + w.last)
                }
                val talk = bulldog.frameStateAt((chat.talkAt + chat.talkEndsAt) / 2)
                assertEquals(NpcAnimation.TALK, talk.movement.animation)
                assertEquals(chat.line, talk.speech?.line)
                assertEquals(Item.MUG, talk.movement.heldItem)
                assertEquals(com.hoodie.app.pixel.sprite.Facing.FRONT, talk.movement.facing)
            }
        }
        assertTrue("só $chats conversas", chats >= 4)
    }

    @Test
    fun `cena desenha a folha so enquanto esta na mesa`() {
        val (deliveries, found) = (1..12).map { deliveries(it) }.first { it.second.isNotEmpty() }
        val d = found.first()
        assertNotNull(deliveries.snapshotAt(d.cycleStart + 10))
        assertEquals(StoryObjectHolder.ON_DESK, deliveries.snapshotAt(d.startAt)!!.objects.single().holder)
        assertEquals(StoryObjectHolder.HELD_BY_RABBIT, deliveries.snapshotAt(d.pickupAt)!!.objects.single().holder)
    }
}
