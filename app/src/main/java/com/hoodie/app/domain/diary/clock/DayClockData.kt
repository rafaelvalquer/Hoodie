package com.hoodie.app.domain.diary.clock

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.daycycle.SleepConfidence
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Categorias do anel: as mesmas dos cards "Seu dia" (Casa, Trabalho, Transporte,
 * Almoço, Academia, Lazer, Outros), para a barra "Tempo por lugar" bater com eles.
 */
enum class ClockCategory {
    HOME, WORK, COMMUTE, MEAL, GYM, LEISURE, OTHER;

    companion object {
        /** Mesmo agrupamento do DailySummaryCalculator (DINING, estudo, compras… caem em Outros). */
        fun of(context: UserContextType?): ClockCategory = when (context) {
            UserContextType.HOME -> HOME
            UserContextType.WORK -> WORK
            UserContextType.LUNCH -> MEAL
            UserContextType.GYM -> GYM
            UserContextType.LEISURE -> LEISURE
            else -> OTHER
        }
    }
}

/** Hora cheia do relógio de parede e o minuto do mostrador onde ela cai (dias de 23/25 h). */
data class ClockHourMark(val hour: Int, val minute: Int)

/**
 * O dia no mostrador de 24 h: minutos contados a partir da meia-noite local
 * ([dayLengthMinutes] = 1380, 1440 ou 1500). Segmentos ordenados, sem sobreposição,
 * cobrindo de 0 até [endMinute].
 */
data class DayClockData(
    val date: LocalDate,
    val zone: ZoneId,
    val dayStart: Instant,
    val dayLengthMinutes: Int,
    /** Minuto do "agora" (só hoje); null em dias passados. */
    val nowMinute: Int?,
    val segments: List<ClockSegment>,
    /** Minutos por categoria (permanências + deslocamentos; buracos ficam de fora). */
    val totals: Map<ClockCategory, Int>,
    val hourMarks: List<ClockHourMark>,
) {
    val endMinute: Int get() = nowMinute ?: dayLengthMinutes
    val stays: List<ClockSegment.Stay> get() = segments.filterIsInstance<ClockSegment.Stay>()
    val stopCount: Int get() = stays.size
    val isEmpty: Boolean get() = segments.none { it !is ClockSegment.Unknown }

    fun segment(id: String?): ClockSegment? = id?.let { i -> segments.firstOrNull { it.id == i } }

    /** Segmento que contém o minuto (o último que começa nele, para trechos de 0 min). */
    fun segmentAt(minute: Float): ClockSegment? =
        segments.lastOrNull { minute >= it.startMinute && minute < it.endMinute.coerceAtLeast(it.startMinute + 1) }

    /** Minuto do relógio de parede (0..1439) para um minuto do mostrador. */
    fun wallMinute(minute: Int): Int {
        val t = dayStart.plusSeconds(minute * 60L).atZone(zone)
        return t.hour * 60 + t.minute
    }

    fun instantOf(minute: Int): Long = dayStart.toEpochMilli() + minute * 60_000L

    companion object {
        fun empty(date: LocalDate, zone: ZoneId): DayClockData {
            val start = date.atStartOfDay(zone).toInstant()
            val len = ((date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - start.toEpochMilli()) / 60_000L).toInt()
            return DayClockData(date, zone, start, len, null, emptyList(), emptyMap(), emptyList())
        }
    }
}

sealed interface ClockSegment {
    val id: String
    val startMinute: Int
    val endMinute: Int
    val minutes: Int get() = endMinute - startMinute

    data class Stay(
        override val id: String,
        override val startMinute: Int,
        override val endMinute: Int,
        val placeId: Long?,
        val placeName: String,
        val placeType: PlaceType,
        val category: ClockCategory,
        /** Atividade do Hoodie com maior duração sobreposta à visita. */
        val hoodieActivity: HoodieActivity?,
        /** "parada 3 de 5" (1-based). */
        val stopIndex: Int,
        /** 1 = primeira vez no lugar hoje; 2 = "Retorno #2"… */
        val visitNumber: Int,
        /** A visita ainda não terminou (vai até o "agora"). */
        val ongoing: Boolean = false,
    ) : ClockSegment

    data class Move(
        override val id: String,
        override val startMinute: Int,
        override val endMinute: Int,
        /** Meio dominante (o mesmo da Jornada); null = deslocamento sem meio identificado. */
        val mode: MovementMode?,
        val fromStopId: String?,
        val toStopId: String?,
    ) : ClockSegment

    data class Sleep(
        override val id: String,
        override val startMinute: Int,
        override val endMinute: Int,
        val phase: SleepPhase,
        val confidence: SleepConfidence,
    ) : ClockSegment

    /** Buraco sem contexto: nunca é preenchido com suposição. */
    data class Unknown(
        override val id: String,
        override val startMinute: Int,
        override val endMinute: Int,
    ) : ClockSegment
}

enum class SleepPhase { BEFORE_WAKE, AFTER_ACTIVE_DAY }
