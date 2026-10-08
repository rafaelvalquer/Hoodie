package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.npc.PathPhase
import com.hoodie.app.pixel.npc.office.OfficeNpcDirector
import com.hoodie.app.pixel.scene.SceneEnv
import org.junit.Assert.assertTrue
import org.junit.Test

class OfficeMotionContinuityTest {
    @Test fun visibleOfficeNpcsStayWithinTheirAuthorizedMovementAcrossTenMinutes() {
        for (seed in listOf(7, 42, 91)) {
            val brains = OfficeNpcDirector.plan(SceneEnv(DayPeriod.DAY, 9 * 60, variant = seed, daySeed = seed))
                .mapNotNull { it.officeBrain }
            var previous = brains.associate { it.npcId to it.movementAt(0) }
            for (time in 33L..600_000L step 33L) {
                for (brain in brains) {
                    val current = brain.movementAt(time)
                    val before = previous.getValue(brain.npcId)
                    val entryOrExit = current.phase in setOf(PathPhase.ENTER, PathPhase.EXIT) ||
                        before.phase in setOf(PathPhase.ENTER, PathPhase.EXIT)
                    if (!entryOrExit && current.x >= 0 && before.x >= 0) {
                        val delta = maxOf(kotlin.math.abs(current.x - before.x), kotlin.math.abs(current.floorY - before.floorY))
                        assertTrue("${brain.npcId} moved $delta px between 33ms frames at $time (seed=$seed): $before -> $current", delta <= 3)
                    }
                    previous = previous + (brain.npcId to current)
                }
            }
        }
    }
}
