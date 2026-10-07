package com.hoodie.app.engine.daycycle

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.model.UserContextType
import org.junit.Assert.assertEquals
import org.junit.Test

class RealUserActivityBuilderTest {
    @Test fun onlyRealUserSignalsContributeAndPhoneToggleIsRespected() {
        val home = ContextEventEntity(1, UserContextType.HOME, 10, 100, 1f, 1, ContextSource.GEOFENCE)
        val timeline = listOf(
            TimelineEventEntity(1, 20, TimelineActor.USER, "🏠", "Chegou", TimelineSourceType.CONTEXT, 1),
            TimelineEventEntity(2, 30, TimelineActor.HOODIE, "🌙", "Dormiu", TimelineSourceType.HOODIE_ACTIVITY, 30),
        )

        val actual = RealUserActivityBuilder.build(0, 101, contexts = listOf(home), timeline = timeline)

        assertEquals(listOf(10L, 20L, 100L), actual.map { it.timestamp })
        assertEquals(RealUserActivitySource.TIMELINE, actual[1].source)
        val phoneOff = RealUserActivityBuilder.build(
            0, 101,
            appSessions = listOf(com.hoodie.app.domain.phoneinsights.model.AppSession("app", 40, 50)),
            analysisEnabled = false,
        )
        assertEquals(emptyList<RealUserActivity>(), phoneOff)
    }
}
