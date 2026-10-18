package com.hoodie.app.backup

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.backup.*
import com.hoodie.app.core.database.*
import com.hoodie.app.core.datastore.*
import com.hoodie.app.core.model.*
import com.hoodie.app.engine.backup.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/** Real Room + SQLCipher + independent Keystore aliases. Never touches the active installation. */
@RunWith(AndroidJUnit4::class)
class BackupRoundTripIntegrationTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val staging = BackupStagingManager(context)
    private val secret get() = "hoodie recovery phrase".toCharArray()

    @Test fun thirtyDayJourneySurvivesIndependentDeviceKeysAndDigitalChoice() = runBlocking {
        val source = staging.create()
        val settings = SettingsRepository(generationSettingsStore(context, source.generation))
        try {
            seed(source)
            settings.setCatName("Nuvem")
            settings.setSleep(SleepSchedule(420, 1380))
            val repository = BackupRepository(dagger.Lazy { source.database }, dagger.Lazy { source.coordinates }, settings)
            for (digital in listOf(true, false)) {
                val archive = ByteArrayOutputStream()
                BackupExporter(repository).export(archive, secret, digital) { }
                val preview = BackupImporter(staging).validate(ByteArrayInputStream(archive.toByteArray()), secret) { }
                try {
                    assertNotEquals(source.generation.keyAlias, preview.stage.generation.keyAlias)
                    val oldPass = (source.keys.read() as com.hoodie.app.core.security.DatabaseKeyResult.Available).passphrase
                    val newPass = (preview.stage.keys.read() as com.hoodie.app.core.security.DatabaseKeyResult.Available).passphrase
                    assertFalse(oldPass.contentEquals(newPass)); oldPass.fill(0); newPass.fill(0)
                    val original = source.database.placeDao().getAll().sortedBy { it.id }
                    val restored = preview.stage.database.placeDao().getAll().sortedBy { it.id }
                    assertEquals(original.map { it.id }, restored.map { it.id })
                    assertEquals(original.map { it.name }, restored.map { it.name })
                    original.zip(restored).forEach { (a, b) ->
                        assertNotEquals(a.encryptedCoordinates, b.encryptedCoordinates)
                        assertEquals(source.coordinates.decrypt(a.encryptedCoordinates), preview.stage.coordinates.decrypt(b.encryptedCoordinates))
                        assertNull(preview.stage.coordinates.decrypt(a.encryptedCoordinates))
                    }
                    assertEquals(4L, preview.manifest.counts["places"])
                    assertEquals(120L, preview.manifest.counts["context_events"])
                    assertEquals(digital, preview.manifest.includeDigital)
                    assertEquals("Nuvem", preview.preferences[androidx.datastore.preferences.core.stringPreferencesKey("cat_name")])
                    val sql = preview.stage.database.openHelper.writableDatabase
                    for (dataset in BackupSchemaV1.datasets) {
                        val count = count(sql, dataset.name)
                        if (dataset.digital && !digital) assertEquals(0, count)
                        else assertEquals("Count ${dataset.name}", count(source.database.openHelper.writableDatabase, dataset.name), count)
                    }
                    assertEquals(0, count(sql, "context_questions")); assertEquals(0, count(sql, "notification_delivery"))
                    sql.query("SELECT endedAt FROM context_events WHERE id = 120").use { it.moveToFirst(); assertEquals(preview.manifest.createdAt, it.getLong(0)) }
                    sql.query("SELECT state, endedAt, pendingModeQuestion FROM mobility_sessions").use { it.moveToFirst(); assertEquals("ARRIVED", it.getString(0)); assertEquals(preview.manifest.createdAt, it.getLong(1)); assertEquals(0, it.getInt(2)) }
                    BackupValidator.validateDatabase(sql)
                    // Verify Room can map restored values, not merely that SQLite accepted the rows.
                    assertEquals(WorkMode.OFFICE, preview.stage.database.routineDao().get()!!.workMode)
                } finally { staging.discard(preview.stage) }
            }
        } finally { staging.discard(source) }
    }

    @Test fun invalidPasswordTruncationDuplicateIdsAndCancellationLeaveSourceUntouched() = runBlocking {
        val source = staging.create()
        try {
            seed(source)
            val settings = SettingsRepository(generationSettingsStore(context, source.generation))
            val exporter = BackupExporter(BackupRepository(dagger.Lazy { source.database }, dagger.Lazy { source.coordinates }, settings))
            val out = ByteArrayOutputStream(); exporter.export(out, secret, true) { }
            val before = count(source.database.openHelper.writableDatabase, "places")
            val data = out.toByteArray()
            val original = StorageGeneration.current(context)
            for ((bytes, password) in listOf(data to "wrong".toCharArray(), data.copyOf(data.size - 20) to secret)) {
                try { BackupImporter(staging).validate(ByteArrayInputStream(bytes), password) { }; fail() } catch (_: BackupException) { }
            }
            try { BackupImporter(staging).validate(ByteArrayInputStream(data), secret) { throw kotlinx.coroutines.CancellationException() }; fail() }
            catch (_: kotlinx.coroutines.CancellationException) { }
            val records = mutableListOf<JSONObject>()
            BackupCrypto.read(ByteArrayInputStream(data), secret) { records.add(BackupJson.parse(it)) }
            val duplicate = ByteArrayOutputStream()
            BackupCrypto.Writer(duplicate, secret).use { writer ->
                records.forEach { row -> writer.record(row.toString().toByteArray()); if (row.optString("dataset") == "places" && row.getJSONObject("data").getLong("id") == 1L) writer.record(row.toString().toByteArray()) }
                writer.finish()
            }
            try { BackupImporter(staging).validate(ByteArrayInputStream(duplicate.toByteArray()), secret) { }; fail() }
            catch (_: Exception) { }
            assertEquals(before, count(source.database.openHelper.writableDatabase, "places"))
            assertEquals(original, StorageGeneration.current(context))
        } finally { staging.discard(source) }
    }

    private fun count(sql: androidx.sqlite.db.SupportSQLiteDatabase, table: String) = sql.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }

    private suspend fun seed(source: BackupStage) {
        val db = source.database
        val start = java.time.Instant.parse("2026-09-01T10:00:00Z").toEpochMilli()
        listOf(PlaceType.HOME, PlaceType.WORK, PlaceType.GYM, PlaceType.RESTAURANT).forEachIndexed { index, type ->
            db.placeDao().insert(PlaceEntity(id = index + 1L, name = type.label, type = type, encryptedCoordinates = source.coordinates.encrypt(-23.5 + index * .01, -46.6), radiusMeters = 150f, confidence = .9f, createdAt = start))
        }
        db.routineDao().upsert(RoutineEntity(workMode = WorkMode.OFFICE, daysMask = 31, startMinute = 540, endMinute = 1080, lunchStartMinute = 720, lunchEndMinute = 780, updatedAt = start))
        val sql = db.openHelper.writableDatabase
        for (day in 0 until 30) {
            for (visit in 0..3) {
                val id = day * 4L + visit + 1
                val at = start + day * 86400000L + visit * 3600000L
                val end = if (id == 120L) null else at + 3000000L
                sql.execSQL("INSERT INTO context_events (id,type,startedAt,endedAt,confidence,placeId,source,venueType) VALUES (?,?,?,?,?,?,?,?)", arrayOf<Any?>(id, "HOME", at, end, .9, visit + 1L, "GEOFENCE", null))
                sql.execSQL("INSERT INTO timeline_events (id,timestamp,actor,emoji,text,sourceType,sourceId) VALUES (?,?,?,?,?,?,?)", arrayOf<Any?>(id, at, "USER", "", "Visita", "CONTEXT", id))
                sql.execSQL("INSERT INTO location_events (placeId,transition,timestamp) VALUES (?,?,?)", arrayOf<Any?>(visit + 1L, "ENTER", at))
            }
            val date = java.time.LocalDate.of(2026, 9, 1).plusDays(day.toLong()).toString()
            sql.execSQL("INSERT INTO daily_device_usage VALUES (?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(date, 60000L, 2, 2, start, start + 60000, 60000L, 0, 1, start))
        }
        sql.execSQL("INSERT INTO memories VALUES (?,?,?,?)", arrayOf<Any?>("first", "", "Primeira memória", start))
        sql.execSQL("INSERT INTO context_confirmations (type,placeId,dayOfWeek,minuteOfDay,accepted,timestamp) VALUES (?,?,?,?,?,?)", arrayOf<Any?>("HOME", 1, 2, 600, 1, start))
        sql.execSQL("INSERT INTO hoodie_activities (activity,startedAt,endedAt,userContext) VALUES (?,?,?,?)", arrayOf<Any?>(HoodieActivity.entries.first().name, start, start + 1000, "HOME"))
        sql.execSQL("INSERT INTO day_exceptions VALUES (?,?,?)", arrayOf<Any?>(20700, "DAY_OFF", start))
        sql.execSQL("INSERT INTO daily_app_usage VALUES (?,?,?,?,?,?,?,?,?)", arrayOf<Any?>("2026-09-01", "com.example.app", "Exemplo", "OTHER", 60000, 2, start, start + 60000, start))
        sql.execSQL("INSERT INTO daily_context_app_usage VALUES (?,?,?,?,?,?)", arrayOf<Any?>("2026-09-01", "HOME", "com.example.app", "Exemplo", 60000, 2))
        sql.execSQL("INSERT INTO daily_screen_hourly VALUES (?,?,?)", arrayOf<Any?>("2026-09-01", 10, 60000))
        sql.execSQL("INSERT INTO daily_context_usage VALUES (?,?,?,?)", arrayOf<Any?>("2026-09-01", "HOME", 60000, 2))
        sql.execSQL("INSERT INTO daily_phone_timeline VALUES (?,?,?,?,?,?,?,?)", arrayOf<Any?>("digital1", "2026-09-01", "com.example.app", "Exemplo", "OTHER", start, start + 60000, "HOME"))
        sql.execSQL("INSERT INTO phone_app_sessions VALUES (?,?,?,?,?)", arrayOf<Any?>("session1", java.time.LocalDate.of(2026, 9, 1).toEpochDay(), "com.example.app", start, start + 60000))
        sql.execSQL("INSERT INTO app_category_overrides VALUES (?,?,?)", arrayOf<Any?>("com.example.app", "OTHER", start))
        sql.execSQL("INSERT INTO learned_routine_slots (dayGroup,type,medianMinute,deviationMinutes,sampleCount,confidence,updatedAt) VALUES (?,?,?,?,?,?,?)", arrayOf<Any?>("WEEKDAY", "WORK_START", 540, 10, 15, .9, start))
        sql.execSQL("INSERT INTO mobility_sessions (startedAt,endedAt,originPlaceId,destinationPlaceId,initialMode,currentMode,state,confidence,confirmed,source,leftOrigin,arrivalConfirmed,arrivalAutoConfirmed,movingSince,stillSince,pendingModeQuestion,questionsAsked,lastObservedMovement,lastObservationAt,lastVehicleAt,vehicleExitAt,pendingMovementMode,pendingMovementAt,speedSampleAttempts,lastSpeedSampleAt,speedSampleCount,meanSpeedKmh,maxSpeedKmh,speedVariation) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(start, null, 1, 2, "WALKING", "WALKING", "WALKING", .9, 1, "ACTIVITY_RECOGNITION", 1, null, 0, start, null, 1, 0, "WALKING", start, null, null, null, null, 0, null, 0, null, null, 0.0))
        sql.execSQL("INSERT INTO mobility_segments (sessionId,mode,startedAt,endedAt,confidence,confirmed,source,evidenceId,confirmedAt) VALUES (?,?,?,?,?,?,?,?,?)", arrayOf<Any?>(1, "WALKING", start, null, .9, 1, "ACTIVITY_RECOGNITION", null, start))
        db.transportEvidenceDao().insert(TransportEvidenceEntity(sessionId = 1, startedAt = start, sampleCount = 3, meanSpeedKmh = 15f, reservationId = "old-work", lastWorkRequestId = "old-request"))
        db.mobilitySessionDao().insert(MobilitySessionEntity(startedAt = start - 10000, endedAt = start - 1000, originPlaceId = 1, destinationPlaceId = 2,
            initialMode = com.hoodie.app.core.mobility.MovementMode.BUS, currentMode = com.hoodie.app.core.mobility.MovementMode.BUS,
            state = com.hoodie.app.core.mobility.MobilityState.ARRIVED, confidence = .9f, source = com.hoodie.app.core.mobility.MobilitySource.CONFIRMATION))
        db.intelligenceDao().saveTransportPattern(TransportPatternEntity(1, 2, "WEEKDAY", 10, "BUS", 4, 0, .9f, start))
        val values = JSONObject()
        mapOf("WAKE" to 420, "SLEEP" to 1380, "WORK_START" to 540, "WORK_END" to 1080, "LUNCH_START" to 720, "LUNCH_END" to 780).forEach { (name, minute) ->
            values.put(name, JSONObject().put("minute", minute).put("origin", "MANUAL").put("count", 0))
        }
        val snapshot = JSONObject().put("date", "2026-09-01").put("from", start).put("workMode", "OFFICE")
            .put("days", org.json.JSONArray(listOf("MONDAY", "TUESDAY"))).put("manualMinutes", org.json.JSONArray(listOf(540,1080,720,780,420,1380))).put("values", values)
        db.effectiveRoutineDao().insert(EffectiveRoutineVersionEntity(epochDay = Long.MIN_VALUE, effectiveFrom = Long.MIN_VALUE, inputKey = "manual", snapshot = snapshot.toString()))
        sql.execSQL("INSERT INTO diary_corrections (targetType,targetId,originalContext,correctedContext,originalPlaceId,correctedPlaceId,originalStartAt,correctedStartAt,originalEndAt,correctedEndAt,originalMode,correctedMode,originalSource,originalConfidence,createdAt,originalVenueType,correctedVenueType) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)", arrayOf<Any?>("CONTEXT", 1, "HOME", "WORK", 1, 2, start, start, start + 1000, start + 1000, null, null, "GEOFENCE", .9, start + 2000, null, null))
    }
}
