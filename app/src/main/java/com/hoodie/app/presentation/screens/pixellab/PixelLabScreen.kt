package com.hoodie.app.presentation.screens.pixellab

import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import android.graphics.Bitmap
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.animation.AnimGroup
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.ClipTiming
import com.hoodie.app.pixel.debug.CompareMode
import com.hoodie.app.pixel.debug.DebugOptions
import com.hoodie.app.pixel.debug.SourceCompare
import com.hoodie.app.pixel.debug.SpriteDebugRenderer
import com.hoodie.app.pixel.scene.MicroAction
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.scene.SpotId
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.sprite.AndroidSpriteSheets
import com.hoodie.app.pixel.sprite.CompositeSpriteProvider
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Expression
import com.hoodie.app.pixel.sprite.HoodieSprites
import com.hoodie.app.pixel.sprite.PoseOverlay
import com.hoodie.app.pixel.sprite.Posture
import com.hoodie.app.pixel.sprite.RequiredShippedAnimations
import com.hoodie.app.pixel.sprite.SpriteRequest
import com.hoodie.app.pixel.sprite.viewFor
import com.hoodie.app.presentation.components.AnimatedHoodie
import com.hoodie.app.presentation.components.ChipRow
import com.hoodie.app.presentation.components.HoodieSceneView
import com.hoodie.app.presentation.components.PixelButton
import com.hoodie.app.presentation.components.PixelImage
import com.hoodie.app.presentation.components.PixelPanel
import com.hoodie.app.presentation.components.SceneThumbnail
import com.hoodie.app.presentation.components.SectionLabel
import com.hoodie.app.presentation.theme.HoodieColors

/**
 * Ferramenta interna de animação: revisar clips, direções, expressões e cenas
 * sem simular localização. Inclui onion-skin, âncoras, bounding box e pés.
 */
@Composable
fun PixelLabScreen(onBack: () -> Unit) {
    val closeLabel = stringResource(R.string.pixel_lab_close)
    var tab by remember { mutableIntStateOf(0) }
    Column(
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PIXEL LAB", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("✕", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = closeLabel }.padding(8.dp))
        }
        ChipRow(listOf("Animação", "Cena", "Galeria", "Cenas", "Sprites", "Transportes"), tab, { tab = it })
        when (tab) {
            0 -> AnimationTool()
            1 -> SceneLab()
            2 -> AnimationGallery()
            3 -> SceneGallery()
            4 -> SpriteSources()
            else -> TransportLab()
        }
    }
}

