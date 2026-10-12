package com.hoodie.app.core.debug

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualDirector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class HomeVisualDiagnosticsTest {
    @Test fun selectedAndPresentedScenesRemainDistinctAndLastPresentationSurvivesHomeClose() {
        val diagnostics = HomeVisualDiagnostics()
        val selected = VisualDirector.resolve(HoodieActivity.COMMUTING, UserContextType.COMMUTING)
        diagnostics.selected(selected, 100L)
        diagnostics.presented(SceneId.BUS, 120L)
        diagnostics.visibility(true)

        val live = diagnostics.snapshot.value
        assertEquals(selected.scene, live.selected)
        assertEquals(SceneId.BUS, live.presented)
        assertNotNull(live.presentedAt)

        diagnostics.visibility(false)
        val closed = diagnostics.snapshot.value
        assertFalse(closed.homeVisible)
        assertEquals(SceneId.BUS, closed.presented)
        assertEquals(120L, closed.presentedAt)
    }
}
