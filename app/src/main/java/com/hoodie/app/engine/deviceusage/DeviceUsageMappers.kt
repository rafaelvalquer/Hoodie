package com.hoodie.app.engine.deviceusage

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

/** Domínio ↔ Room. Só agregados vão para o banco; eventos brutos e timeline não. */
object DeviceUsageMappers {

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
        val byContext = contexts
            .mapNotNull { e ->
                val ctx = runCatching { UserContextType.valueOf(e.context) }.getOrNull() ?: return@mapNotNull null
                ContextAppUsage(ctx, e.packageName, e.appLabel, e.foregroundMs, e.sessionCount)
            }
            .groupBy { it.context }
            .map { (ctx, list) ->
                // Só o top de cada contexto é salvo: o total de sessões do contexto vira uma estimativa (máximo por app).
                ContextUsageSummary(ctx, list.sumOf { it.foregroundMs }, list.maxOf { it.sessionCount }, list.sortedByDescending { it.foregroundMs })
            }
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
            usageByContext = byContext,
            appTimeline = emptyList(),
            categoryUsage = DailyPhoneUsageCalculator.categories(entries),
            hourlyScreenMs = emptyList(),
            appCount = day.appCount,
        )
    }
}
