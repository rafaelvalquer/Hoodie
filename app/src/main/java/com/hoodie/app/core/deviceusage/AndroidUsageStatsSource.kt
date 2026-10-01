package com.hoodie.app.core.deviceusage

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lê UsageEvents e descarta tudo que não é foreground/background de app,
 * tela ligada/desligada, bloqueio e liga/desliga. Os eventos brutos nunca são
 * persistidos: viram sessões e agregados por dia.
 */
@Singleton
class AndroidUsageStatsSource @Inject constructor(
    @ApplicationContext private val context: Context,
    private val access: UsageAccessManager,
) : UsageStatsSource {

    override suspend fun events(from: Long, to: Long): List<RawUsageEvent> = withContext(Dispatchers.IO) {
        if (to <= from || !access.isGranted()) return@withContext emptyList()
        val manager = context.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager ?: return@withContext emptyList()
        val result = ArrayList<RawUsageEvent>(512)
        val events = runCatching { manager.queryEvents(from, to) }.getOrNull() ?: return@withContext emptyList()
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val type = map(event.eventType) ?: continue
            val pkg = if (type == RawUsageEventType.APP_FOREGROUND || type == RawUsageEventType.APP_BACKGROUND) event.packageName else null
            if (pkg == null && (type == RawUsageEventType.APP_FOREGROUND || type == RawUsageEventType.APP_BACKGROUND)) continue
            result += RawUsageEvent(type, event.timeStamp, pkg)
        }
        result.sortBy { it.timestamp }
        result
    }

    /** Códigos numéricos para funcionar desde a API 26 (vários nomes só existem em versões novas). */
    private fun map(code: Int): RawUsageEventType? = when (code) {
        EVENT_ACTIVITY_RESUMED -> RawUsageEventType.APP_FOREGROUND
        EVENT_ACTIVITY_PAUSED -> RawUsageEventType.APP_BACKGROUND
        EVENT_ACTIVITY_STOPPED -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) RawUsageEventType.APP_BACKGROUND else null
        EVENT_SCREEN_INTERACTIVE -> RawUsageEventType.SCREEN_INTERACTIVE
        EVENT_SCREEN_NON_INTERACTIVE -> RawUsageEventType.SCREEN_NON_INTERACTIVE
        EVENT_KEYGUARD_SHOWN -> RawUsageEventType.KEYGUARD_SHOWN
        EVENT_KEYGUARD_HIDDEN -> RawUsageEventType.KEYGUARD_HIDDEN
        EVENT_DEVICE_SHUTDOWN -> RawUsageEventType.DEVICE_SHUTDOWN
        EVENT_DEVICE_STARTUP -> RawUsageEventType.DEVICE_STARTUP
        else -> null
    }

    private companion object {
        const val EVENT_ACTIVITY_RESUMED = 1 // MOVE_TO_FOREGROUND
        const val EVENT_ACTIVITY_PAUSED = 2 // MOVE_TO_BACKGROUND
        const val EVENT_SCREEN_INTERACTIVE = 15 // API 28
        const val EVENT_SCREEN_NON_INTERACTIVE = 16 // API 28
        const val EVENT_KEYGUARD_SHOWN = 17 // API 28
        const val EVENT_KEYGUARD_HIDDEN = 18 // API 28
        const val EVENT_ACTIVITY_STOPPED = 23 // API 29
        const val EVENT_DEVICE_SHUTDOWN = 26 // API 29
        const val EVENT_DEVICE_STARTUP = 27 // API 29
    }
}
