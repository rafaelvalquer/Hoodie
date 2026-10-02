package com.hoodie.app.core.database

import com.hoodie.app.core.security.DatabaseKeyResult
import com.hoodie.app.core.security.DatabaseKeySource
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** O que um banco contém, para comparar origem e cópia cifrada. */
data class DatabaseFingerprint(val userVersion: Int, val tables: Set<String>)

/** Operações SQLCipher, separadas da orquestração para teste na JVM. */
interface DatabaseCipherOps {
    /** Copia o banco em texto puro [plain] para [target] cifrado; devolve o que havia na origem. */
    fun exportEncrypted(plain: File, target: File, passphrase: ByteArray): DatabaseFingerprint

    /** Abre [file] cifrado com [passphrase]; null se não abrir (senha errada, arquivo inválido). */
    fun inspect(file: File, passphrase: ByteArray): DatabaseFingerprint?
}

/** Prepara o banco para o Room abrir — sempre cifrado. */
interface SecureDatabaseBootstrap {
    fun prepare(): DatabaseBootstrapResult
}

/**
 *     banco não existe       → cria cifrado (Room cria com a senha)
 *     banco em texto puro    → exporta para .encrypted.tmp → VALIDA → troca atômica → abre cifrado
 *     banco cifrado          → abre normalmente (confere a senha antes)
 *     migração falhou        → NÃO abre em texto puro; original intacto; tenta de novo depois
 *     senha irrecuperável    → KeyUnrecoverable (nunca gera senha nova por cima de um banco existente)
 */
class DefaultSecureDatabaseBootstrap(
    private val db: File,
    private val keys: DatabaseKeySource,
    private val ops: DatabaseCipherOps,
    private val onState: (DatabaseSecurityState) -> Unit = {},
) : SecureDatabaseBootstrap {

    val tmp: File get() = File(db.parentFile, db.name + TMP_SUFFIX)

    override fun prepare(): DatabaseBootstrapResult {
        // Restos de uma migração interrompida nunca são usados.
        tmp.delete()

        val passphrase = when (val key = keys.read()) {
            is DatabaseKeyResult.Available -> key.passphrase
            DatabaseKeyResult.Unrecoverable -> return fail(DatabaseSecurityState.KeyUnrecoverable)
            DatabaseKeyResult.FirstInstall -> {
                // Banco cifrado sem arquivo de senha: gerar outra senha não abriria nada.
                if (db.exists() && !isPlaintext(db)) return fail(DatabaseSecurityState.KeyUnrecoverable)
                keys.createForFirstInstall()
            }
        }

        when {
            !db.exists() -> Unit
            isPlaintext(db) -> {
                onState(DatabaseSecurityState.MigrationRequired)
                migrate(passphrase)?.let { return fail(DatabaseSecurityState.MigrationFailed(it)) }
            }
            ops.inspect(db, passphrase) == null -> return fail(DatabaseSecurityState.KeyUnrecoverable)
        }
        onState(DatabaseSecurityState.Encrypted)
        return DatabaseBootstrapResult.Ready(passphrase)
    }

    /** null = sucesso; senão o motivo. O banco original só é tocado depois da validação. */
    private fun migrate(passphrase: ByteArray): String? {
        val source = try {
            ops.exportEncrypted(db, tmp, passphrase)
        } catch (e: Exception) {
            tmp.delete()
            return "export: ${e.message ?: e::class.simpleName}"
        }
        // 1. arquivo novo existe
        if (!tmp.exists() || tmp.length() == 0L) return abort("cópia cifrada não foi criada")
        // 2. não é SQLite em texto puro
        if (isPlaintext(tmp)) return abort("cópia ficou em texto puro")
        // 3. SQLCipher consegue abrir
        val copy = ops.inspect(tmp, passphrase) ?: return abort("SQLCipher não abriu a cópia")
        // 4. schema válido (mesma versão e mesmas tabelas)
        if (copy.userVersion != source.userVersion) return abort("user_version ${copy.userVersion} ≠ ${source.userVersion}")
        if (!copy.tables.containsAll(source.tables)) return abort("tabelas faltando: ${source.tables - copy.tables}")
        // 5. só então substitui o original
        return try {
            listOf("-wal", "-shm", "-journal").forEach { File(db.path + it).delete() }
            try {
                Files.move(tmp.toPath(), db.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (_: AtomicMoveNotSupportedException) {
                Files.move(tmp.toPath(), db.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
            null
        } catch (e: Exception) {
            abort("troca de arquivos: ${e.message}")
        }
    }

    private fun abort(reason: String): String {
        tmp.delete()
        return reason
    }

    private fun fail(state: DatabaseSecurityState): DatabaseBootstrapResult {
        onState(state)
        return DatabaseBootstrapResult.Failed(state)
    }

    companion object {
        const val TMP_SUFFIX = ".encrypted.tmp"
        private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

        /** true quando o arquivo existe e é um SQLite comum (cabeçalho legível). */
        fun isPlaintext(file: File): Boolean {
            if (!file.exists() || file.length() < SQLITE_HEADER.size) return false
            val header = ByteArray(SQLITE_HEADER.size)
            file.inputStream().use { if (it.read(header) != header.size) return false }
            return header.contentEquals(SQLITE_HEADER)
        }
    }
}
