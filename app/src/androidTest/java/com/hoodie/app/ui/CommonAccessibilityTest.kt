package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.MapOverlayButton
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommonAccessibilityTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test fun pixelButtonHasButtonRoleAndMinimumTouchTarget() {
        var clicks = 0
        rule.setContent { HoodieTheme { PixelButton("OK", { clicks++ }) } }
        rule.onNodeWithText("OK")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        rule.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun disabledMapButtonHasMinimumTouchTargetAndDisabledState() {
        rule.setContent { HoodieTheme { MapOverlayButton("📍", false, {}) } }
        rule.onNodeWithText("📍")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).assertIsNotEnabled()
    }

    @Test fun chipExposesSelectedRadioRoleAndMinimumTouchTarget() {
        rule.setContent { HoodieTheme { ChipRow(listOf("A", "B"), 0, {}) } }
        rule.onNodeWithText("A")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.RadioButton))
            .assertIsSelected().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
    }
}
