package com.hoodie.app.pixel.diary.overworld

import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.journey.BiomeType
import com.hoodie.app.domain.diary.journey.JourneyPlan
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.domain.diary.model.DiaryMapNodeType
import com.hoodie.app.pixel.diary.overworld.biomes.BiomePainter
import com.hoodie.app.pixel.diary.overworld.biomes.CafeBiome
import com.hoodie.app.pixel.diary.overworld.biomes.CampBiome
import com.hoodie.app.pixel.diary.overworld.biomes.FamilyLodgeBiome
import com.hoodie.app.pixel.diary.overworld.biomes.HouseBiome
import com.hoodie.app.pixel.diary.overworld.biomes.MarketBiome
import com.hoodie.app.pixel.diary.overworld.biomes.MilestoneBiome
import com.hoodie.app.pixel.diary.overworld.biomes.OfficeCastleBiome
import com.hoodie.app.pixel.diary.overworld.biomes.ParkBiome
import com.hoodie.app.pixel.diary.overworld.biomes.TavernBiome
import com.hoodie.app.pixel.diary.overworld.biomes.TempleBiome
import com.hoodie.app.pixel.diary.overworld.biomes.WizardTowerBiome
import com.hoodie.app.pixel.renderer.PixelBuffer

/** Tipo de lugar → construção do overworld. Todo tipo tem bioma; o desconhecido vira acampamento. */
object OverworldBiomeCatalog {

    fun painter(biome: BiomeType): BiomePainter = when (biome) {
        BiomeType.HOUSE -> HouseBiome
        BiomeType.OFFICE_CASTLE -> OfficeCastleBiome
        BiomeType.TEMPLE -> TempleBiome
        BiomeType.TAVERN -> TavernBiome
        BiomeType.CAFE -> CafeBiome
        BiomeType.WIZARD_TOWER -> WizardTowerBiome
        BiomeType.MARKET -> MarketBiome
        BiomeType.PARK -> ParkBiome
        BiomeType.FAMILY_LODGE -> FamilyLodgeBiome
        BiomeType.CAMP -> CampBiome
        BiomeType.MILESTONE -> MilestoneBiome
    }

    fun biomeFor(type: PlaceType?): BiomeType = type?.let { BiomeType.of(it) } ?: BiomeType.CAMP
    fun biomeFor(type: DiaryMapNodeType?): BiomeType = type?.let { BiomeType.of(it) } ?: BiomeType.CAMP

    /** Bioma de uma parada do plano (fantasma = o da visita original; grupo = marco). */
    fun biomeOf(stop: JourneyStop, plan: JourneyPlan): BiomeType = when (stop) {
        is JourneyStop.Visit -> stop.biome
        is JourneyStop.Ghost -> plan.visitOf(stop.ofStopId)?.biome ?: BiomeType.CAMP
        is JourneyStop.QuickCluster -> BiomeType.MILESTONE
    }

    /** Pinta a construção com o canto superior esquerdo em (x, y). */
    fun paint(b: PixelBuffer, biome: BiomeType, x: Int, y: Int, state: BiomeState, night: Boolean, timeMs: Long) =
        painter(biome).paint(BiomeCanvas(b, x, y, state), night, timeMs)
}
