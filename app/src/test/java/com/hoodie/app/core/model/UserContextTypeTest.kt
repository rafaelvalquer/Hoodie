package com.hoodie.app.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class UserContextTypeTest {
    @Test fun manualOptionsIncludeEverySupportedContextOnceWithOtherLast() {
        assertEquals(UserContextType.entries.toSet(), UserContextType.manualOptions.toSet())
        assertEquals(UserContextType.entries.size, UserContextType.manualOptions.size)
        assertEquals(UserContextType.UNKNOWN, UserContextType.manualOptions.last())
    }
}
