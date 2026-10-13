package com.hoodie.app.domain.correction

import com.hoodie.app.core.model.PlaceType

enum class CorrectionScope {
    /** Cria uma mudança a partir de agora, preservando o histórico anterior. */
    FROM_NOW,

    /** Corrige o evento aberto desde a chegada. */
    SINCE_CURRENT_ARRIVAL,
}

/** Pedido de correção da Home. [placeId] aponta o lugar exato quando há mais de um do mesmo tipo. */
data class QuickContextCorrection(
    val expectedEventId: Long,
    val placeType: PlaceType,
    val placeId: Long?,
    val scope: CorrectionScope,
)

/** [operationId] só existe quando a correção pode ser desfeita com segurança. */
sealed interface QuickCorrectionResult {
    data class Applied(val operationId: String?) : QuickCorrectionResult

    /** O contexto mudou antes de salvar: pedir nova conferência em vez de sobrescrever. */
    data object Stale : QuickCorrectionResult
}

enum class UndoBlockReason { EXPIRED, ALREADY_UNDONE, CONFLICT }

sealed interface UndoResult {
    data object Restored : UndoResult
    data class Blocked(val reason: UndoBlockReason) : UndoResult
    data object NotFound : UndoResult
}