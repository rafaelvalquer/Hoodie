package com.hoodie.app.ui

import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.hoodie.app.core.model.*
import com.hoodie.app.engine.hoodie.HoodieSnapshot
import com.hoodie.app.engine.interaction.*
import com.hoodie.app.engine.reaction.*
import com.hoodie.app.pixel.animation.*
import com.hoodie.app.pixel.scene.*
import com.hoodie.app.presentation.screens.home.*
import com.hoodie.app.presentation.theme.HoodieTheme
import kotlinx.coroutines.flow.MutableSharedFlow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import java.time.ZoneId

/** Production Home Canvas and speech overlay; actual renderer acknowledgements drive the arbiter. */
class ReactionDialogueDeviceTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun userSupersedesUnpresentedPersonalityAndOnlyOneBubbleAppears() {
        rule.mainClock.autoAdvance = false
        val coordinator = HoodieReactionCoordinator()
        val requests = MutableSharedFlow<InteractionRequest>(extraBufferCapacity = 1)
        val starts = mutableListOf<String>()
        var seenLow = false
        var frames = 0
        var finishedUser = false
        var presentation by mutableStateOf<ReactionPresentation?>(null)
        val visual = VisualState(SceneId.HOME, SpotId.CENTER, listOf(MicroAction(AnimationId.IDLE, 1, 60_000, 60_000)),
            presentationRevision = 1)
        rule.setContent {
            HoodieTheme {
                HomeContent(HomeUiState(loading = false, now = 1_000,
                    snapshot = HoodieSnapshot(HoodieState(HoodieActivity.RESTING, 0, null, Needs(), 0, UserContextType.HOME), Needs(), emptyList()),
                    visual = visual, reactionCoordinatorEnabled = true, dialogue = presentation?.text),
                    busy = false, zone = ZoneId.of("UTC"), onOpen = {}, interactionsEnabled = true,
                    interactionRequests = requests,
                    onVisualPresented = { coordinator.context(it.scene, it.presentationRevision, true, SystemClock.uptimeMillis()); frames++ },
                    interactionAllowed = { request, _ ->
                        if (request.reaction?.id == "low") seenLow = true
                        coordinator.current(request.reaction!!.id, SystemClock.uptimeMillis())
                    },
                    onInteractionStarted = { request ->
                        assertTrue(coordinator.started(request.reaction!!.id, SystemClock.uptimeMillis()))
                        starts += request.reaction.id
                        presentation = coordinator.presentation
                    },
                    onInteractionFinished = { request ->
                        coordinator.animationFinished(request.reaction!!.id, SystemClock.uptimeMillis())
                        if (request.reaction.id == "user") finishedUser = true
                    })
            }
        }
        fun step() { rule.mainClock.advanceTimeBy(40); Thread.sleep(40); rule.waitForIdle() }
        step()
        rule.waitUntil(5_000) { frames > 0 }
        fun make(id: String, source: ReactionSource, text: String) = ReactionIntent(id, source, SceneId.HOME, 1,
            SystemClock.uptimeMillis(), SystemClock.uptimeMillis() + 2_000, listOf(AnimationId.SMILE), text)
        fun send(intent: ReactionIntent) {
            assertTrue(coordinator.request(intent, SystemClock.uptimeMillis()))
            assertEquals(intent.id, coordinator.dispatch(SystemClock.uptimeMillis(), true)!!.id)
            assertTrue(requests.tryEmit(InteractionRequest(
                InteractionResponse(intent.id, intent.animations, intent.dialogue, intent.durationMs, source = intent.source),
                intent.sceneId, 1, createdAtMs = intent.requestedAt, expiresAtMs = intent.expiresAt, reaction = intent)))
        }
        rule.runOnIdle { send(make("low", ReactionSource.PERSONALITY_REACTION, "Fala espontânea.")) }
        // Let the Flow authorize the old request, without presenting another animation frame yet.
        rule.waitUntil(5_000) { seenLow }
        rule.runOnIdle { send(make("user", ReactionSource.USER_INTERACTION, "Que carinho bom!")) }
        val deadline = SystemClock.uptimeMillis() + 8_000
        while (!finishedUser && SystemClock.uptimeMillis() < deadline) step()
        assertTrue(finishedUser)
        assertEquals(listOf("user"), starts)
        rule.onAllNodesWithTag("home_dialogue").assertCountEquals(1)
        rule.onNodeWithText("Que carinho bom!", substring = true).assertIsDisplayed()
        rule.onNodeWithText("Fala espontânea.", substring = true).assertDoesNotExist()
        assertTrue(coordinator.lifecycle.none { it.id == "low" && it.state == ReactionState.STARTED })
    }
}
