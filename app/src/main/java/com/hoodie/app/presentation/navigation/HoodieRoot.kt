package com.hoodie.app.presentation.navigation

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.ui.res.stringResource
import com.hoodie.app.R
import com.hoodie.app.presentation.common.GeofenceFeedbackHost
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.presentation.screens.places.picker.PlacePickerScreen
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.presentation.components.AnimatedHoodie
import com.hoodie.app.presentation.screens.devlab.DeveloperLabScreen
import com.hoodie.app.presentation.screens.diary.DiaryScreen
import com.hoodie.app.presentation.screens.home.HomeScreen
import com.hoodie.app.presentation.screens.memories.MemoriesScreen
import com.hoodie.app.presentation.screens.onboarding.OnboardingScreen
import com.hoodie.app.presentation.screens.pixellab.PixelLabScreen
import com.hoodie.app.presentation.screens.places.PlacesScreen
import com.hoodie.app.presentation.screens.profile.ProfileScreen
import com.hoodie.app.presentation.screens.routine.RoutineScreen
import com.hoodie.app.presentation.screens.settings.SettingsScreen
import com.hoodie.app.presentation.screens.timeline.TimelineScreen
import com.hoodie.app.pixel.icons.PixelIconView
import com.hoodie.app.pixel.icons.PixelIcons
import com.hoodie.app.pixel.icons.PixelSprite
import com.hoodie.app.presentation.theme.HoodieColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

enum class RootState { SPLASH, ONBOARDING, MAIN }

@HiltViewModel
class RootViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    private val splashDone = flow { emit(false); delay(1_100); emit(true) }
    val state = combine(settings.settings, splashDone) { s, done ->
        when {
            !done -> RootState.SPLASH
            !s.onboardingDone -> RootState.ONBOARDING
            else -> RootState.MAIN
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, RootState.SPLASH)
}

@Composable
fun HoodieRoot(vm: RootViewModel = hiltViewModel()) {
    val state by vm.state.collectAsStateWithLifecycle()
    GeofenceFeedbackHost(bottomPadding = if (state == RootState.MAIN) 80.dp else 0.dp) { HoodieRootContent(state) }
}

@Composable
private fun HoodieRootContent(state: RootState) {
    Box(Modifier.fillMaxSize().background(HoodieColors.Night)) {
        when (state) {
            RootState.SPLASH -> SplashScreen()
            RootState.ONBOARDING -> OnboardingScreen()
            RootState.MAIN -> MainScaffold()
        }
    }
}

@Composable
fun SplashScreen() {
    Column(
        Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("HOODIE", style = MaterialTheme.typography.displaySmall, color = HoodieColors.Hood)
        Text("Seu companheiro de rotina.", style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Muted, modifier = Modifier.padding(top = 4.dp, bottom = 24.dp))
        AnimatedHoodie(AnimationId.IDLE, size = 168.dp)
    }
}

/** [shortLabel] é o texto visível (cabe na aba de 72 dp); [label] vai para a descrição de acessibilidade. */
private data class Tab(val route: String, val icon: PixelSprite, @androidx.annotation.StringRes val label: Int, @androidx.annotation.StringRes val shortLabel: Int = label)

private val tabs = listOf(
    Tab(Routes.HOME, PixelIcons.CAT, R.string.nav_home),
    Tab(Routes.TIMELINE, PixelIcons.CALENDAR, R.string.nav_timeline, R.string.nav_timeline_short),
    Tab(Routes.PLACES, PixelIcons.PIN, R.string.nav_places, R.string.nav_places_short),
    Tab(Routes.DIARY, PixelIcons.MAP, R.string.nav_diary),
    Tab(Routes.SETTINGS, PixelIcons.GEAR, R.string.nav_settings),
)

