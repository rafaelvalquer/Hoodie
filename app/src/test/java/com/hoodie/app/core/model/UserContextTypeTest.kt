package com.hoodie.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserContextTypeTest {
    @Test fun manualOptionsIncludePhysicalPlaceChoicesAndKeepFallbackLast() {
        assertEquals(
            listOf(UserContextType.HOME, UserContextType.WORK, UserContextType.STUDY, UserContextType.SHOPPING, UserContextType.GYM, UserContextType.LEISURE, UserContextType.VISITING, UserContextType.LUNCH, UserContextType.UNKNOWN),
            UserContextType.manualOptions,
        )
        assertEquals(UserContextType.UNKNOWN, UserContextType.manualOptions.last())
    }
}
