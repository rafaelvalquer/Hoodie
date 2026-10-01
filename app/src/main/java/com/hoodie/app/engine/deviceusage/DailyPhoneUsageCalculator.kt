package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.AppUsageEntry
import com.hoodie.app.domain.phoneinsights.model.CategoryUsageSummary
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneSummary
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.domain.phoneinsights.model.ScreenSession
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** Agregados finais do dia: tela, apps, categorias e distribuição por hora. */
object DailyPhoneUsageCalculator {

    fun summary(date: LocalDate, screen: ScreenUsage, appSessions: List<AppSession>): DailyPhoneSummary {
        val sessions = screen.sessions
        if (sessions.isEmpty()) return DailyPhoneSummary.empty(date)
        return DailyPhoneSummary(
            date = date,
            screenTimeMs = sessions.sumOf { it.durationMs },
            sessionCount = sessions.size,
            unlockCount = screen.unlockCount,
            firstUseAt = minOf(sessions.first().startedAt, appSessions.minOfOrNull { it.startedAt } ?: Long.MAX_VALUE),
            lastUseAt = maxOf(sessions.last().endedAt, appSessions.maxOfOrNull { it.endedAt } ?: Long.MIN_VALUE),
            longestSessionMs = sessions.maxOf { it.durationMs },
            isEstimated = screen.isEstimated,
        )
    }

    /** Uso por app, maior tempo primeiro. [installed] decide entre o ícone real e o genérico. */
    fun apps(
        appSessions: List<AppSession>,
        labelOf: (String) -> String,
        categoryOf: (String) -> HoodieAppCategory,
        installed: (String) -> Boolean,
    ): List<AppUsageEntry> =
        appSessions.groupBy { it.packageName }.map { (pkg, list) ->
            AppUsageEntry(
                packageName = pkg,
                appLabel = labelOf(pkg),
                appCategory = categoryOf(pkg),
                foregroundMs = list.sumOf { it.durationMs },
                sessionCount = list.count { it.durationMs >= HoodieConfig.MIN_APP_SESSION_MS }.coerceAtLeast(1),
                firstUsedAt = list.minOf { it.startedAt },
                lastUsedAt = list.maxOf { it.endedAt },
                iconSource = if (installed(pkg)) AppIconSource.Installed(pkg) else AppIconSource.Generic,
            )
        }.sortedWith(compareByDescending<AppUsageEntry> { it.foregroundMs }.thenBy { it.appLabel })

    fun categories(apps: List<AppUsageEntry>): List<CategoryUsageSummary> =
        apps.groupBy { it.appCategory }
            .map { (category, list) -> CategoryUsageSummary(category, list.sumOf { it.foregroundMs }, list.size) }
            .filter { it.foregroundMs > 0 }
            .sortedByDescending { it.foregroundMs }

    /** Tempo de tela em cada hora do relógio local (0..23), quebrando sessões que cruzam a hora cheia. */
    fun hourly(sessions: List<ScreenSession>, zone: ZoneId): List<Long> {
        val buckets = LongArray(24)
        sessions.forEach { s ->
            var cursor = s.startedAt
            while (cursor < s.endedAt) {
                val local = Instant.ofEpochMilli(cursor).atZone(zone)
                val nextHour = local.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                val stop = minOf(nextHour, s.endedAt)
                buckets[local.hour] += stop - cursor
                cursor = stop
            }
        }
        return buckets.toList()
    }
}
