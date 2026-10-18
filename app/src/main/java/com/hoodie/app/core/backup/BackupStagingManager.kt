package com.hoodie.app.core.backup

import android.content.Context
import androidx.room.Room
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.SqlCipherNativeLoader
import com.hoodie.app.core.security.DatabaseKeyStore
import com.hoodie.app.core.security.KeystoreCoordinateCipher
import com.hoodie.app.engine.backup.BackupError
import com.hoodie.app.engine.backup.BackupException
import dagger.hilt.android.qualifiers.ApplicationContext
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import javax.inject.Inject
import java.io.File

class BackupStagingManager @Inject constructor(@ApplicationContext private val context: Context) {
    fun create(): BackupStage {
        if (context.filesDir.usableSpace < MIN_FREE_BYTES) throw BackupException(BackupError.NO_SPACE)
        val generation = StorageGeneration.fresh()
        val keys = DatabaseKeyStore(context, generation.keyAlias, generation.keyFile)
        val pass = keys.createForFirstInstall()
        try {
            SqlCipherNativeLoader.ensureLoaded()
            val database = Room.databaseBuilder(context, HoodieDatabase::class.java, generation.databaseName)
                .openHelperFactory(SupportOpenHelperFactory(pass.copyOf())).build()
            return BackupStage(generation, database, keys, KeystoreCoordinateCipher(generation.coordinateAlias))
        } finally { pass.fill(0) }
    }
    fun ensureSpace() {
        if (context.filesDir.usableSpace < MIN_FREE_BYTES) throw BackupException(BackupError.NO_SPACE)
    }
    fun seal(stage: BackupStage) {
        BackupRestoreJournal.durableWrite(File(context.noBackupFilesDir, "${stage.generation.id}.ready"), stage.generation.id)
    }
    fun discard(stage: BackupStage) {
        stage.database.close()
        context.deleteDatabase(stage.generation.databaseName)
        stage.keys.delete()
        runCatching { java.security.KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(stage.generation.coordinateAlias) }
        File(context.noBackupFilesDir, "${stage.generation.id}.ready").delete()
        // Preferences may have a live DataStore instance. Clear via its owner, never delete underneath it.
    }
    companion object { const val MIN_FREE_BYTES = 32L * 1024 * 1024 }
}

data class BackupStage(
    val generation: StorageGeneration,
    val database: HoodieDatabase,
    val keys: DatabaseKeyStore,
    val coordinates: KeystoreCoordinateCipher,
)
