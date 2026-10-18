package com.hoodie.app.engine.backup

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.hoodie.app.core.backup.BackupStage
import com.hoodie.app.core.backup.BackupStagingManager
import org.json.JSONObject
import java.io.InputStream
import javax.inject.Inject

data class BackupPreview(val manifest: BackupManifest, val stage: BackupStage, val preferences: androidx.datastore.preferences.core.Preferences)

class BackupImporter @Inject constructor(private val staging: BackupStagingManager) {
    fun validate(input: InputStream, password: CharArray, checkCancelled: () -> Unit): BackupPreview {
        val stage = staging.create()
        try {
            val db = stage.database.openHelper.writableDatabase
            var manifest: BackupManifest? = null
            var preferences: androidx.datastore.preferences.core.Preferences? = null
            val counts = mutableMapOf<String, Long>()
            var previousDataset = -1
            db.beginTransaction()
            try {
                BackupCrypto.read(input, password) { bytes ->
                    checkCancelled()
                    val row = BackupJson.parse(bytes)
                    when (row.getString("kind")) {
                        "manifest" -> {
                            requireBackup(manifest == null && preferences == null && counts.isEmpty())
                            manifest = BackupManifest.parse(row)
                        }
                        "settings" -> {
                            requireBackup(row.keys().asSequence().toSet() == setOf("kind", "version", "data"))
                            requireBackup(row.get("version") is Int || row.get("version") is Long)
                            requireBackup(manifest != null && preferences == null && counts.isEmpty())
                            requireBackup(row.getLong("version") == 1L, BackupError.INCOMPATIBLE)
                            preferences = BackupSettingsMapper.restore(row.getJSONObject("data"))
                        }
                        "record" -> {
                            requireBackup(row.keys().asSequence().toSet() == setOf("kind", "dataset", "version", "data"))
                            requireBackup(row.get("version") is Int || row.get("version") is Long)
                            val m = manifest ?: throw BackupException(BackupError.INVALID_FILE)
                            requireBackup(preferences != null)
                            requireBackup(row.getLong("version") == 1L, BackupError.INCOMPATIBLE)
                            val dataset = row.getString("dataset")
                            val index = BackupSchemaV1.datasets.indexOfFirst { it.name == dataset }
                            requireBackup(index >= previousDataset && index >= 0 && dataset in m.counts)
                            previousDataset = index
                            val schema = BackupSchemaV1.datasets[index]
                            val data = row.getJSONObject("data")
                            BackupSchemaAdapter.validateRecord(schema, data)
                            BackupSchemaAdapter.reconcile(dataset, data, m.createdAt)
                            val count = (counts[dataset] ?: 0) + 1
                            requireBackup(count <= m.counts.getValue(dataset))
                            counts[dataset] = count
                            if (count % 128L == 0L) staging.ensureSpace()
                            val values = ContentValues()
                            schema.fields.filter { it.name !in setOf("latitude", "longitude") }.forEach { field ->
                                val value = data.get(field.name)
                                if (value == JSONObject.NULL) values.putNull(field.column)
                                else when (field.type) {
                                    BackupValueType.INTEGER -> values.put(field.column, (value as Number).toLong())
                                    BackupValueType.REAL -> values.put(field.column, (value as Number).toDouble())
                                    BackupValueType.TEXT -> values.put(field.column, value as String)
                                }
                            }
                            if (dataset == "places") values.put("encryptedCoordinates", stage.coordinates.encrypt(data.getDouble("latitude"), data.getDouble("longitude")))
                            requireBackup(db.insert(schema.table, SQLiteDatabase.CONFLICT_ABORT, values) >= 0)
                        }
                        else -> throw BackupException(BackupError.INVALID_FILE)
                    }
                }
                val m = manifest ?: throw BackupException(BackupError.INVALID_FILE)
                requireBackup(preferences != null && m.counts.all { (table, count) -> (counts[table] ?: 0) == count })
                m.counts.forEach { (dataset, expected) ->
                    val table = BackupSchemaV1.datasets.first { it.name == dataset }.table
                    db.query("SELECT COUNT(*) FROM `$table`").use { requireBackup(it.moveToFirst() && it.getLong(0) == expected) }
                }
                BackupValidator.validateDatabase(db)
                db.setTransactionSuccessful()
            } finally { db.endTransaction() }
            db.query("PRAGMA wal_checkpoint(FULL)").close()
            staging.seal(stage)
            return BackupPreview(manifest!!, stage, preferences!!)
        } catch (e: Throwable) { staging.discard(stage); throw e }
    }
}
