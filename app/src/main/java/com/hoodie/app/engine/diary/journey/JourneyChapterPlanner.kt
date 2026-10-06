package com.hoodie.app.engine.diary.journey

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.domain.diary.journey.BiomeType
import com.hoodie.app.domain.diary.journey.ChapterPlan
import com.hoodie.app.domain.diary.journey.DayChapter
import com.hoodie.app.domain.diary.journey.JourneyLeg
import com.hoodie.app.domain.diary.journey.JourneyPlan
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.domain.diary.model.JourneyMapData
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Números do planejamento (padrão em [HoodieConfig]; testes e o Diary Lab podem variar). */
data class JourneyPlanConfig(
    val singleMaxStops: Int = HoodieConfig.JOURNEY_SINGLE_MAP_MAX_STOPS,
    val chapterMaxStops: Int = HoodieConfig.JOURNEY_CHAPTER_MAX_STOPS,
    val quickStopMs: Long = HoodieConfig.JOURNEY_QUICK_STOP_MS,
    val afternoonStart: LocalTime = HoodieConfig.JOURNEY_AFTERNOON_START,
    val nightStart: LocalTime = HoodieConfig.JOURNEY_NIGHT_START,
    /** Diary Lab: força um modo (null = regra normal). */
    val forceChapters: Boolean? = null,
)

/** Intervalo de um capítulo no dia selecionado. */
data class ChapterWindow(val chapter: DayChapter, val startAt: Long, val endAt: Long) {
    operator fun contains(t: Long) = t in startAt until endAt
}

/**
 * Jornada do Dia 3.0 — regra pura que decide entre um mapa só (SINGLE) e três
 * capítulos (Manhã/Tarde/Noite). Fronteiras calculadas com ZonedDateTime (horário
 * de verão seguro); a visita pertence ao capítulo da chegada e, se continua no
 * seguinte, ele abre com um nó fantasma.
 */
object JourneyChapterPlanner {

    fun ghostId(chapter: DayChapter, ofStopId: String) = "ghost-${chapter.name.lowercase()}-$ofStopId"

    /** Data do mapa: a do início da janela do replay, no fuso atual. */
    fun dateOf(data: JourneyMapData, zone: ZoneId): LocalDate = Instant.ofEpochMilli(data.startAt).atZone(zone).toLocalDate()

    fun windows(date: LocalDate, zone: ZoneId, config: JourneyPlanConfig = JourneyPlanConfig()): List<ChapterWindow> {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val afternoon = date.atTime(config.afternoonStart).atZone(zone).toInstant().toEpochMilli()
        val night = date.atTime(config.nightStart).atZone(zone).toInstant().toEpochMilli()
        val end = date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return listOf(
            ChapterWindow(DayChapter.MORNING, start, afternoon),
            ChapterWindow(DayChapter.AFTERNOON, afternoon, night),
            ChapterWindow(DayChapter.NIGHT, night, end),
        )
    }

    /** Capítulo de um instante; antes do dia → Manhã, depois → Noite. */
    fun chapterOf(t: Long, windows: List<ChapterWindow>): DayChapter = when {
        t < windows[1].startAt -> DayChapter.MORNING
        t < windows[2].startAt -> DayChapter.AFTERNOON
        else -> DayChapter.NIGHT
    }

