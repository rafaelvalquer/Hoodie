package com.hoodie.app.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.R
import com.hoodie.app.presentation.navigation.HoodieBottomNavigation
import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.presentation.theme.HoodieTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Production navigation component, selection, callbacks and every secondary destination. */
@RunWith(AndroidJUnit4::class)
class BottomNavigationTest {
    @get:Rule val rule = createAndroidComposeRule<ComponentActivity>()
    private val route = mutableStateOf<String?>(Routes.HOME)
    private val tabs = listOf(Routes.HOME to R.string.nav_home, Routes.TIMELINE to R.string.nav_timeline,
        Routes.PLACES to R.string.nav_places, Routes.DIARY to R.string.nav_diary, Routes.SETTINGS to R.string.nav_settings)
    private fun label(resource: Int) = InstrumentationRegistry.getInstrumentation().targetContext.getString(resource)
    private fun content() { rule.setContent { HoodieTheme { HoodieBottomNavigation(route.value) { route.value = it } } } }

    @Test fun primaryTabsRemainVisibleSelectedAndAccessible() {
        content()
        tabs.forEach { (destination, resource) ->
            rule.runOnIdle { route.value = destination }
            rule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
            rule.onNodeWithContentDescription(label(resource)).assertIsSelected().assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp)
        }
    }
    @Test fun secondaryDestinationsHideBarAndReturningRestoresIt() {
        content()
        listOf(Routes.PLACE_PICKER, Routes.ROUTINE, Routes.MEMORIES, Routes.PROFILE, Routes.PIXEL_LAB, Routes.DEV_LAB).forEach {
            rule.runOnIdle { route.value = it }
            rule.onNodeWithTag("bottom_navigation").assertDoesNotExist()
            rule.runOnIdle { route.value = Routes.HOME }
            rule.onNodeWithTag("bottom_navigation").assertIsDisplayed()
        }
    }
    @Test fun tabClickPublishesItsRoute() {
        content()
        rule.onNodeWithContentDescription(label(R.string.nav_diary)).performClick().assertIsSelected()
        rule.runOnIdle { assertEquals(Routes.DIARY, route.value) }
    }
}
