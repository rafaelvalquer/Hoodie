package com.hoodie.app.engine.performance

import com.hoodie.app.core.debug.DebugEventLogger
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Debug-only diary timing events. Values are technical; no place, app, coordinate, or event text is accepted. */
@Singleton
class DiaryPerformanceMonitor @Inject constructor(
    private val log: DebugEventLogger,
) {
    fun record(
        event: Event,
        date: LocalDate,
        requestId: Long,
        elapsedMs: Long? = null,
        count: Int? = null,
    ) {
        val details = buildList {
            add("date=$date")
            add("request=$requestId")
            elapsedMs?.let { add("elapsedMs=${it.coerceAtLeast(0)}") }
            count?.let { add("count=${it.coerceAtLeast(0)}") }
        }.joinToString(" ")
        log.log(DebugEventLogger.Category.SYSTEM, "${event.name} $details")
    }

    enum class Event {
        DIARY_LOAD_REQUESTED,
        DIARY_DB_LOAD_STARTED,
        DIARY_DB_LOAD_FINISHED,
        DIARY_CORE_READY,
        DIARY_REPORT_READY,
        DIARY_CLOCK_READY,
        DIARY_JOURNEY_READY,
        DIARY_LOAD_CANCELLED,
        DIARY_LOAD_FAILED,
        DIARY_DATA_INVALIDATED,
        DIGITAL_REFRESH_STARTED,
        DIGITAL_REFRESH_FINISHED,
    }
}
