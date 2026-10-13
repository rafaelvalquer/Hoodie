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

    // ───── Farmácia ─────

    @Test fun pharmacyShelvesShowGapsUntilTheClerkRestocks() {
        val s = VenueTestFixtures.session(SceneId.PHARMACY, 0, 75_000)
        s.snapshotAt(900_000)
        val events = s.coordinatorForTests().events()
        val taken = events.filter { it.event == VenueEvent.PRODUCT_TAKEN }
        assertTrue("ninguém levou produto em 15 min", taken.isNotEmpty())
        taken.forEach { e ->
            val a = s.snapshotAt(e.at + 50).ambient as com.hoodie.app.pixel.npc.venue.PharmacyAmbient
            assertTrue("vão em ${e.tag} não apareceu", e.tag in a.shelfGaps)
        }
        val restocks = events.filter { it.event == VenueEvent.SHELF_RESTOCKED }
        assertTrue("atendente nunca repôs em 15 min", restocks.isNotEmpty())
        restocks.forEach { r ->
            val a = s.snapshotAt(r.at + 50).ambient as com.hoodie.app.pixel.npc.venue.PharmacyAmbient
            assertFalse("${r.tag} continua com vão depois da reposição", r.tag in a.shelfGaps && taken.none { it.tag == r.tag && it.at in r.at..r.at + 50 })
        }
    }

    @Test fun clerkLeavesTheCounterOnlyWhenNobodyIsComingToPay() {
        (0 until 3).forEach { v ->
            val s = VenueTestFixtures.session(SceneId.PHARMACY, v, 76_000 + v)
            val track = VenueTestFixtures.track(s, "pharmacy_clerk", 900_000, 200)
            val orders = s.coordinatorForTests().orders()
            track.filter { (_, st) -> st.floorY > com.hoodie.app.pixel.npc.venue.PharmacyVenue.COUNTER_FRONT_Y }.forEach { (t, _) ->
                // Fora do balcão, nenhum pedido esperando há mais que o tempo de voltar.
                assertTrue("atendente fora com cliente esperando em t=$t", orders.none { o -> o.placedAt < t - 9_000 && (o.takenAt ?: Long.MAX_VALUE) > t })
            }
            assertTrue("atendente nunca saiu do balcão (v$v)", track.any { (_, st) -> st.floorY > com.hoodie.app.pixel.npc.venue.PharmacyVenue.COUNTER_FRONT_Y })
        }
    }

    /** Privacidade: nenhuma fala, produto ou evento menciona remédio, sintoma ou motivo da visita. */
    @Test fun pharmacyNeverInfersHealth() {
        val forbidden = listOf("REMEDIO", "DOR", "GRIPE", "RECEITA", "DOENCA", "FEBRE", "PRESSAO", "ANTIBIOTICO", "TOSSE", "VACINA")
        val s = VenueTestFixtures.session(SceneId.PHARMACY, 0, 77_000)
        val lines = (0L until 900_000L step 100L).mapNotNull { t -> s.snapshotAt(t).npcs.mapNotNull { it.speech?.line } }.flatten().toSet()
        assertTrue("ninguém falou em 15 min", lines.isNotEmpty())
        lines.forEach { line -> forbidden.forEach { word -> assertFalse("fala \"$line\" menciona $word", line.uppercase().contains(word)) } }
        s.coordinatorForTests().orders().forEach { assertEquals("produto genérico", com.hoodie.app.pixel.npc.venue.PharmacyVenue.BAG, it.product) }
    }

    // ───── Praia ─────

    @Test fun onlyTheBallOwnerThrowsAndTheCatcherCatchesOnArrival() {
        (0 until 3).forEach { v ->
            val s = VenueTestFixtures.session(SceneId.BEACH, v, 78_000 + v)
            s.snapshotAt(300_000)
            val flights = s.coordinatorForTests().flights()
            assertTrue("sem bola em 5 min (v$v)", flights.size >= 10)
            // A bola alterna: quem recebe é quem arremessa depois.
            flights.zipWithNext().forEach { (a, b) -> assertEquals("v$v arremesso sem estar com a bola", a.to, b.from); assertTrue(b.release >= a.arrive) }
            flights.take(30).forEach { f ->
                val atRelease = s.snapshotAt(f.release).npcs
                assertEquals("v$v soltou sem arremessar", NpcAnimation.THROW_BALL, atRelease.single { it.npcId == f.from }.movement.animation)
                val atArrive = s.snapshotAt(f.arrive).npcs
                assertEquals("v$v pegou sem agarrar", NpcAnimation.CATCH_BALL, atArrive.single { it.npcId == f.to }.movement.animation)
                // Em voo, a bola nunca atravessa outro personagem.
                (f.release until f.arrive step 50).forEach { t ->
                    val snap = s.snapshotAt(t)
                    val ball = snap.ambient as com.hoodie.app.pixel.npc.venue.BeachAmbientState
                    snap.npcs.filter { !it.hidden && it.role != VenueRole.BALL_PLAYER }.forEach { n ->
                        val inside = abs(ball.ballX - n.x) < 8 && ball.ballY in n.floorY - 44..n.floorY
                        assertFalse("v$v t=$t bola atravessa ${n.npcId}", inside)
                    }
                }
            }
        }
    }

    @Test fun ballStaysInTheHandsOfWhoHasIt() {
        val s = VenueTestFixtures.session(SceneId.BEACH, 0, 79_000)
        (0L until 120_000L step 100L).forEach { t ->
            val snap = s.snapshotAt(t)
            val a = snap.ambient as com.hoodie.app.pixel.npc.venue.BeachAmbientState
            val owner = a.ballOwnerId ?: return@forEach
            val holder = snap.npcs.single { it.npcId == owner }
            assertTrue("t=$t bola longe de $owner", abs(a.ballX - holder.x) <= 8 && abs(a.ballY - (holder.floorY - 26)) <= 6)
        }
    }

    @Test fun beachIsCalmAtNightWithoutTheVendor() {
        val night = VenueTestFixtures.session(SceneId.BEACH, period = DayPeriod.NIGHT)
        assertTrue(night.npcSlots.none { it.definition.id == "ice_cream_vendor" || it.definition.id.startsWith("ball_player") })
        val a = night.snapshotAt(10_000).ambient as com.hoodie.app.pixel.npc.venue.BeachAmbientState
        assertFalse(a.vendorOpen); assertFalse(a.ballVisible)
        // Ondas e gaivotas seguem o relógio da cena.
        val b = night.snapshotAt(12_800).ambient as com.hoodie.app.pixel.npc.venue.BeachAmbientState
        assertTrue(a.wavePhase != b.wavePhase)
    }

    @Test fun nightIsCalmer() {
        val night = VenueTestFixtures.session(SceneId.BAKERY, period = DayPeriod.NIGHT)
        val day = VenueTestFixtures.session(SceneId.BAKERY)
        assertTrue(night.npcSlots.size < day.npcSlots.size)
    }
}
