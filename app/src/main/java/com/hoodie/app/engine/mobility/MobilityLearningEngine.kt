package com.hoodie.app.engine.mobility

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.time.atZone
import com.hoodie.app.core.time.minuteOfDay
import java.time.DayOfWeek
import java.time.ZoneId
import kotlin.math.abs

/**
 * Um deslocamento já encerrado, como o aprendizado o vê: origem, destino, horário,
 * modos e confirmações. Nenhuma posição — o Hoodie aprende "Casa → Trabalho de ônibus
 * por volta das 8h", não por onde a pessoa passou.
 */
data class TripRecord(
    val startedAt: Long,
    val originPlaceId: Long?,
    val destinationPlaceId: Long?,
    /** Modos dos trechos, na ordem (WALKING, BUS, WALKING). */
    val modes: List<MovementMode>,
    /** Modos de veículo que o usuário confirmou/escolheu. */
    val confirmedVehicleModes: List<MovementMode>,
    val arrivalConfirmed: Boolean?,
)

/**
 * Aprendizado de trajetos, sem estado próprio: tudo é derivado do histórico local.
 *
 * - **histórico semelhante**: mesmo ponto de partida, mesmo tipo de dia, horário próximo;
 * - **destino provável** e **modo de veículo provável** para esse trajeto;
 * - **auto-confirmação de chegada**: N confirmações seguidas num lugar, zerada por um "não";
 * - **primeiras vezes**: enquanto não houver confirmações de um ponto de partida, pergunta.
 */
object MobilityLearningEngine {

    private fun weekend(d: DayOfWeek) = d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY

    /** Mesma origem, mesmo tipo de dia (útil/fim de semana), horário dentro da janela. */
    fun similar(trips: List<TripRecord>, originPlaceId: Long?, at: Long, zone: ZoneId): List<TripRecord> {
        val z = at.atZone(zone)
        val minute = z.minuteOfDay()
        val isWeekend = weekend(z.dayOfWeek)
        return trips.filter { t ->
            val tz = t.startedAt.atZone(zone)
            t.originPlaceId == originPlaceId && originPlaceId != null &&
                weekend(tz.dayOfWeek) == isWeekend &&
                abs(tz.minuteOfDay() - minute) <= HoodieConfig.TRIP_PATTERN_WINDOW_MIN
        }
    }

    fun hasHistory(trips: List<TripRecord>, originPlaceId: Long?, at: Long, zone: ZoneId): Boolean =
        similar(trips, originPlaceId, at, zone).size >= HoodieConfig.TRIP_PATTERN_MIN_COUNT

    /** Destino mais frequente entre os trajetos semelhantes (só com repetição suficiente). */
    fun predictedDestination(trips: List<TripRecord>, originPlaceId: Long?, at: Long, zone: ZoneId): Long? =
        similar(trips, originPlaceId, at, zone).mapNotNull { it.destinationPlaceId }
            .groupingBy { it }.eachCount()
            .filter { it.value >= HoodieConfig.TRIP_PATTERN_MIN_COUNT }
            .maxByOrNull { it.value }?.key

    /**
     * Veículo provável nesse trajeto: o modo escolhido pelo usuário pelo menos
     * [HoodieConfig.TRANSPORT_LEARN_COUNT] vezes e em ao menos 2/3 das escolhas.
     */
    fun learnedVehicleMode(trips: List<TripRecord>, originPlaceId: Long?, at: Long, zone: ZoneId): MovementMode? {
        val choices = similar(trips, originPlaceId, at, zone).flatMap { it.confirmedVehicleModes }.filter { it != MovementMode.VEHICLE_UNKNOWN }
        if (choices.isEmpty()) return null
        val (mode, count) = choices.groupingBy { it }.eachCount().maxByOrNull { it.value } ?: return null
        return mode.takeIf { count >= HoodieConfig.TRANSPORT_LEARN_COUNT && count * 3 >= choices.size * 2 }
    }

    /**
     * Chegada confirmada sozinha: as últimas [HoodieConfig.ARRIVAL_AUTO_CONFIRM_COUNT]
     * chegadas decididas nesse lugar foram "sim". Um "não" recente derruba a confiança.
     */
    fun arrivalAutoConfirm(trips: List<TripRecord>, destinationPlaceId: Long): Boolean {
        val decided = trips.filter { it.destinationPlaceId == destinationPlaceId && it.arrivalConfirmed != null }
            .sortedByDescending { it.startedAt }
            .take(HoodieConfig.ARRIVAL_AUTO_CONFIRM_COUNT)
        return decided.size == HoodieConfig.ARRIVAL_AUTO_CONFIRM_COUNT && decided.all { it.arrivalConfirmed == true }
    }

    /** A última chegada decidida nesse lugar foi negada ou corrigida pelo usuário. */
    fun lastArrivalCorrected(trips: List<TripRecord>, destinationPlaceId: Long): Boolean =
        trips.filter { it.destinationPlaceId == destinationPlaceId && it.arrivalConfirmed != null }
            .maxByOrNull { it.startedAt }?.arrivalConfirmed == false

    /** "Primeiras vezes": deslocamentos já confirmados saindo desse lugar. */
    fun confirmedFrom(trips: List<TripRecord>, originPlaceId: Long?): Int = trips.count { it.originPlaceId == originPlaceId && originPlaceId != null }

    /** Trajeto (origem → destino) que já se repetiu o bastante para propor automatizar. */
    fun repeatedPattern(trips: List<TripRecord>, originPlaceId: Long?, destinationPlaceId: Long?, at: Long, zone: ZoneId): Boolean =
        originPlaceId != null && destinationPlaceId != null &&
            similar(trips, originPlaceId, at, zone).count { it.destinationPlaceId == destinationPlaceId } >= HoodieConfig.TRANSPORT_LEARN_COUNT

    fun patternKey(originPlaceId: Long?, destinationPlaceId: Long?) = "${originPlaceId ?: "?"}>${destinationPlaceId ?: "?"}"
}
