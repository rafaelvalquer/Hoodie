package com.hoodie.app.onboarding

import android.Manifest
import android.content.Context
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.lifecycle.Lifecycle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.core.database.*
import com.hoodie.app.core.database.migrations.ALL_MIGRATIONS
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.debug.DebugEventLogger
import com.hoodie.app.core.geofence.*
import com.hoodie.app.core.location.*
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.notification.Notifier
import com.hoodie.app.core.security.KeystoreCoordinateCipher
import com.hoodie.app.core.time.FixedClock
import com.hoodie.app.data.repository.*
import com.hoodie.app.engine.context.ContextEngine
import com.hoodie.app.engine.context.ContextTransitionService
import com.hoodie.app.engine.hoodie.HoodieEngine
import com.hoodie.app.engine.memory.MemoryEngine
import com.hoodie.app.presentation.screens.onboarding.*
import com.hoodie.app.presentation.theme.HoodieTheme
import com.hoodie.app.worker.CheckScheduler
import com.hoodie.app.worker.WorkScheduler
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Real UI → ContextEngine → repositories → Keystore → Room/SQLCipher → HoodieEngine.
 * Only external position, notifications and geofence registration are controlled.
 */
@RunWith(AndroidJUnit4::class)
class OnboardingHomeSaveInstrumentedTest {
    @get:Rule val ui = createAndroidComposeRule<ComponentActivity>()
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private lateinit var db: HoodieDatabase
    private lateinit var vm: OnboardingViewModel
    private lateinit var position: Position
    private lateinit var settingsJob: Job
    private lateinit var settingsFile: File
    private lateinit var databaseName: String
    private val cipher = KeystoreCoordinateCipher()

    private class Position : LocationSource {
        var point: Pair<Double, Double>? = -23.55052 to -46.633308
        override suspend fun current() = point
        override fun permissionState() = LocationPermissionState.FOREGROUND
    }

    @Before fun setup() {
        // The real sprite requests frames continuously. Drive those frames explicitly
        // so Compose idling does not wait for a live animation to stop forever.
        ui.mainClock.autoAdvance = false
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
        SqlCipherNativeLoader.ensureLoaded()
        val id = UUID.randomUUID().toString()
        databaseName = "onboarding-home-$id.db"
        db = Room.databaseBuilder(context, HoodieDatabase::class.java, databaseName)
            .openHelperFactory(SupportOpenHelperFactory("onboarding-test-$id".toByteArray()))
            .addMigrations(*ALL_MIGRATIONS).allowMainThreadQueries().build()
        db.openHelper.writableDatabase
        settingsFile = File(context.cacheDir, "onboarding-$id.preferences_pb")
        settingsJob = SupervisorJob()
        val settings = SettingsRepository(PreferenceDataStoreFactory.create(
            scope = CoroutineScope(settingsJob + Dispatchers.IO), produceFile = { settingsFile }))
        val clock = FixedClock(1_800_000_000_000L)
        val log = DebugEventLogger(clock)
        val places = PlaceRepository(db.placeDao(), cipher)
        val routines = RoutineRepository(db.routineDao(), db.dayExceptionDao())
        val timeline = TimelineRepository(db.timelineDao())
        val memory = MemoryEngine(db.memoryDao(), settings, timeline, clock)
        val hoodie = HoodieEngine(db.hoodieStateDao(), db.hoodieActivityDao(), db.contextEventDao(), routines, settings, timeline, clock, log)
        val transitions = ContextTransitionService(db.contextEventDao(), timeline, places, RoomTransactionRunner(db), log)
        position = Position()
        val geofences = object : GeofenceRegistrar {
            override val lastResult = MutableStateFlow<GeofenceRegistrationResult?>(null)
            override suspend fun registerAll(): GeofenceRegistrationResult =
                GeofenceRegistrationResult(1, 1, 0, null).also { lastResult.value = it }
            override suspend fun clear() = Unit
        }
        val notifier = object : Notifier {
            override suspend fun event(text: String) = Unit
            override suspend fun askYesNo(questionId: Long, text: String) = Unit
            override suspend fun askInApp(questionId: Long, text: String) = Unit
            override fun cancelQuestion(questionId: Long) = Unit
            override fun cancelAll() = Unit
        }
        val checks = object : CheckScheduler {
            override fun scheduleLunchCheck(exitAt: Long, placeId: Long) = Unit
            override fun scheduleCommuteCheck(eventId: Long) = Unit
            override fun cancelChecks() = Unit
            override fun reconcileNow() = Unit
        }
        val engine = ContextEngine(db.contextEventDao(), transitions, db.confirmationDao(), db.questionDao(),
            db.locationEventDao(), places, routines, settings, memory, hoodie, notifier, checks, position, geofences, cipher, clock, log)
        vm = OnboardingViewModel(settings, routines, engine, position, geofences,
            LocationPermissionManager(context), memory, hoodie, WorkScheduler(context), clock, log)
        ui.setContent { HoodieTheme { OnboardingScreen(vm) } }
    }

