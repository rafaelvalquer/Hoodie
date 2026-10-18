package com.hoodie.app.engine.backup

import com.hoodie.app.core.model.*
import com.hoodie.app.core.mobility.*
import org.json.JSONObject

internal object BackupValueValidation {
    private inline fun <reified T : Enum<T>> oneOf(row: JSONObject, field: String) {
        if (row.has(field) && !row.isNull(field)) requireBackup(enumValues<T>().any { it.name == row.getString(field) })
    }
    fun validate(dataset: String, row: JSONObject) {
        when (dataset) {
            "places" -> oneOf<PlaceType>(row, "type")
            "routines" -> oneOf<WorkMode>(row, "workMode")
            "context_events" -> { oneOf<UserContextType>(row, "type"); oneOf<ContextSource>(row, "source"); oneOf<PlaceType>(row, "venueType") }
            "context_confirmations" -> oneOf<UserContextType>(row, "type")
            "hoodie_activities" -> { oneOf<HoodieActivity>(row, "activity"); oneOf<UserContextType>(row, "userContext") }
            "timeline_events" -> { oneOf<TimelineActor>(row, "actor"); oneOf<TimelineSourceType>(row, "sourceType") }
            "mobility_sessions" -> {
                oneOf<MovementMode>(row, "initialMode"); oneOf<MovementMode>(row, "currentMode")
                oneOf<MovementMode>(row, "pendingMovementMode"); oneOf<MobilityState>(row, "state")
                oneOf<MobilitySource>(row, "source"); oneOf<DetectedMovement>(row, "lastObservedMovement")
            }
            "mobility_segments" -> { oneOf<MovementMode>(row, "mode"); oneOf<MobilitySource>(row, "source") }
            "transport_patterns" -> oneOf<MovementMode>(row, "mode")
            "learned_routine_slots" -> oneOf<com.hoodie.app.domain.routine.RoutineEventType>(row, "type")
            "diary_corrections" -> {
                oneOf<UserContextType>(row, "originalContext"); oneOf<UserContextType>(row, "correctedContext")
                oneOf<PlaceType>(row, "originalVenueType"); oneOf<PlaceType>(row, "correctedVenueType")
                oneOf<MovementMode>(row, "originalMode"); oneOf<MovementMode>(row, "correctedMode")
            }
            "effective_routine_versions" -> validateRoutineSnapshot(row.getString("snapshot"))
        }
        setOf("isFavorite", "accepted", "isEstimated", "confirmed", "leftOrigin", "arrivalConfirmed", "arrivalAutoConfirmed", "pendingModeQuestion").forEach { name ->
            if (row.has(name) && !row.isNull(name)) requireBackup(row.getLong(name) in 0..1)
        }
    }
    private fun validateRoutineSnapshot(value: String) {
        val row = BackupJson.parse(value.toByteArray(Charsets.UTF_8))
        java.time.LocalDate.parse(row.getString("date"))
        requireBackup(row.getLong("from") in 0..4133980800000L)
        oneOf<WorkMode>(row, "workMode")
        val days = row.getJSONArray("days")
        requireBackup(days.length() <= 7)
        (0 until days.length()).forEach { java.time.DayOfWeek.valueOf(days.getString(it)) }
        val minutes = row.getJSONArray("manualMinutes")
        requireBackup(minutes.length() == 6)
        (0..5).forEach { requireBackup(minutes.getInt(it) in 0..1439) }
        val values = row.getJSONObject("values")
        val required = setOf("WAKE", "SLEEP", "WORK_START", "WORK_END", "LUNCH_START", "LUNCH_END")
        requireBackup(values.keys().asSequence().toSet().containsAll(required))
        values.keys().forEach { key ->
            com.hoodie.app.domain.routine.RoutineEventType.valueOf(key)
            val item = values.getJSONObject(key)
            requireBackup(item.getInt("minute") in 0..1439 && item.getInt("count") >= 0)
            oneOf<com.hoodie.app.domain.routine.RoutineValueOrigin>(item, "origin")
            if (item.has("confidence") && !item.isNull("confidence")) requireBackup(item.getDouble("confidence") in 0.0..1.0)
        }
    }
}
