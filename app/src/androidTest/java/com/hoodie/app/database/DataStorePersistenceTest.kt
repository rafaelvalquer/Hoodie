package com.hoodie.app.database

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.UUID

/** Preferências gravadas sobrevivem a um "restart" (nova instância lendo o mesmo arquivo). */
@RunWith(AndroidJUnit4::class)
class DataStorePersistenceTest {

    @Test
    fun valorSobreviveAReabertura() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.cacheDir, "instrumented-test-${UUID.randomUUID()}.preferences_pb")
        val key = stringPreferencesKey("cat_name")

        val job1 = SupervisorJob()
        val job2 = SupervisorJob()
        try {
            PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job1)) { file }.edit { it[key] = "Mingau" }
            job1.cancelAndJoin()
            val value = PreferenceDataStoreFactory.create(scope = CoroutineScope(Dispatchers.IO + job2)) { file }.data.first()[key]
            assertEquals("Mingau", value)
        } finally {
            job1.cancelAndJoin()
            job2.cancelAndJoin()
            file.delete()
        }
    }
}
