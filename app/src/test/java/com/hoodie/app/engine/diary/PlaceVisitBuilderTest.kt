package com.hoodie.app.engine.diary

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaceVisitBuilderTest {
    @Test fun omitsCommuteAndKeepsRepeatedArrivalsAsSeparateVisits() {
        val place = PlaceEntity(id = 1, name = "Casa", type = PlaceType.HOME, encryptedCoordinates = "", radiusMeters = 80f, confidence = 1f, createdAt = 0)
        val contexts = listOf(UserContextType.HOME, UserContextType.COMMUTING, UserContextType.HOME).mapIndexed { i, type ->
            ContextEventEntity(id = i.toLong(), type = type, startedAt = i * 100L, endedAt = i * 100L + 50, confidence = 1f, placeId = if (type == UserContextType.COMMUTING) null else 1, source = ContextSource.GEOFENCE)
        }
        val result = PlaceVisitBuilder.build(contexts, listOf(place), 0, 1_000, 1_000)
        assertEquals(2, result.size)
        assertEquals(listOf(2, 2), result.map { it.visitsCount })
        assertEquals(listOf(0L, 200L), result.map { it.arrivalAt })
    }
}
