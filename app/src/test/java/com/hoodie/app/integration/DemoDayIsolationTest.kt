package com.hoodie.app.integration

import android.app.Application
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.engine.demo.DemoDayFactory
import com.hoodie.app.engine.demo.DemoDiaryAssembler
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

/** ONB-06: a jornada completa é reproduzida em memória, sem tocar no Room real. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class DemoDayIsolationTest {
    private lateinit var g: TestGraph
    private val zone = ZoneId.of("America/Sao_Paulo")

    @Before fun setUp() { g = TestGraph(1_790_000_000_000L) }
    @After fun tearDown() = g.close()

    private suspend fun counts() = listOf(
        g.db.contextEventDao().count(), g.db.timelineDao().count(), g.db.placeDao().getAll().size,
        g.db.confirmationDao().idsAfter(0).size, g.db.questionDao().since(0).size,
        g.db.intelligenceDao().routineSlots().size, g.db.intelligenceDao().correctionsSince(0).size,
    )


    @Test fun `o dia de demonstracao nao grava nada no banco real`() = runBlocking {
        val before = counts()
        val day = DemoDayFactory.create(LocalDate.of(2026, 10, 13), zone)
        DemoDiaryAssembler.assemble(day)
        assertEquals(before, counts())
        assertTrue(g.notifier.events.isEmpty() && g.notifier.questions.isEmpty())
    }

    @Test fun `os sete passos seguem a jornada prometida`() {
        val times = DemoDayFactory.steps.map { it.timeLabel }
        assertEquals(listOf("07:00", "07:45", "08:30", "12:00", "13:00", "18:00", "19:00"), times)
        assertEquals(
            listOf(UserContextType.HOME, UserContextType.COMMUTING, UserContextType.WORK, UserContextType.LUNCH,
                UserContextType.WORK, UserContextType.COMMUTING, UserContextType.HOME),
            DemoDayFactory.steps.map { it.context },
        )
    }

    @Test fun `o diario montado mostra casa trabalho e restaurante`() {
        val view = DemoDiaryAssembler.assemble(DemoDayFactory.create(LocalDate.of(2026, 10, 13), zone))
        val names = view.diary.visits.map { it.placeName }
        assertTrue(names.toString(), "Casa" in names && "Trabalho" in names && "Restaurante" in names)
        assertTrue(view.journey.nodes.isNotEmpty())
    }

}
