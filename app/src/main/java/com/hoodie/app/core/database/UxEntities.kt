package com.hoodie.app.core.database

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query

/** Estados de uma correção reversível. */
object UndoStatus {
    const val AVAILABLE = "AVAILABLE"
    const val UNDONE = "UNDONE"
    const val EXPIRED = "EXPIRED"
}

/**
 * Operação de correção que pode ser desfeita. [snapshot] guarda (JSON versionado por [formatVersion])
 * os eventos canônicos antes e depois, para conferir se nada mudou antes de reverter.
 */
@Entity(tableName = "correction_undo", indices = [Index(value = ["operationId"], unique = true), Index("status")])
data class CorrectionUndoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val operationId: String,
    val kind: String,
    val formatVersion: Int,
    val windowFrom: Long,
    val snapshot: String,
    val createdAt: Long,
    val expiresAt: Long,
    val status: String,
)

@Dao
interface CorrectionUndoDao {
    @Insert
    suspend fun insert(e: CorrectionUndoEntity): Long

    @Query("SELECT * FROM correction_undo WHERE operationId = :operationId")
    suspend fun byOperation(operationId: String): CorrectionUndoEntity?

    @Query("UPDATE correction_undo SET status = :status WHERE id = :id")
    suspend fun setStatus(id: Long, status: String)

    /** Operações anteriores deixam de poder ser desfeitas quando uma nova correção é registrada. */
    @Query("UPDATE correction_undo SET status = 'EXPIRED' WHERE status = 'AVAILABLE' AND expiresAt <= :now")
    suspend fun expireOld(now: Long): Int
}

/** Estados da fila de entrega de notificações (S4). */
object DeliveryStatus {
    const val PENDING = "PENDING"
    const val DEFERRED = "DEFERRED"
    const val DELIVERED = "DELIVERED"
    const val IN_APP_ONLY = "IN_APP_ONLY"
    const val EXPIRED = "EXPIRED"
    const val CANCELLED = "CANCELLED"
}

/** Acompanha a entrega de uma notificação sem alterar a pergunta original. */
@Entity(tableName = "notification_delivery", indices = [Index(value = ["dedupeKey"], unique = true), Index(value = ["status", "nextEligibleAt"])])
data class NotificationDeliveryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val questionId: Long?,
    val kind: String,
    /** Texto a exibir quando a entrega acontecer depois (adiada). */
    val text: String,
    val createdAt: Long,
    val nextEligibleAt: Long,
    val expiresAt: Long,
    val deliveredAt: Long?,
    val status: String,
    val attemptCount: Int,
    val dedupeKey: String,
)

@Dao
interface NotificationDeliveryDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(e: NotificationDeliveryEntity): Long

    @Query("SELECT * FROM notification_delivery WHERE dedupeKey = :key")
    suspend fun byDedupeKey(key: String): NotificationDeliveryEntity?

    @Query("SELECT * FROM notification_delivery WHERE questionId = :questionId")
    suspend fun forQuestion(questionId: Long): List<NotificationDeliveryEntity>

    @Query("SELECT * FROM notification_delivery WHERE status IN ('PENDING','DEFERRED') AND nextEligibleAt <= :now ORDER BY nextEligibleAt")
    suspend fun due(now: Long): List<NotificationDeliveryEntity>

    @Query("UPDATE notification_delivery SET status = :status, deliveredAt = :deliveredAt, attemptCount = attemptCount + :attempts, nextEligibleAt = :nextEligibleAt WHERE id = :id")
    suspend fun update(id: Long, status: String, deliveredAt: Long?, attempts: Int, nextEligibleAt: Long)

    @Query("UPDATE notification_delivery SET status = 'CANCELLED' WHERE questionId = :questionId AND status IN ('PENDING','DEFERRED')")
    suspend fun cancelForQuestion(questionId: Long): Int

    @Query("SELECT COUNT(*) FROM notification_delivery WHERE status = 'DELIVERED' AND deliveredAt >= :since")
    suspend fun deliveredSince(since: Long): Int
}