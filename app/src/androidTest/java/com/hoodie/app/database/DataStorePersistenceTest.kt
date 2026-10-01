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
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Preferências gravadas sobrevivem a um "restart" (nova instância lendo o mesmo arquivo). */
@RunWith(AndroidJUnit4::class)
class DataStorePersistenceTest {

    @Test
    fun valorSobreviveAReabertura() = runBlocking {
        val context: Context = ApplicationProvider.getApplicationContext()
        val file = File(context.filesDir, "datastore/instrumented_test.preferences_pb").apply { delete() }
        val key = stringPreferencesKey("cat_name")

        val scope1 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        PreferenceDataStoreFactory.create(scope = scope1) { file }.edit { it[key] = "Mingau" }
        scope1.cancel()

        val scope2 = CoroutineScope(Dispatchers.IO + SupervisorJob())
        val value = PreferenceDataStoreFactory.create(scope = scope2) { file }.data.first()[key]
        scope2.cancel()
        file.delete()
        assertEquals("Mingau", value)
    }
}
