package com.hoodie.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.HoodieApp
import com.hoodie.app.MainActivity
import com.hoodie.app.R
import com.hoodie.app.core.debug.SceneValidationEntryPoint
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.engine.context.GeofenceTransition
import com.hoodie.app.pixel.scene.SceneId
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Uses the production root, navigation, ViewModel, SQLCipher database and renderer. */
@RunWith(AndroidJUnit4::class)
class StoreSceneDeviceTest {
    @get:Rule val compose = createEmptyComposeRule()

    private val app get() = ApplicationProvider.getApplicationContext<HoodieApp>()
    private val graph get() = EntryPointAccessors.fromApplication(app, SceneValidationEntryPoint::class.java)

    private fun prepare() {
        compose.mainClock.autoAdvance = false
        runBlocking {
            val settings = graph.settings().current()
            graph.settings().completeOnboarding(settings.catName, System.currentTimeMillis())
            // Begin each scenario outside shopping, including after a prior manual test.
            graph.contextEngine().setManualPlace(PlaceType.HOME)
        }
    }

    private fun waitForScene(expected: SceneId) {
        compose.waitUntil(30_000) {
            compose.mainClock.advanceTimeBy(100)
            val snapshot = graph.homeVisuals().snapshot.value
            snapshot.homeVisible && snapshot.selected == expected && snapshot.presented == expected &&
                runCatching { compose.onNodeWithTag("home_live_scene").assertIsDisplayed() }.isSuccess
        }
        compose.onNodeWithTag("home_live_scene").assertIsDisplayed()
    }

    private fun waitForControl(matcher: SemanticsMatcher) {
        try {
            compose.waitUntil(20_000) {
                compose.mainClock.advanceTimeBy(100)
                compose.onAllNodes(matcher).fetchSemanticsNodes().size == 1
            }
        } catch (failure: ComposeTimeoutException) {
            runCatching {
                val instrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                val runnerOutput = androidx.test.platform.app.InstrumentationRegistry.getArguments()
                    .getString("additionalTestOutputDir")?.let { java.io.File(it) }
                val output = java.io.File(runnerOutput ?: app.getExternalFilesDir(null), "store-scene-diagnostics").apply { mkdirs() }
                val roots = compose.onAllNodes(isRoot(), useUnmergedTree = true)
                java.io.File(output, "controls.txt").writeText(
                    "matcher=${matcher.description}\nvisual=${graph.homeVisuals().snapshot.value}\n" +
                        roots.fetchSemanticsNodes().indices.joinToString("\n") { roots[it].printToString() },
                )
                instrumentation.uiAutomation.executeShellCommand("dumpsys window").use { descriptor ->
                    android.os.ParcelFileDescriptor.AutoCloseInputStream(descriptor).use { input ->
                        java.io.File(output, "windows.txt").outputStream().use { input.copyTo(it) }
                    }
                }
                instrumentation.uiAutomation.takeScreenshot()?.let { bitmap ->
                    java.io.File(output, "screen.png").outputStream().use {
                        bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
                    }
                    bitmap.recycle()
                }
            }.exceptionOrNull()?.let(failure::addSuppressed)
            throw failure
        }
    }

    private fun correctThroughHome(type: PlaceType, expected: SceneId) {
        // The scene singleton survives recreation, but Home still refreshes through
        // a loading state. The canonical context card appears only after that
        // reconciliation; tapping its old menu earlier can have it disposed mid-click.
        waitForControl(hasTestTag("home_context_status"))
        waitForControl(hasContentDescription("Mais ações"))
        compose.onNodeWithContentDescription("Mais ações").performClick()
        waitForControl(hasText("Ajustar atividade"))
        compose.onNodeWithText("Ajustar atividade").performClick()
        if (com.hoodie.app.core.config.HoodieConfig.QUICK_CORRECTION_V2 && type !in com.hoodie.app.presentation.screens.home.COMMON_CORRECTION_TYPES) {
            waitForControl(hasTestTag("home_correct_see_all"))
            compose.scrollWithPausedClock(compose.onNodeWithTag("home_correct_see_all")).performClick()
        }
        waitForControl(hasText(type.label))
        compose.scrollWithPausedClock(compose.onNodeWithText(type.label)).performClick()
        waitForControl(hasText("Salvar correção", ignoreCase = true))
        compose.scrollWithPausedClock(compose.onNodeWithText("Salvar correção", ignoreCase = true)).performClick()
        waitForScene(expected)
    }

