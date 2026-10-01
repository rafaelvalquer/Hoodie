package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.deviceusage.RawUsageEvent
import com.hoodie.app.core.deviceusage.RawUsageEventType
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.ScreenSession

data class ScreenUsage(
    val sessions: List<ScreenSession>,
    val unlockCount: Int,
    /** Sessões ou desbloqueios inferidos (sem eventos de tela/bloqueio no aparelho). */
    val isEstimated: Boolean,
)

/**
 * Reconstrói "tela ligada" a partir de SCREEN_INTERACTIVE → SCREEN_NON_INTERACTIVE.
 * Puro: recebe eventos (inclusive alguns de antes da meia-noite) e devolve
 * sessões recortadas em [from, end).
 */
object ScreenSessionBuilder {

    fun build(events: List<RawUsageEvent>, appSessions: List<AppSession>, from: Long, end: Long): ScreenUsage {
        if (end <= from) return ScreenUsage(emptyList(), 0, false)
        val sorted = events.sortedBy { it.timestamp }
        val hasScreenEvents = sorted.any { it.type == RawUsageEventType.SCREEN_INTERACTIVE || it.type == RawUsageEventType.SCREEN_NON_INTERACTIVE }
        if (!hasScreenEvents) {
            // API < 28 (ou fabricante que não registra): a tela "estava ligada" enquanto algum app estava na frente.
            val sessions = fromAppSessions(appSessions, from, end)
            return ScreenUsage(sessions, sessions.size, isEstimated = appSessions.isNotEmpty())
        }

        val raw = mutableListOf<ScreenSession>()
        var openAt: Long? = null
        var seenScreenEvent = false
        for (e in sorted) {
            when (e.type) {
                RawUsageEventType.SCREEN_INTERACTIVE -> {
                    if (openAt == null) openAt = e.timestamp
                    seenScreenEvent = true
                }
                RawUsageEventType.SCREEN_NON_INTERACTIVE, RawUsageEventType.DEVICE_SHUTDOWN -> {
                    val start = openAt ?: if (!seenScreenEvent && e.type == RawUsageEventType.SCREEN_NON_INTERACTIVE) from else null
                    if (start != null) raw += ScreenSession(start, e.timestamp)
                    openAt = null
                    seenScreenEvent = true
                }
                // Religou: qualquer sessão "aberta" de antes do desligamento é lixo.
                RawUsageEventType.DEVICE_STARTUP -> openAt = null
                else -> Unit
            }
        }
        openAt?.let { raw += ScreenSession(it, end) }
        val sessions = raw.mapNotNull { clip(it, from, end) }

        val keyguardEvents = sorted.any { it.type == RawUsageEventType.KEYGUARD_HIDDEN || it.type == RawUsageEventType.KEYGUARD_SHOWN }
        return if (keyguardEvents) {
            val unlocks = sorted.count { it.type == RawUsageEventType.KEYGUARD_HIDDEN && it.timestamp in from until end }
            ScreenUsage(sessions, unlocks, isEstimated = false)
        } else {
            // Sem tela de bloqueio (ou sem eventos dela): cada vez que a tela acendeu conta como um "desbloqueio".
            val unlocks = sorted.count { it.type == RawUsageEventType.SCREEN_INTERACTIVE && it.timestamp in from until end }
            ScreenUsage(sessions, unlocks, isEstimated = unlocks > 0)
        }
    }

    /** Une sessões de app próximas (< 30 s) numa sessão de tela. */
    internal fun fromAppSessions(appSessions: List<AppSession>, from: Long, end: Long): List<ScreenSession> {
        val merged = mutableListOf<ScreenSession>()
        appSessions.sortedBy { it.startedAt }.forEach { s ->
            val last = merged.lastOrNull()
            if (last != null && s.startedAt - last.endedAt <= HoodieConfig.SCREEN_SESSION_FALLBACK_GAP_MS) {
                merged[merged.lastIndex] = last.copy(endedAt = maxOf(last.endedAt, s.endedAt))
            } else {
                merged += ScreenSession(s.startedAt, s.endedAt)
            }
        }
        return merged.mapNotNull { clip(it, from, end) }
    }

    private fun clip(s: ScreenSession, from: Long, end: Long): ScreenSession? {
        val start = maxOf(s.startedAt, from)
        val stop = minOf(s.endedAt, end)
        return if (stop > start) ScreenSession(start, stop) else null
    }
}
