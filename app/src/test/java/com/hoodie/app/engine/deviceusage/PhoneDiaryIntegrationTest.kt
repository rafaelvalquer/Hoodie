package com.hoodie.app.engine.deviceusage

import com.hoodie.app.core.datastore.DigitalSettings
import com.hoodie.app.core.deviceusage.UsagePermissionState
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS
import com.hoodie.app.domain.diary.model.DailyDiary
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryMapData
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType
import com.hoodie.app.domain.diary.model.ReplaySequence
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.domain.phoneinsights.model.AppUsageEntry
import com.hoodie.app.domain.phoneinsights.model.CategoryUsageSummary
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneInsights
import com.hoodie.app.domain.phoneinsights.model.DailyPhoneSummary
import com.hoodie.app.domain.phoneinsights.model.HoodieAppCategory
import com.hoodie.app.domain.phoneinsights.model.PhoneTimelineItem
import com.hoodie.app.engine.ZONE
import com.hoodie.app.engine.diary.DiaryDigitalMerger
import com.hoodie.app.presentation.screens.phoneinsights.PhoneInsightsStatus
import com.hoodie.app.presentation.screens.phoneinsights.PhoneInsightsUiState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Integração com o Diário, reações do Hoodie e estados da tela Digital. */
class PhoneDiaryIntegrationTest {
    private val date = LocalDate.of(2026, 10, 5)

    private fun insights(screen: Long = 2 * HOUR_MS, top: HoodieAppCategory = HoodieAppCategory.VIDEO, longest: Long = 20 * MINUTE_MS, timeline: List<PhoneTimelineItem> = emptyList()) =
        DailyPhoneInsights(
            summary = DailyPhoneSummary(date, screen, 10, 12, mon(7), mon(23), longest),
            topApps = listOf(AppUsageEntry(YOUTUBE, "YouTube", top, screen, 3, mon(7), mon(23), AppIconSource.Installed(YOUTUBE))),
            usageByContext = emptyList(),
            appTimeline = timeline,
            categoryUsage = listOf(CategoryUsageSummary(top, screen, 1)),
        )

    private fun diary(vararg items: DiaryTimelineItem): DailyDiary {
        val timeline = items.toList()
        return DailyDiary(DailySummary(date), timeline, emptyList(), DiaryMapData(), ReplaySequence(DAY_START, DAY_END, emptyList(), timeline))
    }

    @Test
    fun `usos longos entram na timeline do diario e do replay em ordem`() {
        val base = diary(
            DiaryTimelineItem("left-1", mon(12, 16), DiaryTimelineType.LEFT, DiaryActor.USER, "Saiu para almoço"),
            DiaryTimelineItem("hoodie-1", mon(12, 20), DiaryTimelineType.ACTIVITY, DiaryActor.HOODIE, "Hoodie foi almoçar"),
        )
        val phone = insights(
            timeline = listOf(
                PhoneTimelineItem(YOUTUBE, "YouTube", HoodieAppCategory.VIDEO, mon(12, 18), mon(12, 26), UserContextType.LUNCH),
                PhoneTimelineItem(WHATSAPP, "WhatsApp", HoodieAppCategory.SOCIAL, mon(13), mon(13, 4), null), // < 5 min: fica só na aba Digital
            ),
        )
        val merged = DiaryDigitalMerger.merge(base, phone, ZONE)
        assertEquals(listOf("Saiu para almoço", "YouTube · 8min", "Hoodie foi almoçar"), merged.timeline.map { it.title })
        val item = merged.timeline[1]
        assertEquals(DiaryActor.PHONE, item.actor)
        assertEquals(DiaryTimelineType.APP_USAGE, item.type)
        assertEquals("12:18–12:26 · 🎬 Vídeo", item.subtitle)
        assertEquals(UserContextType.LUNCH, item.relatedContext)
        assertEquals(merged.timeline, merged.replay.timeline)
        assertSame(phone, merged.phoneInsights)
    }

    @Test
    fun `sem insights o diario fica intacto`() {
        val base = diary()
        assertSame(base, DiaryDigitalMerger.merge(base, null, ZONE))
        val empty = DailyPhoneInsights(DailyPhoneSummary.empty(date), emptyList(), emptyList(), emptyList(), emptyList())
        assertNull(DiaryDigitalMerger.merge(base, empty, ZONE).phoneInsights)
    }

    @Test
    fun `hoodie reage ao dia digital`() {
        assertEquals(DigitalMood.TIRED, HoodieDigitalReactions.react(insights(screen = 7 * HOUR_MS)).mood)
        assertEquals(DigitalMood.CURIOUS, HoodieDigitalReactions.react(insights(longest = 2 * HOUR_MS)).mood)
        assertEquals(DigitalMood.MUSICAL, HoodieDigitalReactions.react(insights(top = HoodieAppCategory.MUSIC)).mood)
        assertEquals(DigitalMood.BUSY, HoodieDigitalReactions.react(insights(top = HoodieAppCategory.WORK)).mood)
        assertEquals(DigitalMood.CALM, HoodieDigitalReactions.react(null).mood)
        assertTrue(HoodieDigitalReactions.react(null, "Mingau").text.contains("Mingau"))
    }

    @Test
    fun `estados da tela digital`() {
        assertFalse(DigitalSettings().analysisEnabled)
        val on = DigitalSettings(analysisEnabled = true)
        fun status(p: UsagePermissionState, s: DigitalSettings = on, i: DailyPhoneInsights? = null, loading: Boolean = false, error: com.hoodie.app.core.error.AppError? = null) =
            PhoneInsightsUiState.statusOf(p, s, i, loading, error)
        assertEquals(PhoneInsightsStatus.NEEDS_PERMISSION, status(UsagePermissionState.DENIED))
        assertEquals(PhoneInsightsStatus.DISABLED, status(UsagePermissionState.GRANTED, on.copy(analysisEnabled = false)))
        assertEquals(PhoneInsightsStatus.LOADING, status(UsagePermissionState.GRANTED, loading = true))
        assertEquals(PhoneInsightsStatus.EMPTY, status(UsagePermissionState.GRANTED))
        assertEquals(PhoneInsightsStatus.ERROR, status(UsagePermissionState.GRANTED, error = com.hoodie.app.core.error.UsageAccessError.ReadFailed))
        assertEquals(PhoneInsightsStatus.READY, status(UsagePermissionState.GRANTED, i = insights()))
        // Permissão revogada, mas com histórico salvo: mostra o histórico + aviso.
        assertEquals(PhoneInsightsStatus.READY, status(UsagePermissionState.DENIED, i = insights()))
        val ui = PhoneInsightsUiState(date, UsagePermissionState.DENIED, on, insights(), isLoading = false)
        assertTrue(ui.showPermissionBanner)
    }
}
