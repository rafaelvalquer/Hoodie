package com.hoodie.app.pixel.venue

import com.hoodie.app.pixel.npc.venue.VenueNpcState
import org.junit.Assert.assertTrue
import org.junit.Test

/** Cada cena simula dez minutos sem travar e com todos os personagens fazendo algo. */
class VenueSmokeTest {
    @Test fun tenMinutesOfEveryVenue() {
        VenueTestFixtures.scenes.forEach { scene ->
            val s = VenueTestFixtures.session(scene)
            val shots = VenueTestFixtures.snapshots(s, 600_000, 250)
            val ids = VenueTestFixtures.ids(s)
            ids.forEach { id ->
                val states = shots.map { snap -> snap.npcs.single { it.npcId == id }.state }.toSet()
                println("$scene $id: $states")
                assertTrue("$scene $id parado: $states", states.size >= 2)
                assertTrue("$scene $id nunca age: $states", VenueNpcState.PERFORMING_ACTION in states || VenueNpcState.INTERACTING in states)
            }
            val orders = s.coordinatorForTests().orders()
            println("$scene pedidos: ${orders.map { "${it.customerId}:${it.product} ${it.placedAt}/${it.takenAt}/${it.readyAt}/${it.pickedAt}" }}")
        }
    }
}
