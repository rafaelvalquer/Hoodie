package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.presentation.screens.pixellab.PixelLabScreen
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertFalse
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
        listOf("◀ FRAME −", "FRAME + ▶", "☐ Previous frame", "☐ Next frame", "0.25x", "2.0x").forEach { compose.onNodeWithText(it).assertExists() }
    }

    @Test fun inspectorStepsFrameByFrameAndShowsOnionSkin() {
        compose.mainClock.autoAdvance = false
        compose.setContent { HoodieTheme { PixelLabScreen(onBack = {}) } }
        compose.onNodeWithContentDescription("NPC").performClick()
        compose.mainClock.advanceTimeBy(100L)
        compose.onNodeWithText("WALK").performClick()
        compose.onNodeWithText("SIDE").performClick()
        compose.mainClock.advanceTimeBy(100L)

        fun preview() = compose.onNodeWithTag("npc-preview-BULLDOG_EXEC").captureToImage().asAndroidBitmap()

        compose.onNodeWithText("⏸ PAUSE").performClick()
        compose.mainClock.advanceTimeBy(100L)
        val first = preview()
        repeat(3) { compose.onNodeWithText("FRAME + ▶").performClick() }
        compose.mainClock.advanceTimeBy(100L)
        val stepped = preview()
        assertFalse("FRAME + should advance the walk pose", first.sameAs(stepped))
        compose.onNodeWithText("☐ Previous frame").performClick()
        compose.mainClock.advanceTimeBy(100L)
        assertFalse("onion skin should overlay the previous frame", stepped.sameAs(preview()))
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
