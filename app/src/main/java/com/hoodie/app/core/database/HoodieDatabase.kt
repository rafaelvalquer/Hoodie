package com.hoodie.app.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

const val HOODIE_DATABASE_VERSION = 10

/**
 * Banco local cifrado com SQLCipher no DatabaseModule. As coordenadas possuem
 * também cifragem própria; v5 preserva o histórico digital, v6 adiciona
 * mobilidade e v7 otimiza as consultas sem reescrever os dados.
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
        DailyDeviceUsageEntity::class,
        DailyAppUsageEntity::class,
        DailyContextAppUsageEntity::class,
        AppCategoryOverrideEntity::class,
        PhoneAppSessionEntity::class,
        DailyScreenHourlyEntity::class,
        DailyContextUsageEntity::class,
        DailyPhoneTimelineEntity::class,
        MobilitySessionEntity::class,
        MobilitySegmentEntity::class,
        DayStateEntity::class,
        DiaryCorrectionEntity::class,
        LearnedRoutineSlotEntity::class,
        TransportPatternEntity::class,
    ],
    version = HOODIE_DATABASE_VERSION,
    exportSchema = true,
)
abstract class HoodieDatabase : RoomDatabase() {
    abstract fun intelligenceDao(): IntelligenceDao
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
    abstract fun deviceUsageDao(): DeviceUsageDao
    abstract fun mobilitySessionDao(): MobilitySessionDao
    abstract fun mobilitySegmentDao(): MobilitySegmentDao

    companion object {
        const val NAME = "hoodie.db"
    }
}