    fun plan(
        data: JourneyMapData,
        zone: ZoneId,
        protectedStopIds: Set<String> = emptySet(),
        config: JourneyPlanConfig = JourneyPlanConfig(),
        date: LocalDate = dateOf(data, zone),
    ): JourneyPlan {
        if (data.isEmpty) return JourneyPlan.EMPTY
        val visits = data.nodes.map { n ->
            JourneyStop.Visit(
                id = n.id, nodeId = n.id, visitIndex = n.visitIndex,
                biome = BiomeType.of(n.placeType, n.durationMs, n.hoodieActivity),
                placeType = n.placeType, label = n.placeName,
                arrivalAt = n.arrivalAt, departureAt = n.departureAt, durationMs = n.durationMs,
                returnIndex = n.returnNumber,
            )
        }
        val legs = data.segments.map { s ->
            JourneyLeg(s.id, s.index, s.fromNodeId, s.toNodeId, s.startedAt, s.endedAt, s.movementMode)
        }
        val chapters = config.forceChapters ?: (visits.size > config.singleMaxStops)
        if (!chapters) return JourneyPlan.Single(visits, legs)

        val windows = windows(date, zone, config)
        val openEnd = maxOf(data.endAt, visits.last().arrivalAt)
        val chapterOfVisit = visits.associate { it.id to chapterOf(it.arrivalAt, windows) }
        val extraStops = mutableListOf<JourneyStop>()
        val plans = windows.map { w ->
            val arrivals = visits.filter { chapterOfVisit[it.id] == w.chapter }
            // Fantasma: a última visita de antes que ainda está em andamento no início do capítulo.
            val ghost = if (w.chapter == DayChapter.MORNING) null else visits.lastOrNull { v ->
                v.arrivalAt < w.startAt && (v.departureAt ?: openEnd) > w.startAt
            }?.let { JourneyStop.Ghost(ghostId(w.chapter, it.id), it.id, w.startAt) }
            val clustered = QuickStopClusterer.cluster(arrivals, config.chapterMaxStops, config.quickStopMs, protectedStopIds)
            clustered.stops.filterIsInstance<JourneyStop.QuickCluster>().forEach { extraStops += it }
            ghost?.let { extraStops += it }
            val stopIds = listOfNotNull(ghost?.id) + clustered.stops.map { it.id }
            val stay = visits.sumOf { v -> overlap(v.arrivalAt, v.departureAt ?: openEnd, w.startAt, w.endAt) }
            // Portal de entrada: o trecho saiu num capítulo anterior e chega neste (sem fantasma, que já liga os dois).
            val entry = if (ghost != null) null else legs.firstOrNull { l ->
                l.startedAt < w.startAt && chapterOfVisit[l.toStopId] == w.chapter && chapterOfVisit[l.fromStopId] != w.chapter
            }
            val lastVisitId = arrivals.lastOrNull()?.id ?: ghost?.ofStopId
            val exit = legs.firstOrNull { l ->
                l.fromStopId == lastVisitId && l.startedAt < w.endAt && (chapterOfVisit[l.toStopId]?.ordinal ?: -1) > w.chapter.ordinal
            }
            ChapterPlan(
                chapter = w.chapter, startAt = w.startAt, endAt = w.endAt,
                stopIds = stopIds, ghostOfStopId = ghost?.ofStopId, totalStayMs = stay,
                entryLegId = entry?.id, exitLegId = exit?.id,
                visitCount = arrivals.size,
                biomes = clustered.stops.map { s -> (s as? JourneyStop.Visit)?.biome ?: BiomeType.MILESTONE },
            )
        }
        return JourneyPlan.Chapters(visits + extraStops, legs, plans)
    }

    /**
     * Capítulo aberto: replay tocando → o do instante; escolha manual; hoje → a hora
     * atual; senão → o de maior permanência.
     */
    fun activeChapter(
        plan: JourneyPlan.Chapters,
        replayTimestamp: Long?,
        playing: Boolean,
        manual: DayChapter?,
        isToday: Boolean,
        now: Long,
    ): DayChapter {
        val windows = plan.chapters.map { ChapterWindow(it.chapter, it.startAt, it.endAt) }
        fun pick(c: DayChapter): DayChapter = if (!plan.chapter(c).isEmpty) c else nearestNonEmpty(plan, c)
        return when {
            playing && replayTimestamp != null -> pick(chapterOf(replayTimestamp, windows))
            manual != null && !plan.chapter(manual).isEmpty -> manual
            isToday -> pick(chapterOf(now, windows))
            else -> plan.chapters.maxByOrNull { it.totalStayMs }?.takeIf { !it.isEmpty }?.chapter ?: nearestNonEmpty(plan, DayChapter.MORNING)
        }
    }

    /** O capítulo não vazio mais próximo (para trás primeiro); todos vazios → o próprio. */
    private fun nearestNonEmpty(plan: JourneyPlan.Chapters, c: DayChapter): DayChapter {
        val order = DayChapter.entries.sortedWith(compareBy({ kotlin.math.abs(it.ordinal - c.ordinal) }, { -it.ordinal }))
        return order.firstOrNull { !plan.chapter(it).isEmpty } ?: c
    }

    private fun overlap(a0: Long, a1: Long, b0: Long, b1: Long) = (minOf(a1, b1) - maxOf(a0, b0)).coerceAtLeast(0)

    /** Parada onde o replay está (não pode sumir num grupo). */
    fun replayStopId(data: JourneyMapData, timestamp: Long?): String? {
        if (timestamp == null) return null
        return data.nodes.lastOrNull { it.arrivalAt <= timestamp && (it.departureAt ?: Long.MAX_VALUE) > timestamp }?.id
    }
}
