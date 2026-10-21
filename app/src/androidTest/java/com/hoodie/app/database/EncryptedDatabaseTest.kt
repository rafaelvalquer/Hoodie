package com.hoodie.app.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlainSQLiteDatabase
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hoodie.app.core.database.DatabaseBootstrapResult
import com.hoodie.app.core.database.DatabaseEncryption
import com.hoodie.app.core.database.DatabaseSecurityState
import com.hoodie.app.core.database.DefaultSecureDatabaseBootstrap
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.HOODIE_DATABASE_VERSION
import com.hoodie.app.core.database.CorrectionUndoEntity
import com.hoodie.app.core.database.DeliveryStatus
import com.hoodie.app.core.database.NotificationDeliveryEntity
import com.hoodie.app.core.database.UndoStatus
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.database.migrations.ALL_MIGRATIONS
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.security.DatabaseKeyResult
import com.hoodie.app.core.security.DatabaseKeyStore
import java.io.File
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.Rule
import org.junit.runner.RunWith

/**
 * CT-SEC-001..004. Usa nomes próprios de banco/chave para nunca tocar nos dados
 * reais do app instalado.
 */
@RunWith(AndroidJUnit4::class)
class EncryptedDatabaseTest {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbFile get() = context.getDatabasePath(DB)
    private fun keys() = DatabaseKeyStore(context, ALIAS, KEY_FILE)

