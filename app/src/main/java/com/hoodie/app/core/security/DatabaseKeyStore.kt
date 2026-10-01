package com.hoodie.app.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Senha do banco SQLCipher. Nunca fica em BuildConfig, strings.xml ou DataStore:
 * é gerada aleatoriamente no primeiro uso e guardada **cifrada** por uma chave
 * AES-GCM do Android Keystore (não exportável), num arquivo em noBackupFilesDir.
 *
 * Android Keystore → chave de embrulho → senha do banco → SQLCipher → hoodie.db
 */
@Singleton
class DatabaseKeyStore(
    private val context: Context,
    private val alias: String,
    private val fileName: String,
) {
    @Inject constructor(@ApplicationContext context: Context) : this(context, DEFAULT_ALIAS, DEFAULT_FILE)

    private val file: File get() = File(context.noBackupFilesDir, fileName)

    @Volatile private var cached: ByteArray? = null

    /** Senha do banco (64 caracteres hex em UTF-8). Gera na primeira chamada. */
    @Synchronized
    fun passphrase(): ByteArray {
        cached?.let { return it.copyOf() }
        val stored = file.takeIf { it.exists() }?.readText()?.let(::unwrap)
        val pass = stored ?: generate().also { file.writeText(wrap(it)) }
        cached = pass
        return pass.copyOf()
    }

    fun exists(): Boolean = file.exists()

    /** Delete Everything: apaga a senha embrulhada e a chave do Keystore. */
    @Synchronized
    fun delete() {
        cached?.fill(0)
        cached = null
        file.delete()
        runCatching { keystore().deleteEntry(alias) }
    }

    private fun generate(): ByteArray {
        val raw = ByteArray(RAW_BYTES).also { SecureRandom().nextBytes(it) }
        return raw.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.UTF_8)
    }

    private fun wrap(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
    }

    private fun unwrap(payload: String): ByteArray? = runCatching {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
    }.getOrNull()

    private fun keystore() = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }

    private fun existingKey(): SecretKey? = (keystore().getEntry(alias, null) as? KeyStore.SecretKeyEntry)?.secretKey

    private fun wrappingKey(): SecretKey = existingKey() ?: KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE).run {
        init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(KEY_BITS)
                .build(),
        )
        generateKey()
    }

    companion object {
        const val DEFAULT_ALIAS = "hoodie_db_key_wrap"
        const val DEFAULT_FILE = "hoodie_db.key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val RAW_BYTES = 32
        private const val KEY_BITS = 256
        private const val IV_BYTES = 12
        private const val TAG_BITS = 128
    }
}
