package com.hoodie.app.core.security

import com.hoodie.app.core.database.HoodieDatabase
import javax.inject.Inject
import javax.inject.Singleton

/**
 * "Apagar todos os dados" dos Ajustes: nada do usuário sobrevive — geofences, tarefas,
 * notificações pendentes, preferências, o arquivo do banco cifrado, a senha do
 * banco e as chaves do Keystore. Depois o app reinicia limpo no onboarding.
 */
@Singleton
class DataWiper @Inject constructor(
    private val db: HoodieDatabase,
    private val reset: LocalDataReset,
) {
    suspend fun deleteEverything() {
        reset.wipe(closeDb = {
            db.clearAllTables()
            db.close()
        })
        reset.restart()
    }
}
