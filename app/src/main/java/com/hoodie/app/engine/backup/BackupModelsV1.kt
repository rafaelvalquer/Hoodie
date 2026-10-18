package com.hoodie.app.engine.backup

/** Frozen logical V1 DTOs. Adapt explicitly when the internal Room schema changes. */
sealed interface BackupRecordV1 { fun json(): org.json.JSONObject }

data class BackupPlaceV1(
    val id: Long,
    val name: String,
    val type: String,
    val radiusMeters: Double,
    val confidence: Double,
    val isFavorite: Long,
    val confirmationCount: Long,
    val createdAt: Long,
    val lastVisitedAt: Long?,
    val latitude: Double,
    val longitude: Double,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("name", name)
        put("type", type)
        put("radiusMeters", radiusMeters)
        put("confidence", confidence)
        put("isFavorite", isFavorite)
        put("confirmationCount", confirmationCount)
        put("createdAt", createdAt)
        put("lastVisitedAt", lastVisitedAt ?: org.json.JSONObject.NULL)
        put("latitude", latitude)
        put("longitude", longitude)
    }
}

data class BackupRoutineV1(
    val id: Long,
    val workMode: String,
    val daysMask: Long,
    val startMinute: Long,
    val endMinute: Long,
    val lunchStartMinute: Long,
    val lunchEndMinute: Long,
    val updatedAt: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("workMode", workMode)
        put("daysMask", daysMask)
        put("startMinute", startMinute)
        put("endMinute", endMinute)
        put("lunchStartMinute", lunchStartMinute)
        put("lunchEndMinute", lunchEndMinute)
        put("updatedAt", updatedAt)
    }
}

data class BackupDayExceptionsV1(
    val epochDay: Long,
    val kind: String,
    val createdAt: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("epochDay", epochDay)
        put("kind", kind)
        put("createdAt", createdAt)
    }
}

data class BackupLocationEventsV1(
    val id: Long,
    val placeId: Long,
    val transition: String,
    val timestamp: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("placeId", placeId)
        put("transition", transition)
        put("timestamp", timestamp)
    }
}

data class BackupContextEventV1(
    val id: Long,
    val type: String,
    val startedAt: Long,
    val endedAt: Long?,
    val confidence: Double,
    val placeId: Long?,
    val source: String,
    val venueType: String?,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("type", type)
        put("startedAt", startedAt)
        put("endedAt", endedAt ?: org.json.JSONObject.NULL)
        put("confidence", confidence)
        put("placeId", placeId ?: org.json.JSONObject.NULL)
        put("source", source)
        put("venueType", venueType ?: org.json.JSONObject.NULL)
    }
}

data class BackupContextConfirmationsV1(
    val id: Long,
    val type: String,
    val placeId: Long?,
    val dayOfWeek: Long,
    val minuteOfDay: Long,
    val accepted: Long,
    val timestamp: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("type", type)
        put("placeId", placeId ?: org.json.JSONObject.NULL)
        put("dayOfWeek", dayOfWeek)
        put("minuteOfDay", minuteOfDay)
        put("accepted", accepted)
        put("timestamp", timestamp)
    }
}

data class BackupHoodieActivitiesV1(
    val id: Long,
    val activity: String,
    val startedAt: Long,
    val endedAt: Long,
    val userContext: String,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("activity", activity)
        put("startedAt", startedAt)
        put("endedAt", endedAt)
        put("userContext", userContext)
    }
}

data class BackupTimelineEventsV1(
    val id: Long,
    val timestamp: Long,
    val actor: String,
    val emoji: String,
    val text: String,
    val sourceType: String?,
    val sourceId: Long?,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("timestamp", timestamp)
        put("actor", actor)
        put("emoji", emoji)
        put("text", text)
        put("sourceType", sourceType ?: org.json.JSONObject.NULL)
        put("sourceId", sourceId ?: org.json.JSONObject.NULL)
    }
}

data class BackupMemoryV1(
    val key: String,
    val emoji: String,
    val title: String,
    val unlockedAt: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("key", key)
        put("emoji", emoji)
        put("title", title)
        put("unlockedAt", unlockedAt)
    }
}

