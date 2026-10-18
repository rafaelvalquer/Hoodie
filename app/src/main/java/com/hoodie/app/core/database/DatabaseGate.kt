package com.hoodie.app.core.database

import android.content.Context
import com.hoodie.app.core.security.DatabaseKeyStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Porteiro do banco: roda o [SecureDatabaseBootstrap] e guarda o resultado. NÃO
 * depende do HoodieDatabase, então a Activity, os receivers e os workers conseguem
 * consultá-lo mesmo quando o banco não pode ser aberto — e mostrar a tela de
 * recuperação ou sair cedo em vez de derrubar o app.
 *
 * [bootstrapFactory] recebe o callback que publica estados intermediários (MigrationRequired).
 */
@Singleton
class DatabaseGate(bootstrapFactory: ((DatabaseSecurityState) -> Unit) -> SecureDatabaseBootstrap) {

    @Inject constructor(@ApplicationContext context: Context, keys: DatabaseKeyStore) : this({ publish ->
        DefaultSecureDatabaseBootstrap(context.getDatabasePath(com.hoodie.app.core.backup.StorageGeneration.current(context).databaseName), keys, DatabaseEncryption, publish)
    }) {
        restoreBlocked = { com.hoodie.app.core.backup.BackupRestoreStartup.isBlocked(context.noBackupFilesDir) }
        retryRestore = { com.hoodie.app.core.backup.BackupRestoreStartup.prepare(context.noBackupFilesDir) }
    }

    private var restoreBlocked: () -> Boolean = { false }
    private var retryRestore: () -> Unit = {}

    private val _state = MutableStateFlow<DatabaseSecurityState?>(null)

    /** null enquanto o bootstrap não rodou. */
    val state: StateFlow<DatabaseSecurityState?> = _state.asStateFlow()

    private val bootstrap = bootstrapFactory { _state.value = it }

    @Volatile private var ready: ByteArray? = null
    @Volatile private var suspendedForRestore = false
    fun suspendForRestore() { suspendedForRestore = true }

    /** Prepara o banco; depois do primeiro sucesso só devolve a senha. Chamar fora da main thread. */
    @Synchronized
    fun ensure(): DatabaseBootstrapResult {
        if (restoreBlocked()) return DatabaseBootstrapResult.Failed(DatabaseSecurityState.RestoreRecoveryFailed)
        if (suspendedForRestore) return DatabaseBootstrapResult.Failed(DatabaseSecurityState.KeyUnrecoverable)
        ready?.let { return DatabaseBootstrapResult.Ready(it.copyOf()) }
        val r = bootstrap.prepare()
        when (r) {
            is DatabaseBootstrapResult.Ready -> ready = r.passphrase.copyOf()
            is DatabaseBootstrapResult.Failed -> _state.value = r.state
        }
        return r
    }

    /** "Tentar novamente" da tela de recuperação. */
    fun retry(): DatabaseBootstrapResult { retryRestore(); return ensure() }

    fun isReady(): Boolean = ensure() is DatabaseBootstrapResult.Ready

    /** Depois de apagar tudo: o próximo ensure() trata como instalação nova. */
    @Synchronized
    fun reset() {
        ready?.fill(0)
        ready = null
        _state.value = null
    }
}
