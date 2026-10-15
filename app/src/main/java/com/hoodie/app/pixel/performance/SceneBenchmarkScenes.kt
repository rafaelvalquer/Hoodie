package com.hoodie.app.pixel.performance

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualDirector

/** Fictional, fixed workloads. Never reads the user's diary, location or routine. */
data class SceneBenchmarkCase(val scene: SceneId, val activity: HoodieActivity, val context: UserContextType, val place: PlaceType? = null) {
    fun visual() = VisualDirector.resolve(activity, context, shoppingVenue = place,
        venueScenes = VisualDirector.allVenueScenes, now = 0, personality = null).copy(daySeed = 20_026, placeId = 42)
}

object SceneBenchmarkScenes {
    val cases = listOf(
        SceneBenchmarkCase(SceneId.OFFICE, HoodieActivity.WORKING, UserContextType.WORK),
        SceneBenchmarkCase(SceneId.RESTAURANT, HoodieActivity.EATING, UserContextType.DINING),
        SceneBenchmarkCase(SceneId.SHOPPING, HoodieActivity.SHOPPING, UserContextType.SHOPPING),
        SceneBenchmarkCase(SceneId.STORE, HoodieActivity.SHOPPING, UserContextType.SHOPPING, PlaceType.STORE),
        SceneBenchmarkCase(SceneId.LEISURE, HoodieActivity.SIGHTSEEING, UserContextType.LEISURE),
        SceneBenchmarkCase(SceneId.BAKERY, HoodieActivity.EATING, UserContextType.DINING, PlaceType.BAKERY),
        SceneBenchmarkCase(SceneId.MALL, HoodieActivity.SHOPPING, UserContextType.SHOPPING, PlaceType.MALL),
        SceneBenchmarkCase(SceneId.BEACH, HoodieActivity.SIGHTSEEING, UserContextType.LEISURE, PlaceType.BEACH),
        SceneBenchmarkCase(SceneId.CINEMA, HoodieActivity.IDLE, UserContextType.LEISURE, PlaceType.CINEMA),
        SceneBenchmarkCase(SceneId.PHARMACY, HoodieActivity.SHOPPING, UserContextType.SHOPPING, PlaceType.PHARMACY),
        SceneBenchmarkCase(SceneId.HOTEL, HoodieActivity.IDLE, UserContextType.TRAVEL, PlaceType.HOTEL),
    )
}