data class BackupDailyDeviceUsageV1(
    val date: String,
    val screenTimeMs: Long,
    val sessionCount: Long,
    val unlockCount: Long,
    val firstUseAt: Long?,
    val lastUseAt: Long?,
    val longestSessionMs: Long,
    val isEstimated: Long,
    val appCount: Long,
    val updatedAt: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("date", date)
        put("screenTimeMs", screenTimeMs)
        put("sessionCount", sessionCount)
        put("unlockCount", unlockCount)
        put("firstUseAt", firstUseAt ?: org.json.JSONObject.NULL)
        put("lastUseAt", lastUseAt ?: org.json.JSONObject.NULL)
        put("longestSessionMs", longestSessionMs)
        put("isEstimated", isEstimated)
        put("appCount", appCount)
        put("updatedAt", updatedAt)
    }
}

data class BackupDailyAppUsageV1(
    val date: String,
    val packageName: String,
    val appLabel: String,
    val appCategory: String,
    val foregroundMs: Long,
    val sessionCount: Long,
    val firstUsedAt: Long?,
    val lastUsedAt: Long?,
    val updatedAt: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("date", date)
        put("packageName", packageName)
        put("appLabel", appLabel)
        put("appCategory", appCategory)
        put("foregroundMs", foregroundMs)
        put("sessionCount", sessionCount)
        put("firstUsedAt", firstUsedAt ?: org.json.JSONObject.NULL)
        put("lastUsedAt", lastUsedAt ?: org.json.JSONObject.NULL)
        put("updatedAt", updatedAt)
    }
}

data class BackupDailyContextAppUsageV1(
    val date: String,
    val context: String,
    val packageName: String,
    val appLabel: String,
    val foregroundMs: Long,
    val sessionCount: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("date", date)
        put("context", context)
        put("packageName", packageName)
        put("appLabel", appLabel)
        put("foregroundMs", foregroundMs)
        put("sessionCount", sessionCount)
    }
}

data class BackupAppCategoryOverridesV1(
    val packageName: String,
    val category: String,
    val updatedAt: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("packageName", packageName)
        put("category", category)
        put("updatedAt", updatedAt)
    }
}

data class BackupPhoneAppSessionsV1(
    val id: String,
    val epochDay: Long,
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("epochDay", epochDay)
        put("packageName", packageName)
        put("startedAt", startedAt)
        put("endedAt", endedAt)
    }
}

data class BackupDailyScreenHourlyV1(
    val date: String,
    val hour: Long,
    val screenMs: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("date", date)
        put("hour", hour)
        put("screenMs", screenMs)
    }
}

data class BackupDailyContextUsageV1(
    val date: String,
    val context: String,
    val foregroundMs: Long,
    val sessionCount: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("date", date)
        put("context", context)
        put("foregroundMs", foregroundMs)
        put("sessionCount", sessionCount)
    }
}

data class BackupDailyPhoneTimelineV1(
    val id: String,
    val date: String,
    val packageName: String,
    val appLabel: String,
    val category: String,
    val startedAt: Long,
    val endedAt: Long,
    val context: String?,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("date", date)
        put("packageName", packageName)
        put("appLabel", appLabel)
        put("category", category)
        put("startedAt", startedAt)
        put("endedAt", endedAt)
        put("context", context ?: org.json.JSONObject.NULL)
    }
}

