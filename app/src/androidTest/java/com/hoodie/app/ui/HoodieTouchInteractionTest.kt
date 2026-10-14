package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.engine.interaction.InteractionGesture
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.animation.RenderFrame
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.presentation.components.*
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.floor
import kotlin.math.min
import kotlin.random.Random

/** Exercises production PixelImage coordinates and pointer input, including the Android timeout. */
@RunWith(AndroidJUnit4::class)
class HoodieTouchInteractionTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val visual = VisualState(SceneId.HOME, SpotId.CENTER, listOf(MicroAction(AnimationId.IDLE, 1, 60_000, 60_000)))
    private val gestures = mutableListOf<InteractionGesture>()

    private fun show(enabled: Boolean = true) {
        rule.setContent {
            HoodieTheme {
                CompositionLocalProvider(LocalPixelRenderFrame provides PixelRenderFrame(1_000)) {
                    HoodieSceneView(visual, Modifier.fillMaxWidth(), greet = false, interactionsEnabled = enabled,
                        onHoodieGesture = { gesture, _ -> gestures.add(gesture) })
                }
            }
        }
        rule.waitForIdle()
    }

    private fun point(presented: RenderFrame? = null): Offset {
        val machine = AnimationStateMachine(Random(0))
        machine.place(visual, 1)
        val frame = presented ?: machine.frame(1_000, 600, DayPeriod.DAY)!!
        val image = frame.sprite.image
        val index = image.pixels.indexOfFirst { it.ushr(24) != 0 }
        val node = rule.onNodeWithTag("hoodie_interaction_target").fetchSemanticsNode()
        val w = node.boundsInRoot.width
        val h = node.boundsInRoot.height
        val scale = floor(min(w / 240, h / 320)).toInt().coerceAtLeast(1)
        return Offset(
            (w - 240 * scale) / 2 + (frame.x - frame.sprite.anchors.feet.x + index % image.width + .5f) * scale,
            (h - 320 * scale) / 2 + (frame.y - frame.sprite.anchors.feet.y + index / image.width + .5f) * scale,
        )
    }

    @Test fun quickTapIsExactlyOneGestureAndBackgroundIsIgnored() {
        show()
        val target = rule.onNodeWithTag("hoodie_interaction_target")
        target.performTouchInput { click(Offset(2f, 2f)) }
        rule.runOnIdle { assertEquals(emptyList<InteractionGesture>(), gestures) }
        val sprite = point()
        target.performTouchInput { click(sprite) }
        rule.runOnIdle { assertEquals(listOf(InteractionGesture.TAP), gestures) }
    }

    @Test fun longPressReleaseNeverGeneratesAdditionalTap() {
        show()
        val sprite = point()
        rule.onNodeWithTag("hoodie_interaction_target").performTouchInput { longClick(sprite) }
        rule.runOnIdle { assertEquals(listOf(InteractionGesture.LONG_PRESS), gestures) }
    }

    @Test fun draggingFromSpriteCancelsPetting() {
        show()
        val sprite = point()
        rule.onNodeWithTag("hoodie_interaction_target").performTouchInput { swipe(sprite, sprite + Offset(100f, 100f), 800) }
        rule.runOnIdle { assertEquals(emptyList<InteractionGesture>(), gestures) }
    }

    @Test fun accessiblePettingIsAvailableAndFeatureOffInstallsNoTarget() {
        show()
        rule.onNodeWithTag("hoodie_interaction_target").performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick) { it() }
        rule.runOnIdle { assertEquals(listOf(InteractionGesture.LONG_PRESS), gestures) }
    }

    @Test fun disabledFeatureLeavesSceneWithoutGestureSemantics() {
        show(false)
        rule.onNodeWithTag("hoodie_interaction_target").assertDoesNotExist()
    }

    @Test fun androidAccessibilityFrameworkOffersNamedPettingAction() {
        show()
        val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val automation = instrumentation.getUiAutomation(android.app.UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        if (androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("requireTalkBack") == "true") {
            val manager = rule.activity.getSystemService(android.view.accessibility.AccessibilityManager::class.java)
            rule.waitUntil(10_000) {
                manager.getEnabledAccessibilityServiceList(android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_ALL_MASK)
                    .any { it.resolveInfo.serviceInfo.packageName == "com.google.android.marvin.talkback" }
            }
        }
        fun target(node: android.view.accessibility.AccessibilityNodeInfo?): android.view.accessibility.AccessibilityNodeInfo? {
            if (node == null) return null
            if (node.contentDescription?.toString() == "Hoodie") return node
            for (i in 0 until node.childCount) target(node.getChild(i))?.let { return it }
            return null
        }
        var hoodie: android.view.accessibility.AccessibilityNodeInfo? = null
        rule.waitUntil(5_000) { target(automation.rootInActiveWindow).also { hoodie = it } != null }
        val tap = hoodie!!.actionList.first { it.id == android.view.accessibility.AccessibilityNodeInfo.ACTION_CLICK }
        assertEquals("Tocar no Hoodie", tap.label.toString())
        org.junit.Assert.assertTrue(hoodie!!.performAction(tap.id))
        val action = hoodie!!.actionList.first { it.id == android.view.accessibility.AccessibilityNodeInfo.ACTION_LONG_CLICK }
        assertEquals("Fazer carinho no Hoodie", action.label.toString())
        org.junit.Assert.assertTrue(hoodie!!.performAction(action.id))
        rule.waitForIdle()
        rule.runOnIdle { assertEquals(listOf(InteractionGesture.TAP, InteractionGesture.LONG_PRESS), gestures) }
    }

    private fun controlledFrame(scene: SceneId = SceneId.HOME, animation: AnimationId = AnimationId.IDLE): RenderFrame {
        val machine = AnimationStateMachine(Random(0))
        machine.place(VisualState(scene, SpotId.CENTER, listOf(MicroAction(animation, 1, 60_000, 60_000))), 1)
        return machine.frame(2, 600, DayPeriod.DAY)!!
    }

    private fun controlledTarget(frame: () -> RenderFrame?) {
        rule.setContent {
            HoodieTheme {
                Box(Modifier.requiredSize(240.dp, 320.dp).hoodieInteractionTarget(true, frame) { gesture, _ -> gestures.add(gesture) })
            }
        }
    }

    @Test fun aHoldCannotBecomePettingInAnotherScene() {
        val initial = controlledFrame()
        var presented = initial
        controlledTarget { presented }
        val sprite = point(initial)
        rule.onNodeWithTag("hoodie_interaction_target").performTouchInput { down(sprite) }
        rule.runOnIdle { presented = initial.copy(scene = SceneRegistry[SceneId.OFFICE]) }
        rule.onNodeWithTag("hoodie_interaction_target").performTouchInput { advanceEventTime(600); up() }
        rule.runOnIdle { assertEquals(emptyList<InteractionGesture>(), gestures) }
    }

    @Test fun exerciseHoldRequiresANewGestureAfterRestBegins() {
        val initial = controlledFrame(SceneId.GYM, AnimationId.LIFT)
        var presented = initial
        controlledTarget { presented }
        val sprite = point(initial)
        rule.onNodeWithTag("hoodie_interaction_target").performTouchInput { down(sprite) }
        rule.runOnIdle { presented = initial.copy(animation = AnimationId.GYM_REST, occupancy = initial.occupancy.copy(animation = AnimationId.GYM_REST)) }
        rule.onNodeWithTag("hoodie_interaction_target").performTouchInput { advanceEventTime(600); up() }
        rule.runOnIdle { assertEquals(emptyList<InteractionGesture>(), gestures) }
    }

    @Test fun slightBreathingMotionDoesNotCancelAStationaryFinger() {
        val initial = controlledFrame()
        var presented = initial
        controlledTarget { presented }
        val sprite = point(initial)
        rule.onNodeWithTag("hoodie_interaction_target").performTouchInput { down(sprite) }
        rule.runOnIdle { presented = initial.copy(x = initial.x + 1) }
        rule.onNodeWithTag("hoodie_interaction_target").performTouchInput {
            advanceEventTime(250); moveTo(sprite); advanceEventTime(350); up()
        }
        rule.runOnIdle { assertEquals(listOf(InteractionGesture.LONG_PRESS), gestures) }
    }
}
