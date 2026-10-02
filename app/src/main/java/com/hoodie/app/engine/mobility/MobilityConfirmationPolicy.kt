package com.hoodie.app.engine.mobility

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.context.AskedQuestion
import com.hoodie.app.engine.context.ConfirmationPolicy
import java.time.ZoneId

/**
 * Perguntas de mobilidade com parcimônia:
 * - sobre o MOVIMENTO, no máximo [HoodieConfig.MOBILITY_MAX_QUESTIONS_PER_SESSION] por
 *   deslocamento; a escolha do meio de transporte pode ser uma segunda, só quando o veículo
 *   não foi classificado ([questionsAskedInSession] conta só essas duas);
 * - a CHEGADA tem sua própria pergunta (uma por deslocamento, e só até ser aprendida —
 *   ver [MobilityLearningEngine.arrivalAutoConfirm]); o padrão de trajeto idem;
 * - nunca com o veículo em movimento (segurança: a pergunta fica pendente até parar
 *   ou até o app ser aberto);
 * - e respeitando o limite diário e o cooldown do [ConfirmationPolicy].
 */
object MobilityConfirmationPolicy {

    enum class Verdict { ASK, DEFER, SKIP }

    fun evaluate(
        kind: QuestionKind,
        questionsAskedInSession: Int,
        vehicleMoving: Boolean,
        appInForeground: Boolean,
        now: Long,
        zone: ZoneId,
        candidate: UserContextType?,
        recent: List<AskedQuestion>,
    ): Verdict {
        val sessionLimit = when (kind) {
            QuestionKind.CONFIRM_MOVEMENT -> HoodieConfig.MOBILITY_MAX_QUESTIONS_PER_SESSION
            QuestionKind.SELECT_TRANSPORT_MODE -> HoodieConfig.MOBILITY_MAX_QUESTIONS_PER_SESSION + 1
            else -> Int.MAX_VALUE
        }
        if (questionsAskedInSession >= sessionLimit) return Verdict.SKIP
        // Nada de pergunta que peça toque enquanto alguém pode estar dirigindo.
        if (vehicleMoving && !appInForeground) return Verdict.DEFER
        if (!ConfirmationPolicy.canAsk(now, zone, kind, candidate, recent)) {
            // A escolha do transporte não se perde: espera a próxima oportunidade.
            return if (kind == QuestionKind.SELECT_TRANSPORT_MODE) Verdict.DEFER else Verdict.SKIP
        }
        return Verdict.ASK
    }
}
