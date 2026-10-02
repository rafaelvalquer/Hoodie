package com.hoodie.app.core.database

import com.hoodie.app.core.security.DatabaseKeyResult
import com.hoodie.app.core.security.DatabaseKeyStore
import com.hoodie.app.core.security.DatabaseKeyStoreTest
import com.hoodie.app.core.security.LocalDataReset
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/**
 * Orquestração da segurança do banco com arquivos reais (pasta temporária) e um
 * "SQLCipher" de mentira. Cobre: nova instalação, banco legado em texto puro,
 * migração correta, interrompida e inválida, senha incorreta, chave corrompida,
 * Keystore sem chave, banco cifrado normal e apagar tudo.
 */
class SecureDatabaseBootstrapTest {

    private val header = "SQLite format 3\u0000"

    /** Formato fake: texto puro = cabeçalho SQLite + "versão|tabelas"; cifrado = "ENC:<senha>:" + mesmo conteúdo. */
    private inner class FakeOps : DatabaseCipherOps {
        var failExport: String? = null
        var exportPlaintext = false
        var dropTable = false
        var wrongVersion = false

        override fun exportEncrypted(plain: File, target: File, passphrase: ByteArray): DatabaseFingerprint {
            failExport?.let { target.writeText("parcial"); throw IllegalStateException(it) }
            val content = plain.readText().removePrefix(header)
            val source = parse(content)
            var copy = source
            if (dropTable) copy = copy.copy(tables = copy.tables - copy.tables.first())
            if (wrongVersion) copy = copy.copy(userVersion = copy.userVersion + 1)
            target.writeText((if (exportPlaintext) header else "ENC:${String(passphrase)}:") + format(copy))
            return source
        }

        override fun inspect(file: File, passphrase: ByteArray): DatabaseFingerprint? {
            val text = file.readText()
            val prefix = "ENC:${String(passphrase)}:"
            return if (text.startsWith(prefix)) parse(text.removePrefix(prefix)) else null
        }

        private fun format(f: DatabaseFingerprint) = "${f.userVersion}|${f.tables.sorted().joinToString(",")}"
        private fun parse(s: String): DatabaseFingerprint {
            val (v, t) = s.split('|')
            return DatabaseFingerprint(v.toInt(), t.split(',').filter { it.isNotEmpty() }.toSet())
        }
    }

    private val dir: File = Files.createTempDirectory("hoodie-db").toFile()
    private val db = File(dir, "hoodie.db")
    private val wrapper = DatabaseKeyStoreTest.FakeWrapper()
    private val ops = FakeOps()
    private val states = mutableListOf<DatabaseSecurityState>()
    private fun keys() = DatabaseKeyStore(dir, "db.key", wrapper)
    private fun bootstrap() = DefaultSecureDatabaseBootstrap(db, keys(), ops) { states += it }
    private fun legacyPlaintext() = db.writeText(header + "3|places,timeline_events,memories")
    private val tmp get() = File(dir, "hoodie.db" + DefaultSecureDatabaseBootstrap.TMP_SUFFIX)

    @Test
    fun `nova instalacao - cria senha e deixa o Room criar o banco cifrado`() {
        val r = bootstrap().prepare()
        assertTrue(r is DatabaseBootstrapResult.Ready)
        assertTrue(keys().exists())
        assertFalse(db.exists())
        assertEquals(listOf(DatabaseSecurityState.Encrypted), states)
    }

    @Test
    fun `banco legado em texto puro e migrado, validado e trocado`() {
        legacyPlaintext()
        val r = bootstrap().prepare() as DatabaseBootstrapResult.Ready
        assertFalse("não pode continuar em texto puro", DefaultSecureDatabaseBootstrap.isPlaintext(db))
        assertEquals(DatabaseFingerprint(3, setOf("places", "timeline_events", "memories")), ops.inspect(db, r.passphrase))
        assertFalse("sem sobras do temporário", tmp.exists())
        assertEquals(listOf(DatabaseSecurityState.MigrationRequired, DatabaseSecurityState.Encrypted), states)
    }

    @Test
    fun `migracao interrompida nao toca no original, nao abre em texto puro e tenta de novo`() {
        legacyPlaintext()
        val original = db.readBytes()
        ops.failExport = "disco cheio"
        val r = bootstrap().prepare()
        assertTrue(r is DatabaseBootstrapResult.Failed)
        assertTrue((r as DatabaseBootstrapResult.Failed).state is DatabaseSecurityState.MigrationFailed)
        assertArrayEquals("original intacto", original, db.readBytes())
        assertFalse(tmp.exists())

        // Próxima abertura: a migração roda de novo com a MESMA senha.
        ops.failExport = null
        val retry = bootstrap().prepare()
        assertTrue(retry is DatabaseBootstrapResult.Ready)
        assertFalse(DefaultSecureDatabaseBootstrap.isPlaintext(db))
    }

