package com.hoodie.app.pixel.venue

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.venue.FamilyAmbient
import com.hoodie.app.pixel.npc.venue.FamilyVenue
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pai, mãe e irmão do Hoodie na sala: estão lá, conversam um de cada vez e o Hoodie entra na conversa. */
class FamilyVenueTest {
    private val legal = Regex("[A-Za-z0-9 ,.]+")

    @Test fun theWholeFamilyIsInTheRoom() {
        val s = VenueTestFixtures.session(SceneId.FAMILY)
        assertEquals(listOf(FamilyVenue.DAD_ID, FamilyVenue.MOM_ID, FamilyVenue.BROTHER_ID), VenueTestFixtures.ids(s))
        val first = s.snapshotAt(0).npcs
        assertTrue("pai e mãe começam sentados", first.filter { it.npcId != FamilyVenue.BROTHER_ID }.all { it.seated && !it.hidden })
    }

    @Test fun everyLineFitsThePixelBubble() {
        FamilyVenue.TALKS.flatMap { it.lines }.forEach { (_, line) ->
            assertTrue("fala longa: $line", line.length <= 22)
            assertTrue("caractere fora do balão: $line", legal.matches(line))
        }
    }

    @Test fun oneBubbleAtATimeAndTheHoodieTakesPart() {
        listOf(DayPeriod.DAY, DayPeriod.NIGHT).forEach { period ->
            val s = VenueTestFixtures.session(SceneId.FAMILY, period = period)
            var npcLines = 0; var hoodieLines = 0
            VenueTestFixtures.snapshots(s, 10 * 60_000L, 200).forEach { snap ->
                val talking = snap.npcs.count { it.speech != null }
                val hoodie = (snap.ambient as FamilyAmbient).hoodieSpeech != null
                assertTrue("$period t=${snap.timeMs}: dois balões", talking + (if (hoodie) 1 else 0) <= 1)
                npcLines += talking; if (hoodie) hoodieLines++
            }
            assertTrue("$period: a família não conversou", npcLines > 0)
            assertTrue("$period: o Hoodie não falou", hoodieLines > 0)
        }
    }

    @Test fun hoodieSpeechOnlyFromHisSofaSpot() {
        assertTrue(FamilyVenue.hoodieOnSofa(FamilyVenue.HOODIE_SOFA_X, FamilyVenue.SOFA_Y))
        assertTrue(!FamilyVenue.hoodieOnSofa(120, 236))
    }
}