data class BackupMobilitySessionV1(
    val id: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val originPlaceId: Long?,
    val destinationPlaceId: Long?,
    val initialMode: String,
    val currentMode: String,
    val state: String,
    val confidence: Double,
    val confirmed: Long,
    val source: String,
    val leftOrigin: Long,
    val arrivalConfirmed: Long?,
    val arrivalAutoConfirmed: Long,
    val movingSince: Long?,
    val stillSince: Long?,
    val pendingModeQuestion: Long,
    val questionsAsked: Long,
    val lastObservedMovement: String?,
    val lastObservationAt: Long?,
    val lastVehicleAt: Long?,
    val vehicleExitAt: Long?,
    val pendingMovementMode: String?,
    val pendingMovementAt: Long?,
    val speedSampleAttempts: Long,
    val lastSpeedSampleAt: Long?,
    val speedSampleCount: Long,
    val meanSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val speedVariation: Double,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("startedAt", startedAt)
        put("endedAt", endedAt ?: org.json.JSONObject.NULL)
        put("originPlaceId", originPlaceId ?: org.json.JSONObject.NULL)
        put("destinationPlaceId", destinationPlaceId ?: org.json.JSONObject.NULL)
        put("initialMode", initialMode)
        put("currentMode", currentMode)
        put("state", state)
        put("confidence", confidence)
        put("confirmed", confirmed)
        put("source", source)
        put("leftOrigin", leftOrigin)
        put("arrivalConfirmed", arrivalConfirmed ?: org.json.JSONObject.NULL)
        put("arrivalAutoConfirmed", arrivalAutoConfirmed)
        put("movingSince", movingSince ?: org.json.JSONObject.NULL)
        put("stillSince", stillSince ?: org.json.JSONObject.NULL)
        put("pendingModeQuestion", pendingModeQuestion)
        put("questionsAsked", questionsAsked)
        put("lastObservedMovement", lastObservedMovement ?: org.json.JSONObject.NULL)
        put("lastObservationAt", lastObservationAt ?: org.json.JSONObject.NULL)
        put("lastVehicleAt", lastVehicleAt ?: org.json.JSONObject.NULL)
        put("vehicleExitAt", vehicleExitAt ?: org.json.JSONObject.NULL)
        put("pendingMovementMode", pendingMovementMode ?: org.json.JSONObject.NULL)
        put("pendingMovementAt", pendingMovementAt ?: org.json.JSONObject.NULL)
        put("speedSampleAttempts", speedSampleAttempts)
        put("lastSpeedSampleAt", lastSpeedSampleAt ?: org.json.JSONObject.NULL)
        put("speedSampleCount", speedSampleCount)
        put("meanSpeedKmh", meanSpeedKmh ?: org.json.JSONObject.NULL)
        put("maxSpeedKmh", maxSpeedKmh ?: org.json.JSONObject.NULL)
        put("speedVariation", speedVariation)
    }
}

data class BackupMobilitySegmentsV1(
    val id: Long,
    val sessionId: Long,
    val mode: String,
    val startedAt: Long,
    val endedAt: Long?,
    val confidence: Double,
    val confirmed: Long,
    val source: String,
    val evidenceId: Long?,
    val confirmedAt: Long?,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("sessionId", sessionId)
        put("mode", mode)
        put("startedAt", startedAt)
        put("endedAt", endedAt ?: org.json.JSONObject.NULL)
        put("confidence", confidence)
        put("confirmed", confirmed)
        put("source", source)
        put("evidenceId", evidenceId ?: org.json.JSONObject.NULL)
        put("confirmedAt", confirmedAt ?: org.json.JSONObject.NULL)
    }
}

data class BackupDiaryCorrectionsV1(
    val id: Long,
    val targetType: String,
    val targetId: Long,
    val originalContext: String?,
    val correctedContext: String?,
    val originalPlaceId: Long?,
    val correctedPlaceId: Long?,
    val originalStartAt: Long?,
    val correctedStartAt: Long?,
    val originalEndAt: Long?,
    val correctedEndAt: Long?,
    val originalMode: String?,
    val correctedMode: String?,
    val originalSource: String,
    val originalConfidence: Double,
    val createdAt: Long,
    val originalVenueType: String?,
    val correctedVenueType: String?,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("targetType", targetType)
        put("targetId", targetId)
        put("originalContext", originalContext ?: org.json.JSONObject.NULL)
        put("correctedContext", correctedContext ?: org.json.JSONObject.NULL)
        put("originalPlaceId", originalPlaceId ?: org.json.JSONObject.NULL)
        put("correctedPlaceId", correctedPlaceId ?: org.json.JSONObject.NULL)
        put("originalStartAt", originalStartAt ?: org.json.JSONObject.NULL)
        put("correctedStartAt", correctedStartAt ?: org.json.JSONObject.NULL)
        put("originalEndAt", originalEndAt ?: org.json.JSONObject.NULL)
        put("correctedEndAt", correctedEndAt ?: org.json.JSONObject.NULL)
        put("originalMode", originalMode ?: org.json.JSONObject.NULL)
        put("correctedMode", correctedMode ?: org.json.JSONObject.NULL)
        put("originalSource", originalSource)
        put("originalConfidence", originalConfidence)
        put("createdAt", createdAt)
        put("originalVenueType", originalVenueType ?: org.json.JSONObject.NULL)
        put("correctedVenueType", correctedVenueType ?: org.json.JSONObject.NULL)
    }
}

