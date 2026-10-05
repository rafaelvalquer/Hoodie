package com.hoodie.app.integration

import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.ms
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Explicit restaurant selections must never be rewritten by lunch-time heuristics. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ManualRestaurantContextTest {
    private lateinit var graph: TestGraph
    private var restaurantId = 0L

    @Before fun setUp() = runBlocking {
        graph = TestGraph(at(MONDAY, 8).ms())
        restaurantId = graph.addPlace(PlaceType.RESTAURANT, -23.62).id
    }

    @After fun tearDown() = graph.close()

    @Test fun manualRestaurantRemainsDiningAtEveryHour() = runBlocking {
        listOf(9 to 0, 12 to 0, 14 to 30, 20 to 0, 23 to 0).forEach { (hour, minute) ->
            val now = at(MONDAY, hour, minute).ms()
            graph.clock.millis = now
            graph.engine.setManualPlace(PlaceType.RESTAURANT)

            val context = graph.contextDao.current()
            assertNotNull(context)
            assertEquals("manual at $hour:$minute", UserContextType.DINING, context!!.type)
            assertEquals(restaurantId, context.placeId)
            assertEquals(ContextSource.MANUAL, context.source)
            assertEquals(UserContextType.DINING, graph.db.hoodieStateDao().get()!!.userContext)
        }
    }
}
