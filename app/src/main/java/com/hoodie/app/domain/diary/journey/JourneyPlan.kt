package com.hoodie.app.domain.diary.journey

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.domain.diary.model.DiaryMapNodeType

/** Período do dia que vira um capítulo da Jornada. */
enum class DayChapter { MORNING, AFTERNOON, NIGHT }

/** Construção do overworld que representa cada tipo de lugar. */
enum class BiomeType {
    HOUSE, OFFICE_CASTLE, TEMPLE, TAVERN, CAFE, WIZARD_TOWER,
    MARKET, PARK, FAMILY_LODGE, CAMP, MILESTONE;

    companion object {
        /** Restaurante curto (ou café do Hoodie) é a cabana do café; refeição de verdade é a taverna. */
        const val CAFE_MAX_MS = 30 * 60_000L

        fun of(type: PlaceType, durationMs: Long = Long.MAX_VALUE, activity: HoodieActivity? = null): BiomeType = when (type) {
            PlaceType.HOME -> HOUSE
            PlaceType.WORK -> OFFICE_CASTLE
            PlaceType.GYM -> TEMPLE
            PlaceType.RESTAURANT -> if (activity == HoodieActivity.COFFEE || durationMs < CAFE_MAX_MS) CAFE else TAVERN
            PlaceType.SCHOOL -> WIZARD_TOWER
            PlaceType.MARKET, PlaceType.STORE -> MARKET
            PlaceType.LEISURE -> PARK
            PlaceType.FAMILY -> FAMILY_LODGE
            PlaceType.OTHER -> CAMP
        }

        fun of(type: DiaryMapNodeType): BiomeType = when (type) {
            DiaryMapNodeType.HOME -> HOUSE
            DiaryMapNodeType.WORK -> OFFICE_CASTLE
            DiaryMapNodeType.RESTAURANT -> TAVERN
            DiaryMapNodeType.GYM -> TEMPLE
            DiaryMapNodeType.SCHOOL -> WIZARD_TOWER
            DiaryMapNodeType.MARKET -> MARKET
            DiaryMapNodeType.LEISURE -> PARK
            DiaryMapNodeType.FAMILY -> FAMILY_LODGE
            DiaryMapNodeType.OTHER -> CAMP
        }
    }
}

/** Uma parada no plano: visita, fantasma (visita que continua) ou marco de paradas rápidas. */
sealed interface JourneyStop {
    val id: String
    /** Início no tempo (chegada, começo do capítulo para o fantasma, primeira chegada do grupo). */
    val startAt: Long

    data class Visit(
        override val id: String,
        /** Id do [com.hoodie.app.domain.diary.model.JourneyNode] (detalhe da parada). */
        val nodeId: String,
        val visitIndex: Int,
        val biome: BiomeType,
        val placeType: PlaceType,
        val label: String,
        val arrivalAt: Long,
        val departureAt: Long?,
        val durationMs: Long,
        /** 1 = primeira vez no dia; 2, 3… nos retornos (selo). */
        val returnIndex: Int,
    ) : JourneyStop {
        override val startAt get() = arrivalAt
    }

    data class Ghost(
        override val id: String,
        val ofStopId: String,
        override val startAt: Long,
    ) : JourneyStop

    data class QuickCluster(
        override val id: String,
        val stopIds: List<String>,
        override val startAt: Long,
        val endAt: Long,
    ) : JourneyStop {
        val count get() = stopIds.size
    }
}

/** Deslocamento entre duas visitas consecutivas (mesmo índice do [com.hoodie.app.domain.diary.model.JourneySegment]). */
data class JourneyLeg(
    val id: String,
    val index: Int,
    val fromStopId: String,
    val toStopId: String,
    val startedAt: Long,
    val endedAt: Long,
    val mode: MovementMode?,
)

/** Um capítulo: intervalo, paradas na ordem (fantasma primeiro) e trechos pelos portais. */
data class ChapterPlan(
    val chapter: DayChapter,
    val startAt: Long,
    val endAt: Long,
    /** Ids das paradas na ordem do capítulo, incluindo fantasma e grupos. */
    val stopIds: List<String>,
    /** Visita que continua do capítulo anterior (nó fantasma no topo). */
    val ghostOfStopId: String?,
    val totalStayMs: Long,
    /** Trecho que chega pela borda de cima (vem do capítulo anterior). */
    val entryLegId: String?,
    /** Trecho que sai pela borda de baixo (vai para um capítulo seguinte). */
    val exitLegId: String?,
    /** Visitas reais (sem fantasma; grupos contam as paradas internas). */
    val visitCount: Int,
    /** Mini ícones do resumo fechado, na ordem. */
    val biomes: List<BiomeType>,
) {
    /** Sem chegada nem visita em andamento: "Sem paradas", não expande. */
    val isEmpty: Boolean get() = stopIds.isEmpty()
    val onlyGhost: Boolean get() = stopIds.size == 1 && ghostOfStopId != null
}

/** Resultado do planejamento: um mapa só ou três capítulos. */
sealed interface JourneyPlan {
    val stops: List<JourneyStop>
    val legs: List<JourneyLeg>

    fun stop(id: String?): JourneyStop? = stops.firstOrNull { it.id == id }
    fun leg(id: String?): JourneyLeg? = legs.firstOrNull { it.id == id }

    /** Visita real por id (fantasma → a visita original; grupo → null). */
    fun visitOf(id: String?): JourneyStop.Visit? = when (val s = stop(id)) {
        is JourneyStop.Visit -> s
        is JourneyStop.Ghost -> stop(s.ofStopId) as? JourneyStop.Visit
        else -> null
    }

    data class Single(
        override val stops: List<JourneyStop>,
        override val legs: List<JourneyLeg>,
    ) : JourneyPlan

    data class Chapters(
        override val stops: List<JourneyStop>,
        override val legs: List<JourneyLeg>,
        /** Sempre 3, na ordem MORNING, AFTERNOON, NIGHT. */
        val chapters: List<ChapterPlan>,
    ) : JourneyPlan {
        fun chapter(c: DayChapter) = chapters.first { it.chapter == c }
    }

    companion object {
        val EMPTY: JourneyPlan = Single(emptyList(), emptyList())
    }
}
