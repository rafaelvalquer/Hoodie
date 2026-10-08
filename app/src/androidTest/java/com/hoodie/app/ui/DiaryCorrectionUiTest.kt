package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.correction.*
import com.hoodie.app.presentation.screens.diary.DiaryCorrectionSheet
import com.hoodie.app.presentation.screens.diary.DiaryEditState
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class DiaryCorrectionUiTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    @Test fun editingChangesContextAndPreservesExactTimes() {
        val zone = ZoneId.of("America/Sao_Paulo")
        val start = java.time.LocalDate.of(2026, 10, 5).atTime(12, 10).atZone(zone).toInstant().toEpochMilli() + 42_321
        val original = DiaryCorrection(CorrectionTargetType.CONTEXT, 8, UserContextType.WORK, null, start, start + 3_600_000)
        var saved: DiaryCorrection? = null
        rule.setContent { HoodieTheme { DiaryCorrectionSheet(DiaryEditState(original), zone, {}, { saved = it }) } }
        rule.onNodeWithText("Contexto: Trabalho").performClick()
        rule.onNodeWithText("Almoço").performClick()
        rule.onNodeWithTag("diary_edit_save").assertIsDisplayed().performClick()
        rule.runOnIdle {
            assertEquals(UserContextType.LUNCH, saved?.context)
            assertEquals(start, saved?.startedAt)
            assertEquals(start + 3_600_000, saved?.endedAt)
        }
    }
}
