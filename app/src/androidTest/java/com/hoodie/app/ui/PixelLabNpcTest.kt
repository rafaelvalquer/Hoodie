package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeDown
import androidx.compose.ui.test.swipeUp
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.presentation.screens.pixellab.PixelLabScreen
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Verifica no Compose real os controles do comparador de personagens do Pixel Lab. */
@RunWith(AndroidJUnit4::class)
class PixelLabNpcTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun npcTabShowsBothRendersPoseControlsAndOverlays() {
        compose.mainClock.autoAdvance = false
        compose.setContent { HoodieTheme { PixelLabScreen(onBack = {}) } }

        compose.onNodeWithContentDescription("NPC").performClick()
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithContentDescription("NPC").assertIsSelected()
        // Abre na roupa registrada do personagem (o Bulldog veste o terno que define sua identidade).
        compose.onNodeWithContentDescription("Registrada").assertIsSelected()
        listOf(
            "[ NPC ART ]", "IDENTIDADE", "INSPECTOR", "ANIMAÇÃO", "FACING", "ESCALA", "AMBIENTE",
            "ROUPA", "PALETA", "POSE · POSTURA", "EXPRESSÃO · OLHOS", "EXPRESSÃO · BOCA",
        ).forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("HOODIE").assertExists()
        compose.onNodeWithText("BULLDOG_EXEC").assertExists()
        compose.onNodeWithText("☑ Overlays").assertExists()
        listOf("90%", "95%", "100%", "□ COMPARE SCALE", "□ LEGACY SCALE COMPARISON").forEach {
            compose.onNodeWithText(it).assertExists()
        }
        listOf("◀ FRAME −", "FRAME + ▶", "☐ Previous frame", "☐ Next frame", "0.25x", "2.0x").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test fun scaleComparisonShowsProductionScalesAndKeepsLegacyValuesDiagnostic() {
        compose.mainClock.autoAdvance = false
        compose.setContent { HoodieTheme { PixelLabScreen(onBack = {}) } }
        compose.onNodeWithContentDescription("NPC").performClick()
        compose.mainClock.advanceTimeBy(100L)
        val scroller = compose.onNodeWithTag("pixel-lab-scroll")
        scroller.performTouchInput { swipeUp(durationMillis = 1_000) }
        compose.mainClock.advanceTimeBy(1_200L)
        compose.onNodeWithText("□ COMPARE SCALE").performClick()
        compose.mainClock.advanceTimeBy(100L)
        listOf("90%", "95%", "100%").forEach { compose.onAllNodesWithText(it).assertCountEquals(2) }
        scroller.performTouchInput { swipeUp(durationMillis = 600) }
        compose.mainClock.advanceTimeBy(500L)
        compose.onNodeWithText("□ LEGACY SCALE COMPARISON").performClick()
        compose.mainClock.advanceTimeBy(100L)
        listOf("75%", "80%", "85%").forEach { compose.onAllNodesWithText(it).assertCountEquals(2) }
    }

    @Test fun inspectorStepsFrameByFrameAndShowsOnionSkin() {
        compose.mainClock.autoAdvance = false
        compose.setContent { HoodieTheme { PixelLabScreen(onBack = {}) } }
        compose.onNodeWithContentDescription("NPC").performClick()
        compose.mainClock.advanceTimeBy(100L)
        val scroller = compose.onNodeWithTag("pixel-lab-scroll")
        scroller.performTouchInput { swipeUp(durationMillis = 1_000) }
        compose.mainClock.advanceTimeBy(1_200L)
        compose.onNodeWithContentDescription("WALK").assertExists()
        compose.onNodeWithContentDescription("WALK").assertIsDisplayed()
        compose.onNodeWithContentDescription("WALK").performClick()
        compose.onNodeWithContentDescription("SIDE").performClick()
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithContentDescription("WALK").assertIsSelected()
        compose.onNodeWithContentDescription("SIDE").assertIsSelected()
        fun inspectorStatus() = compose.onNodeWithTag("npc-inspector-status")
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

        compose.onNodeWithText("⏸ PAUSE").performClick()
        compose.mainClock.advanceTimeBy(100L)
        val beforeStatus = inspectorStatus()
        scroller.performTouchInput { swipeDown(durationMillis = 1_000) }
        compose.mainClock.advanceTimeBy(1_200L)
        fun preview() = compose.onNodeWithTag("npc-preview-BULLDOG_EXEC").captureToImage().asAndroidBitmap()
        val first = preview()

        scroller.performTouchInput { swipeUp(durationMillis = 1_000) }
        compose.mainClock.advanceTimeBy(1_200L)
        compose.onNodeWithText("FRAME + ▶").performClick()
        compose.mainClock.advanceTimeBy(100L)
        val afterStatus = inspectorStatus()
        assertNotEquals("FRAME + should advance the inspector time", beforeStatus, afterStatus)
        compose.onNodeWithText("◀ FRAME −").performClick()
        compose.mainClock.advanceTimeBy(100L)
        assertEquals("FRAME − should return to the same frame", beforeStatus, inspectorStatus())
        compose.onNodeWithContentDescription("☐ Previous frame").performClick()
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithContentDescription("☑ Previous frame").assertExists()
        scroller.performTouchInput { swipeDown(durationMillis = 1_000) }
        compose.mainClock.advanceTimeBy(1_200L)
        assertFalse("onion skin should overlay the previous frame", first.sameAs(preview()))
    }

    @Test fun changingSpeciesUpdatesTheVisibleRenderedNpcPixels() {
        compose.mainClock.autoAdvance = false
        compose.setContent { HoodieTheme { PixelLabScreen(onBack = {}) } }
        compose.onNodeWithContentDescription("NPC").performClick()
        compose.mainClock.advanceTimeBy(100L)

        fun preview(title: String) = compose.onNodeWithTag("npc-preview-$title")
            .captureToImage()
            .asAndroidBitmap()

        val bulldogFront = preview("BULLDOG_EXEC")
        compose.onNodeWithText("dog worker").performClick()
        compose.mainClock.advanceTimeBy(100L)
        val dogFront = preview("DOG_WORKER")
        assertFalse("species selection should update the NPC preview", bulldogFront.sameAs(dogFront))
    }
}
