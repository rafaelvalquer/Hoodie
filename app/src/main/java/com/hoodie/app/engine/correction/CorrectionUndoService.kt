package com.hoodie.app.engine.correction

import com.hoodie.app.core.config.HoodieConfig
import com.hoodie.app.core.database.CorrectionUndoEntity
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.TransactionRunner
import com.hoodie.app.core.database.UndoStatus
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.domain.correction.UndoBlockReason
import com.hoodie.app.domain.correction.UndoResult
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Estado capturado antes de aplicar uma correção. */
class PendingCorrection internal constructor(
    internal val windowFrom: Long,
    internal val before: List<ContextEventEntity>,
    internal val maxConfirmationId: Long,
    internal val unansweredQuestions: Set<Long>,
    val undoable: Boolean,
)

/**
 * Correções reversíveis. [begin] fotografa os registros canônicos, a correção roda pela camada existente,
 * e [commit] grava a operação com o estado depois. Se o app fechar no meio, a correção fica consistente e
 * apenas o Desfazer deixa de ser oferecido. [undo] só reverte se nada mudou depois; senão bloqueia.
 */
@Singleton
class CorrectionUndoService @Inject constructor(
    private val db: HoodieDatabase,
    private val transactions: TransactionRunner,
    private val collector: CorrectionSnapshotCollector,
    private val refresher: CorrectionProjectionRefresher,
    private val clock: ClockProvider,
    private val learning: dagger.Lazy<com.hoodie.app.engine.routine.RoutineLearningCoordinator>? = null,
) {
    suspend fun begin(windowFrom: Long): PendingCorrection {
        val before = collector.events(windowFrom)
        // Deslocamentos mexem em sessões e trechos de mobilidade, que este snapshot não cobre: sem Desfazer.
        val undoable = before.none { it.type == UserContextType.COMMUTING }
        return PendingCorrection(windowFrom, before, collector.maxConfirmationId(), collector.unansweredQuestionIds(), undoable)
    }

    /** Devolve o id da operação, ou null quando nada mudou ou não é seguro desfazer. */
    suspend fun commit(pending: PendingCorrection, kind: String): String? {
        if (!pending.undoable) return null
        val now = clock.nowMillis()
        val after = collector.events(pending.windowFrom)
        if (after == pending.before || after.any { it.type == UserContextType.COMMUTING }) return null
        val superseded = pending.unansweredQuestions.filter { db.questionDao().getById(it)?.answeredAt != null }
        val snapshot = CorrectionSnapshot(pending.before, after, db.confirmationDao().idsAfter(pending.maxConfirmationId), superseded)
        val operationId = UUID.randomUUID().toString()
        transactions.run {
            db.correctionUndoDao().expireOld(now)
            db.correctionUndoDao().insert(CorrectionUndoEntity(
                operationId = operationId, kind = kind, formatVersion = CorrectionSnapshot.FORMAT_VERSION, windowFrom = pending.windowFrom,
                snapshot = snapshot.encode(), createdAt = now, expiresAt = now + UNDO_VALID_MS, status = UndoStatus.AVAILABLE,
            ))
        }
        return operationId
    }

    /** Idempotente: repetir a chamada devolve [UndoBlockReason.ALREADY_UNDONE]. */
    suspend fun undo(operationId: String): UndoResult {
        var windowFrom = 0L
        val result = transactions.run {
            val dao = db.correctionUndoDao()
            val row = dao.byOperation(operationId) ?: return@run UndoResult.NotFound
            if (row.formatVersion != CorrectionSnapshot.FORMAT_VERSION) return@run UndoResult.Blocked(UndoBlockReason.EXPIRED)
            val snapshot = CorrectionSnapshot.decode(row.snapshot)
            val reason = CorrectionUndoValidator.validate(row.status, row.expiresAt, clock.nowMillis(), snapshot.after, collector.events(row.windowFrom))
            if (reason != null) {
                if (reason == UndoBlockReason.EXPIRED && row.status == UndoStatus.AVAILABLE) dao.setStatus(row.id, UndoStatus.EXPIRED)
                return@run UndoResult.Blocked(reason)
            }
            val contextDao = db.contextEventDao()
            val beforeIds = snapshot.before.map { it.id }.toSet()
            snapshot.after.filter { it.id !in beforeIds }.forEach {
                contextDao.delete(it.id)
                refresher.removeTimeline(it.id)
            }
            snapshot.before.forEach {
                if (contextDao.getById(it.id) == null) contextDao.insert(it) else contextDao.update(it)
                refresher.rewriteTimeline(it)
            }
            if (snapshot.confirmationIds.isNotEmpty()) db.confirmationDao().deleteByIds(snapshot.confirmationIds)
            snapshot.supersededQuestionIds.forEach { id ->
                db.questionDao().getById(id)?.let { db.questionDao().update(it.copy(answeredAt = null, answer = null)) }
            }
            dao.setStatus(row.id, UndoStatus.UNDONE)
            windowFrom = row.windowFrom
            UndoResult.Restored
        }
        if (result == UndoResult.Restored) {
            // Derivados e aprendizado só depois de a transação ter sido confirmada.
            refresher.recalculatePhone(windowFrom)
            if (HoodieConfig.LEARNED_ROUTINE) learning?.get()?.recompute()
        }
        return result
    }

    companion object {
        /** A Snackbar dura ~15 s; a reversão continua válida por mais tempo se nada mudar. */
        const val UNDO_VALID_MS = 10 * 60_000L
    }
}