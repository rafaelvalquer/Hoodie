package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.engine.timeline.ContextSpan

data class ContextualAppSession(
    val packageName: String,
    val startedAt: Long,
    val endedAt: Long,
    val context: UserContextType?,
)

/** Recorta nos limites dos contextos. Lacunas ficam sem contexto; nunca são atribuídas pelo ponto médio. */
object AppSessionContextSplitter {
    fun split(sessions: List<AppSession>, contexts: List<ContextSpan>, end: Long): List<ContextualAppSession> {
        val ordered = contexts.sortedBy { it.startedAt }
        return sessions.sortedBy { it.startedAt }.flatMap { session ->
            val stop = minOf(session.endedAt, end)
            if (stop <= session.startedAt) return@flatMap emptyList()
            val cuts = (listOf(session.startedAt, stop) + ordered.flatMap {
                listOf(it.startedAt, it.endedAt ?: end)
            }.filter { it > session.startedAt && it < stop }).distinct().sorted()
            val pieces = mutableListOf<ContextualAppSession>()
            cuts.zipWithNext().forEach { (from, to) ->
                val context = ordered.lastOrNull { from >= it.startedAt && from < (it.endedAt ?: end) }?.type
                val previous = pieces.lastOrNull()
                if (previous != null && previous.context == context && previous.endedAt == from) {
                    pieces[pieces.lastIndex] = previous.copy(endedAt = to)
                } else pieces += ContextualAppSession(session.packageName, from, to, context)
            }
            pieces
        }
    }
}
