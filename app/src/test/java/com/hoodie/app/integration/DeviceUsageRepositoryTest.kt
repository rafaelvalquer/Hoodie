package com.hoodie.app.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieDatabase
import com.hoodie.app.core.database.RoomTransactionRunner
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.deviceusage.RawUsageEvent
import com.hoodie.app.core.deviceusage.UsageAccessChecker
import com.hoodie.app.core.deviceusage.UsageStatsSource
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.FixedClock
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.data.repository.DeviceUsageRepositoryImpl
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.deviceusage.FakeAppMetadata
import com.hoodie.app.engine.deviceusage.SPOTIFY
import com.hoodie.app.engine.deviceusage.TEAMS
import com.hoodie.app.engine.deviceusage.WHATSAPP
import com.hoodie.app.engine.deviceusage.YOUTUBE
import com.hoodie.app.engine.deviceusage.mon
import com.hoodie.app.engine.deviceusage.use
import com.hoodie.app.engine.ms
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** Processa um dia inteiro, cruza com contextos, persiste e relê do Room. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DeviceUsageRepositoryTest {

    private class FakeSource(var events: List<RawUsageEvent>) : UsageStatsSource {
        var calls = 0
        override suspend fun events(from: Long, to: Long): List<RawUsageEvent> {
            calls++
            return events.filter { it.timestamp in from until to }
        }
    }

    private val context: Context = ApplicationProvider.getApplicationContext()
    private lateinit var db: HoodieDatabase
    private lateinit var settings: SettingsRepository
    private val monday = LocalDate.of(2026, 10, MONDAY)
    private val clock = FixedClock(at(MONDAY, 23).ms())
    private var granted = true
    private val source = FakeSource(
        use(WHATSAPP, mon(9, 2), 5) + use(TEAMS, mon(9, 15), 27) + use(YOUTUBE, mon(12, 18), 8) + use(SPOTIFY, mon(19), 58),
    )
    private lateinit var repo: DeviceUsageRepositoryImpl

    @Before
    fun setUp() = runBlocking {
        db = Room.inMemoryDatabaseBuilder(context, HoodieDatabase::class.java).allowMainThreadQueries().build()
        settings = SettingsRepository(context)
        settings.clear()
        settings.setDigital(settings.current().digital.copy(analysisEnabled = true))
        repo = DeviceUsageRepositoryImpl(source, FakeAppMetadata(), UsageAccessChecker { granted }, db.deviceUsageDao(), db.contextEventDao(), settings, clock, RoomTransactionRunner(db))
        db.contextEventDao().insert(ContextEventEntity(type = UserContextType.WORK, startedAt = mon(8, 49), endedAt = mon(12, 16), confidence = 1f, placeId = null, source = ContextSource.GEOFENCE))
        db.contextEventDao().insert(ContextEventEntity(type = UserContextType.LUNCH, startedAt = mon(12, 16), endedAt = mon(13), confidence = 1f, placeId = null, source = ContextSource.GEOFENCE))
        Unit
    }

    @After
    fun tearDown() = runBlocking {
        db.close()
        settings.clear()
        Unit
    }

    @Test
    fun `refresh calcula, salva e o dia volta igual do banco`() = runBlocking {
        val fresh = repo.refreshDay(monday)
        assertEquals(listOf(SPOTIFY, TEAMS, YOUTUBE, WHATSAPP), fresh.topApps.map { it.packageName })
        assertEquals(TEAMS, fresh.usageIn(UserContextType.WORK)!!.apps.first().packageName)

        val stored = repo.loadDay(monday)!!
        assertEquals(fresh.summary, stored.summary)
        assertEquals(fresh.topApps.map { it.packageName to it.foregroundMs }, stored.topApps.map { it.packageName to it.foregroundMs })
        assertEquals(fresh.categoryUsage, stored.categoryUsage)
        assertEquals(YOUTUBE, stored.usageIn(UserContextType.LUNCH)!!.apps.single().packageName)
        assertEquals(4, stored.appCount)
        assertEquals(fresh.appTimeline, stored.appTimeline)
        assertEquals(fresh.hourlyScreenMs, stored.hourlyScreenMs)
        assertEquals(fresh.usageByContext, stored.usageByContext)
        assertTrue(fresh.appSessions.isNotEmpty())
        assertEquals(fresh.appSessions, stored.appSessions)
        // Recalcular o mesmo dia não duplica sessões.
        repo.refreshDay(monday)
        assertEquals(fresh.appSessions.size, db.deviceUsageDao().sessions(monday.toEpochDay()).size)
    }

    @Test
    fun `sem permissao mostra so o historico e nao le o Android`() = runBlocking {
        granted = false
        assertNull(repo.insightsFor(monday))
        assertEquals(0, source.calls)
        granted = true
        repo.refreshDay(monday)
        granted = false
        val before = source.calls
        assertNotNull(repo.insightsFor(monday))
        assertEquals(before, source.calls)
    }

    @Test
    fun `insightsFor e leitura pura mesmo com permissao e analise ativas`() = runBlocking {
        val readsBefore = source.calls
        assertNull(repo.insightsFor(monday))
        assertEquals(readsBefore, source.calls)
        assertNull(db.deviceUsageDao().day(monday.toString()))

        repo.refreshDay(monday)
        val persisted = db.deviceUsageDao().day(monday.toString())!!
        val readsAfterRefresh = source.calls
        assertNotNull(repo.insightsFor(monday))
        assertEquals(readsAfterRefresh, source.calls)
        assertEquals(persisted, db.deviceUsageDao().day(monday.toString()))
    }

    @Test
    fun `refresh de conteudo igual nao regrava o agregado`() = runBlocking {
        repo.refreshDay(monday)
        val original = db.deviceUsageDao().day(monday.toString())!!
        clock.millis += 60 * MINUTE_MS

        repo.refreshDay(monday)

        assertEquals(original, db.deviceUsageDao().day(monday.toString()))
    }

    /** use() deixa o app na frente 2 s a menos que a tela ligada. */
    private val SUNDAY_YT_MS = 60 * MINUTE_MS - 2_000

    @Test
    fun `dia antigo com menos eventos no Android nao apaga o historico`() = runBlocking {
        val sunday = monday.minusDays(1)
        source.events = use(YOUTUBE, at(MONDAY - 1, 20).ms(), 60)
        assertEquals(SUNDAY_YT_MS, repo.refreshDay(sunday).topApps.single().foregroundMs)
        // O Android já descartou parte do domingo.
        source.events = use(YOUTUBE, at(MONDAY - 1, 20).ms(), 10)
        val again = repo.refreshDay(sunday)
        assertEquals(SUNDAY_YT_MS, again.topApps.single().foregroundMs)
        source.events = emptyList()
        assertEquals(SUNDAY_YT_MS, repo.insightsFor(sunday)!!.topApps.single().foregroundMs)
    }

    @Test
    fun `categoria manual vale para dias salvos e recalculados`() = runBlocking {
        repo.refreshDay(monday)
        repo.setCategoryOverride(YOUTUBE, HoodieAppCategory.WORK)
        assertEquals(HoodieAppCategory.WORK, repo.loadDay(monday)!!.topApps.first { it.packageName == YOUTUBE }.appCategory)
        assertEquals(HoodieAppCategory.WORK, repo.refreshDay(monday).topApps.first { it.packageName == YOUTUBE }.appCategory)
        repo.setCategoryOverride(YOUTUBE, null)
        assertEquals(HoodieAppCategory.VIDEO, repo.refreshDay(monday).topApps.first { it.packageName == YOUTUBE }.appCategory)
    }

    @Test
    fun `historico desligado nao grava nada`() = runBlocking {
        settings.setDigital(settings.current().digital.copy(saveHistory = false))
        assertTrue(repo.refreshDay(monday).topApps.isNotEmpty())
        assertEquals(0, db.deviceUsageDao().dayCount())
    }

    @Test
    fun `analise desligada nao le o Android`() = runBlocking {
        settings.setDigital(settings.current().digital.copy(analysisEnabled = false))
        assertNull(repo.insightsFor(monday))
        assertEquals(0, source.calls)
    }

    @Test
    fun `apagar historico digital remove os dias e mantem as categorias escolhidas`() = runBlocking {
        repo.refreshDay(monday)
        repo.setCategoryOverride(SPOTIFY, HoodieAppCategory.WORK)
        repo.clearHistory()
        assertNull(repo.loadDay(monday))
        assertTrue(db.deviceUsageDao().apps(monday.toString()).isEmpty())
        assertTrue(db.deviceUsageDao().contextApps(monday.toString()).isEmpty())
        assertEquals(0, db.deviceUsageDao().sessionCount())
        assertEquals(1, db.deviceUsageDao().overrides().size)
    }

    @Test
    fun `dia futuro nao tem dados`() = runBlocking {
        assertNull(repo.insightsFor(monday.plusDays(1)))
    }
}
