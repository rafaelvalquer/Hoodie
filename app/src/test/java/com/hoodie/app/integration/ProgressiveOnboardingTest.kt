package com.hoodie.app.integration

import androidx.test.core.app.ApplicationProvider
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.time.FixedClock
import com.hoodie.app.engine.setup.ProgressiveOnboardingCoordinator
import com.hoodie.app.engine.setup.ProgressiveStep
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** ONB-01, ONB-02, ONB-03, ONB-05 e ONB-07 no nível do estado persistido. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ProgressiveOnboardingTest {
    private lateinit var settings: SettingsRepository
    private val clock = FixedClock(1_790_000_000_000L)
    private lateinit var flow: ProgressiveOnboardingCoordinator

    @Before fun setUp() {
        val context: android.content.Context = ApplicationProvider.getApplicationContext()
        settings = SettingsRepository(context)
        flow = ProgressiveOnboardingCoordinator(settings, clock)
    }

    @After fun tearDown() { runBlocking { settings.clear() } }

    @Test fun `primeira instalacao conclui sem configurar trabalho nem notificacoes`() = runBlocking {
        assertEquals(0, flow.complete("Mimi", emptyList()))
        val s = settings.current()
        assertTrue(s.onboardingDone)
        assertEquals("Mimi", s.catName)
        assertFalse(s.setupWorkDone)          // rotina padrao nao e informacao confirmada
        assertFalse(s.setupNotificationsDone)
    }

    @Test fun `falha numa etapa opcional nao impede o acesso`() = runBlocking {
        var ran = 0
        val failures = flow.complete("Mimi", listOf<suspend () -> Unit>({ error("geofence indisponivel") }, { ran++ }))
        assertEquals(1, failures)
        assertEquals(1, ran) // as demais continuam
        assertTrue(settings.current().onboardingDone)
    }

    @Test fun `fechar o app no meio preserva etapa e nome`() = runBlocking {
        flow.saveDraft(ProgressiveStep.NAME, "Mi")
        val restored = ProgressiveOnboardingCoordinator(settings, clock).restore()
        assertEquals(ProgressiveStep.NAME, restored.step)
        assertEquals("Mi", restored.name)
        assertFalse(settings.current().onboardingDone)
    }

    @Test fun `concluir descarta o rascunho`() = runBlocking {
        flow.saveDraft(ProgressiveStep.TRACKING, "Mimi")
        flow.complete("Mimi", emptyList())
        assertNull(settings.current().onboardingDraftStep)
    }

    @Test fun `instalacao existente continua configurada`() = runBlocking {
        settings.completeOnboarding("Hoodie", clock.nowMillis()) // fluxo completo antigo, sem marcador v2
        val s = settings.current()
        assertTrue(s.setupWorkDone)
        assertTrue(s.setupNotificationsDone)
    }

    @Test fun `trabalho decidido depois e persistido`() = runBlocking {
        flow.complete("Mimi", emptyList())
        settings.setSetupWorkDone(true)
        assertTrue(settings.current().setupWorkDone)
        assertFalse(settings.current().setupNotificationsDone)
    }
}