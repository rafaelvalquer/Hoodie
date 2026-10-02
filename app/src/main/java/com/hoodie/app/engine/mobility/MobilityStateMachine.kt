package com.hoodie.app.engine.mobility

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.mobility.MobilityState
import com.hoodie.app.core.mobility.MobilityState.ARRIVED
import com.hoodie.app.core.mobility.MobilityState.ARRIVING
import com.hoodie.app.core.mobility.MobilityState.IN_VEHICLE
import com.hoodie.app.core.mobility.MobilityState.MOVEMENT_CANDIDATE
import com.hoodie.app.core.mobility.MobilityState.STATIONARY
import com.hoodie.app.core.mobility.MobilityState.WALKING
import com.hoodie.app.core.mobility.MovementMode

/**
 * Regras do núcleo da mobilidade:
 *
 *     STATIONARY ─movimento→ MOVEMENT_CANDIDATE ─confirmação→ WALKING | IN_VEHICLE
 *     WALKING ⇄ IN_VEHICLE (novo trecho a cada troca)
 *     WALKING | IN_VEHICLE ─geofence/parado→ ARRIVING ─lugar conhecido→ ARRIVED
 *
 * Nada muda por um evento isolado: caminhada só conta depois de [HoodieConfig.WALK_CONFIRM_MS],
 * veículo depois de [HoodieConfig.VEHICLE_CONFIRM_MS], chegada por parada depois de
 * [HoodieConfig.STILL_ARRIVAL_MS].
 */
object MobilityStateMachine {

    private val ALLOWED: Map<MobilityState, Set<MobilityState>> = mapOf(
        STATIONARY to setOf(MOVEMENT_CANDIDATE),
        MOVEMENT_CANDIDATE to setOf(WALKING, IN_VEHICLE, STATIONARY),
        WALKING to setOf(IN_VEHICLE, ARRIVING, ARRIVED),
        IN_VEHICLE to setOf(WALKING, ARRIVING, ARRIVED),
        ARRIVING to setOf(ARRIVED, WALKING, IN_VEHICLE),
        ARRIVED to setOf(STATIONARY, WALKING, IN_VEHICLE),
    )

    fun canTransition(from: MobilityState, to: MobilityState): Boolean = from == to || to in ALLOWED.getValue(from)

    /** Estado de quem está se movendo neste modo. */
    fun stateFor(mode: MovementMode): MobilityState = when {
        mode.isVehicle -> IN_VEHICLE
        mode == MovementMode.NONE -> MOVEMENT_CANDIDATE
        else -> WALKING
    }

    /** Tempo de movimento contínuo para um candidato deixar de ser ruído. */
    fun sustainThreshold(mode: MovementMode): Long =
        if (mode.isVehicle) HoodieConfig.VEHICLE_CONFIRM_MS else HoodieConfig.WALK_CONFIRM_MS

    /** Movimento sustentado: começou há tempo suficiente e não parou desde então. */
    fun isSustained(mode: MovementMode, movingSince: Long, stillSince: Long?, now: Long): Boolean =
        mode != MovementMode.NONE && stillSince == null && now - movingSince >= sustainThreshold(mode)

    /** Parado tempo suficiente durante um deslocamento = resolver a chegada. */
    fun isArrivalStill(stillSince: Long?, now: Long): Boolean =
        stillSince != null && now - stillSince >= HoodieConfig.STILL_ARRIVAL_MS

    /** Trocar de trecho: a pé ⇄ veículo. Veículo → veículo (ou a pé → a pé) mantém o trecho. */
    fun isNewSegment(current: MovementMode, observed: MovementMode): Boolean =
        observed != MovementMode.NONE && current.isVehicle != observed.isVehicle

    /** Candidato velho que nunca virou deslocamento (andou pela casa e parou). */
    fun isExpiredCandidate(startedAt: Long, now: Long): Boolean = now - startedAt >= HoodieConfig.MOVEMENT_CANDIDATE_MAX_MS

    /** Sessão aberta há tempo demais (evento perdido, aparelho desligado). */
    fun isStaleSession(startedAt: Long, now: Long): Boolean = now - startedAt >= HoodieConfig.MOBILITY_SESSION_MAX_MS

    /** Saída e volta ao mesmo lugar dentro da janela de oscilação do GPS. */
    fun isFlap(startedAt: Long, reenteredAt: Long): Boolean = reenteredAt - startedAt < HoodieConfig.GPS_FLAP_MS
}