data class BackupLearnedRoutineSlotsV1(
    val id: Long,
    val dayGroup: String,
    val type: String,
    val medianMinute: Long,
    val deviationMinutes: Long,
    val sampleCount: Long,
    val confidence: Double,
    val updatedAt: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("dayGroup", dayGroup)
        put("type", type)
        put("medianMinute", medianMinute)
        put("deviationMinutes", deviationMinutes)
        put("sampleCount", sampleCount)
        put("confidence", confidence)
        put("updatedAt", updatedAt)
    }
}

data class BackupTransportPatternsV1(
    val originPlaceId: Long,
    val destinationPlaceId: Long,
    val dayGroup: String,
    val timeBucket: Long,
    val mode: String,
    val confirmations: Long,
    val rejections: Long,
    val confidence: Double,
    val updatedAt: Long,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("originPlaceId", originPlaceId)
        put("destinationPlaceId", destinationPlaceId)
        put("dayGroup", dayGroup)
        put("timeBucket", timeBucket)
        put("mode", mode)
        put("confirmations", confirmations)
        put("rejections", rejections)
        put("confidence", confidence)
        put("updatedAt", updatedAt)
    }
}

data class BackupTransportEvidenceV1(
    val id: Long,
    val sessionId: Long,
    val startedAt: Long,
    val endedAt: Long?,
    val attempts: Long,
    val lastAttemptAt: Long?,
    val lastWorkRequestId: String?,
    val consumedWorkRequestIds: String,
    val reservationId: String?,
    val sampleCount: Long,
    val meanSpeedKmh: Double?,
    val maxSpeedKmh: Double?,
    val speedVariation: Double,
    val stopStart: Long?,
    val stopCount: Long,
    val stoppedMs: Long,
    val stopSquareSum: Double,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("sessionId", sessionId)
        put("startedAt", startedAt)
        put("endedAt", endedAt ?: org.json.JSONObject.NULL)
        put("attempts", attempts)
        put("lastAttemptAt", lastAttemptAt ?: org.json.JSONObject.NULL)
        put("lastWorkRequestId", lastWorkRequestId ?: org.json.JSONObject.NULL)
        put("consumedWorkRequestIds", consumedWorkRequestIds)
        put("reservationId", reservationId ?: org.json.JSONObject.NULL)
        put("sampleCount", sampleCount)
        put("meanSpeedKmh", meanSpeedKmh ?: org.json.JSONObject.NULL)
        put("maxSpeedKmh", maxSpeedKmh ?: org.json.JSONObject.NULL)
        put("speedVariation", speedVariation)
        put("stopStart", stopStart ?: org.json.JSONObject.NULL)
        put("stopCount", stopCount)
        put("stoppedMs", stoppedMs)
        put("stopSquareSum", stopSquareSum)
    }
}

data class BackupEffectiveRoutineVersionsV1(
    val id: Long,
    val epochDay: Long,
    val effectiveFrom: Long,
    val inputKey: String,
    val snapshot: String,
) : BackupRecordV1 {
    override fun json() = org.json.JSONObject().apply {
        put("id", id)
        put("epochDay", epochDay)
        put("effectiveFrom", effectiveFrom)
        put("inputKey", inputKey)
        put("snapshot", snapshot)
    }
}
