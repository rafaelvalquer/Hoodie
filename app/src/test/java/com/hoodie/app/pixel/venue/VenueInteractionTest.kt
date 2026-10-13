package com.hoodie.app.pixel.venue

import com.hoodie.app.pixel.npc.venue.VenueNpcBrain
import com.hoodie.app.pixel.npc.venue.VenueAction
import com.hoodie.app.pixel.npc.venue.VenueDecisionContext
import com.hoodie.app.pixel.npc.venue.VenueNpcProfile
import com.hoodie.app.pixel.npc.venue.VenueRole
import com.hoodie.app.pixel.npc.venue.VenueNpcState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Ações encadeadas em ordem válida: pedir → atender → preparar → entregar → retirar. */
class VenueInteractionTest {

    @Test fun ordersFollowAValidChainOneAtATimePerCounter() {
        VenueTestFixtures.scenes.forEach { scene ->
            (0 until 3).forEach { variant ->
                val s = VenueTestFixtures.session(scene, variant, 49_000 + variant)
                s.snapshotAt(600_000)
                val orders = s.coordinatorForTests().orders()
                orders.forEach { o ->
                    val taken = o.takenAt; val ready = o.readyAt; val picked = o.pickedAt
                    if (taken != null) assertTrue("$scene #${o.id}: atendido antes do pedido", taken >= o.placedAt)
                    if (ready != null) assertTrue("$scene #${o.id}: pronto antes de atender", taken != null && ready >= taken)
                    if (picked != null) assertTrue("$scene #${o.id}: retirado antes de ficar pronto", ready != null && picked >= ready)
                }
                // Um atendimento por vez por balcão: [pedido, retirada) nunca se sobrepõem.
                orders.groupBy { it.service }.forEach { (service, list) ->
                    list.filter { it.pickedAt != null }.sortedBy { it.placedAt }.zipWithNext().forEach { (a, b) ->
                        assertTrue("$scene $service: #${a.id} e #${b.id} ao mesmo tempo", b.placedAt >= a.pickedAt!!)
                    }
                }
            }
        }
    }

    /** O objeto só aparece na mão do cliente depois de ele retirar o pedido (nunca durante o preparo). */
    @Test fun customersHoldTheirOrderOnlyAfterPickingItUp() {
        VenueTestFixtures.scenes.forEach { scene ->
            val s = VenueTestFixtures.session(scene, 0, 50_000)
            s.snapshotAt(300_000)
            val orders = s.coordinatorForTests().orders()
            val customers = orders.map { it.customerId }.toSet()
            customers.forEach { id ->
                val mine = orders.filter { it.customerId == id }
                VenueTestFixtures.track(s, id, 300_000, 100).forEach { (t, st) ->
                    val serviceItem = st.held != com.hoodie.app.pixel.sprite.Item.NONE && st.held in SERVICE_ITEMS
                    if (!serviceItem) return@forEach
                    assertTrue("$scene $id t=$t segura ${st.held} sem ter retirado", mine.any { o -> o.pickedAt != null && o.pickedAt!! <= t })
                }
            }
        }
    }

    @Test fun everyNpcActsOverTenMinutes() {
        VenueTestFixtures.scenes.forEach { scene ->
            val s = VenueTestFixtures.session(scene, 0, 51_000)
            val shots = VenueTestFixtures.snapshots(s, 600_000, 250)
            VenueTestFixtures.ids(s).forEach { id ->
                val states = shots.map { snap -> snap.npcs.single { it.npcId == id }.state }.toSet()
                assertTrue("$scene $id parado: $states", states.size >= 2)
                assertTrue("$scene $id nunca age: $states", VenueNpcState.PERFORMING_ACTION in states || VenueNpcState.INTERACTING in states)
            }
        }
    }

    @Test fun brainRespectsAvailabilityCooldownAndRepetition() {
        val p = VenueNpcProfile("x", VenueRole.CAFE_CUSTOMER, mapOf(VenueAction.CONSUME to 80, VenueAction.PHONE to 20), 0f, 1_000, 2_000)
        assertEquals(VenueAction.PHONE, VenueNpcBrain.choose(p, 1, 0, VenueDecisionContext(0, setOf(VenueAction.PHONE), emptyList())))
        assertEquals(null, VenueNpcBrain.choose(p, 1, 0, VenueDecisionContext(0, setOf(VenueAction.READ), emptyList())))
        val cooled = VenueDecisionContext(0, setOf(VenueAction.CONSUME, VenueAction.PHONE), emptyList(), readyAt = mapOf(VenueAction.CONSUME to 5_000))
        assertEquals(VenueAction.PHONE, VenueNpcBrain.choose(p, 1, 3, cooled))
        val repeated = VenueDecisionContext(0, setOf(VenueAction.CONSUME, VenueAction.PHONE), listOf(VenueAction.CONSUME, VenueAction.CONSUME))
        repeat(50) { assertEquals(VenueAction.PHONE, VenueNpcBrain.choose(p, 1, it.toLong(), repeated)) }
        // Pesos: em muitas decisões, a proporção segue o perfil.
        val free = VenueDecisionContext(0, setOf(VenueAction.CONSUME, VenueAction.PHONE), emptyList())
        val share = (0L until 2_000L).count { VenueNpcBrain.choose(p, 9, it, free) == VenueAction.CONSUME } / 2_000.0
        assertEquals(0.8, share, 0.04)
        assertNotNull(VenueNpcBrain.idleMs(p, 1, 1).takeIf { it in 1_000L..2_000L })
        assertFalse(VenueNpcBrain.MAX_REPEAT < 1)
    }

    private companion object {
        /** Objetos que só existem depois de um atendimento. */
        val SERVICE_ITEMS = setOf(
            com.hoodie.app.pixel.sprite.Item.MUG, com.hoodie.app.pixel.sprite.Item.POPCORN, com.hoodie.app.pixel.sprite.Item.ICE_CREAM,
            com.hoodie.app.pixel.sprite.Item.KEYCARD,
        )
    }
}
