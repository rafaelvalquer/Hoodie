package com.hoodie.app.presentation.screens.diary

import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.platform.LocalContext
import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import android.graphics.Bitmap
import android.os.SystemClock
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.domain.phoneinsights.model.AppIconSource
import com.hoodie.app.pixel.diary.DiaryMapLayout
import com.hoodie.app.pixel.diary.DiaryMapPlaceNode
import com.hoodie.app.pixel.diary.DiaryMapRenderCache
import com.hoodie.app.pixel.diary.DiaryMapRenderer
import com.hoodie.app.pixel.diary.DiaryMapScene
import com.hoodie.app.pixel.diary.DiaryMapTiles
import com.hoodie.app.pixel.diary.DiaryMapViewport
import com.hoodie.app.pixel.diary.MapPoint
import com.hoodie.app.pixel.phoneinsights.AppBadge
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors
import kotlinx.coroutines.delay


/** Cadência do mapa: ~10 FPS com replay ativo, ~2,5 FPS parado; pausa fora da tela. */
object DiaryMapClock {
    const val ACTIVE_MS = 100L
    const val IDLE_MS = 400L
    fun intervalMs(activeReplay: Boolean) = if (activeReplay) ACTIVE_MS else IDLE_MS
    fun targetFps(activeReplay: Boolean) = (1000 / intervalMs(activeReplay)).toInt()
}

/** Relógio próprio do mapa (não roda a cada frame da UI) e só enquanto a tela está RESUMED. */
@Composable
fun rememberDiaryMapClock(activeReplay: Boolean): Long {
    com.hoodie.app.presentation.components.LocalPixelRenderFrame.current?.let { return it.animationMillis }
    var time by remember { mutableLongStateOf(SystemClock.uptimeMillis()) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(activeReplay, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                time = SystemClock.uptimeMillis()
                delay(DiaryMapClock.intervalMs(activeReplay))
            }
        }
    }
    return time
}

/**
 * Estado do replay → cena do renderer. Luz: pelo horário do replay sempre que houver
 * um (inclusive FINISHED — um dia que termina às 22h continua noturno). Estados dos
 * prédios (visitado/atual/ainda não): só durante PLAYING/PAUSED.
 */
fun diaryMapScene(layout: DiaryMapLayout, replay: ReplayUiState, timeMs: Long): DiaryMapScene = DiaryMapScene(
    layout = layout,
    replaying = replay.replaying,
    activeVisitIndex = DiaryMapScene.indexOf(replay.activeNodeId, "visit-"),
    activeTripIndex = DiaryMapScene.indexOf(replay.activeEdgeId, "edge-"),
    tripProgress = replay.edgeProgress,
    period = if (replay.hasReplayTimestamp) replay.dayPeriod else DayPeriod.DAY,
    timeMs = timeMs,
)

/**
 * Mapa do dia em pixel art. Canvas, etiquetas, áreas de toque, Hoodie e balão do
 * app usam o mesmo [DiaryMapViewport]. A cidade estática vem de um cache por layout.
 */
