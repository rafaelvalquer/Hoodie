package com.hoodie.app.data.repository

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.AppCategoryOverrideEntity
import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.DeviceUsageDao
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

    /**
     * O que a UI/Diário devem mostrar: recalcula quando há permissão e a análise
     * está ligada; senão cai no histórico salvo. null = nada a mostrar.
     */
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
    private val transactions: com.hoodie.app.core.database.TransactionRunner? = null,
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
        val digital = settings.current().digital
        val insights = if (digital.analysisEnabled && access.isGranted()) {
            withContext(Dispatchers.IO) { mutex.withLock { refreshLocked(date, digital) } }
        } else {
            loadDay(date)
        }
        return insights?.takeUnless { it.isEmpty }
    }

    override suspend fun setCategoryOverride(packageName: String, category: HoodieAppCategory?) = withContext(Dispatchers.IO) {
        if (category == null) dao.deleteOverride(packageName)
        else dao.upsertOverride(AppCategoryOverrideEntity(packageName, category.name, clock.nowMillis()))
    }

    override suspend fun clearHistory() = withContext(Dispatchers.IO) { mutex.withLock { dao.clearHistory() } }

    override suspend fun storedSessionCount(): Int = withContext(Dispatchers.IO) { dao.sessionCount() }

    private suspend fun refreshLocked(date: LocalDate, digital: DigitalSettings): DailyPhoneInsights =
        transactions?.run { refreshCanonicalLocked(date, digital) } ?: refreshCanonicalLocked(date, digital)

    private suspend fun refreshCanonicalLocked(date: LocalDate, digital: DigitalSettings): DailyPhoneInsights {
        val zone = clock.zone()
        val now = clock.nowMillis()
        val from = startOfDay(date, zone)
        val to = startOfDay(date.plusDays(1), zone)
        val end = minOf(to, now)
        val stored = loadStoredLocked(date)
        if (end <= from) return stored ?: emptyInsights(date)

        val events = source.events(from - HoodieConfig.USAGE_LOOKBACK_MS, end)
        val spans = contexts.overlapping(from, to).map { ContextSpan(it.type, it.startedAt, it.endedAt) }
        val computed = PhoneInsightsAssembler.assemble(
            date, events, spans, metadata, AppCategoryResolver(overrides()), from, end, zone,
        )

        // O Android guarda eventos só por alguns dias: um dia antigo recalculado
        // "menor" que o salvo perdeu eventos, não uso. Fica o histórico.
        if (stored != null && (computed.isEmpty || (to <= now && computed.summary.screenTimeMs < stored.summary.screenTimeMs))) {
            return stored
        }
        if (digital.saveHistory && !computed.isEmpty) {
            dao.replaceDay(
                computed.toDayEntity(now),
                computed.topApps.map { it.toEntity(date, now) },
                computed.contextTotalEntities(),
                computed.usageByContext.flatMap { ctx -> ctx.apps.map { it.toEntity(date) } },
                computed.hourlyEntities(),
                computed.timelineEntities(),
                DeviceUsageMappers.sessionEntities(computed.appSessions, date.toEpochDay()),
                epochDay = date.toEpochDay(),
            )
        }
        return computed
    }

    private suspend fun loadStoredLocked(date: LocalDate): DailyPhoneInsights? {
        val day = dao.day(date.toString()) ?: return null
        val overrides = overrides()
        return DeviceUsageMappers.fromStored(
            day, dao.apps(day.date), dao.contextApps(day.date),
            categoryOf = { pkg, stored -> overrides[pkg] ?: stored },
            installed = metadata::isInstalled,
            sessions = dao.sessions(date.toEpochDay()),
            contextTotals = dao.contextTotals(day.date),
            hourly = dao.hourly(day.date),
            timeline = dao.phoneTimeline(day.date),
        )
    }

    private suspend fun overrides(): Map<String, HoodieAppCategory> =
        dao.overrides().associate { it.packageName to HoodieAppCategory.parse(it.category) }

    private fun emptyInsights(date: LocalDate) =
        DailyPhoneInsights(DailyPhoneSummary.empty(date), emptyList(), emptyList(), emptyList(), emptyList())
}