    @get:Rule
    val migrationHelper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), HoodieDatabase::class.java)

    /** Senha atual (cria na primeira vez), como o bootstrap faz. */
    private fun pass(): ByteArray = when (val k = keys().read()) {
        is DatabaseKeyResult.Available -> k.passphrase
        DatabaseKeyResult.FirstInstall -> keys().createForFirstInstall()
        DatabaseKeyResult.Unrecoverable -> error("chave irrecuperável no teste")
    }

    private fun bootstrap() = DefaultSecureDatabaseBootstrap(dbFile, keys(), DatabaseEncryption)

    @Before
    fun setUp() {
        DatabaseEncryption.loadLibrary()
        cleanup()
    }

    @After
    fun cleanup() {
        context.deleteDatabase(DB)
        context.deleteDatabase(LEGACY_DB)
        keys().delete()
    }

    private fun open(pass: ByteArray) = Room.databaseBuilder(context, HoodieDatabase::class.java, DB)
        .openHelperFactory(SupportOpenHelperFactory(pass))
        .addMigrations(*ALL_MIGRATIONS)
        .build()

    @Test
    fun ctSec001_instalacaoNovaJaNasceCifrada() = runBlocking {
        val db = open(pass())
        db.timelineDao().insert(TimelineEventEntity(timestamp = 1, actor = TimelineActor.USER, emoji = "🏠", text = "Casa"))
        db.close()
        assertFalse("arquivo não pode ter cabeçalho SQLite legível", DatabaseEncryption.isPlaintext(dbFile))
        assertFalse(dbFile.readBytes().toString(Charsets.ISO_8859_1).contains("Casa"))
    }

    @Test
    fun ctSec002_upgradeDeTextoPuroParaCifradoSemPerderDados() = runBlocking {
        // Banco no schema atual em texto puro; a migração histórica tem teste próprio abaixo.
        val plainRoom = Room.databaseBuilder(context, HoodieDatabase::class.java, DB).addMigrations(*ALL_MIGRATIONS).build()
        plainRoom.timelineDao().insert(TimelineEventEntity(timestamp = 1, actor = TimelineActor.USER, emoji = "🏠", text = "Casa antiga"))
        plainRoom.close()
        assertTrue(DatabaseEncryption.isPlaintext(dbFile))

        // Bootstrap real: exporta, VALIDA com SQLCipher e só então troca o arquivo.
        val ready = bootstrap().prepare() as DatabaseBootstrapResult.Ready
        val pass = ready.passphrase
        assertFalse(DatabaseEncryption.isPlaintext(dbFile))
        assertFalse(File(dbFile.path + DefaultSecureDatabaseBootstrap.TMP_SUFFIX).exists())
        assertTrue("segunda abertura só confere a senha", bootstrap().prepare() is DatabaseBootstrapResult.Ready)

        // Integridade do banco migrado no SQLCipher real.
        val check = DatabaseEncryption.verify(dbFile, pass)!!
        assertTrue("quick_check", check.quickCheckOk)
        assertTrue("cipher_integrity_check", check.cipherIntegrityOk != false)
        assertEquals(1L, check.rowCounts["timeline_events"])

        val db = open(pass)
        assertEquals(listOf("Casa antiga"), db.timelineDao().range(0, Long.MAX_VALUE).map { it.text })
        db.close()
    }

    @Test
    fun encryptedSchema11PreservesRelatedHistoryThroughMigrationAndReopen() = runBlocking {
        migrationHelper.createDatabase(LEGACY_DB, 11).use { db ->
            db.execSQL("INSERT INTO places VALUES (71, 'Casa antiga', 'HOME', 'ciphertext-fixture', 120, 0.9, 1, 3, 10, 20)")
            db.execSQL("INSERT INTO context_events (id,type,startedAt,endedAt,confidence,placeId,source,venueType) VALUES (72,'HOME',100,200,0.9,71,'GEOFENCE',NULL)")
            db.execSQL("INSERT INTO timeline_events (id,timestamp,actor,emoji,text,sourceType,sourceId) VALUES (73,100,'USER','','Chegada antiga','CONTEXT',72)")
            db.execSQL("INSERT INTO routines VALUES (1,'OFFICE',31,510,1050,720,780,1000)")
        }
        val password = pass()
        try {
            val original = DatabaseEncryption.exportEncrypted(context.getDatabasePath(LEGACY_DB), dbFile, password)
            assertEquals(11, original.userVersion)
            assertEquals(1L, original.rowCounts["context_events"])
            assertFalse(DatabaseEncryption.isPlaintext(dbFile))
            assertEquals(11, DatabaseEncryption.verify(dbFile, password)!!.userVersion)

            val migrated = open(password.copyOf())
            try {
                assertEquals(HOODIE_DATABASE_VERSION, migrated.openHelper.writableDatabase.version)
                assertEquals(71L, migrated.placeDao().getAll().single().id)
                assertEquals("Casa antiga", migrated.placeDao().getAll().single().name)
                assertEquals(510, migrated.routineDao().get()!!.startMinute)
                val event = migrated.contextEventDao().all().single()
                assertEquals(72L, event.id)
                assertEquals(71L, event.placeId!!)
                assertEquals(100L, event.startedAt)
                assertEquals(200L, event.endedAt!!)
                val timeline = migrated.timelineDao().range(0, Long.MAX_VALUE).single()
                assertEquals(73L, timeline.id)
                assertEquals("Chegada antiga", timeline.text)
                assertEquals(72L, timeline.sourceId!!)
                assertEquals("CONTEXT", timeline.sourceType!!.name)
                migrated.correctionUndoDao().insert(CorrectionUndoEntity(operationId = "migration-op", kind = "FROM_NOW", formatVersion = 1, windowFrom = 100, snapshot = "{}", createdAt = 1, expiresAt = 2, status = UndoStatus.AVAILABLE))
                val delivery = NotificationDeliveryEntity(questionId = null, kind = "QUESTION", text = "Teste", createdAt = 1, nextEligibleAt = 1, expiresAt = 9, deliveredAt = null, status = DeliveryStatus.PENDING, attemptCount = 0, dedupeKey = "migration-delivery")
                assertTrue(migrated.notificationDeliveryDao().insertIgnore(delivery) > 0)
                assertEquals(-1L, migrated.notificationDeliveryDao().insertIgnore(delivery))
                migrated.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            } finally { migrated.close() }

            val reopened = open(password.copyOf())
            try {
                assertEquals(HOODIE_DATABASE_VERSION, reopened.openHelper.writableDatabase.version)
                assertEquals(1, reopened.contextEventDao().count())
                assertEquals(listOf("Chegada antiga"), reopened.timelineDao().range(0, Long.MAX_VALUE).map { it.text })
                assertEquals("migration-op", reopened.correctionUndoDao().byOperation("migration-op")!!.operationId)
                assertEquals(1, reopened.notificationDeliveryDao().due(5).size)
            } finally { reopened.close() }
            val integrity = DatabaseEncryption.verify(dbFile, password)!!
            assertTrue(integrity.quickCheckOk)
            assertEquals(true, integrity.cipherIntegrityOk)
            original.rowCounts.forEach { (table, count) ->
                assertEquals("Preserved count for $table", count, integrity.rowCounts.getValue(table))
            }
            assertEquals(1L, integrity.rowCounts.getValue("correction_undo"))
            assertEquals(1L, integrity.rowCounts.getValue("notification_delivery"))
        } finally { password.fill(0) }
    }

    @Test
    fun ctSec003_reaberturaComNovaInstanciaUsaAMesmaSenha() = runBlocking {
        val first = pass()
        open(first).apply {
            timelineDao().insert(TimelineEventEntity(timestamp = 1, actor = TimelineActor.USER, emoji = "🏢", text = "Trabalho"))
            close()
        }
        // "Reboot": nova instância, sem cache em memória.
        val second = pass()
        assertArrayEquals(first, second)
        val db = open(second)
        assertEquals(1, db.timelineDao().count())
        db.close()
    }

    @Test
    fun ctSec004_deleteRemoveBancoESenha() {
        val before = pass()
        open(before).apply { openHelper.writableDatabase; close() } // Room só cria o arquivo ao abrir
        assertTrue(dbFile.exists())
        context.deleteDatabase(DB)
        keys().delete()
        assertFalse(dbFile.exists())
        assertFalse(keys().exists())
        assertFalse("senha nova depois de apagar", before.contentEquals(pass()))
    }

    @Test
    fun senhaIrrecuperavelNaoAbreNemRegeneraSenha() {
        open(pass()).apply { openHelper.writableDatabase; close() }
        // Simula o Keystore perdendo a chave: o arquivo de senha existe mas não decifra.
        File(context.noBackupFilesDir, KEY_FILE).writeText("corrompido")
        val r = bootstrap().prepare() as DatabaseBootstrapResult.Failed
        assertEquals(DatabaseSecurityState.KeyUnrecoverable, r.state)
        assertEquals("corrompido", File(context.noBackupFilesDir, KEY_FILE).readText())
        assertFalse(DatabaseEncryption.isPlaintext(dbFile))
    }

    @Test
    fun senhaErradaNaoAbre() {
        open(pass()).apply { openHelper.writableDatabase; close() }
        val wrong = open("x".repeat(64).toByteArray())
        val failed = runCatching { wrong.openHelper.writableDatabase }.isFailure
        wrong.close()
        assertTrue(failed)
        // Nem o SQLite comum consegue ler.
        assertTrue(runCatching { PlainSQLiteDatabase.openDatabase(dbFile.path, null, PlainSQLiteDatabase.OPEN_READONLY).version }.isFailure)
    }

    private companion object {
        const val DB = "encrypted-test.db"
        const val LEGACY_DB = "encrypted-legacy-v11-test.db"
        const val ALIAS = "hoodie_test_db_key"
        const val KEY_FILE = "hoodie_test_db.key"
    }
}
