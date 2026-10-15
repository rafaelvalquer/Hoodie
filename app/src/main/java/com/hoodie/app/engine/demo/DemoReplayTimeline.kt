package com.hoodie.app.engine.demo

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType

enum class DemoReplayMode { QUICK_TOUR, FULL_DAY }
enum class DemoReplayHighlight { HOME, OUTBOUND_ROUTE, WORK, RESTAURANT, WORK_RETURN, INBOUND_ROUTE, HOME_RETURN }

data class DemoTourStep(
    val timestamp: Long, val elapsedStartMs: Long, val durationMs: Long, val title: String,
    val narrative: String, val context: UserContextType, val activity: HoodieActivity,
    val highlight: DemoReplayHighlight,
)

/** One fictitious time axis. Tour and complete replay use the same positions and event identities. */
class DemoReplayTimeline(val day: DemoDay) {
    private val window = requireNotNull(day.activityWindow) { "Replay requires the explicit fictitious active window" }
    val startAt = window.activeStartAt
    val endAt = window.activeEndAt
    val eventTimes: List<Long> = (day.contexts.map { it.startedAt } + day.activities.map { it.startedAt } + endAt)
        .filter { it in startAt..endAt }.distinct().sorted()
    val tour: List<DemoTourStep>
    val tourDurationMs: Long

    init {
        require(day.steps.size == 7 && day.activities.isNotEmpty())
        val durations = listOf(6_000L, 7_000, 8_000, 7_000, 8_000, 7_000, 7_000)
        val narratives = listOf(
            "Neste exemplo, o Hoodie começa o dia em Casa e acompanha uma rotina fictícia.",
            "A Jornada ilustra um deslocamento da Casa para o Trabalho.",
            "O cenário de Escritório mostra uma atividade de trabalho fictícia.",
            "No exemplo, o almoço muda o contexto e a atividade do Hoodie.",
            "O retorno usa o mesmo Trabalho, preservando a identidade do lugar.",
            "O marcador percorre o trajeto de volta, guiado pelo horário da demonstração.",
            "A Casa é a mesma do início. O dia fictício termina com uma pausa e TV.",
        )
        var elapsed = 0L
        tour = day.steps.mapIndexed { index, step ->
            val timestamp = day.contexts[index].startedAt
            DemoTourStep(timestamp, elapsed, durations[index], step.title, narratives[index], step.context,
                activityAt(timestamp), DemoReplayHighlight.entries[index]).also { elapsed += it.durationMs }
        }
        tourDurationMs = elapsed
        require(tourDurationMs == 50_000L)
    }

    fun activityAt(timestamp: Long): HoodieActivity {
        val t = timestamp.coerceIn(startAt, (endAt - 1).coerceAtLeast(startAt))
        return day.activities.last { t >= it.startedAt && t < it.endedAt }.activity
    }

    fun stepAt(timestamp: Long): DemoTourStep = tour.lastOrNull { it.timestamp <= timestamp } ?: tour.first()

    /** Linear travel inside each segment, including both commute legs. No independent map timer. */
    fun timestampAtTourElapsed(elapsedMs: Long): Long {
        val elapsed = elapsedMs.coerceIn(0, tourDurationMs)
        if (elapsed == tourDurationMs) return endAt
        val index = tour.indexOfLast { it.elapsedStartMs <= elapsed }.coerceAtLeast(0)
        val step = tour[index]
        val next = tour.getOrNull(index + 1)?.timestamp ?: endAt
        return step.timestamp + (next - step.timestamp) * (elapsed - step.elapsedStartMs) / step.durationMs
    }

    fun tourElapsedAt(timestamp: Long): Long {
        val t = timestamp.coerceIn(startAt, endAt)
        if (t == endAt) return tourDurationMs
        val index = tour.indexOfLast { it.timestamp <= t }.coerceAtLeast(0)
        val step = tour[index]
        val next = tour.getOrNull(index + 1)?.timestamp ?: endAt
        return step.elapsedStartMs + (t - step.timestamp) * step.durationMs / (next - step.timestamp).coerceAtLeast(1)
    }

    fun next(timestamp: Long, mode: DemoReplayMode): Long = navigation(mode).firstOrNull { it > timestamp } ?: endAt
    fun previous(timestamp: Long, mode: DemoReplayMode): Long = navigation(mode).lastOrNull { it < timestamp } ?: startAt
    private fun navigation(mode: DemoReplayMode) = if (mode == DemoReplayMode.QUICK_TOUR) tour.map { it.timestamp } + endAt else eventTimes
}
