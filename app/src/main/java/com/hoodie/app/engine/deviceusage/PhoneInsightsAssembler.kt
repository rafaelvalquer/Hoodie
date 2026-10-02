package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.deviceusage.AppMetadataResolver
import com.hoodie.app.core.deviceusage.RawUsageEvent
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.domain.phoneinsights.model.PhoneTimelineItem
import com.hoodie.app.engine.timeline.ContextSpan
import java.time.LocalDate
import java.time.ZoneId

/**
 * Eventos brutos de um dia → insights prontos para o Diário. Sem Android:
 * metadados de apps chegam por [AppMetadataResolver] (fake nos testes).
 */
object PhoneInsightsAssembler {

    fun assemble(
        date: LocalDate,
        events: List<RawUsageEvent>,
        contexts: List<ContextSpan>,
        metadata: AppMetadataResolver,
        categories: AppCategoryResolver,
        from: Long,
        end: Long,
        zone: ZoneId,
    ): DailyPhoneInsights {
        val allSessions = AppSessionBuilder.build(events, from, end)
        val ignored = metadata.ignoredPackages()
        val appSessions = allSessions.filter { it.packageName !in ignored }
        // A tela inicial conta como tela ligada, só não como "app usado".
        val screen = ScreenSessionBuilder.build(events, allSessions, from, end)

        val labels = HashMap<String, String>()
        val labelOf: (String) -> String = { pkg -> labels.getOrPut(pkg) { metadata.label(pkg) } }
        val cats = HashMap<String, HoodieAppCategory>()
        val categoryOf: (String) -> HoodieAppCategory = { pkg -> cats.getOrPut(pkg) { categories.resolve(pkg, metadata.systemCategory(pkg)) } }

        val apps = DailyPhoneUsageCalculator.apps(appSessions, labelOf, categoryOf, metadata::isInstalled)
        return DailyPhoneInsights(
            summary = DailyPhoneUsageCalculator.summary(date, screen, appSessions),
            topApps = apps,
            usageByContext = ContextUsageCorrelator.correlate(appSessions, contexts, labelOf, end, topAppsPerContext = Int.MAX_VALUE),
            appTimeline = timeline(appSessions, contexts, labelOf, categoryOf, end),
            categoryUsage = DailyPhoneUsageCalculator.categories(apps),
            hourlyScreenMs = DailyPhoneUsageCalculator.hourly(screen.sessions, zone),
            appCount = apps.size,
            appSessions = appSessions,
        )
    }

    /** Blocos relevantes: usos próximos do mesmo app fundidos, só os que passam de alguns minutos. */
    fun timeline(
        sessions: List<AppSession>,
        contexts: List<ContextSpan>,
        labelOf: (String) -> String,
        categoryOf: (String) -> HoodieAppCategory,
        end: Long,
    ): List<PhoneTimelineItem> {
        val merged = mutableListOf<AppSession>()
        sessions.sortedBy { it.startedAt }.forEach { s ->
            val last = merged.lastOrNull()
            if (last != null && last.packageName == s.packageName && s.startedAt - last.endedAt <= HoodieConfig.PHONE_TIMELINE_MERGE_GAP_MS) {
                merged[merged.lastIndex] = last.copy(endedAt = maxOf(last.endedAt, s.endedAt))
            } else {
                merged += s
            }
        }
        val relevant = merged
            .filter { it.durationMs >= HoodieConfig.PHONE_TIMELINE_MIN_MS }
            .sortedByDescending { it.durationMs }
            .take(HoodieConfig.PHONE_TIMELINE_MAX_ITEMS)
            .sortedBy { it.startedAt }
        return AppSessionContextSplitter.split(relevant, contexts, end)
            .map { s ->
                PhoneTimelineItem(s.packageName, labelOf(s.packageName), categoryOf(s.packageName), s.startedAt, s.endedAt, s.context)
            }
    }
}
