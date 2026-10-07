package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertCountEquals
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.presentation.screens.pixellab.PixelLabScreen
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.Timeout
import org.junit.runner.RunWith

/** Verifica no Compose real os controles do comparador de personagens do Pixel Lab. */
@RunWith(AndroidJUnit4::class)
class PixelLabNpcTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @get:Rule val timeout = Timeout.seconds(60)

    private fun show(node: SemanticsNodeInteraction) = compose.scrollWithPausedClock(node)

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
        show(compose.onNodeWithText("□ COMPARE SCALE")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(100L)
        listOf("90%", "95%", "100%").forEach { compose.onAllNodesWithText(it).assertCountEquals(2) }
        show(compose.onNodeWithText("□ LEGACY SCALE COMPARISON")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(100L)
        listOf("75%", "80%", "85%").forEach { compose.onAllNodesWithText(it).assertCountEquals(2) }
    }

    @Test fun inspectorStepsFrameByFrameAndShowsOnionSkin() {
        compose.mainClock.autoAdvance = false
        compose.setContent { HoodieTheme { PixelLabScreen(onBack = {}) } }
        compose.onNodeWithContentDescription("NPC").performClick()
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithContentDescription("WALK").assertExists()
        show(compose.onNodeWithContentDescription("WALK")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        show(compose.onNodeWithContentDescription("SIDE")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithContentDescription("WALK").assertIsSelected()
        compose.onNodeWithContentDescription("SIDE").assertIsSelected()
        fun inspectorStatus() = compose.onNodeWithTag("npc-inspector-status")
            .fetchSemanticsNode().config[SemanticsProperties.Text].joinToString { it.text }

        show(compose.onNodeWithText("⏸ PAUSE")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(100L)
        val beforeStatus = inspectorStatus()
        fun preview() = show(compose.onNodeWithTag("npc-preview-BULLDOG_EXEC")).captureToImage().asAndroidBitmap()
        val first = preview()

        show(compose.onNodeWithText("FRAME + ▶")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(100L)
        val afterStatus = inspectorStatus()
        assertNotEquals("FRAME + should advance the inspector time", beforeStatus, afterStatus)
        show(compose.onNodeWithText("◀ FRAME −")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(100L)
        assertEquals("FRAME − should return to the same frame", beforeStatus, inspectorStatus())
        show(compose.onNodeWithContentDescription("☐ Previous frame")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithContentDescription("☑ Previous frame").assertExists()
        assertFalse("onion skin should overlay the previous frame", first.sameAs(preview()))
    }

    @Test fun changingSpeciesUpdatesTheVisibleRenderedNpcPixels() {
        compose.mainClock.autoAdvance = false
        compose.setContent { HoodieTheme { PixelLabScreen(onBack = {}) } }
        compose.onNodeWithContentDescription("NPC").performClick()
        compose.mainClock.advanceTimeBy(100L)

        fun preview(title: String) = show(compose.onNodeWithTag("npc-preview-$title"))
            .captureToImage()
            .asAndroidBitmap()

        val bulldogFront = preview("BULLDOG_EXEC")
        show(compose.onNodeWithText("dog worker")).performSemanticsAction(SemanticsActions.OnClick) { it() }
        compose.mainClock.advanceTimeBy(100L)
        val dogFront = preview("DOG_WORKER")
        assertFalse("species selection should update the NPC preview", bulldogFront.sameAs(dogFront))
    }
}
