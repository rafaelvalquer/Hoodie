package com.hoodie.app.engine.backup

import androidx.datastore.preferences.core.*
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class BackupValidationTest {
    @Test fun everyRoomEntityAndColumnHasAnExplicitCoverageDecision() {
        val schema = JSONObject(File("schemas/com.hoodie.app.core.database.HoodieDatabase/${com.hoodie.app.core.database.HOODIE_DATABASE_VERSION}.json").readText()).getJSONObject("database").getJSONArray("entities")
        val actual = (0 until schema.length()).map { schema.getJSONObject(it).getString("tableName") }.toSet()
        val covered = BackupSchemaV1.datasets.map { it.table }.toSet() + BackupSchemaV1.reconstructed + BackupSchemaV1.excluded
        assertEquals(29, actual.size)
        assertEquals(actual, covered)
        (0 until schema.length()).forEach { index ->
            val entity = schema.getJSONObject(index)
            val dataset = BackupSchemaV1.datasets.firstOrNull { it.table == entity.getString("tableName") } ?: return@forEach
            val fields = entity.getJSONArray("fields")
            val columns = (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }.toSet()
            val contract = dataset.fields.map { it.column }.toSet()
            assertEquals(columns, if (dataset.name == "places") contract - setOf("latitude", "longitude") + "encryptedCoordinates" else contract)
        }
        val encrypted = (0 until schema.length()).flatMap { index ->
            val e = schema.getJSONObject(index); val fields = e.getJSONArray("fields")
            (0 until fields.length()).map { fields.getJSONObject(it).getString("columnName") }.filter { it.contains("encrypted", true) }.map { "${e.getString("tableName")}.$it" }
        }.toSet()
        assertEquals(BackupSchemaV1.localEncryptedFields, encrypted)
    }
    @Test fun invalidCoordinatesEnumsNumbersAndUnexpectedFieldsRejected() {
        val schema = BackupSchemaV1.datasets.first { it.name == "places" }
        fun row() = BackupPlaceV1(1, "Casa", "HOME", 100.0, .9, 0, 0, 1700000000000, null, -23.0, -46.0).json()
        BackupSchemaAdapter.validateRecord(schema, row())
        listOf(row().put("latitude", 91.0), row().put("confidence", 2), row().put("id", 1.5), row().put("extra", 3), row().put("type", "ALIEN"), row().put("name", JSONObject.NULL)).forEach {
            rejects { BackupSchemaAdapter.validateRecord(schema, it) }
        }
    }
    @Test fun deeplyNestedJsonAndInvalidUtf8RejectedBeforeParsing() {
        rejects { BackupJson.parse(("[".repeat(10000) + "]".repeat(10000)).toByteArray()) }
        try { BackupJson.parse(byteArrayOf(0xc3.toByte(), 0x28)); fail() } catch (_: java.nio.charset.CharacterCodingException) { }
    }
    @Test fun openIntervalsEndAtSnapshotAndMobilityNeverResumes() {
        val row = JSONObject().put("startedAt", 1000L).put("endedAt", JSONObject.NULL).put("state", "IN_VEHICLE")
        BackupSchemaAdapter.reconcile("mobility_sessions", row, 2000L)
        assertEquals(2000L, row.getLong("endedAt")); assertEquals("ARRIVED", row.getString("state"))
        assertTrue(row.isNull("pendingMovementMode")); assertEquals(0, row.getInt("pendingModeQuestion"))
    }
}

class BackupSettingsMapperTest {
    @Test fun onlyPortablePreferencesExportAndPermissionStateResets() {
        val input = mutablePreferencesOf(stringPreferencesKey("cat_name") to "Nuvem", booleanPreferencesKey("digital_analysis_enabled") to true,
            longPreferencesKey("last_geofence_register_day") to 100, stringPreferencesKey("secret") to "never", longPreferencesKey("last_backup_at") to 1)
        val json = BackupSettingsMapper.export(input)
        assertFalse(json.has("secret")); assertFalse(json.has("last_backup_at")); assertFalse(json.has("last_geofence_register_day"))
        val restored = BackupSettingsMapper.restore(json)
        assertEquals("Nuvem", restored[stringPreferencesKey("cat_name")])
        assertEquals(false, restored[booleanPreferencesKey("digital_analysis_enabled")])
        assertEquals(true, restored[booleanPreferencesKey("digital_analysis_requested")])
    }
    @Test fun allSettingsHaveAnExplicitDecision() {
        val source = File("src/main/java/com/hoodie/app/core/datastore/SettingsRepository.kt").readText()
        val names = Regex("(?:string|boolean|long|int|stringSet)PreferencesKey\\(\"([^\"]+)\"\\)").findAll(source).map { it.groupValues[1] }.toSet()
        assertEquals(names, BackupSettingsMapper.included + BackupSettingsMapper.excluded)
    }
    @Test fun malformedOrUnexpectedSettingsRejected() {
        rejects { BackupSettingsMapper.restore(JSONObject().put("cat_name", 5)) }
        rejects { BackupSettingsMapper.restore(JSONObject().put("wake_minute", 1440)) }
        rejects { BackupSettingsMapper.restore(JSONObject().put("token", "secret")) }
    }
}

class BackupManifestTest {
    private fun sample() = BackupManifest("test", 1792300000000, "America/Sao_Paulo", java.util.UUID.randomUUID().toString(), false,
        BackupSchemaV1.datasets.filter { !it.digital }.associate { it.name to 0L })
    @Test fun manifestRoundTripAndDigitalCategoriesAreExplicit() {
        assertEquals(sample().counts, BackupManifest.parse(sample().json()).counts)
        rejects { BackupManifest.parse(sample().json().put("includeDigital", true)) }
    }
    @Test fun excessiveCountsAndNewDatasetVersionRejected() {
        val j = sample().json(); j.getJSONObject("counts").put("places", Long.MAX_VALUE)
        rejects { BackupManifest.parse(j) }
        val next = sample().json(); next.getJSONObject("datasetVersions").put("places", 2)
        assertEquals(BackupError.INCOMPATIBLE, rejects { BackupManifest.parse(next) }.error)
        rejects { BackupManifest.parse(sample().json().put("includeDigital", "false")) }
        rejects { BackupManifest.parse(sample().json().put("formatVersion", 1.5)) }
        rejects { BackupManifest.parse(sample().json().put("path", "../unexpected")) }
    }
}
