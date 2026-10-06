package com.hoodie.app.domain.diary.journey

import com.hoodie.app.core.mobility.MovementMode

/** Relógio do dia: anel de 24 h com 00 h no topo, sentido horário. Ângulos em graus. */
data class DayClockData(
    /** Permanências (grossas) e deslocamentos (finos). */
    val arcs: List<ClockArc>,
    /** Paradas curtas (individuais ou agrupadas "×k"). */
    val ticks: List<ClockTick>,
    /** Só arcos ≥ CLOCK_MIN_LABEL_DEG, sem colisão. */
    val labels: List<ClockLabel>,
    /** Quantas horas o dia selecionado tem (23/24/25 com horário de verão). */
    val dayHours: Float = 24f,
    val dayStart: Long = 0L,
    val dayEnd: Long = 0L,
) {
    val isEmpty: Boolean get() = arcs.isEmpty() && ticks.isEmpty()

    companion object {
        val EMPTY = DayClockData(emptyList(), emptyList(), emptyList())
    }
}

data class ClockArc(
    val id: String,
    val kind: Kind,
    val startDeg: Float,
    val sweepDeg: Float,
    /** Bioma da permanência (cor do tipo de lugar); null nos trechos. */
    val biome: BiomeType?,
    /** Meio de transporte do trecho (cor + padrão); null nas permanências. */
    val mode: MovementMode?,
    /** Parada do detalhe (permanência) ou null. */
    val stopId: String?,
    val startAt: Long,
    val endAt: Long,
) {
    enum class Kind { STAY, LEG }

    val endDeg get() = startDeg + sweepDeg
    val midDeg get() = startDeg + sweepDeg / 2f
    operator fun contains(deg: Float) = deg >= startDeg && deg <= endDeg
}

/** Parada curta: um tique; vários consecutivos viram "×k". */
data class ClockTick(
    val id: String,
    val deg: Float,
    val stopIds: List<String>,
    val startAt: Long,
    val endAt: Long,
) {
    val count get() = stopIds.size
    val grouped get() = count > 1
}

data class ClockLabel(
    val arcId: String,
    val text: String,
    val deg: Float,
    val biome: BiomeType?,
)
