package com.hoodie.app.core.database

/** Resultado da comparação entre o banco original e a cópia cifrada. */
sealed interface MigrationValidationResult {
    data object Valid : MigrationValidationResult
    data class Invalid(val reasons: List<String>) : MigrationValidationResult
}

/** Decide se a cópia cifrada pode substituir o original. */
interface DatabaseMigrationValidator {
    fun validate(source: DatabaseFingerprint, encrypted: DatabaseFingerprint): MigrationValidationResult
}

/**
 * Só troca o banco se a cópia for equivalente à origem:
 * mesma user_version, todas as tabelas, a mesma quantidade de linhas em cada
 * tabela, `PRAGMA quick_check` = ok e `PRAGMA cipher_integrity_check` sem erros
 * (null = pragma indisponível nesta versão do SQLCipher, não reprova).
 */
object DefaultDatabaseMigrationValidator : DatabaseMigrationValidator {

    override fun validate(source: DatabaseFingerprint, encrypted: DatabaseFingerprint): MigrationValidationResult {
        val reasons = mutableListOf<String>()
        if (!source.quickCheckOk) reasons += "quick_check da origem falhou"
        if (encrypted.userVersion != source.userVersion) reasons += "user_version ${encrypted.userVersion} ≠ ${source.userVersion}"
        val missing = source.tables - encrypted.tables
        if (missing.isNotEmpty()) reasons += "tabelas faltando: ${missing.sorted()}"
        source.rowCounts.forEach { (table, count) ->
            val copied = encrypted.rowCounts[table]
            if (table !in missing && copied != count) reasons += "linhas em $table: ${copied ?: "?"} ≠ $count"
        }
        if (!encrypted.quickCheckOk) reasons += "quick_check da cópia falhou"
        if (encrypted.cipherIntegrityOk == false) reasons += "cipher_integrity_check da cópia falhou"
        return if (reasons.isEmpty()) MigrationValidationResult.Valid else MigrationValidationResult.Invalid(reasons)
    }
}
