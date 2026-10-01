package com.hoodie.app.core.database

import net.zetetic.database.sqlcipher.SQLiteDatabase
import java.io.File

/**
 * Upgrade de quem já tinha o banco Room em texto puro: copia tudo para um
 * arquivo cifrado com `sqlcipher_export` e troca os arquivos — sem perder dados.
 * Roda antes do Room abrir o banco.
 */
object DatabaseEncryption {

    private val SQLITE_HEADER = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    @Volatile private var loaded = false

    fun loadLibrary() {
        if (loaded) return
        System.loadLibrary("sqlcipher")
        loaded = true
    }

    /** true quando o arquivo existe e ainda é um SQLite comum (cabeçalho legível). */
    fun isPlaintext(db: File): Boolean {
        if (!db.exists() || db.length() < SQLITE_HEADER.size) return false
        val header = ByteArray(SQLITE_HEADER.size)
        db.inputStream().use { if (it.read(header) != header.size) return false }
        return header.contentEquals(SQLITE_HEADER)
    }

    /** Converte [db] para SQLCipher com [passphrase]. Não faz nada se já estiver cifrado ou não existir. */
    fun migrateIfNeeded(db: File, passphrase: ByteArray): Boolean {
        if (!isPlaintext(db)) return false
        loadLibrary()
        val tmp = File(db.parentFile, db.name + ".encrypting")
        tmp.delete()
        val key = String(passphrase, Charsets.UTF_8).replace("'", "''")
        val plain = SQLiteDatabase.openOrCreateDatabase(db, "", null, null)
        try {
            // Room deixa o arquivo em WAL; sem WAL o pool usa uma única conexão,
            // e o ATTACH continua valendo para os comandos seguintes.
            plain.disableWriteAheadLogging()
            val version = plain.version
            // execSQL é o caminho suportado para ATTACH/DETACH; o export é um SELECT.
            plain.execSQL("ATTACH DATABASE '${tmp.absolutePath.replace("'", "''")}' AS encrypted KEY '$key'")
            plain.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
            // A versão do schema (usada pelas migrações do Room) não é copiada pelo export.
            plain.execSQL("PRAGMA encrypted.user_version = $version")
            plain.execSQL("DETACH DATABASE encrypted")
        } finally {
            plain.close()
        }
        check(tmp.exists() && !isPlaintext(tmp)) { "sqlcipher_export não gerou o banco cifrado" }
        listOf("", "-wal", "-shm", "-journal").forEach { File(db.path + it).delete() }
        check(tmp.renameTo(db)) { "Falha ao substituir o banco pelo cifrado" }
        return true
    }
}