    @Test
    fun `copia invalida e rejeitada antes da troca`() {
        for (break_ in listOf<FakeOps.() -> Unit>({ exportPlaintext = true }, { dropTable = true }, { wrongVersion = true })) {
            db.delete(); states.clear()
            legacyPlaintext()
            val original = db.readBytes()
            val o = FakeOps().apply(break_)
            val r = DefaultSecureDatabaseBootstrap(db, keys(), o) { states += it }.prepare()
            assertTrue(r is DatabaseBootstrapResult.Failed)
            assertArrayEquals(original, db.readBytes())
            assertFalse(tmp.exists())
        }
    }

    @Test
    fun `banco cifrado normal abre`() {
        val pass = keys().createForFirstInstall()
        db.writeText("ENC:${String(pass)}:3|places")
        assertTrue(bootstrap().prepare() is DatabaseBootstrapResult.Ready)
    }

    @Test
    fun `senha incorreta e KeyUnrecoverable`() {
        keys().createForFirstInstall()
        db.writeText("ENC:outra-senha:3|places")
        val r = bootstrap().prepare() as DatabaseBootstrapResult.Failed
        assertEquals(DatabaseSecurityState.KeyUnrecoverable, r.state)
    }

    @Test
    fun `arquivo de chave corrompido e KeyUnrecoverable e nada e regenerado`() {
        val pass = keys().createForFirstInstall()
        db.writeText("ENC:${String(pass)}:3|places")
        File(dir, "db.key").writeText("corrompido")
        val r = bootstrap().prepare() as DatabaseBootstrapResult.Failed
        assertEquals(DatabaseSecurityState.KeyUnrecoverable, r.state)
        assertEquals("corrompido", File(dir, "db.key").readText())
    }

    @Test
    fun `Keystore sem chave e KeyUnrecoverable`() {
        val pass = keys().createForFirstInstall()
        db.writeText("ENC:${String(pass)}:3|places")
        wrapper.keyPresent = false
        assertEquals(DatabaseSecurityState.KeyUnrecoverable, (bootstrap().prepare() as DatabaseBootstrapResult.Failed).state)
    }

    @Test
    fun `banco cifrado sem arquivo de senha nao gera senha nova`() {
        db.writeText("ENC:senha-perdida:3|places")
        val r = bootstrap().prepare() as DatabaseBootstrapResult.Failed
        assertEquals(DatabaseSecurityState.KeyUnrecoverable, r.state)
        assertEquals(DatabaseKeyResult.FirstInstall, keys().read())
    }

    @Test
    fun `sobra de migracao anterior e descartada`() {
        legacyPlaintext()
        tmp.writeText("lixo de um crash")
        assertTrue(bootstrap().prepare() is DatabaseBootstrapResult.Ready)
        assertFalse(tmp.exists())
    }

    @Test
    fun `apagar tudo remove banco, auxiliares, temporario e senha`() {
        val k = keys()
        k.createForFirstInstall()
        listOf("", "-wal", "-shm", "-journal", DefaultSecureDatabaseBootstrap.TMP_SUFFIX).forEach { File(db.path + it).writeText("x") }
        assertTrue(LocalDataReset.deleteDatabaseFiles(db).isEmpty())
        k.delete()
        assertFalse(db.exists())
        assertEquals(DatabaseKeyResult.FirstInstall, keys().read())
        // Depois de apagar, recomeça como instalação nova.
        assertTrue(bootstrap().prepare() is DatabaseBootstrapResult.Ready)
    }

    @Test
    fun `gate guarda o sucesso e permite tentar de novo depois de falhar`() {
        legacyPlaintext()
        ops.failExport = "falha"
        val gate = DatabaseGate { publish -> DefaultSecureDatabaseBootstrap(db, keys(), ops, publish) }
        assertTrue(gate.ensure() is DatabaseBootstrapResult.Failed)
        assertTrue(gate.state.value is DatabaseSecurityState.MigrationFailed)
        assertFalse(gate.isReady())
        ops.failExport = null
        assertTrue(gate.retry() is DatabaseBootstrapResult.Ready)
        assertEquals(DatabaseSecurityState.Encrypted, gate.state.value)
        // Depois de pronto não roda o bootstrap de novo.
        ops.failExport = "não deveria ser chamado"
        assertTrue(gate.ensure() is DatabaseBootstrapResult.Ready)
    }
}
