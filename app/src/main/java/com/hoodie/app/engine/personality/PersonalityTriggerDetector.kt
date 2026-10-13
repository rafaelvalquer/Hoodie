package com.hoodie.app.engine.personality

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.daystate.DayState

/** O que a Home observou num instante; o detector compara com a observação anterior. */
data class PersonalityObservation(
    val now: Long,
    val daySeed: Int,
    val hour: Int,
    val contextId: Long?,
    val contextType: UserContextType?,
    val contextStartedAt: Long?,
    val placeId: Long?,
    val activity: HoodieActivity,
    val dayState: DayState? = null,
    val dayStateStartedAt: Long? = null,
)

/** Um momento detectado e a visita a que ele pertence. */
data class DetectedTrigger(val trigger: PersonalityTrigger, val visitSeed: Long)

/**
 * Transforma mudanças observadas em gatilhos. Só detecta bordas (algo que acabou de mudar);
 * limites de frequência e a escolha da reação ficam com o [HoodiePersonalityEngine].
 * Nada aqui afirma algo que não foi observado: sem contexto anterior conhecido, não há "voltou".
 */
class PersonalityTriggerDetector(
    private val arrivalWindowMs: Long = 45 * 60_000L,
    private val longActivityMs: Long = 90 * 60_000L,
    private val dayStateWindowMs: Long = 2 * 60 * 60_000L,
    private val appReturnAwayMs: Long = 30 * 60_000L,
) {
    private var previous: PersonalityObservation? = null
    private var foregroundPending = false
    private var awayLongEnough = true
    private var backgroundAt: Long? = null
    private val newPlaces = ArrayDeque<Long>()

    @Synchronized fun onForeground(now: Long) {
        foregroundPending = true
        awayLongEnough = backgroundAt?.let { now - it >= appReturnAwayMs } ?: true
    }

    @Synchronized fun onBackground(now: Long) { backgroundAt = now }

    /** O usuário confirmou um lugar novo (resposta à pergunta ou lugar salvo). */
    @Synchronized fun onNewPlaceConfirmed(visitSeed: Long) { newPlaces.addLast(visitSeed) }

    @Synchronized fun detect(o: PersonalityObservation): List<DetectedTrigger> {
        val before = previous
        previous = o
        val visit = o.contextId ?: 0L
        val result = mutableListOf<DetectedTrigger>()
        if (foregroundPending) {
            foregroundPending = false
            result += DetectedTrigger(PersonalityTrigger.FIRST_OPEN_OF_DAY, o.daySeed.toLong())
            if (awayLongEnough) result += DetectedTrigger(PersonalityTrigger.APP_RETURN, o.daySeed.toLong())
        }
        while (newPlaces.isNotEmpty()) result += DetectedTrigger(PersonalityTrigger.NEW_PLACE_CONFIRMED, newPlaces.removeFirst())
        val contextChanged = before != null && before.contextId != o.contextId
        val freshContext = o.contextStartedAt?.let { o.now - it in 0..arrivalWindowMs } == true
        if (o.contextType == UserContextType.WORK && (before == null || contextChanged) && freshContext) {
            result += DetectedTrigger(PersonalityTrigger.ARRIVED_AT_WORK, visit)
        }
        if (o.contextType == UserContextType.HOME && contextChanged && before?.contextType != null &&
            before.contextType != UserContextType.HOME && before.contextType != UserContextType.UNKNOWN) {
            result += DetectedTrigger(PersonalityTrigger.BACK_HOME, visit)
        }
        if (contextChanged && before?.contextType != null && before.contextType != UserContextType.COMMUTING &&
            before.contextStartedAt?.let { o.now - it >= longActivityMs } == true) {
            result += DetectedTrigger(PersonalityTrigger.LONG_ACTIVITY_FINISHED, before.contextId ?: 0L)
        }
        if (enteredDayState(before, o, DayState.WAKING)) result += DetectedTrigger(PersonalityTrigger.DAY_WAKING, o.daySeed.toLong())
        if (enteredDayState(before, o, DayState.WINDING_DOWN)) result += DetectedTrigger(PersonalityTrigger.DAY_WINDING_DOWN, o.daySeed.toLong())
        val breakActivities = setOf(HoodieActivity.COFFEE, HoodieActivity.RESTING, HoodieActivity.EATING)
        if (o.contextType == UserContextType.WORK && o.activity in breakActivities &&
            (before == null || before.activity !in breakActivities || contextChanged)) {
            result += DetectedTrigger(PersonalityTrigger.WORK_BREAK, visit)
        }
        return result.sortedBy { ORDER.indexOf(it.trigger) }
    }

    private fun enteredDayState(before: PersonalityObservation?, o: PersonalityObservation, state: DayState): Boolean =
        o.dayState == state && (before == null || before.dayState != state) &&
            o.dayStateStartedAt?.let { o.now - it in 0..dayStateWindowMs } != false

    companion object {
        /** Quando dois momentos chegam juntos, o cumprimento vem antes e a pausa por último. */
        val ORDER = listOf(
            PersonalityTrigger.FIRST_OPEN_OF_DAY, PersonalityTrigger.APP_RETURN, PersonalityTrigger.NEW_PLACE_CONFIRMED,
            PersonalityTrigger.ARRIVED_AT_WORK, PersonalityTrigger.BACK_HOME, PersonalityTrigger.LONG_ACTIVITY_FINISHED,
            PersonalityTrigger.DAY_WAKING, PersonalityTrigger.DAY_WINDING_DOWN, PersonalityTrigger.WORK_BREAK,
        )
    }
}
