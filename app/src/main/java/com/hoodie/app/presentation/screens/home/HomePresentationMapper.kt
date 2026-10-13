package com.hoodie.app.presentation.screens.home

import com.hoodie.app.core.location.LocationStatus
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.QuestionKind
import com.hoodie.app.domain.home.HomeNowSnapshot
import com.hoodie.app.domain.home.HomeNowStatus

data class HomeHeroUiState(
    val hoodieActivity: HoodieActivity?,
    val activityStartedAt: Long?,
    val context: HomeNowSnapshot?,
    val dialogue: String?,
    val primaryAction: HomePrimaryAction?,
    val isReconciling: Boolean,
)

/** Botões rápidos do cartão de contexto: confirmar só o que ainda é inferência. */
data class QuickActionsUi(val canConfirm: Boolean, val confirmEventId: Long?, val canCorrect: Boolean, val confirmed: Boolean)

/** Transformação pura dos modelos existentes; nunca cria nem altera contexto. */
object HomePresentationMapper {
    private val NON_URGENT = setOf(QuestionKind.NEW_PLACE, QuestionKind.SAVE_PLACE)

    fun map(state: HomeUiState, homeNow: HomeNowSnapshot?): HomeHeroUiState = HomeHeroUiState(
        hoodieActivity = state.snapshot?.state?.activity,
        activityStartedAt = state.snapshot?.state?.startedAt,
        context = homeNow,
        dialogue = state.dialogue,
        primaryAction = primaryAction(state),
        isReconciling = homeNow?.status == HomeNowStatus.PROBABLE,
    )

    /**
     * Prioridade: pergunta pendente > monitoramento indisponível > sugestão de lugar.
     * Em veículo, perguntas/sugestões de lugar (não urgentes) ficam ocultas; a pergunta continua pendente.
     */
    fun primaryAction(state: HomeUiState): HomePrimaryAction? {
        state.question?.takeUnless { state.vehicleMoving && it.kind in NON_URGENT }
            ?.let { return HomePrimaryAction.PendingQuestion(it.id) }
        monitoringIssue(state)?.let { return HomePrimaryAction.MonitoringWarning(it) }
        if (!state.vehicleMoving) state.suggestSavePlace?.let { return HomePrimaryAction.SuggestPlace(it) }
        return null
    }

    private fun monitoringIssue(state: HomeUiState): MonitoringIssue? = when (state.locationStatus) {
        LocationStatus.NO_PERMISSION -> MonitoringIssue.NO_PERMISSION
        LocationStatus.NO_BACKGROUND -> MonitoringIssue.NO_BACKGROUND
        LocationStatus.DISABLED -> MonitoringIssue.DISABLED
        LocationStatus.OK -> if (state.probableMode) MonitoringIssue.PROBABLE_ROUTINE else null
    }

    /** Inferência (mesmo com confiança alta) pode ser confirmada; confirmado só pode ser corrigido. */
    fun quickActions(snapshot: HomeNowSnapshot?): QuickActionsUi {
        val id = snapshot?.contextEventId
        val status = snapshot?.status
        val inferred = status == HomeNowStatus.IDENTIFIED || status == HomeNowStatus.PROBABLE
        return QuickActionsUi(
            canConfirm = inferred && id != null && snapshot.context != null,
            confirmEventId = id,
            canCorrect = status == HomeNowStatus.CONFIRMED || inferred,
            confirmed = status == HomeNowStatus.CONFIRMED,
        )
    }
}