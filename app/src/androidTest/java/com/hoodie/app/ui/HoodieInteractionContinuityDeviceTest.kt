package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hoodie.app.engine.interaction.*
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.presentation.components.HoodieSceneView
import com.hoodie.app.presentation.theme.HoodieTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Live Android Canvas, gesture callback, Flow delivery, safe boundary, overlay and resumed action. */
@RunWith(Parameterized::class)
class HoodieInteractionContinuityDeviceTest(
    private val scene: SceneId,
    private val spot: SpotId,
    private val animation: AnimationId,
    private val flags: Set<SceneFlag>,
) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun pettingPreservesObjectsAndResumesTheLiveAction() {
        // A real continuously rendered Canvas never becomes idle under automatic test-clock
        // advancement. Drive frames explicitly while the production clock keeps real timing.
        rule.mainClock.autoAdvance = false
        val requests = MutableSharedFlow<InteractionRequest>(extraBufferCapacity = 1)
        val frames = mutableListOf<RenderFrame>()
        var baseline: RenderFrame? = null
        var resumed: RenderFrame? = null
        var started = 0
        var finished = 0
        var presented = 0
        val response = InteractionResponse("device-pet", listOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE),
            null, 0, 3_000, InteractionGesture.LONG_PRESS)
        val visual = VisualState(scene, spot, listOf(MicroAction(animation, 1, 60_000, 60_000)), steadyFlags = flags)
        rule.setContent {
            HoodieTheme {
                HoodieSceneView(visual, Modifier.fillMaxWidth(), greet = false, interactionsEnabled = true,
                    onScenePresented = { presented++ },
                    interactionRequests = requests,
                    onHoodieGesture = { gesture, frame ->
                        if (baseline == null) {
                            assertEquals(InteractionGesture.LONG_PRESS, gesture)
                            // Exercise successful continuity near a safe pause. A request at
                            // the beginning of the 2.6s garment cycle must expire under V1.
                            if (frame.frameIndex >= animation.frames.size / 2) {
                                baseline = frame
                                assertTrue(requests.tryEmit(InteractionRequest(response, scene, 1)))
                            }
                        } else resumed = frame
                    },
                    interactionAllowed = { _, frame -> frames.add(frame); true },
                    onInteractionStarted = { started++ },
                    onInteractionFinished = { finished++ },
                )
            }
        }
        // Initial placement and renderer presentation precede the first accessible gesture.
        rule.waitForIdle()
        rule.mainClock.advanceTimeBy(40)
        rule.waitUntil(5_000) { presented > 0 }
        val gestureDeadline = android.os.SystemClock.uptimeMillis() + 5_000
        while (baseline == null && android.os.SystemClock.uptimeMillis() < gestureDeadline) {
            rule.onNodeWithTag("hoodie_interaction_target")
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick) { it() }
            rule.mainClock.advanceTimeBy(40)
            Thread.sleep(40)
            rule.waitForIdle()
        }
        assertNotNull("No safe cycle window presented", baseline)
        val deadline = android.os.SystemClock.uptimeMillis() + 15_000
        while (finished == 0 && android.os.SystemClock.uptimeMillis() < deadline) {
            rule.mainClock.advanceTimeBy(40)
            Thread.sleep(40)
            rule.waitForIdle()
        }
        assertEquals("Reaction did not finish; frames=${frames.map { it.animation }.distinct()}", 1, finished)
        rule.onNodeWithTag("hoodie_interaction_target")
            .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnClick) { it() }
        rule.runOnIdle {
            val before = requireNotNull(baseline)
            val after = requireNotNull(resumed)
            assertEquals("Reaction did not start; baseline=${before.animation}, busy=${before.interactionBusy}, frames=${frames.map { it.animation }.distinct()}", 1, started)
            assertEquals(1, finished)
            assertTrue(frames.any { it.animation == AnimationId.PET_HEAD })
            assertTrue(frames.any { it.animation == AnimationId.PURR })
            assertTrue(frames.any { it.animation == AnimationId.NUZZLE })
            for (frame in frames + after) {
                assertEquals(before.x, frame.x)
                assertEquals(before.y, frame.y)
                assertEquals(before.direction, frame.direction)
                assertEquals(before.occupancy.seated, frame.occupancy.seated)
                assertTrue("Lost scene objects: $scene", frame.env.flags.containsAll(flags))
            }
            assertEquals(animation, after.animation)
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = listOf(
            arrayOf(SceneId.OFFICE, SpotId.DESK, AnimationId.COFFEE_SIT, setOf(SceneFlag.MUG_IN_HAND)),
            arrayOf(SceneId.RESTAURANT, SpotId.TABLE, AnimationId.WAIT_FOOD, setOf(SceneFlag.FOOD_SERVED)),
            arrayOf(SceneId.STORE, SpotId.MIRROR, AnimationId.STORE_HOLD_GARMENT, setOf(SceneFlag.GARMENT_HELD)),
        )
    }
}