    @After fun cleanup() = runBlocking {
        db.close()
        settingsJob.cancelAndJoin()
        settingsFile.delete()
        context.deleteDatabase(databaseName)
        Unit
    }

    private fun reachHome() {
        click("COMEÇAR")
        click("CONTINUAR")
        click("CONTINUAR")
        ui.onNodeWithText("Você está em casa agora?", substring = true).assertIsDisplayed()
    }

    private fun click(label: String) {
        ui.onNodeWithText(label).performClick()
        ui.mainClock.advanceTimeBy(32)
    }

    @Test fun validPositionSavesEncryptedHomeAndAdvancesWithLiveApp() = runBlocking {
        reachHome()
        click("SIM")
        ui.waitUntil(15_000) { vm.state.value.homeSaved && !vm.state.value.busy }
        ui.mainClock.advanceTimeBy(32)
        val home = db.placeDao().getAll().single()
        assertEquals(PlaceType.HOME, home.type)
        assertFalse(home.encryptedCoordinates.contains("-23.55052"))
        assertEquals(position.point, cipher.decrypt(home.encryptedCoordinates))
        val current = db.contextEventDao().current()!!
        assertEquals(UserContextType.HOME, current.type)
        assertEquals(home.id, current.placeId)
        assertEquals(UserContextType.HOME, db.hoodieStateDao().get()!!.userContext)
        assertTrue(vm.state.value.step in setOf(OnboardingStep.BACKGROUND, OnboardingStep.WORK))
        ui.onNodeWithText(if (vm.state.value.step == OnboardingStep.BACKGROUND)
            "PASSO 3B · SEGUNDO PLANO" else "PASSO 4 · TRABALHO").assertIsDisplayed()
        ui.runOnIdle { assertTrue(ui.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
        assertFalse(DatabaseEncryption.isPlaintext(context.getDatabasePath(databaseName)))
    }

    @Test fun unavailablePositionKeepsHomeRecoverable() = runBlocking {
        position.point = null
        reachHome()
        click("SIM")
        ui.waitUntil(10_000) { vm.state.value.message != null && !vm.state.value.busy }
        ui.mainClock.advanceTimeBy(32)
        ui.onNodeWithText("Não consegui sua localização agora.", substring = true).assertIsDisplayed()
        assertEquals(OnboardingStep.HOME, vm.state.value.step)
        assertTrue(db.placeDao().getAll().isEmpty())
        click("DEFINIR DEPOIS")
        ui.runOnIdle { assertTrue(ui.activity.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    }

    @Test fun databaseWriteFailureShowsMessageAndAllowsRetry() = runBlocking {
        reachHome()
        db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_home BEFORE INSERT ON places BEGIN SELECT RAISE(ABORT, 'injected'); END")
        click("SIM")
        ui.waitUntil(10_000) { vm.state.value.message != null && !vm.state.value.busy }
        ui.mainClock.advanceTimeBy(32)
        ui.onNodeWithText("Não consegui salvar sua Casa. Tente novamente.").assertIsDisplayed()
        assertTrue(db.placeDao().getAll().isEmpty())
        assertEquals(OnboardingStep.HOME, vm.state.value.step)
        assertNull(db.contextEventDao().current())
        db.openHelper.writableDatabase.execSQL("DROP TRIGGER fail_home")
        click("SIM")
        ui.waitUntil(15_000) { vm.state.value.homeSaved && !vm.state.value.busy }
        assertEquals(1, db.placeDao().getAll().size)
    }
}
