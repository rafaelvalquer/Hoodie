package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.brain.NpcSpeechScheduler
import com.hoodie.app.pixel.npc.office.*
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.scene.SceneEnv
import org.junit.Assert.*
import org.junit.Test

class OfficeSpeechSchedulerTest {
    @Test fun speechLinesFollowTheClockPeriodAndStaySeeded() {
        val morning = NpcSpeechScheduler.profile("rabbit_analyst", 9 * 60, 12)
        assertEquals(morning, NpcSpeechScheduler.profile("rabbit_analyst", 9 * 60, 12))
        val lunch = OfficeSpeechLibrary.linesAt(12 * 60)
        assertTrue(lunch.any { it.topic == NpcSpeechTopic.LUNCH })
        assertTrue(OfficeSpeechLibrary.linesAt(18 * 60).all { it.topic == NpcSpeechTopic.END_OF_DAY })
    }

    @Test fun productionConversationOnlySpeaksDuringAnActiveSocialEvent() {
        val brains = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = 42)).mapNotNull { it.officeBrain }
        for (time in 0L..600_000L step 250) {
            val colleagues = brains.filter { it.npcId != "bulldog_exec" }
            if (colleagues.any { it.shouldSpeak(time) }) {
                assertTrue(colleagues.all {
                    it.stateAt(time).currentIntent == com.hoodie.app.pixel.npc.brain.NpcIntent.SOCIALIZE
                })
            }
        }
    }
}
