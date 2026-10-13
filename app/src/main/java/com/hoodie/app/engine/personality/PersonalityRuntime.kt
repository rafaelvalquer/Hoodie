package com.hoodie.app.engine.personality

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

private val Context.personalityStore: DataStore<Preferences> by preferencesDataStore(name = "hoodie_personality")

/** Formato estável do [PersonalityLedger] guardado no aparelho (nada sai do dispositivo). */
object PersonalityLedgerCodec {
    fun encode(l: PersonalityLedger): String = JSONObject().apply {
        put("lastShownAt", JSONObject().apply { l.lastShownAt.forEach { (k, v) -> put(k.name, v) } })
        put("lastShownDay", JSONObject().apply { l.lastShownDay.forEach { (k, v) -> put(k.name, v) } })
        put("lastCommentAt", l.lastCommentAt)
        put("visitKeys", JSONArray(l.visitKeys))
        put("recentDialogues", JSONObject().apply { l.recentDialogues.forEach { (k, v) -> put(k, v) } })
    }.toString()

    /** Memória corrompida ou de uma versão desconhecida volta vazia, sem derrubar a Home. */
    fun decode(json: String?): PersonalityLedger {
        if (json.isNullOrBlank()) return PersonalityLedger()
        return runCatching {
            val o = JSONObject(json)
            fun triggers(key: String) = o.optJSONObject(key)?.let { m ->
                m.keys().asSequence().mapNotNull { k -> runCatching { PersonalityTrigger.valueOf(k) }.getOrNull()?.let { it to m.getLong(k) } }.toMap()
            }.orEmpty()
            PersonalityLedger(
                lastShownAt = triggers("lastShownAt"),
                lastShownDay = triggers("lastShownDay").mapValues { it.value.toInt() },
                lastCommentAt = o.optLong("lastCommentAt", Long.MIN_VALUE),
                visitKeys = o.optJSONArray("visitKeys")?.let { a -> (0 until a.length()).map { a.getString(it) } }.orEmpty(),
                recentDialogues = o.optJSONObject("recentDialogues")?.let { m -> m.keys().asSequence().associateWith { m.getLong(it) } }.orEmpty(),
            )
        }.getOrDefault(PersonalityLedger())
    }
}

interface PersonalityLedgerStore {
    suspend fun load(): PersonalityLedger
    suspend fun save(ledger: PersonalityLedger)
}

class InMemoryPersonalityLedgerStore(private var ledger: PersonalityLedger = PersonalityLedger()) : PersonalityLedgerStore {
    override suspend fun load() = ledger
    override suspend fun save(ledger: PersonalityLedger) { this.ledger = ledger }
}

private class DataStorePersonalityLedgerStore(private val store: DataStore<Preferences>) : PersonalityLedgerStore {
    private val key = stringPreferencesKey("ledger_v1")
    override suspend fun load() = PersonalityLedgerCodec.decode(store.data.first()[key])
    override suspend fun save(ledger: PersonalityLedger) { store.edit { it[key] = PersonalityLedgerCodec.encode(ledger) } }
}

/** Tudo o que a Home precisa para a personalidade: catálogo, motor, detector e memória. */
@Singleton
class PersonalityRuntime(
    val engine: HoodiePersonalityEngine,
    val detector: PersonalityTriggerDetector,
    val store: PersonalityLedgerStore,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(
        HoodiePersonalityEngine(PersonalityDialogueCatalog.parse(
            context.assets.open(PersonalityDialogueCatalog.ASSET).bufferedReader().use { it.readText() },
        )),
        PersonalityTriggerDetector(),
        DataStorePersonalityLedgerStore(context.personalityStore),
    )
}
