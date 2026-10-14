package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import com.hoodie.app.core.model.*
import com.hoodie.app.engine.home.HomeNowAssembler
import com.hoodie.app.engine.hoodie.HoodieSnapshot
import com.hoodie.app.engine.interaction.*
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.presentation.screens.home.*
import com.hoodie.app.presentation.theme.HoodieTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized
import java.time.ZoneId

/** Uses the production speech timer/binding and live Home Canvas, not a copied timer. */
@RunWith(Parameterized::class)
class HoodieInteractionSpeechDeviceTest(private val fontScale: Float, private val hasAutomatic: Boolean) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun speechWaitsForPresentationHasPriorityAndExpiresWithoutMovingHome() {
        rule.mainClock.autoAdvance = false
        val requests = MutableSharedFlow<InteractionRequest>(extraBufferCapacity = 1)
        val automatic = mutableStateOf<String?>(if (hasAutomatic) "Fala automática inicial." else null)
        val text = "Uma pausa para carinho não faz mal."
        val response = InteractionResponse("speech-review", listOf(AnimationId.PET_HEAD, AnimationId.PURR, AnimationId.NUZZLE),
            text, 3_500, 3_000, InteractionGesture.LONG_PRESS)
        val request = InteractionRequest(response, SceneId.OFFICE, 1)
        val visual = VisualState(SceneId.OFFICE, SpotId.DESK, listOf(MicroAction(AnimationId.COFFEE_SIT, 1, 60_000, 60_000)),
            steadyFlags = setOf(SceneFlag.MUG_IN_HAND, SceneFlag.CHAIR_OCCUPIED))
        val event = ContextEvent(42, UserContextType.WORK, 1_000, null, .94f, 8, ContextSource.GEOFENCE)
        val snapshot = HoodieSnapshot(HoodieState(HoodieActivity.COFFEE, 1_000, null, Needs(), 1_000, UserContextType.WORK), Needs(), emptyList())
        val homeNow = HomeNowAssembler.assemble(null, event, "Escritório", null, null, 4_000)
        var sent = false
        var startedAt: Long? = null
        var reactionShown = false
        var completed = false
        lateinit var speech: InteractiveSpeechState
        rule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                speech = rememberInteractiveSpeech()
                HoodieTheme {
                    HomeContent(HomeUiState(loading = false, now = 4_000, context = event, snapshot = snapshot,
                        visual = visual, dialogue = speech.resolve(automatic.value)),
                        busy = false, zone = ZoneId.of("UTC"), onOpen = {}, homeNow = homeNow,
                        interactionsEnabled = true, interactionRequests = requests,
                        onHoodieGesture = { _, frame ->
                            if (!sent && !frame.interactionBusy && !frame.interactionTransitioning) sent = requests.tryEmit(request)
                        },
                        interactionAllowed = { _, frame ->
                            if (frame.animation in response.animations) reactionShown = true
                            true
                        },
                        onInteractionStarted = {
                            assertNull(startedAt)
                            startedAt = rule.mainClock.currentTime
                            speech.present(it)
                        },
                        onInteractionFinished = { completed = true },
                    )
                }
            }
        }
        fun step() {
            rule.mainClock.advanceTimeBy(40)
            Thread.sleep(40)
            rule.waitForIdle()
        }
        fun until(predicate: () -> Boolean) {
            val deadline = android.os.SystemClock.uptimeMillis() + 15_000
            while (!predicate() && android.os.SystemClock.uptimeMillis() < deadline) step()
            assertTrue("Live Home did not reach the requested state", predicate())
        }
        step()
        val activityBounds = rule.onNodeWithTag("home_hoodie_activity").fetchSemanticsNode().boundsInRoot
        val sceneBounds = rule.onNodeWithTag("home_live_scene").fetchSemanticsNode().boundsInRoot
        val contextBounds = rule.onNodeWithTag("home_user_context").fetchSemanticsNode().boundsInRoot
        until {
            if (!sent) rule.onNodeWithTag("hoodie_interaction_target")
                .performSemanticsAction(androidx.compose.ui.semantics.SemanticsActions.OnLongClick) { it() }
            startedAt != null
        }
        step()
        rule.onNodeWithText(text, substring = true).assertIsDisplayed()
        rule.onNodeWithText("Fala automática inicial.", substring = true).assertDoesNotExist()
        rule.runOnIdle { automatic.value = if (hasAutomatic) "Fala automática atualizada." else null }
        // A silent response cannot erase the active phrase or restart its timeout.
        rule.runOnIdle { speech.present(request.copy(response = response.copy(text = null))) }
        val expiry = startedAt!! + 3_500
        while (rule.mainClock.currentTime < expiry - 120) step()
        rule.onNodeWithText(text, substring = true).assertIsDisplayed()
        assertTrue(reactionShown)
        assertEquals(sceneBounds, rule.onNodeWithTag("home_live_scene").fetchSemanticsNode().boundsInRoot)
        assertEquals(activityBounds, rule.onNodeWithTag("home_hoodie_activity").fetchSemanticsNode().boundsInRoot)
        assertEquals(contextBounds, rule.onNodeWithTag("home_user_context").fetchSemanticsNode().boundsInRoot)
        until { speech.current == null && completed }
        // Finish the bubble fade before inspecting semantics.
        repeat(8) { step() }
        rule.onNodeWithText(text, substring = true).assertDoesNotExist()
        if (hasAutomatic) rule.onNodeWithText("Fala automática atualizada.", substring = true).assertIsDisplayed()
        else rule.onNodeWithTag("home_dialogue").assertDoesNotExist()
        assertEquals(sceneBounds, rule.onNodeWithTag("home_live_scene").fetchSemanticsNode().boundsInRoot)
        assertEquals(activityBounds, rule.onNodeWithTag("home_hoodie_activity").fetchSemanticsNode().boundsInRoot)
        assertEquals(contextBounds, rule.onNodeWithTag("home_user_context").fetchSemanticsNode().boundsInRoot)
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "font={0} automatic={1}")
        fun cases(): List<Array<Any>> = listOf(1f, 1.3f).flatMap { font -> listOf(false, true).map { arrayOf<Any>(font, it) } }
    }
}
