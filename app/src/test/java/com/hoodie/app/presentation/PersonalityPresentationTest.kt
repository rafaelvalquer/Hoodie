package com.hoodie.app.presentation

import com.hoodie.app.core.model.*
import com.hoodie.app.engine.personality.*
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.presentation.screens.home.PersonalityDialogueScheduler
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PersonalityPresentationTest {
    @Test fun `context cancellation discards detected moments that had not reached selection`() = runBlocking {
        val store = InMemoryPersonalityLedgerStore()
        val scheduler = PersonalityDialogueScheduler(PersonalityRuntime(HoodiePersonalityEngine(PersonalityDialogueCatalog.parse(
            File("src/main/assets/${PersonalityDialogueCatalog.ASSET}").readText())), PersonalityTriggerDetector(), store))
        val observation = PersonalityObservation(10_000, 1, 9, 1, UserContextType.HOME, 0, 10, HoodieActivity.IDLE)
        val context: (DetectedTrigger) -> PersonalityContext = { detected ->
            PersonalityContext(detected.trigger, SceneId.HOME, HoodieActivity.IDLE, Needs(), 10, 10_000, 1, detected.visitSeed)
        }
        scheduler.onForeground(0)
        assertNull(scheduler.update(observation, PersonalityGate(sceneTransition = true), true, context))
        scheduler.clearPresentation()
        assertNull(scheduler.update(observation.copy(now = 11_000), PersonalityGate(), true, context))
        assertEquals(PersonalityLedger(), store.load())
    }
    @Test fun `selection and cancellation never write a presented personality memory`() = runBlocking {
        for (confirm in listOf(false, true)) {
            val store = InMemoryPersonalityLedgerStore()
            val runtime = PersonalityRuntime(HoodiePersonalityEngine(PersonalityDialogueCatalog.parse(
                File("src/main/assets/${PersonalityDialogueCatalog.ASSET}").readText())), PersonalityTriggerDetector(), store)
            val scheduler = PersonalityDialogueScheduler(runtime)
            scheduler.onForeground(0)
            val decision = scheduler.update(PersonalityObservation(10_000, 1, 9, 1, UserContextType.HOME,
                0, 10, HoodieActivity.IDLE, null, null), PersonalityGate(), deferPresentation = true) { detected ->
                PersonalityContext(detected.trigger, SceneId.HOME, HoodieActivity.IDLE, Needs(), 10,
                    10_000, 1, detected.visitSeed, UserContextType.HOME)
            }!!
            assertEquals(PersonalityLedger(), store.load())
            assertNull(scheduler.current(10_001))
            if (confirm) {
                assertTrue(scheduler.confirmPresentation(decision.eventId, 12_000))
                assertEquals(12_000L, store.load().lastShownAt[decision.trigger])
                assertFalse(scheduler.confirmPresentation(decision.eventId, 12_001))
            } else {
                scheduler.discardPrepared()
                assertFalse(scheduler.confirmPresentation(decision.eventId, 12_000))
                assertEquals(PersonalityLedger(), store.load())
            }
        }
    }
}