@Composable
private fun AnimationTool() {
    var group by remember { mutableStateOf(AnimGroup.LOCOMOTION) }
    var anim by remember { mutableStateOf(AnimationId.WALK) }
    var direction by remember { mutableStateOf(Direction.LEFT) }
    var expression by remember { mutableStateOf(Expression.NORMAL) }
    var posture by remember { mutableStateOf(Posture.STANDING) }
    var speed by remember { mutableFloatStateOf(1f) }
    var playing by remember { mutableStateOf(true) }
    var manualFrame by remember { mutableIntStateOf(0) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var options by remember { mutableStateOf(DebugOptions(feet = true)) }
    var compare by remember { mutableStateOf(CompareMode.FINAL) }

    val active = HoodieSprites.provider
    val provider = remember(compare, active) { SourceCompare.provider(compare, active) }
    val durations = provider.durations(anim, direction)
    val count = durations.size.coerceAtLeast(1)
    val frameIndex = if (playing) ClipTiming.indexAt(durations, elapsed, loop = true) else manualFrame.mod(count)

    LaunchedEffect(playing, speed) {
        var last = -1L
        while (playing) withFrameMillis { t ->
            if (last >= 0) elapsed += ((t - last) * speed).toLong()
            last = t
        }
    }

    val request = SpriteRequest(anim, direction, frameIndex, posture, PoseOverlay(expression = expression.takeIf { it != Expression.NORMAL }))
    val image = remember(request, options, provider) {
        val buf = SpriteDebugRenderer.render(provider, request, options)
        Bitmap.createBitmap(buf.width, buf.height, Bitmap.Config.ARGB_8888).also { it.setPixels(buf.pixels, 0, buf.width, 0, 0, buf.width, buf.height) }.asImageBitmap()
    }
    val frame = remember(request, provider) { provider.frame(request) }
    val source = when (compare) {
        CompareMode.PROCEDURAL -> "procedural"
        CompareMode.OVERLAY -> "final + procedural (50%)"
        CompareMode.DIFFERENCE -> "final ≠ procedural " + remember(request) {
            "${(SourceCompare.changedRatio(active.frame(request).image, com.hoodie.app.pixel.sprite.ProceduralSpriteProvider.frame(request).image) * 100).toInt()}%"
        }
        CompareMode.FINAL -> (active as? CompositeSpriteProvider)?.providerFor(request)?.name ?: active.name
    }

    ChipRow(listOf("Procedural", "Final", "Overlay", "Difference"), compare.ordinal, { compare = CompareMode.entries[it] })
    PixelPanel(Modifier.fillMaxWidth()) {
        Text(
            "SOURCE ${source.uppercase()} · ANIMATION ${anim.name} · DIRECTION ${viewFor(anim, direction).name} · FRAME ${frameIndex + 1}/$count",
            style = MaterialTheme.typography.labelSmall, color = HoodieColors.Gold,
        )
        Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
            PixelImage(image, SpriteDebugRenderer.WIDTH, SpriteDebugRenderer.HEIGHT, Modifier.fillMaxSize())
        }
        Text("Frame ${frameIndex + 1} / $count · ${frame.durationMs} ms · ${if (anim.loop) "loop" else "uma vez"} · ${anim.clip.interruptPolicy}", style = MaterialTheme.typography.labelLarge)
        Text("fonte: ${frame.source}" + if (frame.events.isNotEmpty()) " · eventos: ${frame.events.joinToString()}" else "", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
            PixelButton("<<", { playing = false; manualFrame = (frameIndex - 1).mod(count) }, Modifier.weight(1f), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
            PixelButton(if (playing) "Pause" else "Play", { if (playing) manualFrame = frameIndex; playing = !playing }, Modifier.weight(1f))
            PixelButton(">>", { playing = false; manualFrame = (frameIndex + 1).mod(count) }, Modifier.weight(1f), color = HoodieColors.PanelLight, textColor = HoodieColors.Ink)
        }
    }

    SectionLabel("Debug")
    val toggles = listOf("Onion-skin" to options.onionSkin, "Âncoras" to options.anchors, "Bounding box" to options.boundingBox, "Pés" to options.feet)
    ChipRow(toggles.map { (n, on) -> (if (on) "☑ " else "☐ ") + n }, null, { i ->
        options = when (i) {
            0 -> options.copy(onionSkin = !options.onionSkin)
            1 -> options.copy(anchors = !options.anchors)
            2 -> options.copy(boundingBox = !options.boundingBox)
            else -> options.copy(feet = !options.feet)
        }
    })
    SectionLabel("Grupo")
    ChipRow(AnimGroup.entries.map { it.name.lowercase() }, group.ordinal, { group = AnimGroup.entries[it]; anim = AnimationId.entries.first { a -> a.group == group }; elapsed = 0; manualFrame = 0 })
    SectionLabel("Animação")
    val inGroup = AnimationId.entries.filter { it.group == group }
    ChipRow(inGroup.map { it.label }, inGroup.indexOf(anim), { anim = inGroup[it]; elapsed = 0; manualFrame = 0 })
    SectionLabel("Direção" + if (!anim.clip.directional) " (clip frontal)" else "")
    ChipRow(Direction.entries.map { it.name }, direction.ordinal, { direction = Direction.entries[it] })
    SectionLabel("Postura (clips que herdam)")
    ChipRow(Posture.entries.map { it.name }, posture.ordinal, { posture = Posture.entries[it] })
    SectionLabel("Expressão")
    ChipRow(Expression.entries.map { it.label }, expression.ordinal, { expression = Expression.entries[it] })
    SectionLabel("Velocidade")
    val speeds = listOf(0.25f, 0.5f, 1f, 2f)
    ChipRow(speeds.map { "${it}x" }, speeds.indexOf(speed), { speed = speeds[it] })
}

@Composable
private fun SceneLab() {
    var scene by remember { mutableStateOf(SceneId.HOME) }
    var anim by remember { mutableStateOf(AnimationId.WORK_TYPING) }
    var group by remember { mutableStateOf<AnimGroup?>(null) }
    var direction by remember { mutableStateOf(Direction.FRONT) }
    var period by remember { mutableStateOf(DayPeriod.DAY) }
    var expression by remember { mutableStateOf(Expression.NORMAL) }
    var speed by remember { mutableFloatStateOf(1f) }
    var spot by remember { mutableStateOf<SpotId?>(null) }
    var variant by remember { mutableIntStateOf(0) }
    // Modo atividade: o VisualDirector monta o estado real (cena, spot, microações, olhar).
    var byActivity by remember { mutableStateOf(false) }
    var transportMode by remember { mutableStateOf(MovementMode.BUS) }
    var activity by remember { mutableStateOf(HoodieActivity.STUDYING) }
    var context by remember { mutableStateOf(UserContextType.STUDY) }
    var energy by remember { mutableIntStateOf(70) }
    var mood by remember { mutableIntStateOf(70) }
    val s = SceneRegistry[scene]
    val chosenSpot = spot?.takeIf { it in s.spots } ?: s.defaultSpot
    val visual = if (byActivity) {
        VisualDirector.resolve(activity, context, mobilityMode = transportMode.takeIf { context == UserContextType.COMMUTING }, variant = variant, energy = energy, mood = mood)
            .let { v -> v.copy(expression = expression.takeIf { it != Expression.NORMAL } ?: v.expression) }
    } else VisualState(
        scene = scene,
        spot = chosenSpot,
        actions = listOf(MicroAction(anim, 1, 60_000, 60_000, direction = if (anim.clip.directional) direction else Direction.FRONT)),
        expression = expression.takeIf { it != Expression.NORMAL },
        tvOn = true,
        variant = variant,
        backpackWalk = anim == AnimationId.WALK_BACKPACK,
    )
    Text("Trocar de cena/spot toca a transição completa (levantar, andar, porta, fade).", color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall)
    Box(Modifier.fillMaxWidth().aspectRatio(240f / 320f)) {
        HoodieSceneView(visual, Modifier.fillMaxSize(), greet = false, speed = speed, periodOverride = period)
    }
    SectionLabel("Modo")
    ChipRow(listOf("Animação", "Atividade"), if (byActivity) 1 else 0, { byActivity = it == 1 })
    if (byActivity) {
        Text("Cena: ${visual.scene.label} · spot ${visual.spot.name}", style = MaterialTheme.typography.labelLarge)
        SectionLabel("Atividade")
        ChipRow(HoodieActivity.entries.map { "${it.emoji} ${it.label}" }, activity.ordinal, { activity = HoodieActivity.entries[it] })
        SectionLabel("Contexto")
        ChipRow(UserContextType.entries.map { it.name.lowercase() }, context.ordinal, { context = UserContextType.entries[it] })
        if (context == UserContextType.COMMUTING) {
            SectionLabel("Transporte detectado")
            ChipRow(MovementMode.entries.filter { it != MovementMode.NONE }.map { "${it.emoji} ${it.label}" }, MovementMode.entries.filter { it != MovementMode.NONE }.indexOf(transportMode), { transportMode = MovementMode.entries.filter { it != MovementMode.NONE }[it] })
            Text("Perfil: ${visual.scene.label} · entrada ${visual.enter.joinToString { it.name }} · saída ${visual.exit.joinToString { it.name }}", color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall)
        }
        val levels = listOf(10, 40, 70, 95)
        SectionLabel("Energia")
        ChipRow(levels.map { "$it" }, levels.indexOf(energy), { energy = levels[it] })
        SectionLabel("Humor")
        ChipRow(levels.map { "$it" }, levels.indexOf(mood), { mood = levels[it] })
    } else {
        SectionLabel("Cena")
        ChipRow(SceneId.entries.map { it.label }, scene.ordinal, { scene = SceneId.entries[it] })
        SectionLabel("Spot")
        val spots = s.spots.keys.toList()
        ChipRow(spots.map { it.name }, spots.indexOf(chosenSpot), { spot = spots[it] })
        SectionLabel("Grupo")
        val groups = listOf<AnimGroup?>(null) + AnimGroup.entries
        ChipRow(groups.map { it?.name?.lowercase() ?: "todos" }, groups.indexOf(group), { group = groups[it] })
        SectionLabel("Animação")
        val anims = AnimationId.entries.filter { group == null || it.group == group }
        ChipRow(anims.map { it.label }, anims.indexOf(anim), { anim = anims[it] })
        if (anim.clip.directional) {
            SectionLabel("Direção")
            ChipRow(Direction.entries.map { it.name }, direction.ordinal, { direction = Direction.entries[it] })
        }
    }
    if (SceneRegistry[visual.scene].backgroundVariants > 1) {
        SectionLabel("Variante do cenário")
        val n = SceneRegistry[visual.scene].backgroundVariants
        ChipRow((0 until n).map { "${it + 1}" }, variant.mod(n), { variant = it })
    }
    SectionLabel("Horário")
    ChipRow(DayPeriod.entries.map { it.label }, period.ordinal, { period = DayPeriod.entries[it] })
    SectionLabel("Expressão")
    ChipRow(Expression.entries.map { it.label }, expression.ordinal, { expression = Expression.entries[it] })
    SectionLabel("Velocidade")
    val speeds = listOf(0.25f, 0.5f, 1f, 2f)
    ChipRow(speeds.map { "${it}x" }, speeds.indexOf(speed), { speed = speeds[it] })
}

/** Revisa um perfil completo e toca explicitamente ENTER, LOOP ou EXIT. */
@Composable
private fun TransportLab() {
    val modes = MovementMode.entries.filter { it != MovementMode.NONE }
    var mode by remember { mutableStateOf(MovementMode.CAR) }
    var phase by remember { mutableStateOf("loop") }
    var period by remember { mutableStateOf(DayPeriod.DAY) }
    var direction by remember { mutableStateOf(Direction.FRONT) }
    var energy by remember { mutableIntStateOf(70) }
    var mood by remember { mutableIntStateOf(70) }
    var speed by remember { mutableFloatStateOf(1f) }
    val profile = com.hoodie.app.pixel.transport.TransportVisualRegistry.profileFor(mode, com.hoodie.app.core.model.CommuteStyle.WALK)
    val visual = remember(mode, phase, energy, mood, direction) {
        val base = VisualDirector.resolve(HoodieActivity.COMMUTING, UserContextType.COMMUTING, mobilityMode = mode, energy = energy, mood = mood)
        val clip = when (phase) {
            "enter" -> profile.animationSet.enter.firstOrNull()
            "exit" -> profile.animationSet.exit.firstOrNull()
            else -> profile.animationSet.primary.firstOrNull()
        }
        if (clip == null) base else base.copy(actions = listOf(MicroAction(clip, 1, 60_000, 60_000, direction = if (clip.clip.directional) direction else Direction.FRONT)))
    }
    SectionLabel("TRANSPORTES · ${profile.scene.label.uppercase()}")
    Text("Perfil visual único · ${profile.journey.vehicle} · rota ${profile.journey.routeStyle}", color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall)
    Box(Modifier.fillMaxWidth().aspectRatio(240f / 320f)) {
        HoodieSceneView(visual, Modifier.fillMaxSize(), greet = false, speed = speed, periodOverride = period)
    }
    SectionLabel("Meio de transporte")
    ChipRow(modes.map { "${it.emoji} ${it.label}" }, modes.indexOf(mode), { mode = modes[it] })
    SectionLabel("Fase do clip")
    ChipRow(listOf("PLAY ENTER", "PLAY LOOP", "PLAY EXIT"), listOf("enter", "loop", "exit").indexOf(phase), { phase = listOf("enter", "loop", "exit")[it] })
    Text("Clip: ${when (phase) { "enter" -> profile.animationSet.enter.firstOrNull(); "exit" -> profile.animationSet.exit.firstOrNull(); else -> profile.animationSet.primary.firstOrNull() }?.name ?: "sem clip"} · cenas ${profile.scene.label}", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Gold)
    SectionLabel("Horário")
    ChipRow(DayPeriod.entries.map { it.label }, period.ordinal, { period = DayPeriod.entries[it] })
    SectionLabel("Direção")
    ChipRow(Direction.entries.map { it.name }, direction.ordinal, { direction = Direction.entries[it] })
    val levels = listOf(10, 40, 70, 95)
    SectionLabel("Energia")
    ChipRow(levels.map { "$it" }, levels.indexOf(energy), { energy = levels[it] })
    SectionLabel("Humor")
    ChipRow(levels.map { "$it" }, levels.indexOf(mood), { mood = levels[it] })
    SectionLabel("Velocidade")
    val speeds = listOf(.25f, .5f, 1f, 2f)
    ChipRow(speeds.map { "${it}x" }, speeds.indexOf(speed), { speed = speeds[it] })
}

@Composable
private fun AnimationGallery() {
    var playing by remember { mutableStateOf<AnimationId?>(null) }
    playing?.let { a ->
        PixelPanel(Modifier.fillMaxWidth()) {
            Text("${a.label} · ${a.frames.size} frames · ${a.durationMs} ms · ${if (a.loop) "loop" else "uma vez"}", style = MaterialTheme.typography.labelLarge)
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                AnimatedHoodie(a, size = 200.dp, direction = if (a.clip.directional) Direction.LEFT else Direction.FRONT)
            }
        }
    }
    AnimGroup.entries.forEach { g ->
        SectionLabel(g.name)
        AnimationId.entries.filter { it.group == g }.forEach { a ->
            PixelPanel(Modifier.fillMaxWidth(), onClick = { playing = a }) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(a.label, modifier = Modifier.weight(1f))
                    Text("${a.frames.size}f ▶", color = HoodieColors.Gold)
                }
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

/** De onde vem cada animação (sprite sheet do Aseprite ou procedural) e problemas de importação. */
@Composable
private fun SpriteSources() {
    val report = AndroidSpriteSheets.lastReport
    val available = AndroidSpriteSheets.available
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("SPRITE COVERAGE")
        val total = RequiredShippedAnimations.coverage(available)
        val status = AndroidSpriteSheets.artStatus
        Text("Arte final: ${total.percent}% (${total.finalCount}/${total.total} clips)", style = MaterialTheme.typography.titleMedium, color = HoodieColors.Gold)
        AnimGroup.entries.forEach { g ->
            val c = RequiredShippedAnimations.coverage(available, g)
            val reviewed = com.hoodie.app.pixel.sprite.ArtReviewStatus.reviewedPercent(status, g)
            Column(Modifier.padding(top = 6.dp)) {
                Text(g.name, style = MaterialTheme.typography.labelLarge)
                Row { Text("Final asset", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text("${c.percent}% (${c.finalCount}/${c.total})", style = MaterialTheme.typography.bodySmall, color = if (c.percent == 100) HoodieColors.Gold else HoodieColors.Muted) }
                Row { Text("Manual reviewed", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall); Text("$reviewed%", style = MaterialTheme.typography.bodySmall, color = if (reviewed == 100) HoodieColors.Gold else HoodieColors.Muted) }
            }
        }
        val pending = com.hoodie.app.pixel.sprite.ArtReviewStatus.pendingReview(status)
        if (pending.isNotEmpty()) Text("Revisão manual pendente (bloqueia a release): ${pending.joinToString()}", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Coral)
        val missing = RequiredShippedAnimations.missing(available)
        Text(
            if (missing.isEmpty()) "Obrigatórios da release: completos" else "Faltam na release: " + missing.joinToString { "${it.first.name}/${it.second.name}" },
            style = MaterialTheme.typography.labelSmall, color = if (missing.isEmpty()) HoodieColors.Muted else HoodieColors.Coral,
        )
    }
    PixelPanel(Modifier.fillMaxWidth()) {
        SectionLabel("Provider ativo")
        Text(HoodieSprites.provider.name)
        SectionLabel("Sprite sheets carregados (assets/pixel/hoodie)")
        Text(if (report.loaded.isEmpty()) "Nenhum — tudo procedural." else report.loaded.joinToString())
        if (report.errors.isNotEmpty()) {
            SectionLabel("Erros · clip rejeitado, usa o procedural")
            report.errors.forEach { Text("• ${it.message}", color = HoodieColors.Coral, style = MaterialTheme.typography.bodySmall) }
        }
        if (report.warnings.isNotEmpty()) {
            SectionLabel("Avisos")
            report.warnings.forEach { Text("• ${it.message}", color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall) }
        }
    }
    Text("Para substituir uma animação: exporte do Aseprite (ver assets-source/hoodie/README.md) e coloque o .png + .json em app/src/main/assets/pixel/hoodie/.", color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall)
}
