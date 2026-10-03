package com.hoodie.app.ui.golden

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import com.hoodie.app.core.datastore.AppSettings
import com.hoodie.app.core.datastore.DigitalSettings
import com.hoodie.app.core.deviceusage.UsagePermissionState
import com.hoodie.app.core.error.DatabaseError
import com.hoodie.app.core.error.PlaceError
import com.hoodie.app.core.location.LocationPermissionState
import com.hoodie.app.core.mobility.ActivityRecognitionPermissionState
import com.hoodie.app.core.model.*
import androidx.compose.ui.test.performScrollTo
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.diary.model.*
import com.hoodie.app.domain.phoneinsights.model.*
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.presentation.common.appErrorText
import com.hoodie.app.presentation.components.LocalPixelRenderFrame
import com.hoodie.app.presentation.components.PixelRenderFrame
import com.hoodie.app.presentation.screens.diary.*
import com.hoodie.app.presentation.screens.home.HomeContent
import com.hoodie.app.presentation.screens.home.HomeUiState
import com.hoodie.app.presentation.screens.onboarding.*
import com.hoodie.app.presentation.screens.phoneinsights.PhoneInsightsContent
import com.hoodie.app.presentation.screens.phoneinsights.PhoneInsightsUiState
import com.hoodie.app.presentation.screens.places.PlaceLoadState
import com.hoodie.app.presentation.screens.places.PlacePickerState
import com.hoodie.app.presentation.screens.places.picker.PlacePickerActions
import com.hoodie.app.presentation.screens.places.picker.PlacePickerLayout
import com.hoodie.app.presentation.screens.settings.SettingsContent
import com.hoodie.app.presentation.theme.HoodieColors
import com.hoodie.app.presentation.theme.HoodieTheme
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

data class ScreenGoldenCase(val screen: String, val state: String, val width: Int, val height: Int, val fontScale: Float) {
    val name get() = "${screen}_${state}_${width}x${height}_f${(fontScale * 100).toInt()}"
    override fun toString() = name
}

/** Six production screens × five states × three viewports × two font scales = 180 baselines. */
@RunWith(Parameterized::class)
class ScreenGoldenMatrixTest(private val case: ScreenGoldenCase) {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()

    @Test(timeout = 60_000) fun screenMatchesReviewedBaseline() {
        rule.runOnUiThread {
            WindowCompat.setDecorFitsSystemWindows(rule.activity.window, false)
            WindowCompat.getInsetsController(rule.activity.window, rule.activity.window.decorView)
                .hide(WindowInsetsCompat.Type.systemBars())
        }
        rule.mainClock.autoAdvance = false
        rule.setContent {
            CompositionLocalProvider(
                LocalDensity provides Density(1f, case.fontScale),
                LocalPixelRenderFrame provides PixelRenderFrame(),
            ) {
                HoodieTheme {
                    Box(Modifier.requiredSize(case.width.dp, case.height.dp).background(HoodieColors.Night).testTag("golden_viewport")) {
                        GoldenScreen(case)
                    }
                }
            }
        }
        rule.mainClock.advanceTimeBy(1_000)
        rule.waitForIdle()
        ScreenGoldenCapture.verify(case.name, rule.onNodeWithTag("golden_viewport"))
        if (case.screen == "diary" && case.state == "full") {
            // performScrollTo may perform animated scroll; let it consume frames.
            rule.mainClock.autoAdvance = true
            rule.onNodeWithTag("diary_map").performScrollTo()
            rule.mainClock.autoAdvance = false
            rule.mainClock.advanceTimeBy(1_000)
            rule.waitForIdle()
            ScreenGoldenCapture.verify("${case.name}_map", rule.onNodeWithTag("golden_viewport"))
        }
        if (case.state == "full") {
            rule.onNodeWithTag(if (case.screen == "place_picker") com.hoodie.app.presentation.screens.places.picker.PlacePickerTags.DETAILS else "${case.screen}_scroll").performSemanticsAction(SemanticsActions.ScrollBy) { it(0f, 100_000f) }
            rule.mainClock.advanceTimeBy(1_000)
            rule.waitForIdle()
            ScreenGoldenCapture.verify("${case.name}_bottom", rule.onNodeWithTag("golden_viewport"))
        }
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{0}")
        fun cases(): List<Array<Any>> = buildList<Array<Any>> {
            for (screen in listOf("home", "diary", "digital", "onboarding", "settings", "place_picker"))
                for (state in listOf("normal", "loading", "error", "empty", "full"))
                    for ((width, height) in listOf(360 to 640, 360 to 800, 411 to 891))
                        for (font in listOf(1f, 1.3f)) add(arrayOf(ScreenGoldenCase(screen, state, width, height, font)))
        }.filter { (it[0] as ScreenGoldenCase).name.matches(Regex(androidx.test.platform.app.InstrumentationRegistry.getArguments().getString("goldenCasesRegex") ?: ".*")) }
    }
}

