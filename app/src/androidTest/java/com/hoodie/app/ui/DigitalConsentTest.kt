package com.hoodie.app.ui

import android.app.AppOpsManager
import android.content.Context
import android.os.Process
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.core.datastore.DigitalSettings
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.deviceusage.UsageAccessManager
import com.hoodie.app.core.time.FixedClock
import com.hoodie.app.data.repository.DeviceUsageRepository
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.domain.phoneinsights.usecase.LoadPhoneInsightsUseCase
import com.hoodie.app.domain.phoneinsights.usecase.SetAppCategoryUseCase
import com.hoodie.app.presentation.screens.phoneinsights.PhoneInsightsUiEvent
import com.hoodie.app.presentation.screens.phoneinsights.PhoneInsightsViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.util.UUID

/** Real Android AppOp + production ViewModel + preferences persisted across reopening. */
@RunWith(AndroidJUnit4::class)
class DigitalConsentTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private lateinit var file: File
    private lateinit var settingsJob: Job
    private lateinit var settings: SettingsRepository
    private var originalMode = AppOpsManager.MODE_DEFAULT
    private var store = ViewModelStore()
    private val repository = object : DeviceUsageRepository {
        override suspend fun insightsFor(date: LocalDate): DailyPhoneInsights? = null
        override suspend fun loadDay(date: LocalDate): DailyPhoneInsights? = null
        override suspend fun refreshDay(date: LocalDate): DailyPhoneInsights = error("Unexpected refresh")
        override suspend fun setCategoryOverride(packageName: String, category: HoodieAppCategory?) = Unit
        override suspend fun clearHistory() = Unit
        override suspend fun storedSessionCount() = 0
    }

    @Before fun setup() {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        originalMode = ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        file = File(context.cacheDir, "digital-consent-${UUID.randomUUID()}.preferences_pb")
        openSettings()
    }
    @After fun cleanup() {
        instrumentation.runOnMainSync { store.clear() }
        runBlocking { settingsJob.cancelAndJoin() }
        setMode(originalMode)
        file.delete()
    }
    private fun openSettings() {
        settingsJob = SupervisorJob()
        settings = SettingsRepository(PreferenceDataStoreFactory.create(scope = CoroutineScope(settingsJob + Dispatchers.IO), produceFile = { file }))
    }
    private fun vm(): PhoneInsightsViewModel {
        lateinit var result: PhoneInsightsViewModel
        instrumentation.runOnMainSync {
            result = ViewModelProvider(store, object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T = PhoneInsightsViewModel(
                    LoadPhoneInsightsUseCase(repository), SetAppCategoryUseCase(repository), UsageAccessManager(context), settings,
                    FixedClock(1_800_000_000_000L),
                ) as T
            })[PhoneInsightsViewModel::class.java]
        }
        return result
    }
    private fun setMode(mode: Int) {
        val label = when (mode) {
            AppOpsManager.MODE_ALLOWED -> "allow"
            AppOpsManager.MODE_IGNORED -> "ignore"
            AppOpsManager.MODE_ERRORED -> "deny"
            else -> "default"
        }
        android.os.ParcelFileDescriptor.AutoCloseInputStream(instrumentation.uiAutomation.executeShellCommand("appops set ${context.packageName} GET_USAGE_STATS $label")).bufferedReader().use { it.readText() }
    }
    private fun awaitDigital(predicate: (DigitalSettings) -> Boolean) = runBlocking {
        withTimeout(10_000) { settings.settings.first { predicate(it.digital) }.digital }
    }
    private fun restart() {
        instrumentation.runOnMainSync { store.clear() }
        runBlocking { settingsJob.cancelAndJoin() }
        store = ViewModelStore()
        openSettings()
    }

    @Test fun permissionAloneNeverOptsIn() {
        setMode(AppOpsManager.MODE_ALLOWED)
        val viewModel = vm()
        instrumentation.runOnMainSync { viewModel.onResume() }
        assertFalse(runBlocking { settings.current().digital.analysisEnabled })
        assertFalse(runBlocking { settings.current().digital.analysisRequested })
    }

    @Test fun explicitRequestAndGrantedReturnSurviveReopening() {
        setMode(AppOpsManager.MODE_IGNORED)
        var viewModel = vm()
        instrumentation.runOnMainSync { viewModel.enableAnalysis() }
        assertEquals(PhoneInsightsUiEvent.OpenUsageSettings, runBlocking { withTimeout(10_000) { viewModel.events.first() } })
        assertFalse(awaitDigital { it.analysisRequested }.analysisEnabled)
        restart()
        assertTrue(runBlocking { settings.current().digital.analysisRequested })
        setMode(AppOpsManager.MODE_ALLOWED)
        viewModel = vm()
        instrumentation.runOnMainSync { viewModel.onResume() }
        assertFalse(awaitDigital { it.analysisEnabled }.analysisRequested)
        restart()
        assertTrue(runBlocking { settings.current().digital.analysisEnabled })
        assertFalse(runBlocking { settings.current().digital.analysisRequested })
    }

    @Test fun deniedReturnAndCancelledRequestStayDisabled() {
        setMode(AppOpsManager.MODE_IGNORED)
        var viewModel = vm()
        instrumentation.runOnMainSync { viewModel.enableAnalysis() }
        awaitDigital { it.analysisRequested }
        instrumentation.runOnMainSync { viewModel.onResume() }
        assertFalse(runBlocking { settings.current().digital.analysisEnabled })
        runBlocking { settings.setDigital(DigitalSettings()) }
        restart()
        setMode(AppOpsManager.MODE_ALLOWED)
        viewModel = vm()
        instrumentation.runOnMainSync { viewModel.onResume() }
        assertFalse(runBlocking { settings.current().digital.analysisEnabled })
        assertFalse(runBlocking { settings.current().digital.analysisRequested })
    }
}
