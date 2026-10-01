package com.hoodie.app.presentation.screens.pixellab

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.sprite.Expression
import com.hoodie.app.presentation.components.AnimatedHoodie
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.HoodieSceneView
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SceneThumbnail
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors

/**
 * Ferramenta interna: revisar cenas, animações, períodos e expressões sem
 * precisar simular localização. Inclui Animation Gallery e Scene Gallery.
 */
@Composable
fun PixelLabScreen(onBack: () -> Unit) {
    var tab by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PIXEL LAB", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("✕", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.clickable(onClick = onBack).padding(8.dp))
        }
        ChipRow(listOf("Lab", "Animações", "Cenas"), tab, { tab = it })
        when (tab) {
            0 -> Lab()
            1 -> AnimationGallery()
            else -> SceneGallery()
        }
    }
}

@Composable
private fun Lab() {
    var scene by remember { mutableStateOf(SceneId.HOME) }
    var anim by remember { mutableStateOf(AnimationId.WORK_TYPING) }
    var period by remember { mutableStateOf(DayPeriod.DAY) }
    var expression by remember { mutableStateOf(Expression.NORMAL) }
    var speed by remember { mutableFloatStateOf(1f) }
    var spot by remember { mutableStateOf<com.hoodie.app.pixel.scene.SpotId?>(null) }
    val s = SceneRegistry[scene]
    val chosenSpot = spot?.takeIf { it in s.spots } ?: s.defaultSpot
    val visual = VisualState(
        scene = scene,
        spot = chosenSpot,
        actions = listOf(MicroAction(anim, 1, 60_000, 60_000)),
        expression = expression.takeIf { it != Expression.NORMAL },
        tvOn = true,
        backpackWalk = anim == AnimationId.WALK_BACKPACK,
    )
    Box(Modifier.fillMaxWidth().aspectRatio(240f / 320f)) {
        HoodieSceneView(visual, Modifier.fillMaxSize(), greet = false, speed = speed, periodOverride = period)
    }
    SectionLabel("Cena")
    ChipRow(SceneId.entries.map { it.label }, scene.ordinal, { scene = SceneId.entries[it] })
    SectionLabel("Spot")
    val spots = s.spots.keys.toList()
    ChipRow(spots.map { it.name }, spots.indexOf(chosenSpot), { spot = spots[it] })
    SectionLabel("Animação")
    ChipRow(AnimationId.entries.map { it.label }, anim.ordinal, { anim = AnimationId.entries[it] })
    SectionLabel("Horário")
    ChipRow(DayPeriod.entries.map { it.label }, period.ordinal, { period = DayPeriod.entries[it] })
    SectionLabel("Expressão")
    ChipRow(Expression.entries.map { it.label }, expression.ordinal, { expression = Expression.entries[it] })
    SectionLabel("Velocidade")
    val speeds = listOf(0.25f, 0.5f, 1f, 2f)
    ChipRow(speeds.map { "${it}x" }, speeds.indexOf(speed), { speed = speeds[it] })
}

@Composable
private fun AnimationGallery() {
    var playing by remember { mutableStateOf<AnimationId?>(null) }
    playing?.let { a ->
        PixelPanel(Modifier.fillMaxWidth()) {
            Text("${a.label} · ${a.frames.size} frames · ${a.fps} fps · ${if (a.loop) "loop" else "uma vez"}", style = MaterialTheme.typography.labelLarge)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AnimatedHoodie(a, size = 200.dp) }
        }
    }
    AnimationId.entries.forEach { a ->
        PixelPanel(Modifier.fillMaxWidth(), onClick = { playing = a }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(a.label, modifier = Modifier.weight(1f))
                Text("▶", color = HoodieColors.Gold)
            }
        }
    }
}

@Composable
private fun SceneGallery() {
    SceneId.entries.forEach { id ->
        Text(id.label.uppercase(), style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DayPeriod.entries.forEach { p ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                    SceneThumbnail(id, p, Modifier.fillMaxWidth().aspectRatio(240f / 320f))
                    Text(p.label, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
                }
            }
        }
    }
}
