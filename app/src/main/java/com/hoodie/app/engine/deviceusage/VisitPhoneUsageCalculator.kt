package com.hoodie.app.engine.deviceusage

import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.domain.phoneinsights.model.VisitAppUsage
import com.hoodie.app.domain.phoneinsights.model.VisitPhoneUsage

/**
 * Celular por visita: cada sessão de app ∩ intervalo da visita.
 *
 *     overlapStart = max(app.startedAt, visit.arrivalAt)
 *     overlapEnd   = min(app.endedAt,   visit.departureAt)
 *     duração      = max(0, overlapEnd - overlapStart)
 */
object VisitPhoneUsageCalculator {

    fun overlap(sessionStart: Long, sessionEnd: Long, from: Long, to: Long): Long =
        maxOf(0L, minOf(sessionEnd, to) - maxOf(sessionStart, from))

    /** [departureAt] null = visita em andamento: vai até [now]. null quando não houve uso. */
    fun calculate(
        arrivalAt: Long,
        departureAt: Long?,
        now: Long,
        sessions: List<AppSession>,
        labelOf: (String) -> String,
        categoryOf: (String) -> HoodieAppCategory,
    ): VisitPhoneUsage? {
        val end = departureAt ?: now
        if (end <= arrivalAt) return null
        val byApp = LinkedHashMap<String, Long>()
        sessions.forEach { s ->
            val ms = overlap(s.startedAt, s.endedAt, arrivalAt, end)
            if (ms > 0) byApp[s.packageName] = (byApp[s.packageName] ?: 0L) + ms
        }
        if (byApp.isEmpty()) return null
        val apps = byApp.entries
            .map { (pkg, ms) -> VisitAppUsage(pkg, labelOf(pkg), categoryOf(pkg), ms) }
            .sortedWith(compareByDescending<VisitAppUsage> { it.foregroundMs }.thenBy { it.packageName })
        return VisitPhoneUsage(apps.sumOf { it.foregroundMs }, apps)
    }

    /** App em primeiro plano em [timestamp] (se duas sessões se tocam, vale a que começou por último). */
    fun activeAppAt(timestamp: Long, sessions: List<AppSession>): AppSession? =
        sessions.filter { timestamp >= it.startedAt && timestamp < it.endedAt }.maxByOrNull { it.startedAt }
}
