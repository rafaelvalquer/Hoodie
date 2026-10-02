package com.hoodie.app.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64

/** Regras da senha do banco: nunca gerar uma senha nova por cima de uma existente. */
class DatabaseKeyStoreTest {

    /** "Keystore" de mentira: embrulho reversível enquanto a chave existir. */
    class FakeWrapper : KeyWrapper {
        var keyPresent = true
        var deleted = false
        override fun wrap(plain: ByteArray): String { keyPresent = true; return "W1:" + Base64.getEncoder().encodeToString(plain.reversedArray()) }
        override fun unwrap(payload: String): ByteArray? {
            if (!keyPresent || !payload.startsWith("W1:")) return null
            return runCatching { Base64.getDecoder().decode(payload.removePrefix("W1:")).reversedArray() }.getOrNull()
        }
        override fun deleteKey() { keyPresent = false; deleted = true }
    }

    private val dir: File = Files.createTempDirectory("hoodie-keys").toFile()
    private val wrapper = FakeWrapper()
    private fun store() = DatabaseKeyStore(dir, "db.key", wrapper)
    private val keyFile get() = File(dir, "db.key")

    @Test
    fun `nova instalacao - sem arquivo e FirstInstall e gera senha`() {
        assertEquals(DatabaseKeyResult.FirstInstall, store().read())
        val pass = store().createForFirstInstall()
        assertEquals(DatabaseKeyStore.PASS_CHARS, pass.size)
        assertTrue(keyFile.exists())
    }

    @Test
    fun `reabertura com nova instancia devolve a mesma senha`() {
        val created = store().createForFirstInstall()
        val read = store().read() as DatabaseKeyResult.Available
        assertArrayEquals(created, read.passphrase)
    }

    @Test
    fun `arquivo de chave corrompido e Unrecoverable e nao e sobrescrito`() {
        store().createForFirstInstall()
        keyFile.writeText("lixo-que-nao-decifra")
        val before = keyFile.readText()
        assertEquals(DatabaseKeyResult.Unrecoverable, store().read())
        assertEquals("o arquivo antigo não pode ser trocado", before, keyFile.readText())
    }

    @Test
    fun `Keystore sem a chave e Unrecoverable`() {
        store().createForFirstInstall()
        wrapper.keyPresent = false // ex.: usuário removeu a trava de tela / restore em outro aparelho
        assertEquals(DatabaseKeyResult.Unrecoverable, store().read())
    }

    @Test
    fun `arquivo vazio ou senha de tamanho errado e Unrecoverable`() {
        keyFile.writeText("")
        assertEquals(DatabaseKeyResult.Unrecoverable, store().read())
        keyFile.writeText(wrapper.wrap("curta".toByteArray()))
        assertEquals(DatabaseKeyResult.Unrecoverable, store().read())
    }

    @Test(expected = IllegalStateException::class)
    fun `nunca gera senha nova quando ja existe arquivo`() {
        store().createForFirstInstall()
        keyFile.writeText("corrompido")
        store().createForFirstInstall()
    }

    @Test
    fun `apagar tudo remove arquivo e chave e volta a ser primeira instalacao`() {
        val s = store()
        s.createForFirstInstall()
        s.delete()
        assertFalse(keyFile.exists())
        assertTrue(wrapper.deleted)
        assertEquals(DatabaseKeyResult.FirstInstall, store().read())
    }
}