private val date = LocalDate.of(2026, 10, 2)
private val zone = ZoneId.of("America/Sao_Paulo")
private fun at(hour: Int, minute: Int = 0) = date.atTime(hour, minute).atZone(zone).toInstant().toEpochMilli()

@Composable
private fun GoldenScreen(case: ScreenGoldenCase) {
    val full = case.state == "full"
    val loading = case.state == "loading"
    val error = case.state == "error"
    val empty = case.state == "empty"
    when (case.screen) {
        "home" -> {
            val snackbar = remember { SnackbarHostState() }
            val context = LocalContext.current
            LaunchedEffect(error) { if (error) snackbar.showSnackbar(context.appErrorText(DatabaseError.ReadFailed), duration = SnackbarDuration.Indefinite) }
            HomeContent(
                HomeUiState(
                    snapshot = if (empty || loading) null else homeSnapshot(),
                    context = if (empty || loading) null else ContextEvent(type = UserContextType.WORK, startedAt = at(9), endedAt = null, confidence = 0.95f, placeId = 2L, source = ContextSource.GEOFENCE),
                    next = if (full) com.hoodie.app.engine.routine.UpcomingEvent("🍽", "Almoço", 12 * 60, false) else null,
                    isWorkDay = !empty,
                    question = if (full) ContextQuestion(1L, QuestionKind.CONFIRM_CONTEXT, UserContextType.LUNCH, 2L, PlaceType.WORK, 1L, at(10), null, null) else null,
                    loading = loading, error = if (error) DatabaseError.ReadFailed else null, now = at(10), catName = if (full) "Hoodie Companheiro" else "Hoodie",
                    visual = if (empty || loading) null else VisualDirector.resolve(HoodieActivity.WORKING, UserContextType.WORK),
                    dialogue = if (full) "Bom dia! Vamos acompanhar sua rotina juntos." else null,
                    suggestSavePlace = if (full) PlaceType.WORK else null, canSaveHere = full,
                ), busy = false, zone = zone, onOpen = {}, snackbar = snackbar,
            )
        }
        "diary" -> DiaryContent(
            DiaryUiState(date, diary = if (empty || loading || error) null else diary(full), isLoading = loading,
                error = if (error) DatabaseError.ReadFailed else null), zone, at(22),
        )
        "digital" -> PhoneInsightsContent(
            PhoneInsightsUiState(date, permission = UsagePermissionState.GRANTED, settings = DigitalSettings(analysisEnabled = true),
                insights = if (empty || loading || error) null else phone(full), isLoading = loading,
                error = if (error) com.hoodie.app.core.error.UsageAccessError.ReadFailed else null),
            today = date, zone = zone, modifier = Modifier.verticalScroll(rememberScrollState()).testTag("digital_scroll"),
        )
        "onboarding" -> OnboardingContent(
            OnboardingState(
                step = when { loading || error -> OnboardingStep.HOME; empty -> OnboardingStep.NAME; full -> OnboardingStep.DONE; else -> OnboardingStep.WELCOME },
                catName = if (empty) "" else "Hoodie", busy = loading, homeSaved = full, workSaved = full,
                error = if (error) PlaceError.SaveFailed else null,
            ), LocationPermissionState.BACKGROUND, hasLocation = true,
        )
        "settings" -> SettingsContent(
            AppSettings(catName = if (full) "Hoodie Companheiro" else "Hoodie", digital = DigitalSettings(analysisEnabled = !empty)),
            isLoading = loading, error = if (error) DatabaseError.ReadFailed else null,
            permission = if (empty) LocationPermissionState.NONE else LocationPermissionState.BACKGROUND,
            geofenceResult = null, usagePermission = if (empty) UsagePermissionState.DENIED else UsagePermissionState.GRANTED,
            activityPermission = ActivityRecognitionPermissionState.GRANTED, onOpen = {},
        )
        "place_picker" -> PlacePickerLayout(
            PlacePickerState(
                editingId = if (loading || error) 1L else null,
                loadState = when { loading -> PlaceLoadState.Loading; error -> PlaceLoadState.Error(IllegalStateException("fixture")); else -> PlaceLoadState.Ready },
                query = if (full) "Avenida Paulista" else "",
                results = if (full) listOf(
                    com.hoodie.app.core.location.AddressResult("Avenida Paulista, Bela Vista, São Paulo", -23.5614, -46.6559),
                    com.hoodie.app.core.location.AddressResult("Avenida Paulista, Consolação, São Paulo", -23.5580, -46.6600),
                    com.hoodie.app.core.location.AddressResult("Avenida Paulista, Paraíso, São Paulo", -23.5700, -46.6450),
                ) else emptyList(),
                type = PlaceType.HOME, name = if (empty) "" else if (full) "Casa da família e amigos" else "Casa",
                address = if (empty) null else if (full) "Avenida Paulista, 1578, Bela Vista, São Paulo, SP, Brasil" else "Avenida Paulista, São Paulo",
                hasPoint = !empty, latitude = -23.5614, longitude = -46.6559,
            ), PlacePickerActions(),
        )
        else -> kotlin.error("Unknown screen ${case.screen}")
    }
}

