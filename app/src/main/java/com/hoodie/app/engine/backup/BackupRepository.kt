package com.hoodie.app.engine.backup

import androidx.room.withTransaction
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.security.CoordinateCipher
import org.json.JSONObject
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/** The Lazy database is essential: import/recovery must work with an unreadable active database. */
class BackupRepository @Inject constructor(
    private val database: dagger.Lazy<HoodieDatabase>,
    private val coordinates: dagger.Lazy<CoordinateCipher>,
    private val settings: com.hoodie.app.core.datastore.SettingsRepository,
) {
    suspend fun snapshot(includeDigital: Boolean, emit: (JSONObject) -> Unit, checkCancelled: () -> Unit) {
        val db = database.get()
        val preferences = settings.backupPreferences()
        db.withTransaction {
            val sql = db.openHelper.writableDatabase
            val datasets = BackupSchemaV1.datasets.filter { includeDigital || !it.digital }
            val counts = datasets.associate { schema -> schema.name to sql.query("SELECT COUNT(*) FROM `${schema.table}`").use { it.moveToFirst(); it.getLong(0) } }
            requireBackup(counts.values.sum() <= BackupFormat.MAX_RECORDS - 2)
            requireBackup(counts.all { (name, count) -> count <= BackupFormat.recordLimit(name) })
            val cutoff = System.currentTimeMillis()
            val manifest = BackupManifest(com.hoodie.app.BuildConfig.VERSION_NAME, cutoff, ZoneId.systemDefault().id, UUID.randomUUID().toString(), includeDigital, counts)
            emit(manifest.json())
            emit(JSONObject().put("kind", "settings").put("version", 1).put("data", BackupSettingsMapper.export(preferences)))
            datasets.forEach { schema ->
                sql.query("SELECT * FROM `${schema.table}` ORDER BY " + schema.primaryKeys.joinToString { key -> "`${schema.fields.first { it.name == key }.column}`" }).use { cursor ->
                    while (cursor.moveToNext()) {
                        checkCancelled()
                        val row = JSONObject()
                        schema.fields.filter { it.name !in setOf("latitude", "longitude") }.forEach { field ->
                            val index = cursor.getColumnIndexOrThrow(field.column)
                            row.put(field.name, if (cursor.isNull(index)) JSONObject.NULL else when (field.type) {
                                BackupValueType.TEXT -> cursor.getString(index)
                                BackupValueType.INTEGER -> cursor.getLong(index)
                                BackupValueType.REAL -> cursor.getDouble(index)
                            })
                        }
                        if (schema.name == "places") {
                            val pair = coordinates.get().decrypt(cursor.getString(cursor.getColumnIndexOrThrow("encryptedCoordinates")))
                                ?: throw BackupException(BackupError.COORDINATES)
                            requireBackup(pair.first.isFinite() && pair.first in -90.0..90.0 && pair.second.isFinite() && pair.second in -180.0..180.0, BackupError.COORDINATES)
                            row.put("latitude", pair.first).put("longitude", pair.second)
                        }
                        BackupSchemaAdapter.reconcile(schema.name, row, cutoff)
                        BackupSchemaAdapter.validateRecord(schema, row)
                        emit(JSONObject().put("kind", "record").put("dataset", schema.name).put("version", 1).put("data", row))
                    }
                }
            }
        }
    }
    fun closeActiveIfOpen() {
        val db = database.get()
        db.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        db.close()
    }
}
