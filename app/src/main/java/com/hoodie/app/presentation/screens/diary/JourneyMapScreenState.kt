package com.hoodie.app.presentation.screens.diary

import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.JourneyMapData
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.domain.diary.model.JourneySegment
import com.hoodie.app.engine.diary.JourneyMapAssembler
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.pixel.diary.journey.JourneyLayout
import com.hoodie.app.pixel.diary.journey.JourneyLayoutEngine
import com.hoodie.app.pixel.diary.journey.JourneyLightingRenderer
import com.hoodie.app.pixel.diary.journey.JourneyScene
import com.hoodie.app.pixel.diary.journey.NodeState
import com.hoodie.app.pixel.diary.journey.JourneyMapRenderer
import java.time.ZoneId

/** Dados + layout da jornada de um dia: calculados uma vez por dia carregado, não por quadro. */
data class JourneyMapModel(val data: JourneyMapData, val layout: JourneyLayout) {
    companion object {
        fun from(diary: DailyDiary, now: Long): JourneyMapModel {
            val data = JourneyMapAssembler.build(diary, now)
            return JourneyMapModel(data, JourneyLayoutEngine.layout(data))
        }
    }
}

/**
 * Replay da tela → cena do renderer. A luz segue o horário do replay sempre que
 * houver um (inclusive FINISHED); parado, é dia claro.
 */
fun journeyScene(model: JourneyMapModel, replay: ReplayUiState, selectedNodeId: String?, zone: ZoneId, timeMs: Long): JourneyScene =
    JourneyScene(
        data = model.data,
        layout = model.layout,
        replay = JourneyReplayAssembler.stateAt(model.data, replay.currentTimestamp, replay.replaying),
        light = if (replay.hasReplayTimestamp) JourneyLightingRenderer.resolve(replay.currentTimestamp, zone) else JourneyLightingRenderer.DAYLIGHT,
        selectedNodeId = selectedNodeId,
        timeMs = timeMs,
    )

/** Textos dos cartões e selos (puros, para teste). */
object JourneyText {
    /** "08:15–12:08" ou "08:15–agora" quando a visita ainda não terminou. */
    fun times(node: JourneyNode, zone: ZoneId, nowLabel: String = "agora"): String =
        "${formatClock(node.arrivalAt, zone)}–${node.departureAt?.let { formatClock(it, zone) } ?: nowLabel}"

    /** "🚌 31 min" (ou "🚶 deslocamento" sem meio detectado no texto genérico). */
    fun segmentChip(segment: JourneySegment): String =
        "${segment.movementMode?.emoji ?: "🧭"} ${formatDuration(segment.durationMs)}"

    /** "07:54 → 08:25" */
    fun segmentTimes(segment: JourneySegment, zone: ZoneId): String =
        "${formatClock(segment.startedAt, zone)} → ${formatClock(segment.endedAt, zone)}"

    /** Nome curto para caber no cartão. */
    fun shortName(name: String, max: Int = 14): String = if (name.length <= max) name else name.take(max - 1).trimEnd() + "…"
}

/** Estado visual de cada parada na tela (o mesmo que o renderer usa). */
fun journeyNodeState(scene: JourneyScene, index: Int): NodeState = JourneyMapRenderer.nodeState(scene.replay, index)
