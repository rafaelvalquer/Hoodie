package com.hoodie.app.core.mobility

/** Single-use database reservation for one bounded point speed lookup. */
data class SpeedSampleReservation(
    val sessionId: Long,
    val startedAt: Long,
    val reservedAt: Long,
    val sampleCountAtReservation: Int,
    val attemptNumber: Int,
    val evidenceId: Long = 0,
    val workRequestId: String = "",
)
