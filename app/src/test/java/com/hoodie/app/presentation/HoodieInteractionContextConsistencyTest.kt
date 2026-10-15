package com.hoodie.app.presentation

import com.hoodie.app.core.model.*
import com.hoodie.app.engine.interaction.*
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.presentation.screens.home.PresentedHoodieContextTracker
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import kotlin.random.Random

class HoodieInteractionContextConsistencyTest {
    @Test fun `home office keeps HOME scene while interaction uses the canonical WORK context`() {
        val t = PresentedHoodieContextTracker()
        val event = presentationEvent(type = UserContextType.WORK)
        val visual = presentationVisual(SceneId.HOME)
        val selected = t.select(event, UserContextType.WORK, HoodieActivity.WORKING, visual)
        t.acknowledge(selected.visualRevision)
        val p = t.select(event, UserContextType.WORK, HoodieActivity.WORKING, visual)
        val resolver = HoodieInteractionResolver(HoodieInteractionCatalog.parse(
            File("src/main/assets/metadata/interactive_dialogues.json").readText()), Random(0))
        val response = resolver.resolve(InteractionContext(p.sceneId, p.contextType, p.hoodieActivity,
            AnimationId.WORK_TYPING), InteractionGesture.TAP, HoodieInteractionHistory(), 1_000) as InteractionDecision.Accepted
        assertEquals(SceneId.HOME, p.sceneId)
        assertTrue(response.response.ruleId.startsWith("office_"))
    }
    @Test fun `undo and repeated context switches require a fresh frame rather than a timeout`() {
        val tracker = PresentedHoodieContextTracker()
        var lastRevision = 0L
        listOf(SceneId.HOME, SceneId.OFFICE, SceneId.RESTAURANT, SceneId.HOME, SceneId.OFFICE).forEachIndexed { index, scene ->
            val type = when (scene) { SceneId.HOME -> UserContextType.HOME; SceneId.OFFICE -> UserContextType.WORK; else -> UserContextType.LUNCH }
            val event = presentationEvent(id = index + 1L, type = type)
            val visual = presentationVisual(scene)
            val before = tracker.select(event, type, HoodieActivity.IDLE, visual)
            assertTrue(before.visualRevision > lastRevision)
            assertFalse(before.isReconciled)
            assertFalse(before.accepts(visual.copy(presentationRevision = lastRevision)))
            tracker.acknowledge(before.visualRevision)
            assertTrue(tracker.select(event, type, HoodieActivity.IDLE, visual).isReconciled)
            lastRevision = before.visualRevision
        }
    }
}