private fun diary(full: Boolean): DailyDiary {
    val timeline = listOf(
        DiaryTimelineItem("home", at(7), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Acordou em casa", emoji = "🏠"),
        DiaryTimelineItem("work", at(9), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Chegou ao trabalho", emoji = "🏢"),
        DiaryTimelineItem("hoodie", at(9, 5), DiaryTimelineType.ACTIVITY, DiaryActor.HOODIE, "Hoodie começou a trabalhar", emoji = "🐱"),
    )
    val visits = listOf(
        PlaceVisit(1, "Casa", PlaceType.HOME, at(7), at(8), 3_600_000, 1),
        PlaceVisit(2, "Trabalho", PlaceType.WORK, at(9), at(18), 32_400_000, 1),
    )
    return DailyDiary(
        DailySummary(date, homeMs = 16_200_000, workMs = 28_800_000, commutingMs = 5_400_000,
            lunchMs = if (full) 3_120_000 else 0, gymMs = if (full) 2_880_000 else 0, leisureMs = if (full) 3_600_000 else 0),
        timeline, visits, DiaryMapData(), ReplaySequence(at(7), at(22), visits, timeline),
        phoneInsights = if (full) phone(true) else null,
    )
}

private fun phone(full: Boolean): DailyPhoneInsights {
    val apps = listOf(
        AppUsageEntry("fixture.chat", "Conversas", HoodieAppCategory.SOCIAL, 3_600_000, 12, at(8), at(20), AppIconSource.Generic),
        AppUsageEntry("fixture.music", "Música", HoodieAppCategory.MUSIC, 1_800_000, 3, at(9), at(18), AppIconSource.Generic),
    ).take(if (full) 2 else 1)
    return DailyPhoneInsights(
        DailyPhoneSummary(date, apps.sumOf { it.foregroundMs }, 15, 20, at(8), at(20), 1_200_000),
        apps, listOf(ContextUsageSummary(UserContextType.HOME, 3_600_000, 12, emptyList())),
        listOf(PhoneTimelineItem("fixture.chat", "Conversas", HoodieAppCategory.SOCIAL, at(8), at(9), UserContextType.HOME)),
        apps.map { CategoryUsageSummary(it.appCategory, it.foregroundMs, 1) },
    )
}

private fun homeSnapshot(): com.hoodie.app.engine.hoodie.HoodieSnapshot {
    val needs = Needs()
    val state = HoodieState(HoodieActivity.WORKING, at(9), at(12), needs, at(10), UserContextType.WORK)
    return com.hoodie.app.engine.hoodie.HoodieSnapshot(state, needs, emptyList())
}
