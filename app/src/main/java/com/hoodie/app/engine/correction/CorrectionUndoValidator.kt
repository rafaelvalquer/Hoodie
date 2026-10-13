package com.hoodie.app.engine.correction

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.UndoStatus
import com.hoodie.app.domain.correction.UndoBlockReason

/** Decide se uma reversão ainda é segura. Puro: nunca altera nada. */
object CorrectionUndoValidator {
    /** null = seguro. Qualquer mudança posterior nos eventos da janela bloqueia, em vez de sobrescrever dados novos. */
    fun validate(status: String, expiresAt: Long, now: Long, afterEvents: List<ContextEventEntity>, currentEvents: List<ContextEventEntity>): UndoBlockReason? = when {
        status == UndoStatus.UNDONE -> UndoBlockReason.ALREADY_UNDONE
        status == UndoStatus.EXPIRED || now > expiresAt -> UndoBlockReason.EXPIRED
        afterEvents != currentEvents -> UndoBlockReason.CONFLICT
        else -> null
    }
}