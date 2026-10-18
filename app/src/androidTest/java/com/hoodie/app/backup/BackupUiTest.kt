package com.hoodie.app.backup

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.hoodie.app.presentation.screens.backup.*
import com.hoodie.app.presentation.screens.recovery.DatabaseRecoveryScreen
import com.hoodie.app.core.database.DatabaseSecurityState
import com.hoodie.app.engine.backup.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

class BackupPasswordUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun confirmationStrengthAndExplicitDigitalChoiceRequired() {
        var secret: CharArray? = null
        var digital: Boolean? = null
        compose.setContent { MaterialTheme { BackupPasswordDialog(true, {}, { p, d -> secret = p; digital = d }) } }
        compose.onNodeWithText("Escolher onde salvar").assertIsNotEnabled()
        compose.onNodeWithTag("backup_password").performTextInput("hoodie recovery phrase")
        compose.onNodeWithTag("backup_password_confirmation").performTextInput("not matching")
        compose.onNodeWithText("Escolher onde salvar").assertIsNotEnabled()
        compose.onNodeWithTag("backup_password_confirmation").performTextClearance()
        compose.onNodeWithTag("backup_password_confirmation").performTextInput("hoodie recovery phrase")
        compose.onNodeWithText("Escolher onde salvar").assertIsEnabled().performClick()
        compose.runOnIdle { assertArrayEquals("hoodie recovery phrase".toCharArray(), secret); assertEquals(true, digital); secret?.fill('\u0000') }
    }
    @Test fun importCanBeCancelledWithoutStartingRestore() {
        var cancelled = false
        compose.setContent { MaterialTheme { BackupPasswordDialog(false, { cancelled = true }, { _, _ -> fail("Unexpected restore") }) } }
        compose.onNodeWithText("Cancelar").performClick()
        compose.runOnIdle { assertTrue(cancelled) }
    }
}

class BackupRecoveryScreenTest {
    @get:Rule val compose = createComposeRule()
    @Test fun restoreEntryWorksWithoutOpeningAnyDatabase() {
        var opened = false
        compose.mainClock.autoAdvance = false
        compose.setContent { MaterialTheme {
            DatabaseRecoveryScreen(DatabaseSecurityState.KeyUnrecoverable, false, {}, {}, onRestore = { opened = true })
        } }
        compose.mainClock.advanceTimeByFrame()
        compose.onNodeWithContentDescription("Restaurar um backup do Hoodie").performClick()
        compose.runOnIdle { assertTrue(opened) }
    }
}

class BackupImportUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun previewShowsOnlyAuthenticatedSummaryAndNeverStartsSubstitution() {
        val manifest = BackupManifest("test", 1792300000000, "UTC", java.util.UUID.randomUUID().toString(), false,
            mapOf("places" to 14L, "context_events" to 92L, "memories" to 23L, "routines" to 1L))
        compose.setContent { MaterialTheme { BackupRestorePreview(manifest) } }
        compose.onNodeWithText("Lugares: 14").assertExists()
        compose.onNodeWithText("Histórico digital: não incluído").assertExists()
        compose.onNodeWithText("Compatibilidade: compatível. Integridade verificada.").assertExists()
    }
}

class BackupRestoreConfirmationTest {
    @get:Rule val compose = createComposeRule()
    @Test fun substitutionRequiresItsOwnExplicitAction() {
        var restored = 0; var backups = 0; var cancelled = 0
        compose.setContent { MaterialTheme { androidx.compose.foundation.layout.Column {
            BackupRestoreConfirmation(false, { backups++ }, { restored++ }, { cancelled++ })
        } } }
        compose.runOnIdle { assertEquals(0, restored) }
        compose.onNodeWithContentDescription("Criar backup dos dados atuais").performClick()
        compose.runOnIdle { assertEquals(1, backups); assertEquals(0, restored) }
        compose.onNodeWithContentDescription("Cancelar restauração").performClick()
        compose.runOnIdle { assertEquals(1, cancelled); assertEquals(0, restored) }
        compose.onNodeWithContentDescription("Restaurar e substituir").performClick()
        compose.runOnIdle { assertEquals(1, restored) }
    }
}

class BackupExportUiTest {
    @get:Rule val compose = createComposeRule()
    @Test fun digitalHistoryCanBeExplicitlyExcludedBeforeChoosingDestination() {
        var included: Boolean? = null
        compose.setContent { MaterialTheme { BackupPasswordDialog(true, {}, { secret, digital -> secret.fill('\u0000'); included = digital }) } }
        compose.onNodeWithTag("backup_password").performTextInput("hoodie recovery phrase")
        compose.onNodeWithTag("backup_password_confirmation").performTextInput("hoodie recovery phrase")
        compose.onNode(isToggleable()).performScrollTo().performClick()
        compose.onNodeWithText("Escolher onde salvar").performClick()
        compose.runOnIdle { assertEquals(false, included) }
    }
}
