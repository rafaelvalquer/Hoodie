package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.ContextAppUsage
import com.hoodie.app.domain.phoneinsights.model.ContextUsageSummary
import com.hoodie.app.engine.timeline.ContextSpan

/**
 * Cruza sessões de app com os contextos do dia: "quanto WhatsApp em Casa",
 * "quanto YouTube no Transporte". Uma sessão que atravessa uma troca de
 * contexto é dividida proporcionalmente; ela conta como sessão em cada
 * contexto que tocou.
 */
object ContextUsageCorrelator {

    fun correlate(
        sessions: List<AppSession>,
        contexts: List<ContextSpan>,
        labelOf: (String) -> String,
        end: Long,
        topAppsPerContext: Int = HoodieConfig.CONTEXT_TOP_APPS,
    ): List<ContextUsageSummary> {
        data class Acc(var ms: Long = 0, var sessions: Int = 0)
        val byContextApp = LinkedHashMap<Pair<UserContextType, String>, Acc>()
        val sessionsByContext = HashMap<UserContextType, Int>()
        val ordered = contexts.sortedBy { it.startedAt }

        sessions.forEach { s ->
            val touched = mutableSetOf<UserContextType>()
            AppSessionContextSplitter.split(listOf(s), ordered, end).forEach { piece ->
                val type = piece.context
                val overlap = piece.endedAt - piece.startedAt
                if (overlap > 0 && type != null) {
                    val acc = byContextApp.getOrPut(type to s.packageName) { Acc() }
                    acc.ms += overlap
                    if (type !in touched) {
                        acc.sessions++
                        touched += type
                    }
                }
            }
            touched.forEach { sessionsByContext[it] = (sessionsByContext[it] ?: 0) + 1 }
        }

        return byContextApp.entries
            .groupBy({ it.key.first }) { ContextAppUsage(it.key.first, it.key.second, labelOf(it.key.second), it.value.ms, it.value.sessions) }
            .map { (context, apps) ->
                ContextUsageSummary(
                    context = context,
                    foregroundMs = apps.sumOf { it.foregroundMs },
                    sessionCount = sessionsByContext[context] ?: 0,
                    apps = apps.sortedByDescending { it.foregroundMs }.take(topAppsPerContext),
                )
            }
            .sortedByDescending { it.foregroundMs }
    }
}
