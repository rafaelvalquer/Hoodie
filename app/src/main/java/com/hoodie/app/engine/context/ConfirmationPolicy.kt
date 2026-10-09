package com.hoodie.app.engine.context

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.core.time.atZone
import java.time.ZoneId

/** Uma pergunta já feita, só com o necessário para o cooldown. */
data class AskedQuestion(val kind: QuestionKind, val candidate: UserContextType?, val askedAt: Long)

/**
 * Evita spam: no máximo [MAX_PER_DAY] perguntas por dia e nunca repetir o mesmo
 * contexto em menos de [SAME_CONTEXT_COOLDOWN_MIN] minutos.
 */
object ConfirmationPolicy {
    const val MAX_PER_DAY = HoodieConfig.MAX_QUESTIONS_PER_DAY
    const val SAME_CONTEXT_COOLDOWN_MIN = HoodieConfig.QUESTION_COOLDOWN_MINUTES.toLong()

    fun canAsk(
        now: Long,
        zone: ZoneId,
        kind: QuestionKind,
        candidate: UserContextType?,
        recent: List<AskedQuestion>,
    ): Boolean {
        if (HoodieConfig.PASSIVE_CONTEXT_CONFIRMATION && kind == QuestionKind.CONFIRM_CONTEXT) return false
        val today = now.atZone(zone).toLocalDate()
        val askedToday = recent.count {
            it.askedAt.atZone(zone).toLocalDate() == today &&
                !(HoodieConfig.PASSIVE_CONTEXT_CONFIRMATION && it.kind == QuestionKind.CONFIRM_CONTEXT)
        }
        if (askedToday >= MAX_PER_DAY) return false
        val cooldownStart = now - SAME_CONTEXT_COOLDOWN_MIN * MINUTE_MS
        return recent.none { it.kind == kind && it.candidate == candidate && it.askedAt >= cooldownStart }
    }
}
