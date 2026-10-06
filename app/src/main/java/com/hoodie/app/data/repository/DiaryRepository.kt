package com.hoodie.app.data.repository

import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.HoodieActivityDao
import com.hoodie.app.core.database.PlaceDao
import com.hoodie.app.core.database.TimelineDao
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.startOfDay
import com.hoodie.app.domain.diary.model.DailyDiary
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
) {
    suspend fun loadDiary(date: LocalDate): DailyDiary = withContext(Dispatchers.IO) {
        val zone = clock.zone()
        val from = startOfDay(date, zone)
        val to = startOfDay(date.plusDays(1), zone)
        val now = clock.nowMillis()
        val appSettings = try { settings?.current() }
        catch (cancelled: CancellationException) { throw cancelled }
        catch (_: Exception) { null }
        val digitalSettings = appSettings?.digital
        val contextEvents: List<com.hoodie.app.core.database.ContextEventEntity>
        val timelineEvents: List<com.hoodie.app.core.database.TimelineEventEntity>
        val hoodieActivities: List<com.hoodie.app.core.database.HoodieActivityEntity>
        val knownPlaces: List<com.hoodie.app.core.database.PlaceEntity>
        val mobilityTrips: List<MobilityTrip>
        coroutineScope {
            val contextTask = async { contexts.overlapping(from, to) }
            val timelineTask = async { timeline.range(from, to) }
            val activityTask = async { activities.overlapping(from, to) }
            val placesTask = async { places.getAll() }
            val tripsTask = async { try { mobility?.tripsBetween(from, to).orEmpty() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() } }
            contextEvents = contextTask.await()
            timelineEvents = timelineTask.await()
            hoodieActivities = activityTask.await()
            knownPlaces = placesTask.await()
            mobilityTrips = tripsTask.await()
        }
        val phone = if (deviceUsage != null && (digitalSettings?.analysisEnabled == true || digitalSettings?.showInDiary == true)) {
            try { deviceUsage.insightsFor(date) }
            catch (cancelled: CancellationException) { throw cancelled }
            catch (_: Exception) { null }
        } else null
        val sessions = try { mobilityTrips.map { it.session } } catch (_: Exception) { emptyList() }
        val activityWindow = DailyActivityWindowResolver.resolve(
            date = date, civilStartAt = from, civilEndAt = to, now = now, zone = zone,
            sleepSchedule = appSettings?.sleep ?: SleepSchedule(), contexts = contextEvents,
            activities = hoodieActivities, timeline = timelineEvents,
            appSessions = phone?.appSessions.takeIf { digitalSettings?.analysisEnabled == true }.orEmpty(),
            mobilitySessions = sessions,
            analysisEnabled = digitalSettings?.analysisEnabled == true,
            mobilityEnabled = appSettings?.mobility?.detectionEnabled ?: true,
        )
        val diary = withContext(Dispatchers.Default) {
            DiaryAssembler.build(date, contextEvents, timelineEvents, hoodieActivities, knownPlaces, from, to, now, activityWindow)
        }
        val visiblePhone = phone.takeIf { digitalSettings?.showInDiary == true }
        return@withContext DiaryMobilityMerger.merge(DiaryDigitalMerger.merge(diary, visiblePhone, zone), mobilityTrips, from, to, now, zone)
    }
}
