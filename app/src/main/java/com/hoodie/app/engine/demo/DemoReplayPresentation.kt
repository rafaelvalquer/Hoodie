package com.hoodie.app.engine.demo

import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.engine.diary.JourneyReplayAssembler
import com.hoodie.app.engine.diary.ReplayHudAssembler
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.presentation.screens.diary.ReplayState
import com.hoodie.app.presentation.screens.diary.ReplayUiState

/** Map, HUD, narrative and optional scene are all projections of source.timestamp. */
data class DemoReplayPresentation(
    val source: DemoReplaySnapshot,
    val replay: ReplayUiState,
    val stepIndex: Int,
    val step: DemoTourStep,
    val title: String,
    val narrative: String,
    val clockLabel: String,
    val context: UserContextType,
    val activity: HoodieActivity,
    val activePlaceId: Long?,
    val activePlaceName: String?,
    val venueType: PlaceType?,
    val daySeed: Int,
) {
    /** Called only when the optional preview needs a different context/activity. No Home state. */
    fun contextualVisual(): VisualState = VisualDirector.resolve(activity, context, homeOffice = false,
        commute = CommuteStyle.WALK, variant = 0, shoppingVenue = venueType, now = source.timestamp,
        personality = null).copy(daySeed = daySeed, placeId = activePlaceId)

    companion object {
        fun assemble(timeline: DemoReplayTimeline, view: DemoDiaryView, source: DemoReplaySnapshot): DemoReplayPresentation {
            val at = source.timestamp.coerceIn(timeline.startAt, timeline.endAt)
            // The closing instant displays the final known context/activity, with the clock at 20:00.
            val dataAt = minOf(at, timeline.endAt - 1)
            val hud = ReplayHudAssembler.assemble(view.diary.replay, null, dataAt, timeline.day.zone)
                .copy(timestamp = at, dayPeriod = ReplayHudAssembler.periodAt(at, timeline.day.zone))
            val frame = view.diary.replay.frameAt(dataAt)
            val replay = ReplayUiState(state = source.state, speed = source.speed,
                progress = ((at - timeline.startAt).toDouble() / (timeline.endAt - timeline.startAt).coerceAtLeast(1)).toFloat())
                .withVisual(hud, frame.highlightedTimelineItemIds)
            val map = JourneyReplayAssembler.stateAt(view.journey, at, replay.replaying)
            val place = map.activeNodeIndex?.let { view.journey.nodes[it] }
            val stepIndex = timeline.tour.indexOfLast { it.timestamp <= at }.coerceAtLeast(0)
            val step = timeline.tour[stepIndex]
            val context = requireNotNull(hud.context)
            val activity = hud.hoodieActivity ?: timeline.activityAt(at)
            val contextEvent = timeline.day.contexts.last { dataAt >= it.startedAt }
            val finished = source.state == ReplayState.FINISHED
            val fullTitle = when (activity) {
                HoodieActivity.WAKING_UP -> "Um novo dia em Casa"
                HoodieActivity.BREAKFAST -> "Café da manhã"
                HoodieActivity.COMMUTING -> "Em deslocamento"
                HoodieActivity.WORKING -> "Hora de trabalhar"
                HoodieActivity.COFFEE -> "Pausa para café"
                HoodieActivity.EATING -> "Hora do almoço"
                HoodieActivity.RESTING -> "Uma pausa para descansar"
                HoodieActivity.WATCHING_TV -> "Hora de ver TV"
                else -> step.title
            }
            return DemoReplayPresentation(source.copy(timestamp = at), replay, stepIndex, step,
                if (finished) "Fim da demonstração" else if (source.mode == DemoReplayMode.FULL_DAY) fullTitle else step.title,
                if (finished) "Este dia fictício terminou. Você pode reiniciar o tour ou explorar outro horário."
                else if (source.mode == DemoReplayMode.FULL_DAY) "Neste dia fictício, o Hoodie está ${activity.label}." else step.narrative,
                formatClock(at, timeline.day.zone), context, activity, place?.placeId, place?.placeName,
                contextEvent.venueType, timeline.day.date.toEpochDay().toInt())
        }
    }
}
