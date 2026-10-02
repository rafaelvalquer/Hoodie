package com.hoodie.app.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hoodie.app.core.database.*
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.deviceusage.DeviceUsageMappers
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class HistoricalPhoneInsightsTest {
    @Test fun failedReplacementRollsBackEveryTable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, HoodieDatabase::class.java).allowMainThreadQueries().build()
        try {
            val key = "2026-01-01"
            val day = DailyDeviceUsageEntity(key, 100, 1, 1, 0, 100, 100, false, 1, 100)
            val app = DailyAppUsageEntity(key, "old.app", "Old", "OTHER", 100, 1, 0, 100, 100)
            val dao = db.deviceUsageDao()
            dao.replaceDay(day, listOf(app), emptyList(), emptyList(), listOf(DailyScreenHourlyEntity(key, 9, 100)), emptyList())
            db.openHelper.writableDatabase.execSQL("CREATE TRIGGER fail_timeline BEFORE INSERT ON daily_phone_timeline BEGIN SELECT RAISE(ABORT, 'injected'); END")
            var failed = false
            try {
                dao.replaceDay(day.copy(screenTimeMs = 200), listOf(app.copy(packageName = "new.app")), emptyList(), emptyList(),
                    listOf(DailyScreenHourlyEntity(key, 10, 200)),
                    listOf(DailyPhoneTimelineEntity("new", key, "new.app", "New", "OTHER", 0, 200, null)))
            } catch (_: Exception) {
                failed = true
            }
            assertEquals(true, failed)
            assertEquals(100L, dao.day(key)!!.screenTimeMs)
            assertEquals("old.app", dao.apps(key).single().packageName)
            assertEquals(9, dao.hourly(key).single().hour)
            assertEquals(0, dao.phoneTimeline(key).size)
        } finally {
            db.close()
        }
    }

    @Test fun completeDaySurvivesReopenAndReplacementRemovesOldRows() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "historical-phone-v5.db"
        context.deleteDatabase(name)
        val date = LocalDate.of(2026, 1, 1)
        val key = date.toString()
        fun open() = Room.databaseBuilder(context, HoodieDatabase::class.java, name).allowMainThreadQueries().build()
        var db = open()
        try {
            val day = DailyDeviceUsageEntity(key, 400, 40, 40, 0, 400, 10, false, 40, 500)
            val apps = (0 until 40).map { DailyAppUsageEntity(key, "app.$it", "App $it", "OTHER", 10, 1, 0, 10, 500) }
            val total = DailyContextUsageEntity(key, "WORK", 400, 40)
            val hourly = List(24) { DailyScreenHourlyEntity(key, it, if (it == 9) 400 else 0) }
            val timeline = listOf(DailyPhoneTimelineEntity("block", key, "app.0", "App 0", "OTHER", 10, 20, "WORK"))
            val sessions = listOf(PhoneAppSessionEntity("session", date.toEpochDay(), "app.0", 10, 20))
            db.deviceUsageDao().replaceDay(day, apps, listOf(total), emptyList(), hourly, timeline, sessions, date.toEpochDay())
            db.close()
            db = open()
            val dao = db.deviceUsageDao()
            val restored = DeviceUsageMappers.fromStored(dao.day(key)!!, dao.apps(key), dao.contextApps(key),
                { _, stored -> stored }, { false }, dao.sessions(date.toEpochDay()), dao.contextTotals(key), dao.hourly(key), dao.phoneTimeline(key))
            assertEquals(40, restored.topApps.size)
            assertEquals(24, restored.hourlyScreenMs.size)
            assertEquals(400L, restored.hourlyScreenMs[9])
            assertEquals(UserContextType.WORK, restored.appTimeline.single().context)
            assertEquals(400L, restored.usageByContext.single().foregroundMs)
            assertEquals(40, restored.usageByContext.single().sessionCount)
            assertEquals(1, restored.appSessions.size)
            dao.insertSessions(listOf(PhoneAppSessionEntity("expired", date.toEpochDay() - 366, "old.app", 0, 1)))
            assertEquals(1, dao.deleteSessionsOlderThan(date.toEpochDay() - 365))
            assertEquals(1, dao.sessionCount())
            assertEquals(40, dao.apps(key).size)
            assertEquals(1, dao.phoneTimeline(key).size)
            // Sem epochDay explícito: as sessões antigas também devem desaparecer.
            dao.replaceDay(day.copy(appCount = 0), emptyList(), emptyList(), emptyList(), emptyList(), emptyList(), emptyList())
            assertEquals(0, dao.apps(key).size)
            assertEquals(0, dao.hourly(key).size)
            assertEquals(0, dao.phoneTimeline(key).size)
            assertEquals(0, dao.contextTotals(key).size)
            assertEquals(0, dao.sessionCount())
            val settings = com.hoodie.app.core.datastore.SettingsRepository(context)
            settings.setLastGeofenceRegisterDay(date.toEpochDay())
            assertEquals(date.toEpochDay(), com.hoodie.app.core.datastore.SettingsRepository(context).current().lastGeofenceRegisterDay)
            settings.clear()
            Unit
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