object Routes {
    const val HOME = "home"
    const val TIMELINE = "timeline"
    const val PLACES = "places"
    const val DIARY = "diary"
    const val SETTINGS = "settings"
    const val ROUTINE = "routine"
    const val MEMORIES = "memories"
    const val PROFILE = "profile"
    const val PIXEL_LAB = "pixel_lab"
    const val DEV_LAB = "dev_lab"
    const val PLACE_PICKER = "place_picker?type={type}&placeId={placeId}"

    /** Abre o mapa para escolher um lugar (novo, ou [placeId] para mudar o local de um existente). */
    fun placePicker(type: PlaceType, placeId: Long? = null) = "place_picker?type=${type.name}&placeId=${placeId ?: -1}"
}

/** Only exact primary destinations retain the navigation bar. */
internal fun showsBottomNavigation(route: String?): Boolean = tabs.any { it.route == route }

@Composable
internal fun HoodieBottomNavigation(route: String?, onNavigate: (String) -> Unit) {
    if (!showsBottomNavigation(route)) return
    NavigationBar(modifier = Modifier.testTag("bottom_navigation"), containerColor = HoodieColors.Panel) {
        tabs.forEach { tab ->
            val label = stringResource(tab.label)
            val selected = route == tab.route
            val description = stringResource(if (selected) R.string.control_selected else R.string.control_not_selected)
            NavigationBarItem(
                modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).semantics {
                    contentDescription = label
                    stateDescription = description
                },
                selected = selected,
                onClick = { onNavigate(tab.route) },
                icon = { PixelIconView(tab.icon, size = 24.dp, tint = if (selected) HoodieColors.Hood else HoodieColors.Muted) },
                label = { Text(stringResource(tab.shortLabel), style = MaterialTheme.typography.labelSmall, maxLines = 1, softWrap = false, modifier = Modifier.wrapContentWidth(unbounded = true)) },
                colors = NavigationBarItemDefaults.colors(indicatorColor = HoodieColors.PanelLight, selectedTextColor = HoodieColors.Hood, unselectedTextColor = HoodieColors.MutedStrong),
            )
        }
    }
}

@Composable
fun MainScaffold() {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination
    Scaffold(
        containerColor = HoodieColors.Night,
        bottomBar = {
            HoodieBottomNavigation(current?.route) { route ->
                nav.navigate(route) {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        },
    ) { padding ->
        NavHost(nav, startDestination = Routes.HOME, modifier = Modifier.padding(padding)) {
            composable(Routes.HOME) { HomeScreen(onOpen = { nav.navigate(it) }) }
            composable(Routes.TIMELINE) { TimelineScreen() }
            composable(Routes.PLACES) { PlacesScreen(onOpen = { nav.navigate(it) }) }
            composable(Routes.DIARY) { DiaryScreen() }
            composable(Routes.SETTINGS) { SettingsScreen(onOpen = { nav.navigate(it) }) }
            composable(Routes.ROUTINE) { RoutineScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.MEMORIES) { MemoriesScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.PROFILE) { ProfileScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) }) }
            composable(Routes.PIXEL_LAB) { PixelLabScreen(onBack = { nav.popBackStack() }) }
            composable(Routes.DEV_LAB) { DeveloperLabScreen(onBack = { nav.popBackStack() }, onOpen = { nav.navigate(it) }) }
            composable(
                Routes.PLACE_PICKER,
                arguments = listOf(
                    navArgument("type") { type = NavType.StringType; defaultValue = PlaceType.HOME.name },
                    navArgument("placeId") { type = NavType.LongType; defaultValue = -1L },
                ),
            ) { entry ->
                val type = runCatching { PlaceType.valueOf(entry.arguments?.getString("type").orEmpty()) }.getOrDefault(PlaceType.HOME)
                val placeId = entry.arguments?.getLong("placeId")?.takeIf { it > 0 }
                PlacePickerScreen(type, placeId, onBack = { nav.popBackStack() }, scaffoldPadding = padding)
            }
        }
    }
}
