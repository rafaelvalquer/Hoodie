package com.hoodie.app.engine.diary

import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.engine.diary.journey.JourneyOverworldModel
import com.hoodie.app.pixel.diary.journey.JourneyLayout
import com.hoodie.app.pixel.diary.journey.JourneyLayoutEngine
import com.hoodie.app.pixel.diary.journey.JourneyRenderCache
import com.hoodie.app.pixel.diary.journey.JourneyLightingRenderer
import com.hoodie.app.pixel.diary.overworld.OverworldRenderCache
import com.hoodie.app.pixel.diary.overworld.OverworldScene
import com.hoodie.app.pixel.diary.DiaryMapLayout
import com.hoodie.app.pixel.diary.DiaryMapLayoutEngine
import com.hoodie.app.engine.diary.journey.DayClockLegacyAssembler
import com.hoodie.app.domain.diary.journey.DayClockLegacyData
import java.time.ZoneId

/** Immutable, background-prepared inputs shared by the diary's journey and clock views. */
data class PreparedJourney(
    val data: JourneyMapData,
    val legacyLayout: JourneyLayout,
    val legacyCache: JourneyRenderCache?,
    val legacyClock: DayClockLegacyData?,
    val placeLayout: DiaryMapLayout,
    val overworld: JourneyOverworldModel?,
    val overworldCaches: Map<com.hoodie.app.engine.diary.journey.OverworldLayout, OverworldRenderCache>,
)

object PreparedJourneyBuilder {
    fun build(diary: DailyDiary, now: Long, zone: ZoneId): PreparedJourney =
        build(JourneyMapAssembler.build(diary, now), zone, diary.visits)

    fun build(data: JourneyMapData, zone: ZoneId, visits: List<PlaceVisit> = emptyList()): PreparedJourney {
        val placeLayout = DiaryMapLayoutEngine.layout(visits)
        val legacyLayout = JourneyLayoutEngine.layout(data)
        val legacyCache = if (HoodieConfig.DIARY_JOURNEY_PRECOMPUTE) JourneyRenderCache.create(legacyLayout) else null
        val legacyClock = if (!HoodieConfig.DIARY_DAY_CLOCK_V2) DayClockLegacyAssembler.build(data, zone) else null
        val overworld = if (HoodieConfig.DIARY_JOURNEY_MAP_V3) JourneyOverworldModel.build(data, zone) else null
        val preparedOverworld = overworld
        val caches = if (!HoodieConfig.DIARY_JOURNEY_PRECOMPUTE || preparedOverworld == null) emptyMap() else buildMap {
            val layouts = listOfNotNull(preparedOverworld.single) + preparedOverworld.chapters.values
            layouts.distinct().forEach { layout ->
                val idleReplay = JourneyReplayAssembler.overworld(preparedOverworld.plan, layout, null, replaying = false)
                val scene = OverworldScene(
                    plan = preparedOverworld.plan,
                    layout = layout,
                    replay = idleReplay,
                    light = JourneyLightingRenderer.DAYLIGHT,
                    seed = preparedOverworld.seed,
                )
                put(layout, OverworldRenderCache.create(scene))
            }
        }
        return PreparedJourney(data, legacyLayout, legacyCache, legacyClock, placeLayout, overworld, caches)
    }
}
