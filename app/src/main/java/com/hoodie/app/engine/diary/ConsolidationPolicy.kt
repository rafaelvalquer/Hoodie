package com.hoodie.app.engine.diary

/**
 * Regras da consolidação de visitas consecutivas da Jornada, num lugar só (testável).
 *
 * [maxContiguousGapMs] é a folga para a segmentação dos eventos (um evento termina 09:00:00 e o
 * próximo começa 09:01): só vale sem nenhuma evidência de saída nesse intervalo, e o tempo da folga
 * nunca vira permanência.
 */
data class ConsolidationPolicy(
    val enabled: Boolean = true,
    val maxContiguousGapMs: Long = MAX_CONTIGUOUS_GAP_MS,
) {
    init { require(maxContiguousGapMs >= 0) { "folga negativa: $maxContiguousGapMs" } }

    companion object {
        const val MAX_CONTIGUOUS_GAP_MS = 120_000L
        val DEFAULT = ConsolidationPolicy()
        val DISABLED = ConsolidationPolicy(enabled = false)
    }
}
