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

/** Resultado da leitura da senha do banco. Nunca há "gerar outra" quando a antiga existe. */
sealed interface DatabaseKeyResult {
    class Available(val passphrase: ByteArray) : DatabaseKeyResult

    /** Não existe arquivo de senha: primeira instalação (ou depois de apagar tudo). */
    data object FirstInstall : DatabaseKeyResult

    /** O arquivo existe mas não dá para recuperar a senha (Keystore perdeu a chave, arquivo corrompido). */
    data object Unrecoverable : DatabaseKeyResult
}

/** Embrulha/desembrulha a senha com uma chave que nunca sai do hardware. */
interface KeyWrapper {
    fun wrap(plain: ByteArray): String

    /** null quando a chave sumiu ou o conteúdo não decifra. */
    fun unwrap(payload: String): ByteArray?

    fun deleteKey()
}

/** Fonte da senha do SQLCipher, separada do Android para ser testável. */
interface DatabaseKeySource {
    fun read(): DatabaseKeyResult

    /** Só na primeira instalação: falha se já existir um arquivo de senha. */
    fun createForFirstInstall(): ByteArray

    fun exists(): Boolean

    fun delete()
}

/**
 * Senha do banco SQLCipher. Nunca fica em BuildConfig, strings.xml ou DataStore:
 * é gerada aleatoriamente na primeira instalação e guardada **cifrada** por uma chave
 * AES-GCM do Android Keystore (não exportável), num arquivo em noBackupFilesDir.
 *
 *     arquivo não existe            → FirstInstall (gera senha nova)
 *     arquivo existe + unwrap ok    → Available
 *     arquivo existe + unwrap falha → Unrecoverable (NUNCA gera outra: o banco ficaria inacessível)
 */
@Singleton
class DatabaseKeyStore(
    private val dir: File,
    private val fileName: String,
    private val wrapper: KeyWrapper,
) : DatabaseKeySource {

    @Inject constructor(@ApplicationContext context: Context) :
        this(context.noBackupFilesDir, DEFAULT_FILE, AndroidKeystoreWrapper(DEFAULT_ALIAS))

    /** Instâncias isoladas para testes instrumentados (alias/arquivo próprios). */
    constructor(context: Context, alias: String, fileName: String) :
        this(context.noBackupFilesDir, fileName, AndroidKeystoreWrapper(alias))

    private val file: File get() = File(dir, fileName)

    @Volatile private var cached: ByteArray? = null

    @Synchronized
    override fun read(): DatabaseKeyResult {
        cached?.let { return DatabaseKeyResult.Available(it.copyOf()) }
        if (!file.exists()) return DatabaseKeyResult.FirstInstall
        val payload = runCatching { file.readText() }.getOrNull()?.trim()
        if (payload.isNullOrEmpty()) return DatabaseKeyResult.Unrecoverable
        val pass = runCatching { wrapper.unwrap(payload) }.getOrNull() ?: return DatabaseKeyResult.Unrecoverable
        if (pass.size != PASS_CHARS) return DatabaseKeyResult.Unrecoverable
        cached = pass
        return DatabaseKeyResult.Available(pass.copyOf())
    }

    @Synchronized
    override fun createForFirstInstall(): ByteArray {
        check(!file.exists()) { "Já existe uma senha de banco: recusando gerar outra" }
        val pass = generate()
        // Escrita atômica: nunca deixa um arquivo de senha pela metade.
        dir.mkdirs()
        val tmp = File(dir, "$fileName.tmp")
        tmp.writeText(wrapper.wrap(pass))
        check(tmp.renameTo(file)) { "Falha ao gravar a senha do banco" }
        cached = pass
        return pass.copyOf()
    }

    override fun exists(): Boolean = file.exists()

    /** Delete Everything: apaga a senha embrulhada e a chave do Keystore. */
    @Synchronized
    override fun delete() {
        cached?.fill(0)
        cached = null
        file.delete()
        File(dir, "$fileName.tmp").delete()
        runCatching { wrapper.deleteKey() }
    }

    private fun generate(): ByteArray {
        val raw = ByteArray(RAW_BYTES).also { SecureRandom().nextBytes(it) }
        return raw.joinToString("") { "%02x".format(it) }.toByteArray(Charsets.UTF_8)
    }

    companion object {
        const val DEFAULT_ALIAS = "hoodie_db_key_wrap"
        const val DEFAULT_FILE = "hoodie_db.key"
        private const val RAW_BYTES = 32
        /** 32 bytes em hexadecimal. */
        const val PASS_CHARS = RAW_BYTES * 2
    }
}

/** Embrulho AES-256-GCM com chave do Android Keystore. */
class AndroidKeystoreWrapper(private val alias: String) : KeyWrapper {

    override fun wrap(plain: ByteArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(plain), Base64.NO_WRAP)
    }

    override fun unwrap(payload: String): ByteArray? = runCatching {
        val bytes = Base64.decode(payload, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        // Sem a chave no Keystore não há o que tentar: NÃO cria uma chave nova aqui.
        cipher.init(Cipher.DECRYPT_MODE, existingKey() ?: return null, GCMParameterSpec(TAG_BITS, bytes, 0, IV_BYTES))
        cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES)
    }.getOrNull()

    override fun deleteKey() {
        keystore().deleteEntry(alias)
    }

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

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_BITS = 256
        const val IV_BYTES = 12
        const val TAG_BITS = 128
    }
}
