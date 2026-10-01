package com.hoodie.app.app

import android.content.Context
import androidx.room.Room
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.security.CoordinateCipher
import com.hoodie.app.core.security.KeystoreCoordinateCipher
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.SystemClockProvider
import com.hoodie.app.engine.dialogue.DialogueEngine
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun clock(impl: SystemClockProvider): ClockProvider
    @Binds abstract fun cipher(impl: KeystoreCoordinateCipher): CoordinateCipher
}

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides @Singleton
    fun database(@ApplicationContext context: Context): HoodieDatabase =
        Room.databaseBuilder(context, HoodieDatabase::class.java, HoodieDatabase.NAME).build()

    @Provides fun placeDao(db: HoodieDatabase) = db.placeDao()
    @Provides fun routineDao(db: HoodieDatabase) = db.routineDao()
    @Provides fun dayExceptionDao(db: HoodieDatabase) = db.dayExceptionDao()
    @Provides fun locationEventDao(db: HoodieDatabase) = db.locationEventDao()
    @Provides fun contextEventDao(db: HoodieDatabase) = db.contextEventDao()
    @Provides fun confirmationDao(db: HoodieDatabase) = db.confirmationDao()
    @Provides fun questionDao(db: HoodieDatabase) = db.questionDao()
    @Provides fun hoodieStateDao(db: HoodieDatabase) = db.hoodieStateDao()
    @Provides fun hoodieActivityDao(db: HoodieDatabase) = db.hoodieActivityDao()
    @Provides fun timelineDao(db: HoodieDatabase) = db.timelineDao()
    @Provides fun memoryDao(db: HoodieDatabase) = db.memoryDao()

    @Provides @Singleton
    fun dialogues(@ApplicationContext context: Context): DialogueEngine =
        DialogueEngine(DialogueEngine.parse(context.assets.open("metadata/dialogues.json").bufferedReader().use { it.readText() }))
}