@Composable
fun DiaryMapView(layout: DiaryMapLayout, replay: ReplayUiState, onNode: (DiaryMapPlaceNode) -> Unit, modifier: Modifier = Modifier) {
    val uiTextContext = LocalContext.current
    var selectedNodeId by remember(layout) { mutableStateOf<String?>(null) }
    PixelPanel(modifier.fillMaxWidth().testTag("diary_map")) {
        SectionLabel(stringResource(R.string.ui_diary_map_view_1))
        Spacer(Modifier.height(8.dp))
        val time = rememberDiaryMapClock(activeReplay = replay.state == ReplayState.PLAYING)
        val scene = diaryMapScene(layout, replay, time).let { if (layout.isEmpty) it.copy(period = DayPeriod.NIGHT) else it }
        val cache = remember(layout) { DiaryMapRenderCache.create(layout) }
        val buffer = remember { PixelBuffer(DiaryMapTiles.WIDTH, DiaryMapTiles.HEIGHT) }
        val bitmap = remember { Bitmap.createBitmap(DiaryMapTiles.WIDTH, DiaryMapTiles.HEIGHT, Bitmap.Config.ARGB_8888) }
        BoxWithConstraints(Modifier.fillMaxWidth().aspectRatio(DiaryMapTiles.WIDTH.toFloat() / DiaryMapTiles.HEIGHT)) {
            val density = LocalDensity.current
            val viewport = with(density) { DiaryMapViewport(maxWidth.toPx(), maxHeight.toPx()) }
            Canvas(Modifier.fillMaxSize()) {
                DiaryMapRenderer.render(scene, cache, buffer)
                bitmap.setPixels(buffer.pixels, 0, buffer.width, 0, 0, buffer.width, buffer.height)
                drawImage(
                    bitmap.asImageBitmap(),
                    dstOffset = IntOffset(viewport.offsetX.toInt(), viewport.offsetY.toInt()),
                    dstSize = IntSize((DiaryMapTiles.WIDTH * viewport.scale).toInt(), (DiaryMapTiles.HEIGHT * viewport.scale).toInt()),
                    filterQuality = FilterQuality.None,
                )
            }
            if (layout.isEmpty) {
                Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                    PixelPanel(color = HoodieColors.Panel) { Text(stringResource(R.string.diary_map_empty), color = HoodieColors.Ink, style = MaterialTheme.typography.bodyMedium) }
                }
            }
            // Áreas de toque: mesma conversão do Canvas.
            layout.nodes.forEach { node ->
                val nodeDescription = pluralStringResource(R.plurals.diary_place_visits, node.visitIndices.size, node.label, node.visitIndices.size)
                val nodeSelected = selectedNodeId == node.id || replay.activeNodeId == node.id
                val selectionDescription = stringResource(if (nodeSelected) R.string.control_selected else R.string.control_not_selected)
                val (px, py, pw, ph) = node.footprint.pixels.toList()
                val topLeft = viewport.toScreen(MapPoint(px, py))
                val bottomRight = viewport.toScreen(MapPoint(px + pw, py + ph + DiaryMapTiles.SIZE))
                with(density) {
                    Box(
                        Modifier.offset(topLeft.x.toDp(), topLeft.y.toDp())
                            .size((bottomRight.x - topLeft.x).toDp().coerceAtLeast(48.dp), (bottomRight.y - topLeft.y).toDp().coerceAtLeast(48.dp))
                            .semantics {
                                contentDescription = nodeDescription
                                selected = nodeSelected
                                stateDescription = selectionDescription
                                role = Role.Button
                            }
                            .clickable { selectedNodeId = node.id; onNode(node) },
                    )
                }
            }
            // Etiquetas: Casa/Trabalho sempre; o resto quando tocado ou ativo no replay.
            val activeNode = if (replay.replaying) DiaryMapLabelPolicy.activeNodeId(layout, replay.activeNodeId) else null
            val visible = DiaryMapLabelPolicy.visibleNodeIds(layout, activeNode, selectedNodeId)
            layout.nodes.filter { it.id in visible }.forEach { node ->
                val anchor = viewport.toScreen(DiaryMapLabelPolicy.anchor(node))
                var size by remember(node.id) { mutableStateOf(IntSize.Zero) }
                with(density) {
                    DiaryMapNodeLabel(
                        node.label.take(12),
                        highlighted = node.id == activeNode,
                        modifier = Modifier
                            .wrapContentSize()
                            .onSizeChanged { size = it }
                            .offset((anchor.x - size.width / 2f).coerceAtLeast(0f).toDp(), (anchor.y - size.height).coerceAtLeast(0f).toDp()),
                    )
                }
            }
            // Balão do app: aparece quando uma sessão começa, some depois de ~2,5 s (fica só no HUD).
            val app = replay.activePhoneApp
            var badgeFor by remember { mutableStateOf<String?>(null) }
            val sessionKey = app?.let { "${it.packageName}@${it.startedAt}" }
            LaunchedEffect(sessionKey, replay.state) {
                if (sessionKey != null && replay.state == ReplayState.PLAYING) {
                    badgeFor = sessionKey
                    delay(APP_BADGE_MS)
                    if (badgeFor == sessionKey) badgeFor = null
                } else if (sessionKey == null) badgeFor = null
            }
            val marker = DiaryMapRenderer.marker(scene)
            if (app != null && marker != null && badgeFor == sessionKey) {
                val at = viewport.toScreen(MapPoint(marker.position.x, marker.position.y - 26))
                with(density) {
                    Row(
                        Modifier.offset((at.x - 40.dp.toPx()).coerceAtLeast(0f).toDp(), (at.y - 22.dp.toPx()).coerceAtLeast(0f).toDp())
                            .border(2.dp, HoodieColors.Outline).background(HoodieColors.Panel).padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        AppBadge(AppIconSource.Installed(app.packageName), app.category, size = 14.dp, showCategoryDot = false)
                        Text(app.appLabel.take(10), style = MaterialTheme.typography.labelSmall, color = HoodieColors.Ink, maxLines = 1)
                    }
                }
            }
        }
        Text(
            if (layout.isEmpty) uiTextContext.getString(R.string.ui_extra_diary_map_view_1) else uiTextContext.getString(R.string.ui_extra_diary_map_view_2),
            style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

/** Quanto tempo o balão do app fica sobre o Hoodie. */
const val APP_BADGE_MS = 2_500L
