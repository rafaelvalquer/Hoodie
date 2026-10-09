package com.hoodie.app.data.repository

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.AppCategoryOverrideEntity
import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.DailyAppUsageEntity
import com.hoodie.app.core.database.DailyContextAppUsageEntity
import com.hoodie.app.core.database.DailyContextUsageEntity
import com.hoodie.app.core.database.DailyDeviceUsageEntity
import com.hoodie.app.core.database.DailyPhoneTimelineEntity
import com.hoodie.app.core.database.DailyScreenHourlyEntity
import com.hoodie.app.core.database.DeviceUsageDao
import com.hoodie.app.core.database.PhoneAppSessionEntity
import com.hoodie.app.core.database.TransactionRunner
import com.hoodie.app.core.datastore.DigitalSettings
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.deviceusage.AppMetadataResolver
import com.hoodie.app.core.deviceusage.UsageAccessChecker
import com.hoodie.app.core.deviceusage.UsageStatsSource
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.startOfDay
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneSummary
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.engine.deviceusage.AppCategoryResolver
import com.hoodie.app.engine.deviceusage.DeviceUsageMappers
import com.hoodie.app.engine.deviceusage.DeviceUsageMappers.toDayEntity
import com.hoodie.app.engine.deviceusage.DeviceUsageMappers.toEntity
import com.hoodie.app.engine.deviceusage.DeviceUsageMappers.hourlyEntities
import com.hoodie.app.engine.deviceusage.DeviceUsageMappers.contextTotalEntities
import com.hoodie.app.engine.deviceusage.DeviceUsageMappers.timelineEntities
import com.hoodie.app.engine.deviceusage.PhoneInsightsAssembler
import com.hoodie.app.engine.timeline.ContextSpan
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

interface DeviceUsageRepository {
    /** Recalcula o dia a partir do Android e salva os agregados (se o histórico estiver ligado). */
    suspend fun refreshDay(date: LocalDate): DailyPhoneInsights

    /** Só o que está salvo; null se o dia nunca foi processado. */
    suspend fun loadDay(date: LocalDate): DailyPhoneInsights?

    /** Dados para apresentação. Sempre lê o histórico persistido, nunca coleta nem grava. */
    suspend fun insightsFor(date: LocalDate): DailyPhoneInsights?

    /** [category] null volta para a categoria automática. */
    suspend fun setCategoryOverride(packageName: String, category: HoodieAppCategory?)

    suspend fun clearHistory()

    /** Sessões guardadas (Developer Lab). */
    suspend fun storedSessionCount(): Int
}

