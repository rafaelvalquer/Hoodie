package com.hoodie.app.database

import android.content.Context
import android.database.sqlite.SQLiteDatabase as PlainSQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hoodie.app.core.database.DatabaseEncryption
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.TimelineEventEntity
import com.hoodie.app.core.database.migrations.ALL_MIGRATIONS
import com.hoodie.app.core.model.TimelineActor
import com.hoodie.app.core.security.DatabaseKeyStore
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
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

    @Before
    fun setUp() {
        DatabaseEncryption.loadLibrary()
        cleanup()
    }

    @After
    fun cleanup() {
        context.deleteDatabase(DB)
        keys().delete()
    }

    private fun open(pass: ByteArray) = Room.databaseBuilder(context, HoodieDatabase::class.java, DB)
        .openHelperFactory(SupportOpenHelperFactory(pass))
        .addMigrations(*ALL_MIGRATIONS)
        .build()

    @Test
    fun ctSec001_instalacaoNovaJaNasceCifrada() = runBlocking {
        val db = open(keys().passphrase())
        db.timelineDao().insert(TimelineEventEntity(timestamp = 1, actor = TimelineActor.USER, emoji = "🏠", text = "Casa"))
        db.close()
        assertFalse("arquivo não pode ter cabeçalho SQLite legível", DatabaseEncryption.isPlaintext(dbFile))
        assertFalse(dbFile.readBytes().toString(Charsets.ISO_8859_1).contains("Casa"))
    }

    @Test
    fun ctSec002_upgradeDeTextoPuroParaCifradoSemPerderDados() = runBlocking {
        // Banco v3 em texto puro, como uma instalação anterior deixaria.
        val plainRoom = Room.databaseBuilder(context, HoodieDatabase::class.java, DB).addMigrations(*ALL_MIGRATIONS).build()
        plainRoom.timelineDao().insert(TimelineEventEntity(timestamp = 1, actor = TimelineActor.USER, emoji = "🏠", text = "Casa antiga"))
        plainRoom.close()
        assertTrue(DatabaseEncryption.isPlaintext(dbFile))

        val pass = keys().passphrase()
        assertTrue(DatabaseEncryption.migrateIfNeeded(dbFile, pass))
        assertFalse(DatabaseEncryption.isPlaintext(dbFile))
        assertFalse("segunda chamada não faz nada", DatabaseEncryption.migrateIfNeeded(dbFile, pass))

        val db = open(pass)
        assertEquals(listOf("Casa antiga"), db.timelineDao().range(0, Long.MAX_VALUE).map { it.text })
        db.close()
    }

    @Test
    fun ctSec003_reaberturaComNovaInstanciaUsaAMesmaSenha() = runBlocking {
        val first = keys().passphrase()
        open(first).apply {
            timelineDao().insert(TimelineEventEntity(timestamp = 1, actor = TimelineActor.USER, emoji = "🏢", text = "Trabalho"))
            close()
        }
        // "Reboot": nova instância, sem cache em memória.
        val second = keys().passphrase()
        assertArrayEquals(first, second)
        val db = open(second)
        assertEquals(1, db.timelineDao().count())
        db.close()
    }

    @Test
    fun ctSec004_deleteRemoveBancoESenha() {
        val before = keys().passphrase()
        open(before).apply { openHelper.writableDatabase; close() } // Room só cria o arquivo ao abrir
        assertTrue(dbFile.exists())
        context.deleteDatabase(DB)
        keys().delete()
        assertFalse(dbFile.exists())
        assertFalse(keys().exists())
        assertFalse("senha nova depois de apagar", before.contentEquals(keys().passphrase()))
    }

    @Test
    fun senhaErradaNaoAbre() {
        open(keys().passphrase()).apply { openHelper.writableDatabase; close() }
        val wrong = open("x".repeat(64).toByteArray())
        val failed = runCatching { wrong.openHelper.writableDatabase }.isFailure
        wrong.close()
        assertTrue(failed)
        // Nem o SQLite comum consegue ler.
        assertTrue(runCatching { PlainSQLiteDatabase.openDatabase(dbFile.path, null, PlainSQLiteDatabase.OPEN_READONLY).version }.isFailure)
    }

    private companion object {
        const val DB = "encrypted-test.db"
        const val ALIAS = "hoodie_test_db_key"
        const val KEY_FILE = "hoodie_test_db.key"
    }
}
