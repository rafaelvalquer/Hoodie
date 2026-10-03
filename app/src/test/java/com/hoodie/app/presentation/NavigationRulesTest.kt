package com.hoodie.app.presentation

import com.hoodie.app.presentation.navigation.Routes
import com.hoodie.app.presentation.navigation.showsBottomNavigation
import com.hoodie.app.core.model.PlaceType
import org.junit.Assert.*
import org.junit.Test

class NavigationRulesTest {
    @Test fun allPrimaryRoutesShowNavigation() {
        listOf(Routes.HOME, Routes.TIMELINE, Routes.PLACES, Routes.DIARY, Routes.SETTINGS).forEach { assertTrue(it, showsBottomNavigation(it)) }
    }
    @Test fun allSecondaryRoutesHideNavigation() {
        listOf(Routes.PLACE_PICKER, Routes.placePicker(PlaceType.HOME), Routes.ROUTINE, Routes.MEMORIES, Routes.PROFILE, Routes.PIXEL_LAB, Routes.DEV_LAB).forEach { assertFalse(it, showsBottomNavigation(it)) }
    }
    @Test fun missingAndUnknownRoutesHideNavigation() {
        assertFalse(showsBottomNavigation(null))
        assertFalse(showsBottomNavigation("unknown"))
    }
}
