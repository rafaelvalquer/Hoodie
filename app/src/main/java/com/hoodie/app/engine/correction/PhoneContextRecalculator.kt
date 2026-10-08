package com.hoodie.app.engine.correction

import com.hoodie.app.core.database.*
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.engine.deviceusage.ContextUsageCorrelator
import com.hoodie.app.engine.deviceusage.PhoneInsightsAssembler
import com.hoodie.app.engine.timeline.ContextSpan
import javax.inject.Inject

/** Reallocate saved phone sessions, preserving screen totals, apps, hourly data and raw sessions. */
class PhoneContextRecalculator @Inject constructor(private val db: HoodieDatabase, private val clock: ClockProvider) {
    suspend fun recalculate(from: Long, until: Long) {
        var date = java.time.Instant.ofEpochMilli(from).atZone(clock.zone()).toLocalDate()
        val last = java.time.Instant.ofEpochMilli(maxOf(from, until - 1)).atZone(clock.zone()).toLocalDate()
        val dao = db.deviceUsageDao()
        while (!date.isAfter(last)) {
            val key = date.toString()
            if (dao.day(key) != null) {
                val start = date.atStartOfDay(clock.zone()).toInstant().toEpochMilli()
                val end = minOf(date.plusDays(1).atStartOfDay(clock.zone()).toInstant().toEpochMilli(), clock.nowMillis())
                val sessions = dao.sessions(date.toEpochDay()).map { AppSession(it.packageName, it.startedAt, it.endedAt) }
                val contexts = db.contextEventDao().overlapping(start, end).map { ContextSpan(it.type, it.startedAt, it.endedAt) }
                val apps = dao.apps(key).associateBy { it.packageName }
                val label: (String) -> String = { apps[it]?.appLabel ?: it }
                val category: (String) -> HoodieAppCategory = { HoodieAppCategory.parse(apps[it]?.appCategory) }
                val totals = ContextUsageCorrelator.correlate(sessions, contexts, label, end, Int.MAX_VALUE)
                dao.deleteContextApps(key)
                dao.deleteContextTotals(key)
                dao.insertContextTotals(totals.map { DailyContextUsageEntity(key, it.context.name, it.foregroundMs, it.sessionCount) })
                dao.insertContextApps(totals.flatMap { total -> total.apps.map { DailyContextAppUsageEntity(key, it.context.name, it.packageName, it.appLabel, it.foregroundMs, it.sessionCount) } })
                dao.deletePhoneTimeline(key)
                dao.insertPhoneTimeline(PhoneInsightsAssembler.timeline(sessions, contexts, label, category, end).map {
                    DailyPhoneTimelineEntity("$key@${it.packageName}@${it.startedAt}@${it.endedAt}", key, it.packageName, it.appLabel, it.category.name, it.startedAt, it.endedAt, it.context?.name)
                })
            }
            date = date.plusDays(1)
        }
    }
}
