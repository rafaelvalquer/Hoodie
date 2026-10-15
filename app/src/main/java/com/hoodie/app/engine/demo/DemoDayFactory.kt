package com.hoodie.app.engine.demo

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.domain.daycycle.DailyActivityWindow
import com.hoodie.app.domain.daycycle.InferredSleepSpan
import com.hoodie.app.domain.daycycle.SleepConfidence
import com.hoodie.app.domain.daycycle.WakeConfidence
import com.hoodie.app.domain.daycycle.WakeReason
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.startOfDay
import java.time.LocalDate
import java.time.ZoneId

data class DemoStep(val minuteOfDay: Int, val title: String, val context: UserContextType) {
    val timeLabel: String get() = "%02d:%02d".format(minuteOfDay / 60, minuteOfDay % 60)
}

/** Dia fictício em memória. Nada aqui toca no Room real, em geofences, aprendizado ou notificações. */
data class DemoDay(
    val date: LocalDate, val zone: ZoneId, val steps: List<DemoStep>,
    val contexts: List<ContextEventEntity>, val places: List<PlaceEntity>, val timeline: List<TimelineEventEntity>,
    val dayStart: Long, val dayEnd: Long, val now: Long,
    val activities: List<HoodieActivityEntity> = emptyList(),
    val activityWindow: DailyActivityWindow? = null,
)

object DemoDayFactory {
    private const val HOME = 1L
    private const val WORK = 2L
    private const val RESTAURANT = 3L

    val steps: List<DemoStep> = listOf(
        DemoStep(7 * 60, "Hoodie acorda em Casa", UserContextType.HOME),
        DemoStep(7 * 60 + 45, "Saída e deslocamento", UserContextType.COMMUTING),
        DemoStep(8 * 60 + 30, "Chegada ao Trabalho", UserContextType.WORK),
        DemoStep(12 * 60, "Restaurante e almoço", UserContextType.LUNCH),
        DemoStep(13 * 60, "Retorno ao Trabalho", UserContextType.WORK),
        DemoStep(18 * 60, "Viagem de volta", UserContextType.COMMUTING),
        DemoStep(19 * 60, "Casa e descanso", UserContextType.HOME),
    )

    fun create(date: LocalDate, zone: ZoneId, includeReplayActivities: Boolean = true): DemoDay {
        val dayStart = startOfDay(date, zone)
        val dayEnd = startOfDay(date.plusDays(1), zone)
        fun at(minute: Int) = date.atTime(minute / 60, minute % 60).atZone(zone).toInstant().toEpochMilli()
        val now = at(20 * 60)
        val placeIds = mapOf(0 to HOME, 2 to WORK, 3 to RESTAURANT, 4 to WORK, 6 to HOME)
        val contexts = steps.mapIndexed { i, step ->
            val end = steps.getOrNull(i + 1)?.let { at(it.minuteOfDay) } ?: now.takeIf { includeReplayActivities }
            ContextEventEntity(
                id = i + 1L, type = step.context, startedAt = at(step.minuteOfDay), endedAt = end, confidence = 1f,
                placeId = placeIds[i], source = ContextSource.USER_CORRECTION,
                venueType = when (step.context) { UserContextType.LUNCH -> PlaceType.RESTAURANT; else -> null },
            )
        }
        val places = listOf(
            demoPlace(HOME, "Casa", PlaceType.HOME, dayStart),
            demoPlace(WORK, "Trabalho", PlaceType.WORK, dayStart),
            demoPlace(RESTAURANT, "Restaurante", PlaceType.RESTAURANT, dayStart),
        )
        val timeline = steps.mapIndexed { i, step ->
            TimelineEventEntity(
                id = i + 1L, timestamp = at(step.minuteOfDay), actor = TimelineActor.USER, emoji = step.context.emoji,
                text = step.title, sourceType = TimelineSourceType.CONTEXT, sourceId = i + 1L,
            )
        }
        val script = listOf(
            Triple(7 * 60, HoodieActivity.WAKING_UP, UserContextType.HOME),
            Triple(7 * 60 + 15, HoodieActivity.BREAKFAST, UserContextType.HOME),
            Triple(7 * 60 + 45, HoodieActivity.COMMUTING, UserContextType.COMMUTING),
            Triple(8 * 60 + 30, HoodieActivity.WORKING, UserContextType.WORK),
            Triple(10 * 60 + 30, HoodieActivity.COFFEE, UserContextType.WORK),
            Triple(12 * 60, HoodieActivity.EATING, UserContextType.LUNCH),
            Triple(13 * 60, HoodieActivity.WORKING, UserContextType.WORK),
            Triple(16 * 60, HoodieActivity.RESTING, UserContextType.WORK),
            Triple(18 * 60, HoodieActivity.COMMUTING, UserContextType.COMMUTING),
            Triple(19 * 60, HoodieActivity.RESTING, UserContextType.HOME),
            Triple(19 * 60 + 30, HoodieActivity.WATCHING_TV, UserContextType.HOME),
        )
        val activities = if (includeReplayActivities) script.mapIndexed { index, (minute, activity, context) ->
            HoodieActivityEntity(id = index + 1L, activity = activity, startedAt = at(minute),
                endedAt = script.getOrNull(index + 1)?.let { at(it.first) } ?: now, userContext = context)
        } else emptyList()
        val window = if (includeReplayActivities) DailyActivityWindow(dayStart, dayEnd, at(7 * 60), now,
            InferredSleepSpan(dayStart, at(7 * 60), SleepConfidence.HIGH), WakeReason.HOODIE_WAKE, WakeConfidence.HIGH, provisional = false) else null
        return DemoDay(date, zone, steps, contexts, places, timeline, dayStart, dayEnd, now, activities, window)
    }

    // Coordenadas vazias: o dia fictício nunca vira geofence nem posição real.
    private fun demoPlace(id: Long, name: String, type: PlaceType, createdAt: Long) =
        PlaceEntity(id = id, name = name, type = type, encryptedCoordinates = "", radiusMeters = 150f, confidence = 1f, createdAt = createdAt)
}
