package com.hoodie.app.data.repository

import com.hoodie.app.core.database.DayExceptionDao
import com.hoodie.app.core.database.DayExceptionEntity
import com.hoodie.app.core.database.RoutineDao
import com.hoodie.app.core.database.RoutineEntity
import com.hoodie.app.core.model.Routine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutineRepository @Inject constructor(
    private val routineDao: RoutineDao,
    private val exceptionDao: DayExceptionDao,
) {
    val routine: Flow<Routine> = routineDao.observe().map { it?.toDomain() ?: Routine() }

    suspend fun get(): Routine = routineDao.get()?.toDomain() ?: Routine()

    suspend fun save(r: Routine, now: Long) = routineDao.upsert(
        RoutineEntity(
            workMode = r.workMode,
            daysMask = r.days.fold(0) { acc, d -> acc or (1 shl (d.value - 1)) },
            startMinute = r.startMinute, endMinute = r.endMinute,
            lunchStartMinute = r.lunchStartMinute, lunchEndMinute = r.lunchEndMinute,
            updatedAt = now,
        ),
    )

    suspend fun isDayOff(date: LocalDate): Boolean = exceptionDao.get(date.toEpochDay()) != null

    fun observeDayOff(date: LocalDate): Flow<Boolean> = exceptionDao.observe(date.toEpochDay()).map { it != null }

    suspend fun setDayOff(date: LocalDate, off: Boolean, now: Long) {
        if (off) exceptionDao.upsert(DayExceptionEntity(date.toEpochDay(), "DAY_OFF", now))
        else exceptionDao.delete(date.toEpochDay())
    }

    suspend fun daysOff(from: LocalDate, to: LocalDate): Set<LocalDate> =
        exceptionDao.range(from.toEpochDay(), to.toEpochDay()).map { LocalDate.ofEpochDay(it.epochDay) }.toSet()

    private fun RoutineEntity.toDomain() = Routine(
        workMode = workMode,
        days = DayOfWeek.entries.filter { daysMask and (1 shl (it.value - 1)) != 0 }.toSet(),
        startMinute = startMinute, endMinute = endMinute,
        lunchStartMinute = lunchStartMinute, lunchEndMinute = lunchEndMinute,
    )
}
