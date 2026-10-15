package com.hoodie.app.presentation.screens.home

import com.hoodie.app.engine.dialogue.DialogueEngine
import com.hoodie.app.engine.dialogue.DialogueInput
import com.hoodie.app.engine.personality.DetectedTrigger
import com.hoodie.app.engine.personality.PersonalityContext
import com.hoodie.app.engine.personality.PersonalityDecision
import com.hoodie.app.engine.personality.PersonalityGate
import com.hoodie.app.engine.personality.PersonalityLedger
import com.hoodie.app.engine.personality.PersonalityObservation
import com.hoodie.app.engine.personality.PersonalityRuntime
import com.hoodie.app.engine.personality.ReactionPriority
import kotlin.random.Random

/** Uma fala candidata ao balão, com a prioridade de quem a pediu. */
data class DialogueCandidate(val text: String, val priority: ReactionPriority)

/** Só um balão por vez: a fala de maior prioridade vence; empate fica com a que já estava. */
object DialogueArbiter {
    fun choose(vararg candidates: DialogueCandidate?): DialogueCandidate? =
        candidates.filterNotNull().maxByOrNull { it.priority.rank }
}

/**
 * Fala ambiente de sempre (dialogues.json): 40% de chance, fica 9 s; sem fala, tenta de novo em 20 s.
 * Enquanto uma fala mais importante ocupa o balão, ela nem sorteia.
 */
class AmbientDialogueScheduler(private val dialogues: DialogueEngine, private val random: Random = Random) {
    private var text: String? = null
    private var until = 0L
    private var heldUntil = 0L
    private var prepared: DialogueCandidate? = null

    fun reset() { text = null; until = 0; heldUntil = 0; prepared = null }
    fun prepare(now: Long, input: () -> DialogueInput): DialogueCandidate? {
        prepared?.let { return it }
        if (now < until || now < heldUntil) return null
        val line = if (random.nextInt(100) < 40) dialogues.pick(input(), random) else null
        if (line == null) { until = now + 20_000; return null }
        return DialogueCandidate(line, ReactionPriority.AMBIENT_SPEECH).also { prepared = it }
    }
    fun confirmPresentation(line: String, now: Long) {
        if (prepared?.text != line) return
        text = line; until = now + 9_000; prepared = null
    }
    fun discardPrepared() { prepared = null }

    fun holdUntil(at: Long) { heldUntil = maxOf(heldUntil, at); text = null; until = maxOf(until, at) }

    fun next(now: Long, input: () -> DialogueInput): DialogueCandidate? {
        if (now < heldUntil) return null
        if (now >= until) {
            text = if (random.nextInt(100) < 40) dialogues.pick(input(), random) else null
            until = now + if (text != null) 9_000 else 20_000
        }
        return text?.let { DialogueCandidate(it, ReactionPriority.AMBIENT_SPEECH) }
    }
}

/**
 * Reações de personalidade: detecta momentos, espera a tela poder recebê-los (sem trocar de cena,
 * sem caminhar), decide com o motor puro e só então marca como mostrado na memória persistida.
 */
class PersonalityDialogueScheduler(
    private val runtime: PersonalityRuntime,
    private val pendingTtlMs: Long = 3 * 60_000L,
) {
    private var ledger: PersonalityLedger? = null
    private val pending = mutableListOf<Pair<DetectedTrigger, Long>>()
    private var active: PersonalityDecision? = null
    private var activeUntil = 0L
    private var prepared: Pair<PersonalityDecision, PersonalityContext>? = null
    private var deferredObservation: PersonalityObservation? = null
    private var deferredContext: ((DetectedTrigger) -> PersonalityContext)? = null
    val preparedId: String? get() = prepared?.first?.eventId

    fun discardPrepared() { prepared = null }
    fun clearPresentation() {
        pending.clear()
        prepared = null
        active = null
        activeUntil = 0
        deferredObservation = null
        deferredContext = null
    }
    suspend fun retryPending(now: Long, gate: PersonalityGate): PersonalityDecision? {
        val observation = deferredObservation ?: return null
        val context = deferredContext ?: return null
        return update(observation.copy(now = now), gate, deferPresentation = true, context = context)
    }
    suspend fun confirmPresentation(id: String, now: Long): Boolean {
        val (decision, context) = prepared?.takeIf { it.first.eventId == id } ?: return false
        prepared = null
        val memory = ledger ?: runtime.store.load()
        val updated = memory.record(decision, context.copy(occurredAt = now))
        ledger = updated
        runtime.store.save(updated)
        active = decision
        activeUntil = now + decision.durationMs
        return true
    }

    fun onForeground(now: Long) = runtime.detector.onForeground(now)
    fun onBackground(now: Long) = runtime.detector.onBackground(now)
    fun onNewPlaceConfirmed(visitSeed: Long) = runtime.detector.onNewPlaceConfirmed(visitSeed)

    /** Fala de personalidade no balão agora (null = nenhuma). */
    fun current(now: Long): DialogueCandidate? {
        val decision = active?.takeIf { now < activeUntil } ?: return null
        return decision.text?.let { DialogueCandidate(it, decision.priority) }
    }

    val speakingUntil: Long get() = activeUntil

    /** Devolve a decisão nova deste instante (para o gesto) ou null. */
    suspend fun update(
        observation: PersonalityObservation,
        gate: PersonalityGate,
        deferPresentation: Boolean = false,
        context: (DetectedTrigger) -> PersonalityContext,
    ): PersonalityDecision? {
        val now = observation.now
        if (deferPresentation) { deferredObservation = observation; deferredContext = context }
        runtime.detector.detect(observation).forEach { pending += it to now }
        pending.removeAll { (_, at) -> now - at > pendingTtlMs }
        // Tela ocupada (caminhando, trocando de cena, NPC falando): o momento espera um pouco.
        if (prepared != null || pending.isEmpty() || gate.blocks(ReactionPriority.PERSONALITY_REACTION)) return null
        val memory = ledger ?: runtime.store.load().also { ledger = it }
        for ((detected, _) in pending.toList()) {
            val ctx = context(detected)
            val decision = runtime.engine.decide(ctx, memory, gate)
            if (decision == null) {
                // Limite atingido ou nada a dizer: esse momento não volta.
                pending.removeAll { it.first == detected }
                continue
            }
            // Um momento por vez: os outros que chegaram juntos não viram uma enxurrada de reações.
            pending.clear()
            if (deferPresentation) {
                prepared = decision to ctx
                return decision
            }
            val updated = memory.record(decision, ctx)
            ledger = updated
            runtime.store.save(updated)
            if (decision.text != null) {
                active = decision
                activeUntil = now + decision.durationMs
            }
            return decision
        }
        return null
    }
}
