package com.hoodie.app.core.model

import java.time.DayOfWeek

/** Lugar conhecido, já com coordenadas decifradas (só existe em memória). */
data class Place(
    val id: Long,
    val name: String,
    val type: PlaceType,
    val latitude: Double,
    val longitude: Double,
    val radiusMeters: Float,
    val confirmationCount: Int,
    val createdAt: Long,
    val lastVisitedAt: Long?,
    val isFavorite: Boolean = false,
)

enum class WorkMode(val label: String) { OFFICE("Fora de casa"), HOME_OFFICE("Home office"), NONE("Não trabalho") }

enum class CommuteStyle(val label: String) { WALK("Caminhando"), BUS("Ônibus"), RANDOM("Variado") }

val WEEKDAYS: Set<DayOfWeek> = setOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY,
)

/** Rotina de trabalho. Horários em minutos desde 00:00 no fuso local. */
data class Routine(
    val workMode: WorkMode = WorkMode.NONE,
    val days: Set<DayOfWeek> = WEEKDAYS,
    val startMinute: Int = 8 * 60 + 30,
    val endMinute: Int = 17 * 60 + 30,
    val lunchStartMinute: Int = 12 * 60,
    val lunchEndMinute: Int = 13 * 60,
) {
    val hasWork: Boolean get() = workMode != WorkMode.NONE
}

/** Horários de sono do Hoodie (ele acompanha o do usuário). */
data class SleepSchedule(
    val wakeMinute: Int = 7 * 60,
    val sleepMinute: Int = 23 * 60,
)

enum class ContextSource { GEOFENCE, MANUAL, CONFIRMATION, ROUTINE, ONBOARDING, LOCATION_CHECK }

/** Contexto do usuário vigente num intervalo. endedAt == null → ainda ativo. */
data class ContextEvent(
    val id: Long = 0,
    val type: UserContextType,
    val startedAt: Long,
    val endedAt: Long?,
    val confidence: Float,
    val placeId: Long?,
    val source: ContextSource,
)

data class Needs(
    val energy: Int = 75,
    val hunger: Int = 30,
    val mood: Int = 70,
    val social: Int = 50,
    val focus: Int = 60,
)

/**
 * Estado persistido do gato. Nunca existe um contador rodando: guardamos quando
 * a atividade começou e quando deve terminar; o resto é recalculado ao abrir.
 */
data class HoodieState(
    val activity: HoodieActivity,
    val startedAt: Long,
    val expectedEndAt: Long?,
    val needs: Needs,
    /** Momento a que [needs] se refere. */
    val needsAt: Long,
    val userContext: UserContextType,
)

enum class TimelineActor { USER, HOODIE }

/** De onde veio uma linha da timeline (para invalidar/substituir quando a origem é corrigida). */
enum class TimelineSourceType { CONTEXT, HOODIE_ACTIVITY, MEMORY, SYSTEM }

data class TimelineEvent(
    val id: Long = 0,
    val timestamp: Long,
    val actor: TimelineActor,
    val emoji: String,
    val text: String,
)

data class Memory(val key: String, val emoji: String, val title: String, val unlockedAt: Long)

enum class QuestionKind { CONFIRM_CONTEXT, NEW_PLACE, SAVE_PLACE }

data class ContextQuestion(
    val id: Long,
    val kind: QuestionKind,
    val candidate: UserContextType?,
    val placeId: Long?,
    val chosenPlaceType: PlaceType?,
    val contextEventId: Long?,
    val askedAt: Long,
    val answeredAt: Long?,
    val answer: String?,
) {
    val prompt: String
        get() = when (kind) {
            QuestionKind.NEW_PLACE -> "Parece que você está em um lugar novo. O que é?"
            QuestionKind.SAVE_PLACE -> "Salvar este lugar como ${chosenPlaceType?.label?.lowercase() ?: "lugar"}?"
            QuestionKind.CONFIRM_CONTEXT -> when (candidate) {
                UserContextType.LUNCH -> "🍜 Você saiu para almoçar?"
                UserContextType.WORK -> "🏢 Você está trabalhando hoje?"
                null -> "Onde você está?"
                else -> "${candidate.emoji} Você está em: ${candidate.label}?"
            }
        }
}
