package com.hoodie.app.engine.interaction

/** Session-only interaction limits; use monotonic time, never the diary clock. */
data class HoodieInteractionConfig(
    val gestureIntervalMs: Long = 900,
    val speechIntervalMs: Long = 10_000,
    val historySize: Int = 3,
    val bubbleDurationMs: Long = 3_500,
    val maxPettingMs: Long = 3_000,
    val maxPendingMs: Long = 3_000,
    val gestureExitToleranceLogicalPx: Int = DEFAULT_EXIT_TOLERANCE_LOGICAL_PX,
) {
    init {
        require(gestureIntervalMs > 0 && speechIntervalMs >= gestureIntervalMs)
        require(historySize > 0 && bubbleDurationMs > 0 && maxPettingMs > 0 && maxPendingMs > 0)
        require(gestureExitToleranceLogicalPx in 0..16)
    }
    companion object { const val DEFAULT_EXIT_TOLERANCE_LOGICAL_PX = 4 }
}
