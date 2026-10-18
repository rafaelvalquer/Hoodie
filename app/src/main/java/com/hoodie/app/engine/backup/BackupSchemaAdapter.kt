package com.hoodie.app.engine.backup

import org.json.JSONObject

/** V1 JSON properties map explicitly to today's Room columns. Future versions add adapters here. */
object BackupSchemaAdapter {
    fun validateRecord(schema: BackupDataset, data: JSONObject) {
        requireBackup(data.keys().asSequence().toSet() == schema.fields.map { it.name }.toSet())
        schema.fields.forEach { field ->
            val value = data.get(field.name)
            if (value == JSONObject.NULL) requireBackup(!field.required)
            else when (field.type) {
                BackupValueType.TEXT -> requireBackup(value is String && value.length <= 65536)
                BackupValueType.INTEGER -> {
                    requireBackup(value is Int || value is Long)
                    val number = (value as Number).toLong()
                    if (field.name.endsWith("At") || field.name.endsWith("Since") || field.name == "timestamp") requireBackup(number in 0..4133980800000L)
                    if (field.name == "effectiveFrom") requireBackup(number in 0..4133980800000L || schema.name == "effective_routine_versions" && number == Long.MIN_VALUE)
                    if (field.name == "epochDay") requireBackup(number in 0..47847 || schema.name == "effective_routine_versions" && number == Long.MIN_VALUE)
                    if (field.name in setOf("startMinute", "endMinute", "lunchStartMinute", "lunchEndMinute", "medianMinute", "minuteOfDay")) requireBackup(number in 0..1439)
                    if (field.name == "hour") requireBackup(number in 0..23)
                    if (field.name == "daysMask") requireBackup(number in 0..127)
                    if (field.name == "dayOfWeek") requireBackup(number in 1..7)
                    if (field.name.endsWith("Count") || field.name in setOf("confirmations", "rejections", "attempts", "questionsAsked", "speedSampleAttempts", "deviationMinutes", "timeBucket")) requireBackup(number in 0..Int.MAX_VALUE.toLong())
                    if (field.name.endsWith("Ms")) requireBackup(number in 0..31_622_400_000L)
                    if (field.name == "id" || field.name.endsWith("Id")) requireBackup(number in 1..9_007_199_254_740_991L)
                }
                BackupValueType.REAL -> {
                    requireBackup(value is Number && value.toDouble().isFinite())
                    val number = (value as Number).toDouble()
                    when (field.name) {
                        "confidence", "originalConfidence" -> requireBackup(number in 0.0..1.0)
                        "latitude" -> requireBackup(number in -90.0..90.0)
                        "longitude" -> requireBackup(number in -180.0..180.0)
                        "radiusMeters" -> requireBackup(number > 0 && number <= 100000)
                        "meanSpeedKmh", "maxSpeedKmh", "speedVariation" -> requireBackup(number in 0.0..1_000_000.0)
                        "stopSquareSum" -> requireBackup(number in 0.0..1.0e30)
                    }
                }
            }
        }
        if (data.has("date")) requireBackup(java.time.LocalDate.parse(data.getString("date")).toEpochDay() in 0..47847)
        BackupValueValidation.validate(schema.name, data)
        if (data.has("startedAt") && !data.isNull("endedAt") && data.has("endedAt"))
            requireBackup(data.getLong("endedAt") >= data.getLong("startedAt"))
        schema.primaryKeys.forEach { key ->
            val value = data.get(key)
            requireBackup(value != JSONObject.NULL && (value !is Number || value.toLong() > 0 || key != "id"))
            if (value is String) requireBackup(value.isNotBlank() && value.length <= 1024)
        }
    }

    /** All open historical intervals end at the snapshot cut, NEVER at the time of restoration. */
    fun reconcile(dataset: String, row: JSONObject, cutoff: Long): JSONObject {
        if (dataset in setOf("context_events", "mobility_sessions", "mobility_segments", "transport_evidence") && row.isNull("endedAt")) {
            requireBackup(row.getLong("startedAt") <= cutoff)
            row.put("endedAt", cutoff)
        }
        if (dataset == "mobility_sessions") {
            row.put("state", "ARRIVED").put("pendingModeQuestion", 0)
            listOf("movingSince", "stillSince", "pendingMovementMode", "pendingMovementAt").forEach { row.put(it, JSONObject.NULL) }
        }
        if (dataset == "transport_evidence") {
            listOf("reservationId", "lastWorkRequestId", "stopStart").forEach { row.put(it, JSONObject.NULL) }
            row.put("consumedWorkRequestIds", "")
        }
        return row
    }
}
