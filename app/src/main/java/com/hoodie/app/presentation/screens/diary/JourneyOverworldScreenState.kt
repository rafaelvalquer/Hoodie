package com.hoodie.app.presentation.screens.diary

import androidx.annotation.StringRes
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.domain.diary.journey.DayChapter
import com.hoodie.app.domain.diary.journey.JourneyPlan
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.engine.diary.journey.JourneyOverworldModel
import com.hoodie.app.engine.diary.journey.OverworldLayout
import com.hoodie.app.pixel.diary.journey.JourneyLightingRenderer
import com.hoodie.app.pixel.diary.overworld.OverworldScene
import java.time.ZoneId

@StringRes
fun DayChapter.labelRes(): Int = when (this) {
    DayChapter.MORNING -> R.string.journey_chapter_morning
    DayChapter.AFTERNOON -> R.string.journey_chapter_afternoon
    DayChapter.NIGHT -> R.string.journey_chapter_night
}

/**
 * Replay da tela → cena do overworld para um mapa (o dia ou um capítulo). O mesmo
 * timestamp do replay vale para a Jornada e para o Relógio.
 */
fun overworldScene(
    model: JourneyOverworldModel,
    layout: OverworldLayout,
    replay: ReplayUiState,
    selectedStopId: String?,
    zone: ZoneId,
    timeMs: Long,
    framed: Boolean,
): OverworldScene = OverworldScene(
    plan = model.plan,
    layout = layout,
    replay = JourneyReplayAssembler.overworld(model.plan, layout, replay.currentTimestamp, replay.replaying),
    light = if (replay.hasReplayTimestamp) JourneyLightingRenderer.resolve(replay.currentTimestamp, zone) else JourneyLightingRenderer.DAYLIGHT,
    selectedStopId = selectedStopId,
    timeMs = timeMs,
    seed = model.seed,
    framed = framed,
)

/** Textos puros do overworld (testáveis sem Compose). */
object OverworldText {
    /** Nome curto que cabe na plaquinha. */
    fun signName(name: String, max: Int = 9): String = JourneyText.shortName(name, max).uppercase()

    fun arrival(stop: JourneyStop, zone: ZoneId): String = formatClock(stop.startAt, zone)
}

/**
 * Parada do overworld → nó do detalhe. Fantasma abre a visita original; grupo não tem
 * nó próprio (abre a lista das paradas rápidas).
 */
fun JourneyOverworldModel.nodeOf(stopId: String?): JourneyNode? =
    plan.visitOf(stopId)?.let { data.node(it.nodeId) }

/** Visitas internas de um grupo "×k" (para a lista). */
fun JourneyOverworldModel.clusterNodes(stopId: String?): List<JourneyNode> =
    (plan.stop(stopId) as? JourneyStop.QuickCluster)?.stopIds?.mapNotNull { id -> plan.visitOf(id)?.let { data.node(it.nodeId) } }.orEmpty()

/** Paradas protegidas do agrupamento: a atual do replay e a selecionada. */
fun protectedStops(model: JourneyOverworldModel?, data: com.hoodie.app.domain.diary.model.JourneyMapData, replay: ReplayUiState, selectedStopId: String?): Set<String> =
    setOfNotNull(
        com.hoodie.app.engine.diary.journey.JourneyChapterPlanner.replayStopId(data, replay.currentTimestamp),
        model?.plan?.visitOf(selectedStopId)?.id ?: selectedStopId,
    )

val JourneyPlan.chaptersOrNull: JourneyPlan.Chapters? get() = this as? JourneyPlan.Chapters
