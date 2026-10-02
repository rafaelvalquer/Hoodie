package com.hoodie.app.presentation.screens.diary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.database.ContextEventEntity
import com.hoodie.app.core.database.HoodieActivityEntity
import com.hoodie.app.core.model.ContextSource
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.formatClock
import com.hoodie.app.core.time.startOfDay
import com.hoodie.app.domain.diary.model.DailySummary
import com.hoodie.app.domain.diary.model.DiaryActor
import com.hoodie.app.domain.diary.model.DiaryTimelineItem
import com.hoodie.app.domain.diary.model.DiaryTimelineType
import com.hoodie.app.domain.diary.model.PlaceVisit
import com.hoodie.app.domain.diary.model.ReplaySequence
import com.hoodie.app.engine.diary.DailyMapBuilder
import com.hoodie.app.engine.diary.ReplaySequenceBuilder
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import java.time.LocalDate
import java.time.ZoneId

/** Fixture interativa para exercitar o mapa, a seleção de lugares e o replay sem dados reais. */
@Composable
fun DiaryLabScreen(modifier: Modifier = Modifier) {
    val zone = ZoneId.systemDefault()
    val fixture = remember { DiaryLabFixture.create(LocalDate.now(zone), zone) }
    var replay by remember { mutableStateOf(ReplayUiState()) }
    var selectedNodeId by remember { mutableStateOf<String?>(null) }
    val layout = remember { com.hoodie.app.pixel.diary.DiaryMapLayoutEngine.layout(fixture.visits) }

    LaunchedEffect(replay.state, replay.speed) {
        if (replay.state == ReplayState.PLAYING) {
            var timestamp = replay.currentTimestamp ?: fixture.replay.startAt
            var last = android.os.SystemClock.elapsedRealtime()
            while (timestamp < fixture.replay.endAt) {
                kotlinx.coroutines.delay(80)
                val elapsed = (android.os.SystemClock.elapsedRealtime() - last).coerceAtLeast(1)
                last = android.os.SystemClock.elapsedRealtime()
                timestamp = (timestamp + elapsed * replay.speed.multiplier * 60L).coerceAtMost(fixture.replay.endAt)
                val currentFrame = fixture.replay.frameAt(timestamp)
                val span = (fixture.replay.endAt - fixture.replay.startAt).coerceAtLeast(1)
                replay = replay.copy(
                    currentTimestamp = timestamp,
                    activeNodeId = currentFrame.activeNodeId,
                    activeEdgeId = currentFrame.activeEdgeId,
                    markerX = currentFrame.markerX,
                    markerY = currentFrame.markerY,
                    edgeProgress = currentFrame.progressOnEdge,
                    progress = ((timestamp - fixture.replay.startAt).toFloat() / span).coerceIn(0f, 1f),
                )
            }
            val lastNode = fixture.map.nodes.lastOrNull()
            replay = replay.copy(state = ReplayState.FINISHED, currentTimestamp = fixture.replay.endAt, activeNodeId = lastNode?.id, activeEdgeId = null, markerX = lastNode?.x?.toFloat(), markerY = lastNode?.y?.toFloat(), progress = 1f)
        }
    }

    Column(
        modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("DIARY LAB", style = MaterialTheme.typography.titleLarge, color = HoodieColors.Hood)
        PixelPanel(Modifier.fillMaxWidth()) {
            SectionLabel("CENÁRIO FICTÍCIO · ${fixture.date}")
            Text("Casa → Trabalho → Restaurante → Trabalho → Academia → Casa", color = HoodieColors.Muted)
        }
        SummarySection(fixture.summary)
        DiaryMapView(layout, replay, zone, onNode = { selectedNodeId = it.id })
        ReplayControls(
            replay,
            onToggle = {
                replay = when (replay.state) {
                    ReplayState.PLAYING -> replay.copy(state = ReplayState.PAUSED)
                    ReplayState.PAUSED -> replay.copy(state = ReplayState.PLAYING)
                    else -> ReplayUiState(state = ReplayState.PLAYING, currentTimestamp = fixture.replay.startAt, speed = replay.speed)
                }
            },
            onReset = { replay = ReplayUiState(speed = replay.speed) },
            onSpeed = { replay = replay.copy(speed = it) },
        )
        if (replay.currentTimestamp != null) {
            Text("${formatClock(replay.currentTimestamp!!, zone)} · ${replay.speed.multiplier}×", color = HoodieColors.Gold)
            androidx.compose.material3.LinearProgressIndicator(progress = { replay.progress }, modifier = Modifier.fillMaxWidth(), color = HoodieColors.Mint)
        }
        TimelineSection(fixture.timeline, replay.currentTimestamp, zone)
        PixelPanel(Modifier.fillMaxWidth()) {
            Text("O cenário é sintético e permanece no dispositivo. Use-o para revisar pontos, detalhes e a sincronização do replay.", style = MaterialTheme.typography.bodySmall, color = HoodieColors.Muted)
        }
    }

    layout.node(selectedNodeId)?.let { node ->
        PlaceDetailBottomSheet(node, fixture.visits, fixture.timeline, zone) { selectedNodeId = null }
    }
}

