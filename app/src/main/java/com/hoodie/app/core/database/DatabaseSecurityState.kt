package com.hoodie.app.core.database

/** Estado de segurança do banco local. Não existe estado "aberto em texto puro". */
sealed interface DatabaseSecurityState {
    /** Banco cifrado com SQLCipher e aberto normalmente. */
    data object Encrypted : DatabaseSecurityState

    /** Banco legado em texto puro: precisa ser migrado antes de abrir. */
    data object MigrationRequired : DatabaseSecurityState

    /** A migração falhou; o banco original ficou intacto e será tentado de novo. */
    data class MigrationFailed(val reason: String) : DatabaseSecurityState

    /** A senha do banco não pode ser recuperada (Keystore/arquivo de chave). */
    data object KeyUnrecoverable : DatabaseSecurityState
}

sealed interface DatabaseBootstrapResult {
    class Ready(val passphrase: ByteArray) : DatabaseBootstrapResult

    data class Failed(val state: DatabaseSecurityState) : DatabaseBootstrapResult
}

/** Lançada pelo provider do Room quando o banco não pode ser aberto com segurança. */
class DatabaseUnavailableException(val state: DatabaseSecurityState) :
    IllegalStateException("Banco local indisponível: $state")
