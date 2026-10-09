package com.hoodie.app.presentation.screens.diary

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.hoodie.app.R
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.formatDuration
import com.hoodie.app.domain.diary.journey.DayChapter
import com.hoodie.app.domain.diary.journey.JourneyPlan
import com.hoodie.app.domain.diary.journey.JourneyStop
import com.hoodie.app.domain.diary.model.JourneyNode
import com.hoodie.app.engine.diary.journey.JourneyChapterPlanner
import com.hoodie.app.engine.diary.journey.JourneyOverworldModel
import com.hoodie.app.engine.diary.journey.OverworldLayout
import com.hoodie.app.pixel.diary.overworld.OverworldRenderCache
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.ZoneId

/**
 * Dia cheio em três capítulos. Só o aberto tem canvas (e anima); os fechados são
 * Compose puro. No replay o capítulo do horário abre sozinho e a tela rola até ele;
 * pausado, o toque num capítulo fechado o abre.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JourneyChaptersView(
    model: JourneyOverworldModel,
    replay: ReplayUiState,
    zone: ZoneId,
    manualChapter: DayChapter?,
    isToday: Boolean,
    nowMillis: Long,
    selectedStopId: String?,
    onOpenChapter: (DayChapter) -> Unit,
    onStop: (JourneyStop) -> Unit,
    preparedCaches: Map<OverworldLayout, OverworldRenderCache> = emptyMap(),
    modifier: Modifier = Modifier,
) {
    val plan = model.plan as? JourneyPlan.Chapters ?: return
    val active = JourneyChapterPlanner.activeChapter(plan, replay.currentTimestamp, replay.state == ReplayState.PLAYING, manualChapter, isToday, nowMillis)
    val requester = remember { BringIntoViewRequester() }
    LaunchedEffect(active, replay.state) { if (replay.state == ReplayState.PLAYING) requester.bringIntoView() }
    PixelPanel(modifier.fillMaxWidth().testTag("journey_chapters")) {
        SectionLabel(stringResource(R.string.journey_title))
        Column(Modifier.fillMaxWidth().padding(top = 8.dp).animateContentSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            plan.chapters.forEach { chapter ->
                val layout = model.chapters[chapter.chapter]
                if (chapter.chapter == active && layout != null) {
                    val name = stringResource(chapter.chapter.labelRes())
                    val openDescription = stringResource(R.string.journey_chapter_open_description, name)
                    Column(Modifier.fillMaxWidth().bringIntoViewRequester(requester).testTag("journey_chapter_open_${chapter.chapter.name.lowercase()}")) {
                        Row(Modifier.fillMaxWidth().heightIn(min = 32.dp).semantics { heading(); contentDescription = openDescription }) {
                            Text(name.uppercase(), style = MaterialTheme.typography.labelLarge, color = HoodieColors.Gold)
                            Text(
                                "  " + pluralStringResource(R.plurals.journey_chapter_stops, chapter.visitCount, chapter.visitCount) + " · " + formatDuration(chapter.totalStayMs),
                                style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted,
                            )
                        }
                        JourneyOverworldMapView(model, layout, replay, zone, selectedStopId, onStop, framed = true, preparedCache = preparedCaches[layout])
                    }
                } else {
                    JourneyChapterSummary(plan, chapter, onOpen = { onOpenChapter(chapter.chapter) })
                }
            }
        }
        Text(stringResource(R.string.journey_v3_footer), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp))
    }
}

/** Lista das paradas rápidas de um marco "×k" ou de um tique agrupado do relógio. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickStopsSheet(nodes: List<JourneyNode>, zone: ZoneId, onNode: (JourneyNode) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.fillMaxWidth().padding(16.dp).testTag("quick_stops_sheet"), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            SectionLabel(stringResource(R.string.quick_stops_title))
            nodes.forEach { n ->
                Text(
                    "${n.placeType.emoji} ${n.placeName} · ${formatClock(n.arrivalAt, zone)} · ${formatDuration(n.durationMs)}",
                    style = MaterialTheme.typography.bodyMedium, color = HoodieColors.Ink,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable { onNode(n) }.padding(vertical = 12.dp),
                )
            }
        }
    }
}
