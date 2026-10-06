package com.hoodie.app.domain.daycycle

/** Janela civil para consulta e janela ativa para a história visual da Jornada. */
data class DailyActivityWindow(
    val civilStartAt: Long,
    val civilEndAt: Long,
    val activeStartAt: Long,
    val activeEndAt: Long,
    val sleepBeforeStart: InferredSleepSpan?,
    val wakeReason: WakeReason,
    val wakeConfidence: WakeConfidence,
    val provisional: Boolean,
) {
    init {
        require(civilEndAt >= civilStartAt)
        require(activeStartAt in civilStartAt..civilEndAt)
        require(activeEndAt in activeStartAt..civilEndAt)
    }

    companion object {
        val EMPTY = DailyActivityWindow(
            civilStartAt = 0L, civilEndAt = 0L, activeStartAt = 0L, activeEndAt = 0L,
            sleepBeforeStart = null, wakeReason = WakeReason.SCHEDULE_FALLBACK,
            wakeConfidence = WakeConfidence.LOW, provisional = false,
        )

        fun civil(start: Long, end: Long, now: Long): DailyActivityWindow {
            val boundedEnd = minOf(end, now).coerceAtLeast(start)
            return DailyActivityWindow(
                start, end, start, boundedEnd, null, WakeReason.SCHEDULE_FALLBACK,
                WakeConfidence.LOW, provisional = false,
            )
        }
    }
}

data class InferredSleepSpan(
    /** null quando só sabemos o horário em que o despertar ocorreu. */
    val startedAt: Long?,
    val endedAt: Long,
    val confidence: SleepConfidence,
)

enum class SleepConfidence { HIGH, MEDIUM, LOW }
