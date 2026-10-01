package com.hoodie.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Banco 100% local. A camada de persistência já separa os dados sensíveis
 * (coordenadas cifradas no PlaceEntity); a criptografia integral do arquivo
 * (ex.: SQLCipher) pode ser plugada depois no builder sem mudar DAOs.
 */
@Database(
    entities = [
        PlaceEntity::class,
        RoutineEntity::class,
        DayExceptionEntity::class,
        LocationEventEntity::class,
        ContextEventEntity::class,
        ContextConfirmationEntity::class,
        ContextQuestionEntity::class,
        HoodieStateEntity::class,
        HoodieActivityEntity::class,
        TimelineEventEntity::class,
        MemoryEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class HoodieDatabase : RoomDatabase() {
    abstract fun placeDao(): PlaceDao
    abstract fun routineDao(): RoutineDao
    abstract fun dayExceptionDao(): DayExceptionDao
    abstract fun locationEventDao(): LocationEventDao
    abstract fun contextEventDao(): ContextEventDao
    abstract fun confirmationDao(): ConfirmationDao
    abstract fun questionDao(): QuestionDao
    abstract fun hoodieStateDao(): HoodieStateDao
    abstract fun hoodieActivityDao(): HoodieActivityDao
    abstract fun timelineDao(): TimelineDao
    abstract fun memoryDao(): MemoryDao

    companion object {
        const val NAME = "hoodie.db"
    }
}
