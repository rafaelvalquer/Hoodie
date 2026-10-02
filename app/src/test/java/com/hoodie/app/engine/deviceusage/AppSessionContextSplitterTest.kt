package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.engine.timeline.ContextSpan
import org.junit.Assert.assertEquals
import org.junit.Test

class AppSessionContextSplitterTest {
    @Test fun splitsAtContextBoundaryWithoutLosingTime() {
        val pieces = AppSessionContextSplitter.split(listOf(AppSession("video", 0, 30)),
            listOf(ContextSpan(UserContextType.WORK, 0, 15), ContextSpan(UserContextType.LUNCH, 15, 30)), 30)
        assertEquals(listOf(UserContextType.WORK, UserContextType.LUNCH), pieces.map { it.context })
        assertEquals(listOf(15L, 15L), pieces.map { it.endedAt - it.startedAt })
    }

    @Test fun keepsGapsAndClipsOpenContextAtEnd() {
        val pieces = AppSessionContextSplitter.split(listOf(AppSession("video", 0, 100)),
            listOf(ContextSpan(UserContextType.WORK, 20, 40), ContextSpan(UserContextType.HOME, 60, null)), 80)
        assertEquals(listOf(null, UserContextType.WORK, null, UserContextType.HOME), pieces.map { it.context })
        assertEquals(80L, pieces.sumOf { it.endedAt - it.startedAt })
    }

    @Test fun newerContextWinsOverOverlapWithoutDoubleCounting() {
        val pieces = AppSessionContextSplitter.split(listOf(AppSession("video", 0, 100)),
            listOf(ContextSpan(UserContextType.WORK, 0, 100), ContextSpan(UserContextType.LUNCH, 40, 60)), 100)
        assertEquals(listOf(UserContextType.WORK, UserContextType.LUNCH, UserContextType.WORK), pieces.map { it.context })
        assertEquals(100L, pieces.sumOf { it.endedAt - it.startedAt })
    }
}
