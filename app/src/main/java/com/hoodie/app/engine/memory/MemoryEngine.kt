package com.hoodie.app.engine.memory

import com.hoodie.app.core.database.MemoryDao
import com.hoodie.app.core.database.MemoryEntity
import com.hoodie.app.core.datastore.SettingsRepository
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.ClockProvider
import com.hoodie.app.core.time.atZone
import com.hoodie.app.data.repository.TimelineRepository
import java.time.DayOfWeek
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Marcos especiais que viram memórias. Cada um é desbloqueado uma única vez. */
enum class Milestone(val emoji: String, val title: String) {
    FIRST_DAY("🐱", "Primeiro dia juntos"),
    FIRST_MONDAY("📅", "Primeira segunda-feira juntos"),
    FIRST_WORK("🏢", "Primeiro dia de trabalho"),
    FIRST_LUNCH("🍜", "Primeiro almoço juntos"),
    FIRST_GYM("🏋", "Primeira academia"),
    FIRST_NEW_PLACE("📍", "Primeiro lugar novo"),
    FIRST_HOME_RETURN("🏠", "Primeira volta para casa"),
    WEEK_1("🎉", "Primeira semana"),
    DAYS_10("✨", "10 dias juntos"),
    DAYS_30("🏆", "30 dias juntos"),
}

/** Regra pura: quais marcos de tempo já foram atingidos. */
object MilestoneRules {
    fun forDaysTogether(days: Long): List<Milestone> = buildList {
        if (days >= 7) add(Milestone.WEEK_1)
        if (days >= 10) add(Milestone.DAYS_10)
        if (days >= 30) add(Milestone.DAYS_30)
    }

    fun forContext(type: UserContextType, previous: UserContextType?): Milestone? = when (type) {
        UserContextType.WORK -> Milestone.FIRST_WORK
        UserContextType.LUNCH -> Milestone.FIRST_LUNCH
        UserContextType.GYM -> Milestone.FIRST_GYM
        UserContextType.HOME -> if (previous != null && previous != UserContextType.HOME) Milestone.FIRST_HOME_RETURN else null
        else -> null
    }
}

@Singleton
class MemoryEngine @Inject constructor(
    private val dao: MemoryDao,
    private val settings: SettingsRepository,
    private val timeline: TimelineRepository,
    private val clock: ClockProvider,
) {
    /** Retorna true se a memória é nova. */
    suspend fun unlock(m: Milestone, at: Long = clock.nowMillis()): Boolean {
        val inserted = dao.insert(MemoryEntity(m.name, m.emoji, m.title, at)) != -1L
        if (inserted) timeline.recordMemory("✨", "Nova memória: ${m.title}", at)
        return inserted
    }

    suspend fun onContext(type: UserContextType, previous: UserContextType?, at: Long) {
        MilestoneRules.forContext(type, previous)?.let { unlock(it, at) }
    }

    suspend fun checkCalendar() {
        val s = settings.current()
        if (!s.onboardingDone || s.installedAt == 0L) return
        val zone = clock.zone()
        val now = clock.now()
        val days = ChronoUnit.DAYS.between(s.installedAt.atZone(zone).toLocalDate(), now.toLocalDate())
        MilestoneRules.forDaysTogether(days).forEach { unlock(it) }
        if (now.dayOfWeek == DayOfWeek.MONDAY) unlock(Milestone.FIRST_MONDAY)
    }
}
