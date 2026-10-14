package com.hoodie.app.engine.interaction

/** Owned by one visible Home session. No storage, database, or application singleton. */
class HoodieInteractionHistory(private val config: HoodieInteractionConfig = HoodieInteractionConfig()) {
    private val spoken = ArrayDeque<String>()
    var lastGestureAt: Long? = null
        private set
    var lastSpeechAt: Long? = null
        private set
    val recentPhrases: List<String> get() = spoken.toList()

    fun canSpeak(now: Long) = lastSpeechAt?.let { now - it >= config.speechIntervalMs } ?: true
    fun acceptGesture(now: Long) { lastGestureAt = now }
    /** Speech is committed on presentation, separately from acceptance of the gesture. */
    fun remember(response: InteractionResponse, now: Long) {
        response.text?.let {
            lastSpeechAt = now
            spoken.addLast(it)
            while (spoken.size > config.historySize) spoken.removeFirst()
        }
    }
    fun clear() {
        spoken.clear()
        lastGestureAt = null
        lastSpeechAt = null
    }
}
