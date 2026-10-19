package com.hoodie.app.integration

import android.app.Application
import androidx.sqlite.db.SupportSQLiteDatabase
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.at
import com.hoodie.app.engine.ms
import com.hoodie.app.engine.officeRoutine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Count actual SQL writes, including replacements whose values remain identical. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
class HoodieEnginePersistenceTest {
    @Test fun resolvingUnchangedStateDoesNotWriteOrAddDiaryRows() = runBlocking {
        val g = TestGraph(at(MONDAY, 9).ms())
        try {
            g.setRoutine(officeRoutine)
            val first = g.hoodie.resolve()
            val persistedBefore = g.db.hoodieStateDao().get()
            val sql = observeStateWrites(g)
            val timelineBefore = g.timelineTexts()
            val activitiesBefore = rowCount(sql, "hoodie_activities")

            repeat(3) { assertEquals(first.state, g.hoodie.resolve().state) }

            assertEquals(0, writeCount(sql))
            assertEquals(timelineBefore, g.timelineTexts())
            assertEquals(activitiesBefore, rowCount(sql, "hoodie_activities"))
            assertEquals(persistedBefore, g.db.hoodieStateDao().get())
        } finally { g.close() }
    }

    @Test fun advancingNeedsStillPersistsTheChangedState() = runBlocking {
        val g = TestGraph(at(MONDAY, 9).ms())
        try {
            g.setRoutine(officeRoutine)
            val first = g.hoodie.resolve()
            val sql = observeStateWrites(g)

            g.clock.millis += 20 * 60_000L
            val advanced = g.hoodie.resolve()

            assertNotEquals(first.state, advanced.state)
            assertTrue(writeCount(sql) > 0)
            val persisted = g.db.hoodieStateDao().get()!!
            assertEquals(advanced.state.activity, persisted.activity)
            assertEquals(advanced.state.needsAt, persisted.needsAt)
            assertEquals(advanced.state.needs.energy, persisted.energy)
        } finally { g.close() }
    }

    @Test fun liveNeedsAdvanceWithoutRewritingAnUnchangedActivity() = runBlocking {
        val g = TestGraph(at(MONDAY, 9).ms())
        try {
            g.setRoutine(officeRoutine)
            val first = g.hoodie.resolve()
            val sql = observeStateWrites(g)
            g.clock.millis += 5 * 60_000L
            assertTrue(g.clock.millis < first.state.expectedEndAt!!)

            val later = g.hoodie.resolve()

            assertEquals(first.state, later.state)
            assertNotEquals(first.liveNeeds, later.liveNeeds)
            assertEquals(0, writeCount(sql))
        } finally { g.close() }
    }

    private fun observeStateWrites(g: TestGraph): SupportSQLiteDatabase =
        g.db.openHelper.writableDatabase.also { sql ->
            // TEMP objects never alter the application schema or its persisted rows.
            sql.execSQL("CREATE TEMP TABLE qa_state_writes (count INTEGER NOT NULL)")
            sql.execSQL("INSERT INTO qa_state_writes VALUES (0)")
            listOf("INSERT", "UPDATE", "DELETE").forEach { operation ->
                sql.execSQL("CREATE TEMP TRIGGER qa_state_$operation AFTER $operation ON hoodie_state BEGIN UPDATE qa_state_writes SET count = count + 1; END")
            }
        }

    private fun writeCount(sql: SupportSQLiteDatabase) =
        sql.query("SELECT count FROM qa_state_writes").use { it.moveToFirst(); it.getInt(0) }

    private fun rowCount(sql: SupportSQLiteDatabase, table: String) =
        sql.query("SELECT COUNT(*) FROM `$table`").use { it.moveToFirst(); it.getInt(0) }
}
