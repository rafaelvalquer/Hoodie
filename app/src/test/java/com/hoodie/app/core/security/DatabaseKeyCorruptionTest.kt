package com.hoodie.app.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.util.Base64
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Mesmo AES-256-GCM do app (AesGcmKeyWrapper), com a chave em memória no lugar do
 * Android Keystore: cada forma de corrupção vira Unrecoverable e NADA é regenerado.
 */
class DatabaseKeyCorruptionTest {

    private var key: SecretKey? = null
    private val wrapper = AesGcmKeyWrapper(
        existingKey = { key },
        createKey = { KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also { key = it } },
        removeKey = { key = null },
    )
    private val dir: File = Files.createTempDirectory("hoodie-gcm").toFile()
    private fun store() = DatabaseKeyStore(dir, "db.key", wrapper)
    private val keyFile get() = File(dir, "db.key")

    private fun assertUnrecoverableAndUntouched() {
        val before = keyFile.readBytes()
        assertEquals(DatabaseKeyResult.Unrecoverable, store().read())
        assertArrayEquals("o arquivo não pode ser regravado", before, keyFile.readBytes())
        assertFalse("sem .tmp esquecido", File(dir, "db.key.tmp").exists())
    }

    @Test
    fun `ida e volta com GCM real`() {
        val pass = store().createForFirstInstall()
        assertArrayEquals(pass, (store().read() as DatabaseKeyResult.Available).passphrase)
        assertFalse(File(dir, "db.key.tmp").exists())
    }

    @Test
    fun `arquivo truncado`() {
        store().createForFirstInstall()
        keyFile.writeText(keyFile.readText().let { it.take(it.length / 2) })
        assertUnrecoverableAndUntouched()
    }

    @Test
    fun `payload Base64 invalido`() {
        store().createForFirstInstall()
        keyFile.writeText("@@não-é-base64@@")
        assertUnrecoverableAndUntouched()
    }

    @Test
    fun `tag GCM invalida`() {
        store().createForFirstInstall()
        val bytes = Base64.getDecoder().decode(keyFile.readText())
        bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
        keyFile.writeText(Base64.getEncoder().encodeToString(bytes))
        assertUnrecoverableAndUntouched()
    }

    @Test
    fun `chave removida do Keystore`() {
        store().createForFirstInstall()
        key = null
        assertUnrecoverableAndUntouched()
        assertEquals("unwrap nunca cria chave nova", null, key)
    }

    @Test
    fun `arquivo vazio`() {
        store().createForFirstInstall()
        keyFile.writeText("")
        assertUnrecoverableAndUntouched()
    }

    @Test
    fun `outra chave do Keystore nao abre`() {
        store().createForFirstInstall()
        key = KeyGenerator.getInstance("AES").apply { init(256) }.generateKey()
        assertUnrecoverableAndUntouched()
    }

    @Test
    fun `gravacao atomica nao deixa temporario e recusa sobrescrever`() {
        store().createForFirstInstall()
        assertTrue(keyFile.exists())
        assertTrue(runCatching { store().createForFirstInstall() }.isFailure)
    }
}
