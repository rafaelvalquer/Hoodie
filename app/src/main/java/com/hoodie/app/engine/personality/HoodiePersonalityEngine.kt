package com.hoodie.app.engine.personality

import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.Needs
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.domain.personality.HoodiePersonalityProfile
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.SceneId

/** Momentos do dia que podem ganhar uma reação com a cara do Hoodie. */
enum class PersonalityTrigger {
    FIRST_OPEN_OF_DAY, DAY_WAKING, ARRIVED_AT_WORK, WORK_BREAK, NEW_PLACE_CONFIRMED,
    LONG_ACTIVITY_FINISHED, BACK_HOME, DAY_WINDING_DOWN, APP_RETURN,
}

/**
 * Ordem de prioridade entre tudo que disputa o Hoodie na tela (maior vence):
 * segurança/transição → animação essencial → evento social → reação de personalidade → fala ambiente.
 */
enum class ReactionPriority(val rank: Int) {
    AMBIENT_SPEECH(1), PERSONALITY_REACTION(2), SOCIAL_EVENT(3), USER_INTERACTION(4), ESSENTIAL_ANIMATION(5), SAFETY_TRANSITION(6),
}

data class PersonalityContext(
    val trigger: PersonalityTrigger,
    val scene: SceneId?,
    val activity: HoodieActivity,
    val needs: Needs,
    val placeId: Long?,
    val occurredAt: Long,
    val daySeed: Int,
    /** Identifica a visita (evento de contexto); "1 por visita" usa esta chave. */
    val visitSeed: Long,
    val userContext: UserContextType? = null,
    val hour: Int = 12,
)

/**
 * O que está acontecendo na tela agora e não pode ser interrompido. Caminhada, troca de cena,
 * fala de NPC ou algo mais prioritário adiam a reação; uma ação de trabalho só tira o gesto
 * (a fala aparece no balão sem mexer nas mãos que estão digitando).
 */
data class PersonalityGate(
    val walking: Boolean = false,
    val workAction: Boolean = false,
    val sceneTransition: Boolean = false,
    val npcSpeaking: Boolean = false,
    /** Algo de prioridade maior (transição, animação essencial, evento social) já ocupa o Hoodie. */
    val busyWith: ReactionPriority? = null,
) {
    fun blocks(priority: ReactionPriority): Boolean =
        walking || sceneTransition || npcSpeaking || (busyWith != null && busyWith.rank >= priority.rank)
}

data class PersonalityDecision(
    val eventId: String,
    val trigger: PersonalityTrigger,
    /** Gesto curto e seguro em qualquer postura; null = o gesto já vem de outro lugar (abrir o app). */
    val animation: AnimationId?,
    val dialogueId: String?,
    val text: String?,
    val priority: ReactionPriority,
    val durationMs: Long,
)

/** Limites de frequência (configuráveis). */
data class PersonalityLimits(
    val greetingsPerDay: Int = 1,
    val endOfDayPerDay: Int = 1,
    val wakingPerDay: Int = 1,
    val breakIntervalMs: Long = 60 * 60_000L,
    /** Distância mínima entre dois comentários de personalidade. */
    val commentGapMs: Long = 2 * 60_000L,
    /** Uma mesma fala não volta antes disso. */
    val dialogueRepeatGapMs: Long = 6 * 60 * 60_000L,
    /** Voltar ao app só cumprimenta depois de um tempo fora. */
    val appReturnAwayMs: Long = 30 * 60_000L,
    val speechDurationMs: Long = 6_000L,
    val npcEventsPerWindow: Int = 2,
    val npcEventWindowMs: Long = 10 * 60_000L,
)

/**
 * Memória do que já foi mostrado. É separada da decisão: [HoodiePersonalityEngine.decide] só lê,
 * e [PersonalityLedger.record] é quem marca como mostrado depois que a reação foi de fato exibida.
 */
data class PersonalityLedger(
    val lastShownAt: Map<PersonalityTrigger, Long> = emptyMap(),
    val lastShownDay: Map<PersonalityTrigger, Int> = emptyMap(),
    val lastCommentAt: Long = Long.MIN_VALUE,
    /** "TRIGGER:visita" das reações que valem uma vez por visita (só as mais recentes). */
    val visitKeys: List<String> = emptyList(),
    /** Falas recentes: id → quando apareceu. */
    val recentDialogues: Map<String, Long> = emptyMap(),
) {
    fun record(decision: PersonalityDecision, context: PersonalityContext, limits: PersonalityLimits = PersonalityLimits()): PersonalityLedger {
        val at = context.occurredAt
        val keys = if (decision.trigger in PER_VISIT) (visitKeys - visitKey(context) + visitKey(context)).takeLast(MAX_VISIT_KEYS) else visitKeys
        val dialogues = decision.dialogueId?.let { id ->
            (recentDialogues + (id to at)).filterValues { at - it < limits.dialogueRepeatGapMs }
        } ?: recentDialogues
        return copy(
            lastShownAt = lastShownAt + (decision.trigger to at),
            lastShownDay = lastShownDay + (decision.trigger to context.daySeed),
            lastCommentAt = if (decision.text != null) at else lastCommentAt,
            visitKeys = keys,
            recentDialogues = dialogues,
        )
    }

    companion object {
        val PER_VISIT = setOf(PersonalityTrigger.NEW_PLACE_CONFIRMED, PersonalityTrigger.ARRIVED_AT_WORK, PersonalityTrigger.BACK_HOME)
        const val MAX_VISIT_KEYS = 32
        fun visitKey(c: PersonalityContext) = "${c.trigger.name}:${c.visitSeed}"
    }
}

