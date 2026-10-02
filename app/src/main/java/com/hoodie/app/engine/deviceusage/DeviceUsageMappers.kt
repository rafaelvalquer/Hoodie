package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.database.PhoneAppSessionEntity
import com.hoodie.app.core.database.DailyScreenHourlyEntity
import com.hoodie.app.core.database.DailyContextUsageEntity
import com.hoodie.app.core.database.DailyPhoneTimelineEntity
import com.hoodie.app.domain.phoneinsights.model.PhoneTimelineItem
import com.hoodie.app.domain.phoneinsights.model.AppSession

import com.hoodie.app.core.database.DailyAppUsageEntity
import com.hoodie.app.core.database.DailyContextAppUsageEntity
import com.hoodie.app.core.database.DailyDeviceUsageEntity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.domain.phoneinsights.model.AppUsageEntry
import com.hoodie.app.domain.phoneinsights.model.ContextAppUsage
import com.hoodie.app.domain.phoneinsights.model.ContextUsageSummary
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneSummary
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import java.time.LocalDate

/** Domínio ↔ Room. Guarda agregados e blocos digitais derivados, nunca eventos brutos. */
object DeviceUsageMappers {
    fun DailyPhoneInsights.hourlyEntities() = List(24) { hour ->
        DailyScreenHourlyEntity(summary.date.toString(), hour, hourlyScreenMs.getOrElse(hour) { 0L })
    }

    fun DailyPhoneInsights.contextTotalEntities() = usageByContext.map {
        DailyContextUsageEntity(summary.date.toString(), it.context.name, it.foregroundMs, it.sessionCount)
    }

    fun DailyPhoneInsights.timelineEntities() = appTimeline.map {
        DailyPhoneTimelineEntity("${summary.date}@${it.packageName}@${it.startedAt}@${it.endedAt}",
            summary.date.toString(), it.packageName, it.appLabel, it.category.name,
            it.startedAt, it.endedAt, it.context?.name)
    }

    fun DailyPhoneInsights.toDayEntity(updatedAt: Long) = DailyDeviceUsageEntity(
        date = summary.date.toString(),
        screenTimeMs = summary.screenTimeMs,
        sessionCount = summary.sessionCount,
        unlockCount = summary.unlockCount,
        firstUseAt = summary.firstUseAt,
        lastUseAt = summary.lastUseAt,
        longestSessionMs = summary.longestSessionMs,
        isEstimated = summary.isEstimated,
        appCount = appCount,
        updatedAt = updatedAt,
    )

    fun AppUsageEntry.toEntity(date: LocalDate, updatedAt: Long) = DailyAppUsageEntity(
        date = date.toString(),
        packageName = packageName,
        appLabel = appLabel,
        appCategory = appCategory.name,
        foregroundMs = foregroundMs,
        sessionCount = sessionCount,
        firstUsedAt = firstUsedAt,
        lastUsedAt = lastUsedAt,
        updatedAt = updatedAt,
    )

    fun ContextAppUsage.toEntity(date: LocalDate) = DailyContextAppUsageEntity(
        date = date.toString(),
        context = context.name,
        packageName = packageName,
        appLabel = appLabel,
        foregroundMs = foregroundMs,
        sessionCount = sessionCount,
    )

    /**
     * Dia lido do histórico. A categoria pode ter mudado desde que foi salvo
     * (override do usuário), por isso [categoryOf] é reaplicado aqui.
     */
    fun fromStored(
        day: DailyDeviceUsageEntity,
        apps: List<DailyAppUsageEntity>,
        contexts: List<DailyContextAppUsageEntity>,
        categoryOf: (packageName: String, stored: HoodieAppCategory) -> HoodieAppCategory,
        installed: (String) -> Boolean,
        sessions: List<PhoneAppSessionEntity> = emptyList(),
        contextTotals: List<DailyContextUsageEntity> = emptyList(),
        hourly: List<DailyScreenHourlyEntity> = emptyList(),
        timeline: List<DailyPhoneTimelineEntity> = emptyList(),
    ): DailyPhoneInsights {
        val date = LocalDate.parse(day.date)
        val entries = apps.map {
            AppUsageEntry(
                packageName = it.packageName,
                appLabel = it.appLabel,
                appCategory = categoryOf(it.packageName, HoodieAppCategory.parse(it.appCategory)),
                foregroundMs = it.foregroundMs,
                sessionCount = it.sessionCount,
                firstUsedAt = it.firstUsedAt,
                lastUsedAt = it.lastUsedAt,
                iconSource = if (installed(it.packageName)) AppIconSource.Installed(it.packageName) else AppIconSource.Generic,
            )
        }.sortedByDescending { it.foregroundMs }
        val contextApps = contexts
            .mapNotNull { e ->
                val ctx = runCatching { UserContextType.valueOf(e.context) }.getOrNull() ?: return@mapNotNull null
                ContextAppUsage(ctx, e.packageName, e.appLabel, e.foregroundMs, e.sessionCount)
            }
            .groupBy { it.context }
        val byContext = if (contextTotals.isNotEmpty()) contextTotals.mapNotNull { total ->
            val ctx = runCatching { UserContextType.valueOf(total.context) }.getOrNull() ?: return@mapNotNull null
            ContextUsageSummary(ctx, total.foregroundMs, total.sessionCount,
                contextApps[ctx].orEmpty().sortedByDescending { it.foregroundMs })
        } else contextApps.map { (ctx, list) ->
            // Compatibilidade com dias v4: esses totais antigos eram limitados ao ranking salvo.
            ContextUsageSummary(ctx, list.sumOf { it.foregroundMs }, list.maxOf { it.sessionCount }, list.sortedByDescending { it.foregroundMs })
        }
        val sortedContexts = byContext
            .sortedByDescending { it.foregroundMs }
        return DailyPhoneInsights(
            summary = DailyPhoneSummary(
                date = date,
                screenTimeMs = day.screenTimeMs,
                sessionCount = day.sessionCount,
                unlockCount = day.unlockCount,
                firstUseAt = day.firstUseAt,
                lastUseAt = day.lastUseAt,
                longestSessionMs = day.longestSessionMs,
                isEstimated = day.isEstimated,
            ),
            topApps = entries,
            usageByContext = sortedContexts,
            appTimeline = timeline.sortedBy { it.startedAt }.map { item ->
                PhoneTimelineItem(item.packageName, item.appLabel,
                    categoryOf(item.packageName, HoodieAppCategory.parse(item.category)), item.startedAt, item.endedAt,
                    item.context?.let { runCatching { UserContextType.valueOf(it) }.getOrNull() })
            },
            categoryUsage = DailyPhoneUsageCalculator.categories(entries),
            hourlyScreenMs = if (hourly.isEmpty()) emptyList() else {
                val hours = hourly.associate { it.hour to it.screenMs }
                List(24) { hours[it] ?: 0L }
            },
            appCount = day.appCount,
            appSessions = sessions.map { AppSession(it.packageName, it.startedAt, it.endedAt) },
        )
    }

    /** Sessões do dia para persistir; o id é estável (pacote + início), então recalcular não duplica. */
    fun sessionEntities(sessions: List<AppSession>, epochDay: Long): List<PhoneAppSessionEntity> =
        sessions.map { PhoneAppSessionEntity("${it.packageName}@${it.startedAt}", epochDay, it.packageName, it.startedAt, it.endedAt) }
}
