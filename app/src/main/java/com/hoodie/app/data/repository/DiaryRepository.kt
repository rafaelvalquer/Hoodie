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
import kotlinx.coroutines.Dispatchers
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
        val contextEvents = contexts.overlapping(from, to)
        val timelineEvents = timeline.range(from, to)
        val hoodieActivities = activities.overlapping(from, to)
        val knownPlaces = places.getAll()
        val diary = withContext(Dispatchers.Default) {
            DiaryAssembler.build(date, contextEvents, timelineEvents, hoodieActivities, knownPlaces, from, to, now)
        }
        // Camada digital é opcional: sem permissão, desligada ou com erro, o Diário segue igual.
        val phone = if (deviceUsage != null && settings?.current()?.digital?.showInDiary == true) {
            try {
                deviceUsage.insightsFor(date)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                null
            }
        } else null
        val trips = try { mobility?.tripsBetween(from, to).orEmpty() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { emptyList() }
        DiaryMobilityMerger.merge(DiaryDigitalMerger.merge(diary, phone, zone), trips, from, to, now, zone)
    }
}
