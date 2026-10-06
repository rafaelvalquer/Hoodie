package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.scene.SceneEnv
import org.junit.Assert.*
import org.junit.Test

class NpcAmbientBrainTest {
    @Test fun officeSlotsExposeStableIntentMovementAndSpeechQueries() {
        OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = 42)).mapNotNull { it.officeBrain }.forEach {
            assertEquals(it.stateAt(30_000), it.stateAt(30_000))
            assertEquals(it.movementAt(30_000), it.movementAt(30_000))
            assertEquals(it.shouldSpeak(30_000), it.shouldSpeak(30_000))
        }
    }
}
