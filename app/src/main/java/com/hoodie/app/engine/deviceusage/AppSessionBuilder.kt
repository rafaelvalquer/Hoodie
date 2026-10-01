package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.deviceusage.RawUsageEvent
import com.hoodie.app.core.deviceusage.RawUsageEventType
import com.hoodie.app.domain.phoneinsights.model.AppSession

/**
 * Reconstrói sessões por app: um app entra em foreground, fica até ir para o
 * background, outro app assumir a frente ou a tela apagar.
 */
object AppSessionBuilder {

    fun build(events: List<RawUsageEvent>, from: Long, end: Long): List<AppSession> {
        if (end <= from) return emptyList()
        val raw = mutableListOf<AppSession>()
        var current: String? = null
        var startedAt = 0L

        fun close(at: Long) {
            val pkg = current ?: return
            if (at > startedAt) raw += AppSession(pkg, startedAt, at)
            current = null
        }

        for (e in events.sortedBy { it.timestamp }) {
            when (e.type) {
                RawUsageEventType.APP_FOREGROUND -> {
                    val pkg = e.packageName ?: continue
                    if (pkg == current) continue // outra Activity do mesmo app
                    close(e.timestamp)
                    current = pkg
                    startedAt = e.timestamp
                }
                RawUsageEventType.APP_BACKGROUND -> if (e.packageName == current) close(e.timestamp)
                RawUsageEventType.SCREEN_NON_INTERACTIVE, RawUsageEventType.DEVICE_SHUTDOWN -> close(e.timestamp)
                RawUsageEventType.DEVICE_STARTUP -> current = null
                else -> Unit
            }
        }
        close(end)

        return merge(raw).mapNotNull { s ->
            val start = maxOf(s.startedAt, from)
            val stop = minOf(s.endedAt, end)
            if (stop > start) AppSession(s.packageName, start, stop) else null
        }
    }

    /** PAUSED → RESUMED do mesmo app em poucos segundos é navegação interna, não uma sessão nova. */
    private fun merge(sessions: List<AppSession>): List<AppSession> {
        val out = mutableListOf<AppSession>()
        sessions.forEach { s ->
            val last = out.lastOrNull()
            if (last != null && last.packageName == s.packageName && s.startedAt - last.endedAt <= HoodieConfig.APP_SESSION_MERGE_GAP_MS) {
                out[out.lastIndex] = last.copy(endedAt = maxOf(last.endedAt, s.endedAt))
            } else {
                out += s
            }
        }
        return out
    }
}
