package com.hoodie.app.data.repository

import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.HoodieActivityDao
import com.hoodie.app.core.database.PlaceDao
import com.hoodie.app.core.database.TimelineDao
import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.startOfDay
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.database.TransactionRunner
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.engine.diary.DiaryAssembler
import com.hoodie.app.engine.diary.DiaryDigitalMerger
import com.hoodie.app.engine.diary.DiaryMobilityMerger
import com.hoodie.app.engine.daycycle.DailyActivityWindowResolver
import com.hoodie.app.core.model.SleepSchedule
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

data class DiaryCoreSnapshot internal constructor(
    val date: LocalDate,
    val diary: DailyDiary,
    internal val phone: DailyPhoneInsights?,
    internal val mobilityTrips: List<MobilityTrip>,
    internal val showPhone: Boolean,
    internal val from: Long,
    internal val to: Long,
    internal val now: Long,
    internal val zone: java.time.ZoneId,
    internal val isException: Boolean,
)

@Singleton
class DiaryRepository @Inject constructor(
    private val contexts: ContextEventDao,
    private val timeline: TimelineDao,
    private val activities: HoodieActivityDao,
    private val places: PlaceDao,
    private val clock: ClockProvider,
    /** Camada digital: opcional para o Diário funcionar (e ser testado) sem ela. */
    private val deviceUsage: DeviceUsageRepository? = null,
    private val settings: SettingsRepository? = null,
    /** Deslocamentos (Mobilidade Contextual): opcional, como a camada digital. */
    private val mobility: MobilityRepository? = null,
    private val dayExceptions: com.hoodie.app.core.database.DayExceptionDao? = null,
    private val transactions: TransactionRunner? = null,
) {
    suspend fun loadCore(date: LocalDate): DiaryCoreSnapshot = withContext(Dispatchers.IO) {
        val zone = clock.zone()
        val from = startOfDay(date, zone)
        val to = startOfDay(date.plusDays(1), zone)
        val now = clock.nowMillis()
        val isHistorical = date.isBefore(clock.today())
        val evidenceTo = minOf(to + HoodieConfig.SLEEP_END_LOOKAHEAD_MS, now).coerceAtLeast(from)
        val queryTo = maxOf(to, evidenceTo)
        val appSettings = try { settings?.current() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        val digitalSettings = appSettings?.digital
        data class QueryRows(
            val evidenceContexts: List<ContextEventEntity>,
            val evidenceTimeline: List<TimelineEventEntity>,
            val contextEvents: List<ContextEventEntity>,
            val timelineEvents: List<TimelineEventEntity>,
            val activities: List<HoodieActivityEntity>,
            val places: List<PlaceEntity>,
            val mobilityTrips: List<MobilityTrip>,
            val phone: DailyPhoneInsights?,
            val phoneLookahead: DailyPhoneInsights?,
            val isException: Boolean,
        )
        suspend fun readSnapshot(): QueryRows = coroutineScope {
            val contextTask = async { contexts.overlapping(from, queryTo) }
            val timelineTask = async { timeline.range(from, queryTo) }
            val activityTask = async { activities.overlapping(from, to) }
            val placesTask = async { places.getAll() }
            val tripsTask = async { try { mobility?.tripsBetween(from, queryTo).orEmpty() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() } }
            val phoneTask = async {
                if (deviceUsage != null && (digitalSettings?.analysisEnabled == true || digitalSettings?.showInDiary == true)) {
                    try { readDigitalDay(date) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                } else null
            }
            val phoneLookaheadTask = async {
                if (deviceUsage != null && isHistorical && evidenceTo > to && digitalSettings?.analysisEnabled == true) {
                    try { readDigitalDay(date.plusDays(1)) }
                    catch (cancelled: CancellationException) { throw cancelled }
                    catch (_: Exception) { null }
                } else null
            }
            val allContexts = contextTask.await()
            val allTimeline = timelineTask.await()
            QueryRows(
                evidenceContexts = allContexts,
                evidenceTimeline = allTimeline,
                contextEvents = allContexts.filter { it.startedAt < to && (it.endedAt == null || it.endedAt > from) },
                timelineEvents = allTimeline.filter { it.timestamp in from until to },
                activities = activityTask.await(),
                places = placesTask.await(),
                mobilityTrips = tripsTask.await(),
                phone = phoneTask.await(),
                phoneLookahead = phoneLookaheadTask.await(),
                isException = dayExceptions?.get(date.toEpochDay()) != null,
            )
        }
        val rows = transactions?.run { readSnapshot() } ?: readSnapshot()
        val evidenceContexts = rows.evidenceContexts
        val evidenceTimeline = rows.evidenceTimeline
        val contextEvents = rows.contextEvents
        val timelineEvents = rows.timelineEvents
        val hoodieActivities = rows.activities
        val knownPlaces = rows.places
        val mobilityTrips = rows.mobilityTrips
        val phone = rows.phone
        val phoneLookahead = rows.phoneLookahead
        val sessions = try { mobilityTrips.map { it.session } } catch (_: Exception) { emptyList() }
        // Lookahead is evidence for sleep onset only. Keep mobility shown in the diary civil-day scoped.
        val visibleMobilityTrips = mobilityTrips.filter { trip ->
            trip.session.startedAt < to && (trip.session.endedAt ?: trip.session.startedAt) >= from
        }
        val phoneEvidenceSessions = if (digitalSettings?.analysisEnabled == true) {
            (phone?.appSessions.orEmpty() + phoneLookahead?.appSessions.orEmpty()).distinct()
        } else emptyList()
        val activityWindow = DailyActivityWindowResolver.resolve(
            date = date, civilStartAt = from, civilEndAt = to, now = now, zone = zone,
            sleepSchedule = appSettings?.sleep ?: SleepSchedule(), contexts = evidenceContexts,
            activities = hoodieActivities, timeline = evidenceTimeline,
            appSessions = phoneEvidenceSessions,
            mobilitySessions = sessions,
            analysisEnabled = digitalSettings?.analysisEnabled == true,
            mobilityEnabled = appSettings?.mobility?.detectionEnabled ?: true,
            evidenceEndAt = evidenceTo,
        )
        val diary = withContext(Dispatchers.Default) {
            DiaryAssembler.build(date, contextEvents, timelineEvents, hoodieActivities, knownPlaces, from, to, now, activityWindow)
        }
        return@withContext DiaryCoreSnapshot(
            date = date,
            diary = diary.copy(isException = rows.isException),
            phone = phone,
            mobilityTrips = visibleMobilityTrips,
            showPhone = digitalSettings?.showInDiary == true,
            from = from,
            to = to,
            now = now,
            zone = zone,
            isException = rows.isException,
        )
    }

    suspend fun buildFullDiary(core: DiaryCoreSnapshot): DailyDiary = withContext(Dispatchers.Default) {
        val phone = core.phone.takeIf { core.showPhone }
        DiaryMobilityMerger.merge(
            DiaryDigitalMerger.merge(core.diary, phone, core.zone),
            core.mobilityTrips, core.from, core.to, core.now, core.zone,
        ).copy(isException = core.isException)
    }

    suspend fun loadDiary(date: LocalDate): DailyDiary = buildFullDiary(loadCore(date))

    private suspend fun readDigitalDay(date: LocalDate): DailyPhoneInsights? =
        if (HoodieConfig.DIARY_DIGITAL_READ_ONLY) deviceUsage?.insightsFor(date) else deviceUsage?.loadDay(date)
}