@Singleton
class DeviceUsageRepositoryImpl @Inject constructor(
    private val source: UsageStatsSource,
    private val metadata: AppMetadataResolver,
    private val access: UsageAccessChecker,
    private val dao: DeviceUsageDao,
    private val contexts: ContextEventDao,
    private val settings: SettingsRepository,
    private val clock: ClockProvider,
    private val transactions: TransactionRunner? = null,
) : DeviceUsageRepository {
    private val mutex = Mutex()

    override suspend fun refreshDay(date: LocalDate): DailyPhoneInsights = withContext(Dispatchers.IO) {
        mutex.withLock { refreshLocked(date, settings.current().digital) }
    }

    override suspend fun loadDay(date: LocalDate): DailyPhoneInsights? = withContext(Dispatchers.IO) {
        mutex.withLock { loadStoredLocked(date) }
    }

    override suspend fun insightsFor(date: LocalDate): DailyPhoneInsights? {
        if (date.isAfter(clock.today())) return null
        return loadDay(date)?.takeUnless { it.isEmpty }
    }

    override suspend fun setCategoryOverride(packageName: String, category: HoodieAppCategory?) = withContext(Dispatchers.IO) {
        if (category == null) dao.deleteOverride(packageName)
        else dao.upsertOverride(AppCategoryOverrideEntity(packageName, category.name, clock.nowMillis()))
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) { mutex.withLock { dao.clearHistory() } }

    override suspend fun storedSessionCount(): Int = withContext(Dispatchers.IO) { dao.sessionCount() }

    private suspend fun refreshLocked(date: LocalDate, digital: DigitalSettings): DailyPhoneInsights =
        refreshCanonicalLocked(date, digital)

    private suspend fun refreshCanonicalLocked(date: LocalDate, digital: DigitalSettings): DailyPhoneInsights {
        val zone = clock.zone()
        val now = clock.nowMillis()
        val from = startOfDay(date, zone)
        val to = startOfDay(date.plusDays(1), zone)
        val end = minOf(to, now)
        val storedRows = readStoredRows(date)
        val stored = storedRows?.toInsights()
        if (end <= from) return stored ?: emptyInsights(date)

        val events = source.events(from - HoodieConfig.USAGE_LOOKBACK_MS, end)
        val spans = contexts.overlapping(from, to).map { ContextSpan(it.type, it.startedAt, it.endedAt) }
        val computed = PhoneInsightsAssembler.assemble(
            date, events, spans, metadata, AppCategoryResolver(overrides()), from, end, zone,
        )

        // O Android guarda eventos só por alguns dias: um dia antigo recalculado
        // "menor" que o salvo perdeu eventos, não uso. Fica o histórico.
        if (storedRows != null && computed.isEmpty) {
            return requireNotNull(stored)
        }
        if (storedRows != null && isLikelyPartialHistoricalResult(date, computed, storedRows, to <= now)) return requireNotNull(stored)
        if (digital.saveHistory && !computed.isEmpty) {
            val day = computed.toDayEntity(now)
            val apps = computed.topApps.map { it.toEntity(date, now) }
            val contextTotals = computed.contextTotalEntities()
            val contextApps = computed.usageByContext.flatMap { ctx -> ctx.apps.map { it.toEntity(date) } }
            val hourly = computed.hourlyEntities()
            val timeline = computed.timelineEntities()
            val sessions = DeviceUsageMappers.sessionEntities(computed.appSessions, date.toEpochDay())
            if (storedRows == null || !storedRows.hasSameContent(day, apps, contextTotals, contextApps, hourly, timeline, sessions)) {
                // replaceDay is the only transaction: Android collection and all processing stay outside it.
                dao.replaceDay(day, apps, contextTotals, contextApps, hourly, timeline, sessions, date.toEpochDay())
            }
        }
        return computed
    }

    private suspend fun loadStoredLocked(date: LocalDate): DailyPhoneInsights? {
        val rows = readStoredRows(date) ?: return null
        return rows.toInsights()
    }

    private suspend fun readStoredRows(date: LocalDate): StoredDayRows? {
        val rows = transactions?.run { queryStoredRows(date) } ?: queryStoredRows(date)
        return rows
    }

    private suspend fun queryStoredRows(date: LocalDate): StoredDayRows? {
        val day = dao.day(date.toString()) ?: return null
        return StoredDayRows(
            day = day,
            apps = dao.apps(day.date),
            contextApps = dao.contextApps(day.date),
            sessions = dao.sessions(date.toEpochDay()),
            contextTotals = dao.contextTotals(day.date),
            hourly = dao.hourly(day.date),
            timeline = dao.phoneTimeline(day.date),
            overrides = dao.overrides().associate { it.packageName to HoodieAppCategory.parse(it.category) },
        )
    }

    private fun StoredDayRows.toInsights(): DailyPhoneInsights = DeviceUsageMappers.fromStored(
        day, apps, contextApps,
        categoryOf = { pkg, stored -> overrides[pkg] ?: stored },
        installed = metadata::isInstalled,
        sessions = sessions,
        contextTotals = contextTotals,
        hourly = hourly,
        timeline = timeline,
    )

    /** A data source that omits old events must never shrink a completed historical aggregate. */
    private fun isLikelyPartialHistoricalResult(
        date: LocalDate,
        computed: DailyPhoneInsights,
        stored: StoredDayRows,
        dayIsClosed: Boolean,
    ): Boolean {
        if (!dayIsClosed || !date.isBefore(clock.today())) return false
        val storedPackages = stored.apps.mapTo(mutableSetOf()) { it.packageName }
        val computedPackages = computed.topApps.mapTo(mutableSetOf()) { it.packageName }
        val storedHourly = stored.hourly.associate { it.hour to it.screenMs }
        val hourlyShrank = storedHourly.any { (hour, value) ->
            value > 0 && computed.hourlyScreenMs.getOrElse(hour) { 0L } < value
        }
        return computed.summary.screenTimeMs < stored.day.screenTimeMs ||
            computed.appCount < stored.day.appCount ||
            (storedPackages.isNotEmpty() && !computedPackages.containsAll(storedPackages)) ||
            (stored.sessions.isNotEmpty() && computed.appSessions.size < stored.sessions.size) ||
            hourlyShrank
    }

    private data class StoredDayRows(
        val day: DailyDeviceUsageEntity,
        val apps: List<DailyAppUsageEntity>,
        val contextApps: List<DailyContextAppUsageEntity>,
        val sessions: List<PhoneAppSessionEntity>,
        val contextTotals: List<DailyContextUsageEntity>,
        val hourly: List<DailyScreenHourlyEntity>,
        val timeline: List<DailyPhoneTimelineEntity>,
        val overrides: Map<String, HoodieAppCategory>,
    ) {
        fun hasSameContent(
            candidateDay: DailyDeviceUsageEntity,
            candidateApps: List<DailyAppUsageEntity>,
            candidateContextTotals: List<DailyContextUsageEntity>,
            candidateContextApps: List<DailyContextAppUsageEntity>,
            candidateHourly: List<DailyScreenHourlyEntity>,
            candidateTimeline: List<DailyPhoneTimelineEntity>,
            candidateSessions: List<PhoneAppSessionEntity>,
        ): Boolean =
            day.copy(updatedAt = 0) == candidateDay.copy(updatedAt = 0) &&
                apps.map { it.copy(updatedAt = 0) }.toSet() == candidateApps.map { it.copy(updatedAt = 0) }.toSet() &&
                contextTotals.toSet() == candidateContextTotals.toSet() &&
                contextApps.toSet() == candidateContextApps.toSet() &&
                hourly.toSet() == candidateHourly.toSet() &&
                timeline.toSet() == candidateTimeline.toSet() &&
                sessions.toSet() == candidateSessions.toSet()
    }

    private suspend fun overrides(): Map<String, HoodieAppCategory> =
        dao.overrides().associate { it.packageName to HoodieAppCategory.parse(it.category) }

    private fun emptyInsights(date: LocalDate) =
        DailyPhoneInsights(DailyPhoneSummary.empty(date), emptyList(), emptyList(), emptyList(), emptyList())
}
