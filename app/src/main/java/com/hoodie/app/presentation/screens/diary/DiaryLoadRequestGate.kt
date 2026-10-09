package com.hoodie.app.presentation.screens.diary

import java.time.LocalDate

/** Rejects stale async results even if cancellation arrives after their work has completed. */
class DiaryLoadRequestGate(initialDate: LocalDate, initialRequestId: Long = 0) {
    private var sequence = initialRequestId
    var active: DiaryLoadKey = DiaryLoadKey(initialDate, initialRequestId)
        private set

    fun begin(date: LocalDate): DiaryLoadKey = DiaryLoadKey(date, ++sequence).also { active = it }

    fun isCurrent(key: DiaryLoadKey): Boolean = active == key
}
