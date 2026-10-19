package com.hoodie.app.data.repository

import androidx.room.withTransaction
import com.hoodie.app.core.database.*
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.model.*
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.domain.routine.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class EffectiveRoutineRepository @Inject constructor(
    private val db: HoodieDatabase,
    private val manual: RoutineRepository,
    private val settings: SettingsRepository,
    private val clock: ClockProvider,
) {
    private val lock = Mutex()
    private val dao get() = db.effectiveRoutineDao()
    val current: Flow<EffectiveRoutineSnapshot> = combine(manual.routine, settings.settings,
        db.intelligenceDao().observeRoutineSlots(), dao.observe(), com.hoodie.app.core.time.currentDateFlow(clock)) { _, _, _, _, _ -> Unit }
        .map { currentSnapshot() }.distinctUntilChanged()

    /** Historical calls never materialize today's learned patterns retroactively. */
    suspend fun currentSnapshot(): EffectiveRoutineSnapshot = resolveInternal(clock.today(), clock.nowMillis(), true)
    suspend fun resolve(date: LocalDate, asOf: Long): EffectiveRoutineSnapshot = resolveInternal(date, asOf, false)
    private suspend fun resolveInternal(date: LocalDate, asOf: Long, activateCurrent: Boolean): EffectiveRoutineSnapshot = lock.withLock {
        db.withTransaction {
            val now = clock.nowMillis()
            val today = clock.now().toLocalDate()
            val r = manual.get()
            val s = settings.current()
            val modes = s.automaticRoutineEvents.mapNotNull { runCatching { RoutineEventType.valueOf(it) }.getOrNull() }.toSet()
            val key = "$r|${s.sleep}|${modes.sortedBy { it.name }}"
            var baseline = dao.at(BASELINE_DAY, Long.MAX_VALUE)
            if (baseline == null) {
                val snapshot = EffectiveRoutineResolver.resolve(today, now, r, s.sleep, emptySet(), emptyList())
                baseline = EffectiveRoutineVersionEntity(epochDay = BASELINE_DAY, effectiveFrom = Long.MIN_VALUE,
                    inputKey = key, snapshot = encode(snapshot))
                dao.insert(baseline)
            }
            var version = dao.at(date.toEpochDay(), asOf)
            if (date == today && activateCurrent) {
                val existing = dao.at(date.toEpochDay(), now)
                if (existing == null || existing.inputKey != key) {
                    val dayStart = date.atStartOfDay(clock.zone()).toInstant().toEpochMilli()
                    val cutoff = if (existing == null) dayStart else now
                    // Learning later in this day must wait until tomorrow, including after a manual edit.
                    val patterns = patterns().filter { it.updatedAt <= dayStart }
                    var snapshot = EffectiveRoutineResolver.resolve(date, cutoff, r, s.sleep, modes, patterns)
                    if (existing != null) {
                        val frozen = decode(existing.snapshot)
                        val frozenPatterns = frozen.values.filter { it.value.origin == RoutineValueOrigin.LEARNED }
                            .map { (type, value) -> RoutinePattern(if (value.sampleCount >= 4) date.dayOfWeek.name else routineDayGroup(date.dayOfWeek), type,
                                value.learnedMinute ?: value.minute, value.confidence ?: 0f, value.sampleCount, dayStart,
                                value.deviationMinutes ?: 0) }
                        snapshot = EffectiveRoutineResolver.resolve(date, now, r, s.sleep, modes, frozenPatterns + patterns)
                    }
                    val from = if (existing == null && dayStart >= decode(requireNotNull(baseline).snapshot).effectiveFrom) dayStart else now
                    version = EffectiveRoutineVersionEntity(epochDay = date.toEpochDay(), effectiveFrom = from,
                        inputKey = key, snapshot = encode(snapshot.copy(effectiveFrom = from)))
                    dao.insert(requireNotNull(version))
                    prepareTomorrow(r, s.sleep, modes, key)
                } else version = existing
            }
            val result = if (version != null) decode(requireNotNull(version).snapshot) else {
                val reference = dao.all().lastOrNull { it.epochDay != BASELINE_DAY && it.effectiveFrom <= asOf }
                    ?.let { decode(it.snapshot) } ?: decode(requireNotNull(baseline).snapshot)
                EffectiveRoutineResolver.resolve(date, asOf, reference.manualRoutine, reference.manualSleep, emptySet(), emptyList())
            }
            result.copy(date = date, dayOff = manual.isDayOff(date))
        }
    }

    /** Freeze today before replacing learned slots; publish learning only for the next local date. */
    suspend fun beforeLearning() { currentSnapshot() }
    suspend fun afterLearning() = lock.withLock {
        val r = manual.get(); val s = settings.current()
        val modes = s.automaticRoutineEvents.mapNotNull { runCatching { RoutineEventType.valueOf(it) }.getOrNull() }.toSet()
        prepareTomorrow(r, s.sleep, modes, "$r|${s.sleep}|${modes.sortedBy { it.name }}")
    }
    suspend fun manualChanged() {
        currentSnapshot()
        afterLearning()
    }
    suspend fun saveManual(routine: Routine, sleep: SleepSchedule, automatic: Set<String>) {
        beforeLearning()
        lock.withLock {
            db.withTransaction {
                manual.save(routine, clock.nowMillis())
                settings.setSleep(sleep)
                settings.setAutomaticRoutineEvents(automatic)
            }
        }
        manualChanged()
    }

    private suspend fun patterns() = db.intelligenceDao().routineSlots().mapNotNull { row ->
        runCatching { RoutinePattern(row.dayGroup, RoutineEventType.valueOf(row.type), row.medianMinute,
            row.confidence, row.sampleCount, row.updatedAt, row.deviationMinutes) }.getOrNull()
    }
    private suspend fun prepareTomorrow(r: Routine, sleep: SleepSchedule, modes: Set<RoutineEventType>, key: String) {
        val date = clock.now().toLocalDate().plusDays(1)
        val from = date.atStartOfDay(clock.zone()).toInstant().toEpochMilli()
        val resolved = EffectiveRoutineResolver.resolve(date, from, r, sleep, modes, patterns())
        val snapshot = encode(resolved)
        db.withTransaction<Unit> {
            // REPLACE with an autogenerated ID deletes even an identical forecast,
            // changing IDs restored from backup and invalidating observers.
            val previous = dao.at(date.toEpochDay(), from)?.takeIf { it.effectiveFrom == from }
            val next = EffectiveRoutineVersionEntity(id = previous?.id ?: 0,
                epochDay = date.toEpochDay(), effectiveFrom = from, inputKey = key, snapshot = snapshot)
            if (previous == null) dao.insert(next)
            else if (previous != next) dao.update(next)
        }
    }

    /** Preload the same versions for a pure catch-up simulation; midnight is also a schedule boundary. */
    suspend fun history(from: Long, to: Long): List<EffectiveRoutineSnapshot> {
        currentSnapshot()
        val rows = dao.all()
        val baseline = decode(rows.first { it.epochDay == BASELINE_DAY }.snapshot)
        val firstDate = java.time.Instant.ofEpochMilli(from).atZone(clock.zone()).toLocalDate()
        val lastDate = java.time.Instant.ofEpochMilli(to).atZone(clock.zone()).toLocalDate()
        val result = mutableListOf<EffectiveRoutineSnapshot>()
        var date = firstDate
        while (date <= lastDate) {
            val start = date.atStartOfDay(clock.zone()).toInstant().toEpochMilli()
            val off = manual.isDayOff(date)
            val reference = rows.lastOrNull { it.epochDay != BASELINE_DAY && it.effectiveFrom <= start }
                ?.let { decode(it.snapshot) } ?: baseline
            result += EffectiveRoutineResolver.resolve(date, start, reference.manualRoutine, reference.manualSleep, emptySet(), emptyList(), off)
            result += rows.filter { it.epochDay == date.toEpochDay() && it.effectiveFrom <= to }.map { decode(it.snapshot).copy(dayOff = off) }
            date = date.plusDays(1)
        }
        return result.sortedBy { it.effectiveFrom }
    }

    private fun encode(s: EffectiveRoutineSnapshot): String = JSONObject().apply {
        put("date", s.date.toString()); put("from", s.effectiveFrom); put("workMode", s.routine.workMode.name)
        put("days", JSONArray(s.routine.days.map { it.name }))
        put("manualMinutes", JSONArray(listOf(s.manualRoutine.startMinute, s.manualRoutine.endMinute,
            s.manualRoutine.lunchStartMinute, s.manualRoutine.lunchEndMinute, s.manualSleep.wakeMinute, s.manualSleep.sleepMinute)))
        put("values", JSONObject().apply { s.values.forEach { (type, value) -> put(type.name, JSONObject().apply {
            put("minute", value.minute); put("origin", value.origin.name); put("confidence", value.confidence)
            put("count", value.sampleCount); put("reason", value.fallbackReason)
            put("learnedMinute", value.learnedMinute ?: JSONObject.NULL)
            put("deviation", value.deviationMinutes ?: JSONObject.NULL)
            put("shiftLimited", value.shiftLimited)
        }) } })
    }.toString()
    private fun decode(text: String): EffectiveRoutineSnapshot {
        val o = JSONObject(text); val v = o.getJSONObject("values")
        val values = v.keys().asSequence().associate { name ->
            val value = v.getJSONObject(name)
            RoutineEventType.valueOf(name) to ResolvedRoutineValue(value.getInt("minute"), RoutineValueOrigin.valueOf(value.getString("origin")),
                if (value.has("confidence")) value.getDouble("confidence").toFloat() else null,
                value.getInt("count"), value.optString("reason").takeIf { it.isNotEmpty() },
                if (value.has("learnedMinute") && !value.isNull("learnedMinute")) value.getInt("learnedMinute") else null,
                if (value.has("deviation") && !value.isNull("deviation")) value.getInt("deviation") else null,
                value.optBoolean("shiftLimited", false))
        }
        fun minute(type: RoutineEventType) = values.getValue(type).minute
        val days = o.getJSONArray("days")
        val reference = o.getJSONArray("manualMinutes")
        val manualRoutine = Routine(workMode = WorkMode.valueOf(o.getString("workMode")), days = (0 until days.length()).map { DayOfWeek.valueOf(days.getString(it)) }.toSet(),
            startMinute = reference.getInt(0), endMinute = reference.getInt(1), lunchStartMinute = reference.getInt(2), lunchEndMinute = reference.getInt(3))
        return EffectiveRoutineSnapshot(LocalDate.parse(o.getString("date")), o.getLong("from"),
            Routine(workMode = WorkMode.valueOf(o.getString("workMode")), days = (0 until days.length()).map { DayOfWeek.valueOf(days.getString(it)) }.toSet(),
                startMinute = minute(RoutineEventType.WORK_START), endMinute = minute(RoutineEventType.WORK_END),
                lunchStartMinute = minute(RoutineEventType.LUNCH_START), lunchEndMinute = minute(RoutineEventType.LUNCH_END)),
            SleepSchedule(minute(RoutineEventType.WAKE), minute(RoutineEventType.SLEEP)), values, manualRoutine = manualRoutine, manualSleep = SleepSchedule(reference.getInt(4), reference.getInt(5)))
    }
    companion object { private const val BASELINE_DAY = Long.MIN_VALUE }
}
