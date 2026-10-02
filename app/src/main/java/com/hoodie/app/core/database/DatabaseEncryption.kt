package com.hoodie.app.core.database

import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Implementação SQLCipher das operações usadas pelo [DefaultSecureDatabaseBootstrap].
 * Upgrade de quem tinha o banco Room em texto puro: `sqlcipher_export` para um
 * arquivo temporário cifrado; a troca de arquivos só acontece depois da validação.
 */
object DatabaseEncryption : DatabaseCipherOps {

    fun loadLibrary() = SqlCipherNativeLoader.ensureLoaded()

    fun isPlaintext(db: File): Boolean = DefaultSecureDatabaseBootstrap.isPlaintext(db)

    override fun exportEncrypted(plain: File, target: File, passphrase: ByteArray): DatabaseFingerprint {
        loadLibrary()
        target.delete()
        val key = String(passphrase, Charsets.UTF_8).replace("'", "''")
        // CREATE é obrigatório: o ATTACH herda as flags da conexão principal e,
        // sem CREATE, falha silenciosamente ao criar o arquivo de destino.
        val db = SQLiteDatabase.openOrCreateDatabase(plain, "", null, null)
        try {
            // Room deixa o arquivo em WAL; sem WAL o pool usa uma única conexão e o
            // ATTACH continua valendo para os comandos seguintes.
            db.disableWriteAheadLogging()
            val source = fingerprint(db, full = true)
            db.execSQL("ATTACH DATABASE '${target.absolutePath.replace("'", "''")}' AS encrypted KEY '$key'")
            db.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
            // A versão do schema (usada pelas migrações do Room) não é copiada pelo export.
            db.execSQL("PRAGMA encrypted.user_version = ${source.userVersion}")
            db.execSQL("DETACH DATABASE encrypted")
            return source
        } finally {
            db.close()
        }
    }

    override fun inspect(file: File, passphrase: ByteArray): DatabaseFingerprint? = runCatching {
        loadLibrary()
        val db = SQLiteDatabase.openDatabase(file.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null)
        try { fingerprint(db) } finally { db.close() }
    }.getOrNull()

    override fun verify(file: File, passphrase: ByteArray): DatabaseFingerprint? = runCatching {
        loadLibrary()
        val db = SQLiteDatabase.openDatabase(file.path, passphrase, null, SQLiteDatabase.OPEN_READONLY, null, null)
        try { fingerprint(db, full = true, cipher = true) } finally { db.close() }
    }.getOrNull()

    private fun fingerprint(db: SQLiteDatabase, full: Boolean = false, cipher: Boolean = false): DatabaseFingerprint {
        val tables = mutableSetOf<String>()
        db.rawQuery("SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%'", null).use { c ->
            while (c.moveToNext()) tables += c.getString(0)
        }
        if (!full) return DatabaseFingerprint(db.version, tables)
        val counts = tables.associateWith { t ->
            db.rawQuery("SELECT COUNT(*) FROM \"${t.replace("\"", "\"\"")}\"", null).use { c -> if (c.moveToFirst()) c.getLong(0) else -1L }
        }
        val quick = db.rawQuery("PRAGMA quick_check", null).use { c -> c.moveToFirst() && c.getString(0) == "ok" && !c.moveToNext() }
        // cipher_integrity_check devolve uma linha por problema; nenhuma linha = íntegro.
        val integrity = if (!cipher) null else runCatching { db.rawQuery("PRAGMA cipher_integrity_check", null).use { c -> c.count == 0 } }.getOrNull()
        return DatabaseFingerprint(db.version, tables, counts, quick, integrity)
    }
}
