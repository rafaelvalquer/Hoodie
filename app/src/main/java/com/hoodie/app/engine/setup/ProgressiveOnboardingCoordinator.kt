package com.hoodie.app.engine.setup

import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.time.ClockProvider
import kotlinx.coroutines.CancellationException

enum class ProgressiveStep { WELCOME, NAME, TRACKING;
    companion object {
        fun parse(value: String?): ProgressiveStep = entries.firstOrNull { it.name == value } ?: WELCOME
    }
}

data class OnboardingDraft(val step: ProgressiveStep, val name: String)

/**
 * Entrada simplificada: só o essencial é obrigatório. Etapas opcionais (geofences, agendamentos, Hoodie)
 * podem falhar sem impedir o acesso à Home, e nenhuma rotina padrão é gravada como confirmada.
 */
class ProgressiveOnboardingCoordinator(private val settings: SettingsRepository, private val clock: ClockProvider) {
    suspend fun restore(): OnboardingDraft {
        val s = settings.current()
        return OnboardingDraft(ProgressiveStep.parse(s.onboardingDraftStep), s.onboardingDraftName ?: "Hoodie")
    }

    suspend fun saveDraft(step: ProgressiveStep, name: String) = settings.saveOnboardingDraft(step.name, name)

    /** Devolve quantas etapas opcionais falharam. */
    suspend fun complete(name: String, optional: List<suspend () -> Unit>): Int {
        var failures = 0
        optional.forEach { step ->
            try { step() } catch (cancelled: CancellationException) { throw cancelled } catch (_: Exception) { failures++ }
        }
        settings.completeProgressiveOnboarding(name, clock.nowMillis())
        return failures
    }
}