/**
 * Decide, de forma pura e determinística, se um momento vira reação. Mesmo contexto + mesma
 * memória = mesma decisão (a escolha da fala usa a semente do dia e da visita, nunca Random).
 */
class HoodiePersonalityEngine(
    private val catalog: List<PersonalityDialogueRule>,
    private val profile: HoodiePersonalityProfile = HoodiePersonalityProfile.DEFAULT,
    private val limits: PersonalityLimits = PersonalityLimits(),
) {
    fun decide(context: PersonalityContext, ledger: PersonalityLedger, gate: PersonalityGate = PersonalityGate()): PersonalityDecision? {
        val priority = ReactionPriority.PERSONALITY_REACTION
        if (gate.blocks(priority)) return null
        if (!frequencyAllows(context, ledger)) return null
        val animation = if (gate.workAction) null else animationFor(context)
        val canSpeak = ledger.lastCommentAt == Long.MIN_VALUE || context.occurredAt - ledger.lastCommentAt >= limits.commentGapMs
        val rule = if (canSpeak) pickDialogue(context, ledger) else null
        if (animation == null && rule == null) return null
        return PersonalityDecision(
            eventId = "${context.trigger.name}:${context.daySeed}:${context.visitSeed}:${context.occurredAt}",
            trigger = context.trigger,
            animation = animation,
            dialogueId = rule?.id,
            text = rule?.text,
            priority = priority,
            durationMs = limits.speechDurationMs,
        )
    }

    fun frequencyAllows(c: PersonalityContext, ledger: PersonalityLedger): Boolean {
        val lastAt = ledger.lastShownAt[c.trigger]
        val shownToday = ledger.lastShownDay[c.trigger] == c.daySeed
        return when (c.trigger) {
            PersonalityTrigger.FIRST_OPEN_OF_DAY -> !shownToday
            PersonalityTrigger.DAY_WAKING -> !shownToday
            PersonalityTrigger.DAY_WINDING_DOWN -> !shownToday
            PersonalityTrigger.WORK_BREAK -> lastAt == null || c.occurredAt - lastAt >= limits.breakIntervalMs
            PersonalityTrigger.NEW_PLACE_CONFIRMED, PersonalityTrigger.ARRIVED_AT_WORK, PersonalityTrigger.BACK_HOME ->
                PersonalityLedger.visitKey(c) !in ledger.visitKeys
            // Volta ao app: uma vez por dia e nunca logo depois do cumprimento da primeira abertura.
            PersonalityTrigger.APP_RETURN -> !shownToday &&
                ledger.lastShownAt[PersonalityTrigger.FIRST_OPEN_OF_DAY]?.let { c.occurredAt - it >= limits.appReturnAwayMs } != false
            PersonalityTrigger.LONG_ACTIVITY_FINISHED -> lastAt == null || c.occurredAt - lastAt >= limits.breakIntervalMs
        }
    }

    /** Gestos seguros sentado ou em pé; abrir o app fica com o ReactionDirector (sem gesto duplo). */
    fun animationFor(c: PersonalityContext): AnimationId? = when (c.trigger) {
        PersonalityTrigger.FIRST_OPEN_OF_DAY, PersonalityTrigger.APP_RETURN -> null
        PersonalityTrigger.DAY_WAKING -> if (profile.restAffinity >= 60) AnimationId.EAR_FLICK else AnimationId.SMILE
        PersonalityTrigger.ARRIVED_AT_WORK -> if (profile.curiosity >= 60) AnimationId.NOTICE else AnimationId.NOD
        PersonalityTrigger.WORK_BREAK ->
            if (c.activity == HoodieActivity.COFFEE && profile.coffeeAffinity >= 70) AnimationId.HAPPY else AnimationId.SMILE
        PersonalityTrigger.NEW_PLACE_CONFIRMED -> if (profile.curiosity >= 60) AnimationId.NOTICE else AnimationId.GLANCE
        PersonalityTrigger.LONG_ACTIVITY_FINISHED -> AnimationId.NOD
        PersonalityTrigger.BACK_HOME -> if (profile.sociability >= 50) AnimationId.HAPPY else AnimationId.SMILE
        PersonalityTrigger.DAY_WINDING_DOWN -> AnimationId.EAR_FLICK
    }

    fun pickDialogue(c: PersonalityContext, ledger: PersonalityLedger): PersonalityDialogueRule? {
        val eligible = catalog.filter { rule ->
            rule.trigger == c.trigger &&
                (rule.trait == null || profile.value(rule.trait) >= rule.minTrait) &&
                (rule.context == null || rule.context == c.userContext) &&
                (rule.activity == null || rule.activity == c.activity) &&
                (rule.hourFrom == null || c.hour >= rule.hourFrom) &&
                (rule.hourTo == null || c.hour < rule.hourTo) &&
                ledger.recentDialogues[rule.id]?.let { c.occurredAt - it >= limits.dialogueRepeatGapMs } != false
        }
        if (eligible.isEmpty()) return null
        val best = eligible.maxOf { it.priority }
        val top = eligible.filter { it.priority == best }.sortedBy { it.id }
        return top[Math.floorMod(seed(c), top.size)]
    }

    private fun seed(c: PersonalityContext): Int {
        var h = c.daySeed * 31 + c.trigger.ordinal
        h = h * 31 + (c.visitSeed xor (c.visitSeed ushr 32)).toInt()
        h = h xor (h ushr 16)
        return h * 0x45d9f3b
    }
}
