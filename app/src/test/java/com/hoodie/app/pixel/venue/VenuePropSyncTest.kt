package com.hoodie.app.pixel.venue

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.venue.BakeryAmbient
import com.hoodie.app.pixel.npc.venue.BakeryVenue
import com.hoodie.app.pixel.npc.venue.VenueAction
import com.hoodie.app.pixel.npc.venue.VenueEvent
import com.hoodie.app.pixel.npc.venue.VenueRole
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.sprite.Item
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** Xícaras, sacolas, malas e comida acompanham os eventos; o cenário reage ao que os NPCs fazem. */
class VenuePropSyncTest {

    // ───── Padaria ─────

    @Test fun baristaNeverCrossesTheCounter() {
        (0 until 3).forEach { v ->
            val s = VenueTestFixtures.session(SceneId.BAKERY, v, 70_000 + v)
            VenueTestFixtures.track(s, "barista", 600_000, 100).forEach { (t, st) ->
                assertTrue("barista na frente do balcão em t=$t (${st.x},${st.floorY})", st.floorY < BakeryVenue.COUNTER_FRONT_Y)
                assertFalse("barista sumiu em t=$t", st.hidden)
            }
            // Clientes nunca entram atrás do balcão.
            s.npcSlots.map { it.definition.id }.filter { it != "barista" }.forEach { id ->
                VenueTestFixtures.track(s, id, 600_000, 100).forEach { (t, st) ->
                    if (!st.hidden) assertTrue("$id atrás do balcão em t=$t", st.floorY > BakeryVenue.COUNTER_FRONT_Y + 10 || (st.x > BakeryVenue.COUNTER_RIGHT_X))
                }
            }
        }
    }

    @Test fun coffeeAppearsOnTheCounterOnlyAfterItIsPrepared() {
        val s = VenueTestFixtures.session(SceneId.BAKERY, 0, 71_000)
        val shots = VenueTestFixtures.snapshots(s, 600_000, 100)
        val orders = s.coordinatorForTests().orders().filter { it.product == BakeryVenue.COFFEE }
        assertTrue("nenhum café pedido em 10 min", orders.isNotEmpty())
        shots.forEach { snap ->
            val a = snap.ambient as BakeryAmbient
            val t = snap.timeMs
            val ready = orders.count { o -> o.readyAt?.let { it <= t } == true && (o.pickedAt?.let { t < it } ?: true) }
            assertEquals("t=$t xícaras prontas", ready, a.cupsReady)
            val barista = snap.npcs.single { it.role == VenueRole.BARISTA }
            assertEquals("t=$t vapor sem barista na máquina", barista.action == VenueAction.PREPARE && barista.movement.animation == NpcAnimation.PREPARE, a.brewing)
            if (a.brewing) assertTrue("preparando longe da máquina (${barista.x})", abs(barista.x - 174) <= 2)
        }
        // A xícara do barista durante o preparo vira a do cliente: entre pronto e retirado ninguém a segura.
        orders.forEach { o ->
            val ready = o.readyAt!!; val picked = o.pickedAt ?: return@forEach
            shots.filter { it.timeMs in ready + 50 until picked }.forEach { snap ->
                assertTrue("t=${snap.timeMs}: xícara pronta na mão de alguém", snap.npcs.none { it.held == Item.MUG && it.npcId == o.customerId })
            }
        }
    }

    @Test fun baristaCleansOnlyWithoutAPriorityOrder() {
        (0 until 3).forEach { v ->
            val s = VenueTestFixtures.session(SceneId.BAKERY, v, 72_000 + v)
            val track = VenueTestFixtures.track(s, "barista", 600_000, 100)
            val orders = s.coordinatorForTests().orders()
            track.filter { (_, st) -> st.movement.animation == NpcAnimation.WIPE }.forEach { (t, _) ->
                // Ninguém esperando para ser atendido enquanto ele limpa.
                assertTrue("limpando com pedido esperando em t=$t", orders.none { o -> o.placedAt <= t && (o.takenAt ?: Long.MAX_VALUE) > t + 1_000 && o.placedAt < t - 2_800 })
            }
        }
    }

    @Test fun cupsReturnedToTheCounterAreCleanedLater() {
        val s = VenueTestFixtures.session(SceneId.BAKERY, 0, 73_000)
        s.snapshotAt(600_000)
        val events = s.coordinatorForTests().events()
        val returned = events.filter { it.event == VenueEvent.CUP_RETURNED }
        assertTrue("ninguém devolveu a xícara em 10 min", returned.isNotEmpty())
        returned.dropLast(1).forEach { r -> assertTrue("xícara de ${r.at} nunca limpa", events.any { it.event == VenueEvent.COUNTER_CLEANED && it.at > r.at }) }
        val dirty = s.snapshotAt(returned.first().at + 10).ambient as BakeryAmbient
        assertTrue(dirty.dirtyCups >= 1)
    }

    @Test fun quickBuyerLeavesWithTheBagAndTheShowcaseShowsTheGap() {
        val s = VenueTestFixtures.session(SceneId.BAKERY, 0, 74_000)
        val track = VenueTestFixtures.track(s, "quick_buyer", 600_000, 100)
        val bagStart = track.firstOrNull { it.second.held == Item.SHOPPING_BAG }?.first
        assertTrue("sem sacola em 10 min", bagStart != null)
        // Com a sacola até sair pela porta.
        val after = track.dropWhile { it.first < bagStart!! }.takeWhile { !it.second.hidden }
        assertTrue(after.all { it.second.held == Item.SHOPPING_BAG })
        val gap = s.snapshotAt(bagStart!!).ambient as BakeryAmbient
        assertTrue("vitrine sem o vão do salgado", gap.showcaseGap)
    }

    @Test fun nightIsCalmer() {
        val night = VenueTestFixtures.session(SceneId.BAKERY, period = DayPeriod.NIGHT)
        val day = VenueTestFixtures.session(SceneId.BAKERY)
        assertTrue(night.npcSlots.size < day.npcSlots.size)
    }
}
