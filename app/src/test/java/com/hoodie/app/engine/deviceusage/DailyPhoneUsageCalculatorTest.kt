package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.domain.phoneinsights.model.AppSession
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.domain.phoneinsights.model.ScreenSession
import com.hoodie.app.engine.MONDAY
import com.hoodie.app.engine.ZONE
import com.hoodie.app.engine.timeline.ContextSpan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class DailyPhoneUsageCalculatorTest {
    private val date = LocalDate.of(2026, 10, MONDAY)

    /** O dia de exemplo do plano: WhatsApp, Teams, YouTube no almoço, Spotify à noite. */
    private val dayEvents = use(WHATSAPP, mon(9, 2), 5) +
        use(TEAMS, mon(9, 15), 27) +
        use(YOUTUBE, mon(12, 18), 8) +
        use(SPOTIFY, mon(19), 58) +
        use(YOUTUBE, mon(21), 64) +
        listOf(screenOn(mon(23)), unlock(mon(23)), fg(LAUNCHER, mon(23)), screenOff(mon(23, 1)))

    private val contexts = listOf(
        ContextSpan(UserContextType.HOME, DAY_START, mon(8)),
        ContextSpan(UserContextType.COMMUTING, mon(8), mon(8, 49)),
        ContextSpan(UserContextType.WORK, mon(8, 49), mon(12, 16)),
        ContextSpan(UserContextType.LUNCH, mon(12, 16), mon(13, 8)),
        ContextSpan(UserContextType.HOME, mon(18, 30), null),
    )

    private fun assemble(metadata: FakeAppMetadata = FakeAppMetadata(), overrides: Map<String, HoodieAppCategory> = emptyMap()) =
        PhoneInsightsAssembler.assemble(date, dayEvents, contexts, metadata, AppCategoryResolver(overrides), DAY_START, DAY_END, ZONE)

    @Test
    fun `resumo do dia - tela, desbloqueios, sessoes, primeiro e ultimo uso`() {
        val s = assemble().summary
        assertEquals(6, s.unlockCount)
        assertEquals(6, s.sessionCount)
        assertTrue(s.screenTimeMs in (163 * MINUTE_MS)..(164 * MINUTE_MS))
        assertTrue(s.longestSessionMs >= 64 * MINUTE_MS)
        assertEquals(mon(9, 2), s.firstUseAt)
        assertTrue(s.lastUseAt!! >= mon(23, 1))
        assertFalse(s.isEstimated)
    }

    @Test
    fun `top apps ordenados por tempo com sessoes e ignorando o launcher`() {
        val apps = assemble().topApps
        assertEquals(listOf(YOUTUBE, SPOTIFY, TEAMS, WHATSAPP), apps.map { it.packageName })
        val yt = apps.first()
        assertEquals("YouTube", yt.appLabel)
        assertEquals(2, yt.sessionCount)
        assertEquals(HoodieAppCategory.VIDEO, yt.appCategory)
        assertEquals(AppIconSource.Installed(YOUTUBE), yt.iconSource)
        assertEquals(4, assemble().appCount)
    }

    @Test
    fun `app desinstalado usa icone generico`() {
        val apps = assemble(FakeAppMetadata(uninstalled = setOf(SPOTIFY))).topApps
        assertEquals(AppIconSource.Generic, apps.first { it.packageName == SPOTIFY }.iconSource)
    }

    @Test
    fun `categorias somam o tempo dos apps e respeitam override`() {
        val cats = assemble().categoryUsage
        assertEquals(HoodieAppCategory.VIDEO, cats.first().category)
        // appCount é por app, não por sessão: YouTube teve 2 sessões, mas é 1 app.
        assertEquals(1, cats.first().appCount)
        val withOverride = assemble(overrides = mapOf(YOUTUBE to HoodieAppCategory.MUSIC)).categoryUsage
        assertEquals(HoodieAppCategory.MUSIC, withOverride.first().category)
        assertEquals(2, withOverride.first().appCount)
    }

    @Test
    fun `uso por contexto cruza com a rotina`() {
        val byCtx = assemble().usageByContext
        assertEquals(TEAMS, byCtx.first { it.context == UserContextType.WORK }.apps.first().packageName)
        assertEquals(YOUTUBE, byCtx.first { it.context == UserContextType.LUNCH }.apps.single().packageName)
        assertEquals(listOf(YOUTUBE, SPOTIFY), byCtx.first { it.context == UserContextType.HOME }.apps.map { it.packageName })
    }

    @Test
    fun `timeline digital mostra blocos relevantes em ordem com o contexto`() {
        val t = assemble().appTimeline
        // WhatsApp (5 min) entra; nada abaixo de 3 min.
        assertEquals(listOf(WHATSAPP, TEAMS, YOUTUBE, SPOTIFY, YOUTUBE), t.map { it.packageName })
        assertEquals(UserContextType.LUNCH, t[2].context)
        assertTrue(t.zipWithNext().all { (a, b) -> a.startedAt <= b.startedAt })
    }

    @Test
    fun `tempo de tela por hora soma o total e quebra na hora cheia`() {
        val sessions = listOf(ScreenSession(mon(9, 50), mon(10, 20)))
        val hourly = DailyPhoneUsageCalculator.hourly(sessions, ZONE)
        assertEquals(24, hourly.size)
        assertEquals(10 * MINUTE_MS, hourly[9])
        assertEquals(20 * MINUTE_MS, hourly[10])
        assertEquals(assemble().summary.screenTimeMs, assemble().hourlyScreenMs.sum())
    }

    @Test
    fun `sessoes muito curtas somam tempo mas nao contam como sessao`() {
        val apps = DailyPhoneUsageCalculator.apps(
            listOf(AppSession(YOUTUBE, mon(8), mon(8) + 500), AppSession(YOUTUBE, mon(9), mon(9, 10))),
            { it }, { HoodieAppCategory.VIDEO }, { true },
        )
        assertEquals(1, apps.single().sessionCount)
        assertEquals(10 * MINUTE_MS + 500, apps.single().foregroundMs)
    }

    @Test
    fun `dia sem uso`() {
        val i = PhoneInsightsAssembler.assemble(date, emptyList(), contexts, FakeAppMetadata(), AppCategoryResolver(), DAY_START, DAY_END, ZONE)
        assertTrue(i.isEmpty)
        assertNull(i.summary.firstUseAt)
        assertEquals(0L, i.hourlyScreenMs.sum())
    }

    @Test
    fun `muitos apps - armazena no maximo o top configurado`() {
        val events = (0 until 40).flatMap { use("app.many$it", mon(6) + it * 10 * MINUTE_MS, 2 + it % 5L) }
        val i = PhoneInsightsAssembler.assemble(date, events, emptyList(), FakeAppMetadata(), AppCategoryResolver(), DAY_START, DAY_END, ZONE)
        assertEquals(com.hoodie.app.core.config.HoodieConfig.TOP_APPS_STORED, i.topApps.size)
        assertEquals(40, i.appCount)
        assertTrue(i.summary.screenTimeMs < 24 * HOUR_MS)
    }
}