    @Test fun discoveringAndNamingAStoreThroughHomePersistsTheVisitAndSelectsStoreScene() {
        prepare()
        val visitId = runBlocking {
            graph.contextEngine().arriveAtPosition(0.0, 5.0, askNewPlace = false)
            val visit = requireNotNull(graph.database().contextEventDao().current())
            assertNull(visit.placeId)
            // Seed the incoming question, avoiding dependence on the real GPS or
            // previous tests' daily question budget. Answering uses production code.
            graph.database().questionDao().insert(com.hoodie.app.core.database.ContextQuestionEntity(
                kind = com.hoodie.app.core.model.QuestionKind.NEW_PLACE,
                candidate = null, placeId = null,
                encryptedCoordinates = graph.coordinateCipher().encrypt(0.0, 5.0),
                contextEventId = visit.id, askedAt = System.currentTimeMillis(),
            ))
            visit.id
        }
        var savedId: Long? = null
        try {
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitForControl(hasText("Loja", ignoreCase = true))
                compose.scrollWithPausedClock(compose.onNodeWithText("Loja", ignoreCase = true)).performClick()
                waitForControl(hasText("Nome (opcional)"))
                compose.onNodeWithText("Nome (opcional)").performTextInput("Loja descoberta no teste")
                compose.scrollWithPausedClock(compose.onNodeWithText("Salvar", ignoreCase = true)).performClick()
                waitForScene(SceneId.STORE)
                compose.waitUntil(20_000) {
                    compose.mainClock.advanceTimeBy(100)
                    runBlocking { graph.places().all().any { it.name == "Loja descoberta no teste" } }
                }
                runBlocking {
                    val saved = graph.places().all().single { it.name == "Loja descoberta no teste" }
                    savedId = saved.id
                    assertEquals(PlaceType.STORE, saved.type)
                    assertEquals(0.0, saved.latitude, 0.0)
                    assertEquals(5.0, saved.longitude, 0.0)
                    assertEquals(saved.id, graph.database().contextEventDao().getById(visitId)!!.placeId)
                    assertNotNull(saved.lastVisitedAt)
                    val payload = graph.database().placeDao().getById(saved.id)!!.encryptedCoordinates
                    assertFalse(payload == "0.0,5.0")
                    assertEquals(0.0 to 5.0, graph.coordinateCipher().decrypt(payload))
                }
                scenario.recreate()
                waitForScene(SceneId.STORE)
            }
        } finally {
            runBlocking {
                graph.contextEngine().setManualPlace(PlaceType.HOME)
                savedId?.let { graph.places().delete(it) }
            }
        }
    }

    @Test fun manualStoreSurvivesNavigationAndRecreationAndKeepsOtherVenuesDistinct() {
        prepare()
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            compose.waitUntil(20_000) {
                compose.mainClock.advanceTimeBy(100)
                graph.homeVisuals().snapshot.value.homeVisible
            }
            correctThroughHome(PlaceType.STORE, SceneId.STORE)
            compose.onNodeWithContentDescription(app.getString(R.string.nav_places)).performClick()
            compose.mainClock.advanceTimeBy(800)
            compose.onNodeWithContentDescription(app.getString(R.string.nav_home)).performClick()
            waitForScene(SceneId.STORE)
            scenario.recreate()
            waitForScene(SceneId.STORE)
            correctThroughHome(PlaceType.MARKET, SceneId.SHOPPING)
            correctThroughHome(PlaceType.RESTAURANT, SceneId.RESTAURANT)
            correctThroughHome(PlaceType.WORK, SceneId.OFFICE)
            correctThroughHome(PlaceType.STORE, SceneId.STORE)
        }
    }

    @Test fun savedStoreUsesItsPlaceIdAndEncryptedCoordinatesAfterReopening() {
        prepare()
        val store = runBlocking {
            val now = System.currentTimeMillis()
            graph.places().add("Loja de teste de navegação", PlaceType.STORE, -23.55, -46.63, 150f, now, visitedAt = null)
        }
        try {
            runBlocking { graph.contextEngine().arriveAt(store.id, System.currentTimeMillis()) }
            ActivityScenario.launch(MainActivity::class.java).use { scenario ->
                waitForScene(SceneId.STORE)
                runBlocking {
                    assertEquals(store.id, graph.database().contextEventDao().current()!!.placeId)
                    val payload = graph.database().placeDao().getById(store.id)!!.encryptedCoordinates
                    assertFalse(payload.contains("-23.55"))
                    assertFalse(payload.contains("-46.63"))
                }
                scenario.recreate()
                waitForScene(SceneId.STORE)
                runBlocking { assertEquals(store, graph.places().byId(store.id)?.copy(lastVisitedAt = store.lastVisitedAt, confirmationCount = store.confirmationCount)) }
                compose.onNodeWithContentDescription(app.getString(R.string.nav_places)).performClick()
                // During navigation, Home and Places can briefly show the same
                // name. Wait for the actual clickable Places card, not Home's label.
                val storeCard = hasText(store.name) and hasClickAction()
                waitForControl(storeCard)
                compose.scrollWithPausedClock(compose.onNode(storeCard)).assertIsDisplayed()
            }
        } finally {
            runBlocking {
                graph.contextEngine().onGeofence(store.id, GeofenceTransition.EXIT, System.currentTimeMillis())
                graph.places().delete(store.id)
            }
        }
    }
}
