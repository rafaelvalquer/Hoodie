package com.hoodie.app.engine.correction

import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject

/** Estado que uma correção pode alterar: eventos de contexto da janela, confirmações e perguntas encerradas. */
data class CorrectionSnapshot(
    val before: List<ContextEventEntity>,
    val after: List<ContextEventEntity>,
    val confirmationIds: List<Long>,
    val supersededQuestionIds: List<Long>,
) {
    fun encode(): String = JSONObject()
        .put("before", JSONArray(before.map(::eventJson)))
        .put("after", JSONArray(after.map(::eventJson)))
        .put("confirmationIds", JSONArray(confirmationIds))
        .put("questionIds", JSONArray(supersededQuestionIds))
        .toString()

    companion object {
        const val FORMAT_VERSION = 1

        fun decode(json: String): CorrectionSnapshot {
            val o = JSONObject(json)
            return CorrectionSnapshot(
                events(o.getJSONArray("before")), events(o.getJSONArray("after")),
                longs(o.getJSONArray("confirmationIds")), longs(o.getJSONArray("questionIds")),
            )
        }

        private fun longs(a: JSONArray) = List(a.length()) { a.getLong(it) }
        private fun events(a: JSONArray) = List(a.length()) { eventFrom(a.getJSONObject(it)) }

        private fun eventJson(e: ContextEventEntity) = JSONObject()
            .put("id", e.id).put("type", e.type.name).put("startedAt", e.startedAt)
            .put("endedAt", e.endedAt ?: JSONObject.NULL).put("confidence", e.confidence.toDouble())
            .put("placeId", e.placeId ?: JSONObject.NULL).put("source", e.source.name)
            .put("venueType", e.venueType?.name ?: JSONObject.NULL)

        private fun eventFrom(o: JSONObject) = ContextEventEntity(
            id = o.getLong("id"), type = UserContextType.valueOf(o.getString("type")),
            startedAt = o.getLong("startedAt"), endedAt = if (o.isNull("endedAt")) null else o.getLong("endedAt"),
            confidence = o.getDouble("confidence").toFloat(), placeId = if (o.isNull("placeId")) null else o.getLong("placeId"),
            source = ContextSource.valueOf(o.getString("source")),
            venueType = if (o.isNull("venueType")) null else PlaceType.valueOf(o.getString("venueType")),
        )
    }
}

/** Fotografia dos registros canônicos que uma correção pode tocar. */
class CorrectionSnapshotCollector @Inject constructor(private val db: HoodieDatabase) {
    /** Eventos que começam ou continuam a partir de [from]. */
    suspend fun events(from: Long): List<ContextEventEntity> = db.contextEventDao().overlapping(from, Long.MAX_VALUE)

    suspend fun maxConfirmationId(): Long = db.confirmationDao().maxId() ?: 0L

    suspend fun unansweredQuestionIds(): Set<Long> = db.questionDao().since(0).filter { it.answeredAt == null }.map { it.id }.toSet()
}