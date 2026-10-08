package com.hoodie.app.engine.home

import com.hoodie.app.core.location.LocationStatus
import com.hoodie.app.core.model.ContextEvent
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.home.HomeNowStatus
import com.hoodie.app.domain.daystate.DayState
import com.hoodie.app.domain.daystate.DayStateReason
import com.hoodie.app.domain.daystate.DayStateSnapshot
import com.hoodie.app.domain.detection.ConfidenceScore
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt

class HomeNowAssemblerTest {
    @Test fun probableContextCarriesItsCanonicalConfidenceAndPlace() {
        val context = ContextEvent(41, UserContextType.WORK, 1_000, null, .63f, 8, ContextSource.GEOFENCE)
        val result = HomeNowAssembler.assemble(null, context, "Escritório", null, null, 10_000)
        assertEquals(HomeNowStatus.PROBABLE, result.status)
        assertEquals(63, (result.contextConfidence!!.value * 100).roundToInt())
        assertEquals(41L, requireNotNull(result.contextEventId))
        assertEquals("Escritório", result.placeName)
    }

    @Test fun explicitContextIsConfirmedAndMissingContextDoesNotBecomeHome() {
        val confirmed = HomeNowAssembler.assemble(null,
            ContextEvent(2, UserContextType.DINING, 1, null, 1f, null, ContextSource.USER_CORRECTION), null, null, null, 3)
        val unknown = HomeNowAssembler.assemble(null, null, null, null, null, 3)
        assertEquals(HomeNowStatus.CONFIRMED, confirmed.status)
        assertEquals(UserContextType.DINING, confirmed.context)
        assertEquals(HomeNowStatus.UNKNOWN, unknown.status)
        assertEquals(null, unknown.context)
    }

    @Test fun lackOfLocationPermissionIsUnavailableOnlyWithoutAnExistingContext() {
        val unavailable = HomeNowAssembler.assemble(null, null, null, null, null, 3, LocationStatus.NO_PERMISSION)
        val known = HomeNowAssembler.assemble(null,
            ContextEvent(2, UserContextType.WORK, 1, null, .85f, null, ContextSource.GEOFENCE), null, null, null, 3, LocationStatus.NO_PERMISSION)
        assertEquals(HomeNowStatus.UNAVAILABLE, unavailable.status)
        assertEquals(HomeNowStatus.PROBABLE, known.status)
    }

    @Test fun dayStateAndContextRemainIndependentForSleepAndCommute() {
        val sleepingAtHome = HomeNowAssembler.assemble(
            DayStateSnapshot(DayState.SLEEPING, 100L, ConfidenceScore(.9f), DayStateReason.SLEEP_CONFIRMED, false),
            ContextEvent(7, UserContextType.HOME, 50, null, .95f, 8, ContextSource.GEOFENCE),
            "Casa", null, null, 200,
        )
        val commuting = HomeNowAssembler.assemble(
            DayStateSnapshot(DayState.COMMUTING, 100L, ConfidenceScore(.8f), DayStateReason.COMMUTE_INFERRED, false),
            ContextEvent(8, UserContextType.WORK, 50, null, .9f, 9, ContextSource.GEOFENCE),
            "Escritório", null, null, 200,
        )

        assertEquals(DayState.SLEEPING, sleepingAtHome.dayState?.state)
        assertEquals(UserContextType.HOME, sleepingAtHome.context)
        assertEquals(DayState.COMMUTING, commuting.dayState?.state)
        assertEquals(UserContextType.WORK, commuting.context)
    }

    @Test fun routineOnlyLowConfidenceAndMissingStartNeverClaimConfirmation() {
        val routine = HomeNowAssembler.assemble(null,
            ContextEvent(9, UserContextType.WORK, 0, null, .59f, null, ContextSource.ROUTINE),
            null, null, null, 10)
        val absentStart = HomeNowAssembler.assemble(null,
            ContextEvent(10, UserContextType.DINING, 0, null, .82f, null, ContextSource.GEOFENCE),
            null, null, null, 10)

        assertEquals(HomeNowStatus.UNKNOWN, routine.status)
        assertEquals(null, routine.contextStartedAt)
        assertEquals(HomeNowStatus.PROBABLE, absentStart.status)
        assertEquals(null, absentStart.contextStartedAt)
    }
}
