package com.hoodie.app.data.repository

import com.hoodie.app.core.database.ContextEventDao
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityDao
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.database.PlaceDao
import com.hoodie.app.core.database.PlaceEntity
import com.hoodie.app.core.database.TimelineDao
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.model.TimelineSourceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.FixedClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DiaryRepositoryTest {
    @Test fun loadsDayFromDaosAndAssemblesContextVisitsAndHoodieTimeline() = runBlocking {
        val zone = ZoneId.of("UTC")
        val date = LocalDate.of(2026, 2, 3)
        val day = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val place = PlaceEntity(id = 7, name = "Casa", type = PlaceType.HOME, encryptedCoordinates = "", radiusMeters = 100f, confidence = 1f, createdAt = day)
        val context = ContextEventEntity(id = 3, type = UserContextType.HOME, startedAt = day + 8 * 60 * 60_000L, endedAt = day + 9 * 60 * 60_000L, confidence = 1f, placeId = place.id, source = ContextSource.MANUAL)
        val persisted = TimelineEventEntity(id = 4, timestamp = context.startedAt, actor = TimelineActor.USER, emoji = "🏠", text = "Chegou em casa", sourceType = TimelineSourceType.CONTEXT, sourceId = context.id)
        val activity = HoodieActivityEntity(id = 5, activity = HoodieActivity.GAMING, startedAt = context.startedAt + 5_000, endedAt = context.startedAt + 20_000, userContext = UserContextType.HOME)
        val repository = DiaryRepository(
            ContextDaoStub(listOf(context)), TimelineDaoStub(listOf(persisted)), ActivityDaoStub(listOf(activity)), PlaceDaoStub(listOf(place)),
            FixedClock(day + 10 * 60 * 60_000L, zone),
        )

        val diary = repository.loadDiary(date)

        assertEquals(60 * 60_000L, diary.summary.homeMs)
        assertEquals("Casa", diary.visits.single().placeName)
        assertEquals(listOf("Chegou em casa", "Foi jogar", "Saiu de casa"), diary.timeline.map { it.title })
        assertEquals("HOME", diary.map.nodes.single().type.name)
    }

    private class ContextDaoStub(private val rows: List<ContextEventEntity>) : ContextEventDao {
        override suspend fun overlapping(from: Long, to: Long) = rows.filter { it.startedAt < to && (it.endedAt == null || it.endedAt > from) }
        override suspend fun current(): ContextEventEntity? = error("unused")
        override fun observeCurrent(): Flow<ContextEventEntity?> = error("unused")
        override suspend fun getById(id: Long): ContextEventEntity? = error("unused")
        override suspend fun previous(): ContextEventEntity? = error("unused")
        override suspend fun insert(e: ContextEventEntity): Long = error("unused")
        override suspend fun update(e: ContextEventEntity) = error("unused")
        override suspend fun closeOpen(endedAt: Long) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
        override fun observeOverlapping(from: Long, to: Long): Flow<List<ContextEventEntity>> = error("unused")
        override suspend fun firstStartedAfter(after: Long): ContextEventEntity? = error("unused")
        override suspend fun all(): List<ContextEventEntity> = error("unused")
        override suspend fun count(): Int = error("unused")
    }

    private class TimelineDaoStub(private val rows: List<TimelineEventEntity>) : TimelineDao {
        override suspend fun range(from: Long, to: Long) = rows.filter { it.timestamp >= from && it.timestamp < to }
        override suspend fun insert(e: TimelineEventEntity): Long = error("unused")
        override suspend fun insertAll(e: List<TimelineEventEntity>) = error("unused")
        override fun observeRange(from: Long, to: Long): Flow<List<TimelineEventEntity>> = error("unused")
        override suspend fun bySource(type: TimelineSourceType, id: Long): List<TimelineEventEntity> = error("unused")
        override suspend fun deleteBySource(type: TimelineSourceType, id: Long): Int = error("unused")
        override suspend fun deleteSourceRange(type: TimelineSourceType, from: Long, to: Long): Int = error("unused")
        override suspend fun count(): Int = error("unused")
    }

    private class ActivityDaoStub(private val rows: List<HoodieActivityEntity>) : HoodieActivityDao {
        override suspend fun overlapping(from: Long, to: Long) = rows.filter { it.startedAt < to && it.endedAt > from }
        override suspend fun insertAll(items: List<HoodieActivityEntity>) = error("unused")
        override fun observeOverlapping(from: Long, to: Long): Flow<List<HoodieActivityEntity>> = error("unused")
        override suspend fun endedAt(at: Long): HoodieActivityEntity? = error("unused")
        override suspend fun deleteStartedFrom(from: Long): Int = error("unused")
        override suspend fun delete(id: Long) = error("unused")
        override suspend fun count(): Int = error("unused")
    }

    private class PlaceDaoStub(private val rows: List<PlaceEntity>) : PlaceDao {
        override suspend fun getAll() = rows
        override fun observeAll(): Flow<List<PlaceEntity>> = error("unused")
        override suspend fun getById(id: Long): PlaceEntity? = error("unused")
        override suspend fun insert(place: PlaceEntity): Long = error("unused")
        override suspend fun update(place: PlaceEntity) = error("unused")
        override suspend fun delete(id: Long) = error("unused")
        override suspend fun markVisited(id: Long, at: Long) = error("unused")
    }
}
