package com.hoodie.app.engine.backup

import org.json.JSONObject
import java.time.ZoneId
import java.util.UUID

data class BackupManifest(
    val appVersion: String,
    val createdAt: Long,
    val timezone: String,
    val backupId: String,
    val includeDigital: Boolean,
    val counts: Map<String, Long>,
    val databaseSchemaVersion: Int = 12,
) {
    fun json() = JSONObject().put("kind", "manifest").put("formatVersion", 1)
        .put("appVersion", appVersion).put("databaseSchemaVersion", databaseSchemaVersion)
        .put("createdAt", createdAt).put("timezone", timezone).put("backupId", backupId)
        .put("includeDigital", includeDigital).put("counts", JSONObject(counts))
        .put("datasetVersions", JSONObject(counts.keys.associateWith { 1 }))

    companion object {
        fun parse(j: JSONObject): BackupManifest {
            requireBackup(j.keys().asSequence().toSet() == setOf("kind", "formatVersion", "appVersion", "databaseSchemaVersion", "createdAt", "timezone", "backupId", "includeDigital", "counts", "datasetVersions"))
            listOf("formatVersion", "databaseSchemaVersion", "createdAt").forEach { requireBackup(j.get(it) is Int || j.get(it) is Long) }
            listOf("kind", "appVersion", "timezone", "backupId").forEach { requireBackup(j.get(it) is String) }
            requireBackup(j.get("includeDigital") is Boolean)
            requireBackup(j.getString("kind") == "manifest")
            requireBackup(j.getLong("formatVersion") == 1L && j.getLong("databaseSchemaVersion") in 1L..12L, BackupError.INCOMPATIBLE)
            val counts = j.getJSONObject("counts")
            val digital = j.getBoolean("includeDigital")
            val expected = BackupSchemaV1.datasets.filter { digital || !it.digital }.map { it.name }.toSet()
            requireBackup(counts.keys().asSequence().toSet() == expected)
            val versions = j.getJSONObject("datasetVersions")
            requireBackup(versions.keys().asSequence().toSet() == expected)
            val map = expected.associateWith { key ->
                requireBackup(versions.get(key) is Int || versions.get(key) is Long)
                requireBackup(counts.get(key) is Int || counts.get(key) is Long)
                requireBackup(versions.getLong(key) == 1L, BackupError.INCOMPATIBLE)
                counts.getLong(key).also { requireBackup(it in 0..BackupFormat.recordLimit(key)) }
            }
            requireBackup(map.values.sum() <= BackupFormat.MAX_RECORDS - 2)
            val at = j.getLong("createdAt")
            requireBackup(at in 946684800000L..4133980800000L)
            val zone = j.getString("timezone"); ZoneId.of(zone)
            val id = j.getString("backupId"); requireBackup(UUID.fromString(id).toString() == id)
            return BackupManifest(j.getString("appVersion").also { requireBackup(it.length <= 64) }, at, zone, id, digital, map, j.getInt("databaseSchemaVersion"))
        }
    }
}