private data class DiaryLabFixture(
    val date: LocalDate,
    val summary: DailySummary,
    val visits: List<PlaceVisit>,
    val timeline: List<DiaryTimelineItem>,
    val map: com.hoodie.app.domain.diary.model.DiaryMapData,
    val replay: ReplaySequence,
) {
    companion object {
        fun create(date: LocalDate, zone: ZoneId): DiaryLabFixture {
            val day = startOfDay(date, zone)
            fun at(hour: Int, minute: Int = 0) = day + (hour * 60L + minute) * 60_000L
            data class Stop(val name: String, val type: PlaceType, val start: Long, val end: Long)
            val stops = listOf(
                Stop("Casa", PlaceType.HOME, at(6, 50), at(7, 45)),
                Stop("Trabalho", PlaceType.WORK, at(8, 30), at(12, 0)),
                Stop("Café do bairro", PlaceType.RESTAURANT, at(12, 10), at(13, 0)),
                Stop("Trabalho", PlaceType.WORK, at(13, 0), at(17, 30)),
                Stop("Academia", PlaceType.GYM, at(18, 0), at(19, 0)),
                Stop("Casa", PlaceType.HOME, at(19, 45), at(23, 0)),
            )
            val visits = stops.mapIndexed { index, stop ->
                val activity = if (index == 1) HoodieActivity.WORKING else if (index == 4) HoodieActivity.TRAINING else null
                PlaceVisit(
                    placeId = stops.indexOfFirst { it.name == stop.name }.toLong() + 1,
                    placeName = stop.name,
                    placeType = stop.type,
                    arrivalAt = stop.start,
                    departureAt = stop.end,
                    durationMs = stop.end - stop.start,
                    visitsCount = stops.count { it.name == stop.name },
                    dominantHoodieActivity = activity,
                )
            }
            val timeline = listOf(
                DiaryTimelineItem("lab-1", at(6, 50), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Acordou em casa", emoji = "🌤️", relatedPlaceId = 1, relatedContext = UserContextType.HOME),
                DiaryTimelineItem("lab-2", at(7, 45), DiaryTimelineType.LEFT, DiaryActor.USER, "Saiu de casa", emoji = "🚶", relatedPlaceId = 1, relatedContext = UserContextType.HOME),
                DiaryTimelineItem("lab-3", at(8, 30), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Chegou ao trabalho", emoji = "🏢", relatedPlaceId = 2, relatedContext = UserContextType.WORK),
                DiaryTimelineItem("lab-4", at(8, 55), DiaryTimelineType.ACTIVITY, DiaryActor.HOODIE, "Hoodie começou a trabalhar", emoji = "💻", relatedPlaceId = 2, relatedContext = UserContextType.WORK),
                DiaryTimelineItem("lab-5", at(12, 10), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Saiu para almoçar", emoji = "🍽️", relatedPlaceId = 3, relatedContext = UserContextType.LUNCH),
                DiaryTimelineItem("lab-6", at(13, 0), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Voltou ao trabalho", emoji = "🏢", relatedPlaceId = 2, relatedContext = UserContextType.WORK),
                DiaryTimelineItem("lab-7", at(18, 0), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Chegou à academia", emoji = "🏋️", relatedPlaceId = 5, relatedContext = UserContextType.GYM),
                DiaryTimelineItem("lab-8", at(19, 45), DiaryTimelineType.ARRIVED, DiaryActor.USER, "Chegou em casa", emoji = "🏠", relatedPlaceId = 1, relatedContext = UserContextType.HOME),
                DiaryTimelineItem("lab-9", at(20, 10), DiaryTimelineType.ACTIVITY, DiaryActor.HOODIE, "Hoodie foi jogar videogame", emoji = "🎮", relatedPlaceId = 1, relatedContext = UserContextType.HOME),
            )
            val relatedVisits = visits.map { visit ->
                visit.copy(relatedTimelineIds = timeline.filter { it.timestamp in visit.arrivalAt..(visit.departureAt ?: at(23)) }.map { it.id })
            }
            val contexts = stops.mapIndexed { index, stop ->
                ContextEventEntity(id = index.toLong() + 1, type = stop.type.toContext(), startedAt = stop.start, endedAt = stop.end, confidence = 1f, placeId = stops.indexOfFirst { it.name == stop.name }.toLong() + 1, source = ContextSource.GEOFENCE)
            }
            val activities = listOf(
                HoodieActivityEntity(id = 1, activity = HoodieActivity.WORKING, startedAt = at(8, 55), endedAt = at(11, 50), userContext = UserContextType.WORK),
                HoodieActivityEntity(id = 2, activity = HoodieActivity.GAMING, startedAt = at(20, 10), endedAt = at(21, 15), userContext = UserContextType.HOME),
            )
            val map = DailyMapBuilder.build(relatedVisits)
            val replay = ReplaySequenceBuilder.build(relatedVisits, timeline, at(6, 50), at(23), contexts, activities, at(23), map)
            return DiaryLabFixture(
                date = date,
                summary = DailySummary(date, homeMs = 4 * 60 * 60_000L, workMs = 7 * 60 * 60_000L, commutingMs = 72 * 60_000L, lunchMs = 50 * 60_000L, gymMs = 60 * 60_000L),
                visits = relatedVisits,
                timeline = timeline,
                map = map,
                replay = replay,
            )
        }
    }
}
