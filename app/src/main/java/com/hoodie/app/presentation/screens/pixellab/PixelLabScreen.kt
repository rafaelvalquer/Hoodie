package com.hoodie.app.presentation.screens.pixellab

import com.hoodie.app.R
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
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
import androidx.core.graphics.createBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.pixel.animation.AnimGroup
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.ClipTiming
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterComparisonRenderer
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.character.outfit.OutfitStyle
import com.hoodie.app.pixel.character.mirrored
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
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
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Expression
import com.hoodie.app.pixel.sprite.HoodieSprites
import com.hoodie.app.pixel.sprite.Mouth
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
        Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState()).padding(16.dp).testTag("pixel-lab-scroll"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("PIXEL LAB", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            Text("✕", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button, onClick = onBack).semantics { contentDescription = closeLabel }.padding(8.dp))
        }
        ChipRow(listOf("Animação", "NPC", "Cena", "Galeria", "Cenas", "Sprites", "Transportes"), tab, { tab = it })
        when (tab) {
            0 -> AnimationTool()
            1 -> NpcLab()
            2 -> SceneLab()
            3 -> AnimationGallery()
            4 -> SceneGallery()
            5 -> SpriteSources()
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
        createBitmap(buf.width, buf.height, android.graphics.Bitmap.Config.ARGB_8888).also { it.setPixels(buf.pixels, 0, buf.width, 0, 0, buf.width, buf.height) }.asImageBitmap()
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

@Composable
private fun NpcLab() {
    var styleIndex by remember { mutableIntStateOf(0) }
    var animation by remember { mutableStateOf(NpcAnimation.IDLE) }
    var facing by remember { mutableIntStateOf(0) }
    var posture by remember { mutableIntStateOf(0) }
    // -1 = roupa registrada do personagem (o Bulldog abre no terno que define sua identidade).
    var outfitIndex by remember { mutableIntStateOf(-1) }
    var paletteIndex by remember { mutableIntStateOf(0) }
    var eyesIndex by remember { mutableIntStateOf(-1) }
    var mouthIndex by remember { mutableIntStateOf(-1) }
    var elapsed by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(true) }
    var speed by remember { mutableFloatStateOf(1f) }
    var overlays by remember { mutableStateOf(true) }
    var onionPrev by remember { mutableStateOf(false) }
    var onionNext by remember { mutableStateOf(false) }
    var scaleIndex by remember { mutableIntStateOf(4) }
    var environment by remember { mutableIntStateOf(0) }
    val styles = NpcCharacterRegistry.all
    val registeredStyle = styles[styleIndex.coerceIn(styles.indices)]
    val outfits = listOf(OutfitStyle.Hoodie, OutfitStyle.Suit, OutfitStyle.Casual, OutfitStyle.Student, OutfitStyle.Sport, OutfitStyle.Commuter)
    val outfitLabels = listOf("Registrada", "Hoodie", "Terno", "Casual", "Estudante", "Esporte", "Comutante")
    val style = remember(registeredStyle, outfitIndex, paletteIndex) {
        val base = if (outfitIndex < 0) registeredStyle else registeredStyle.copy(outfit = outfits[outfitIndex.mod(outfits.size)])
        val palette = when (paletteIndex) {
            1 -> base.palette.copy(furLight = 0xFFFFF5DC.toInt(), fur = 0xFFD7B98D.toInt(), furDark = 0xFF8D6B4F.toInt(), outfitLight = 0xFFDDD2C2.toInt(), shirt = 0xFFFFF5DC.toInt())
            2 -> base.palette.copy(furLight = 0xFFB6D8FF.toInt(), fur = 0xFF6C91C2.toInt(), furDark = 0xFF354B73.toInt(), outfitLight = 0xFF8298BB.toInt(), outfit = 0xFF303B58.toInt(), outfitDark = 0xFF1A2239.toInt(), shirt = 0xFFDAE5F2.toInt())
            else -> base.palette
        }
        base.copy(palette = palette)
    }
    val motionProfile = remember(style) { SpeciesMotionProfiles.forCharacter(style) }
    LaunchedEffect(playing, speed) {
        var last = -1L
        while (playing) withFrameMillis { frameTime ->
            if (last >= 0L) elapsed += ((frameTime - last) * speed).toLong()
            last = frameTime
        }
    }
    val directions = listOf(com.hoodie.app.pixel.sprite.Facing.FRONT, com.hoodie.app.pixel.sprite.Facing.SIDE, com.hoodie.app.pixel.sprite.Facing.BACK)
    val scales = com.hoodie.app.pixel.npc.AmbientScale.allowed
    val scale = scales[scaleIndex.coerceIn(scales.indices)]
    /** Quadro calculado igual à cena (pose + passada + movimento secundário). */
    fun frameAt(t: Long): com.hoodie.app.pixel.npc.NpcFrame {
        val f = com.hoodie.app.pixel.npc.NpcPoseLibrary.frame(animation, t, style.id.hashCode(), motionProfile, scale = scale)
        val pose = f.pose.let { base ->
            val auto = animation == NpcAnimation.TURN_LEFT || animation == NpcAnimation.TURN_RIGHT
            base.copy(
                facing = if (auto) base.facing else directions[facing],
                eyes = if (eyesIndex >= 0) Eyes.entries[eyesIndex] else base.eyes,
                mouth = if (mouthIndex >= 0) Mouth.entries[mouthIndex] else base.mouth,
            )
        }.let { base -> when (posture) { 1 -> base.withPosture(Posture.STANDING); 2 -> base.withPosture(Posture.SITTING); else -> base } }
        return f.copy(pose = pose)
    }
    fun scaled(frame: com.hoodie.app.pixel.character.CharacterFrame) =
        com.hoodie.app.pixel.npc.NpcRenderer.scaleFrameForAmbient(frame, scale)
    val current = frameAt(elapsed)
    val npc = scaled(CharacterComparisonRenderer.character(style, current.pose, facingRight = facing == 1, motion = current.motion))
    val hoodie = scaled(CharacterComparisonRenderer.hoodie(current.pose, facingRight = facing == 1))
    val onion = buildList {
        if (onionPrev) add(scaled(CharacterComparisonRenderer.character(style, frameAt(elapsed - FRAME_STEP_MS).pose, facing == 1, frameAt(elapsed - FRAME_STEP_MS).motion)).image.pixels to ONION_PREV)
        if (onionNext) add(scaled(CharacterComparisonRenderer.character(style, frameAt(elapsed + FRAME_STEP_MS).pose, facing == 1, frameAt(elapsed + FRAME_STEP_MS).motion)).image.pixels to ONION_NEXT)
    }
    // Overlays de esqueleto: caixas de cabeça e corpo + trajetória dos pés na passada.
    val layout = com.hoodie.app.pixel.character.BodyLayout.resolve(style, current.pose)
    val mirror = current.pose.facing == com.hoodie.app.pixel.sprite.Facing.SIDE && facing != 1
    fun mx(x: Int) = if (mirror) CharacterPainter.WIDTH - 1 - x else x
    val skeleton = LabSkeleton(
        head = intArrayOf(mx(layout.headLeft), layout.headTop, mx(layout.headRight), layout.headBottom),
        body = intArrayOf(mx(layout.shoulderLeft), layout.shoulderY, mx(layout.shoulderRight), layout.torsoBottom),
        motionPath = if (animation == NpcAnimation.WALK) (0 until 24).map { i ->
            val g = com.hoodie.app.pixel.npc.NpcGait.sample(i * motionProfile.stepLength * scale / 6f, motionProfile, scale).gait
            com.hoodie.app.pixel.sprite.Point(mx(CharacterPainter.WIDTH / 2 + g.nearX), CharacterPainter.HEIGHT - 1 - g.nearLift)
        } else emptyList(),
    )
    val backgrounds = listOf(0xFF2B2E4A.toInt(), 0xFFE8DCC4.toInt(), 0xFF5E7FA8.toInt(), 0xFF7CC46B.toInt())
    val bg = backgrounds[environment.coerceIn(backgrounds.indices)]
    SectionLabel("[ NPC ART ]")
    SectionLabel("IDENTIDADE")
    ChipRow(styles.map { it.id.replace('_', ' ') }, styleIndex, { styleIndex = it })
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
        CharacterPreview("HOODIE", hoodie.image.pixels, CharacterPainter.WIDTH, CharacterPainter.HEIGHT, hoodie.anchors, overlays, Modifier.weight(1f), background = bg)
        CharacterPreview(
            style.id.uppercase(), npc.image.pixels, CharacterPainter.WIDTH, CharacterPainter.HEIGHT, npc.anchors, overlays, Modifier.weight(1f),
            background = bg, onion = onion, skeleton = skeleton.takeIf { scale >= 0.999f },
        )
    }
    Text(
        "${animation.name} · ${style.species.id} · ${current.contact} · frame ${elapsed / FRAME_STEP_MS} · ${elapsed} ms · escala ${scale}",
        modifier = Modifier.testTag("npc-inspector-status"),
        color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall,
    )
    SectionLabel("INSPECTOR")
    ChipRow(listOf(if (playing) "⏸ PAUSE" else "▶ PLAY", "◀ FRAME −", "FRAME + ▶", "⟲ 0"), null, {
        when (it) {
            0 -> playing = !playing
            1 -> { playing = false; elapsed = (elapsed - FRAME_STEP_MS).coerceAtLeast(0) }
            2 -> { playing = false; elapsed += FRAME_STEP_MS }
            else -> elapsed = 0L
        }
    })
    val speeds = listOf(0.25f, 0.5f, 1f, 2f)
    ChipRow(speeds.map { "${it}x" }, speeds.indexOf(speed), { speed = speeds[it] })
    ChipRow(
        listOf(if (onionPrev) "☑ Previous frame" else "☐ Previous frame", if (onionNext) "☑ Next frame" else "☐ Next frame", if (overlays) "☑ Overlays" else "☐ Overlays"),
        null, { when (it) { 0 -> onionPrev = !onionPrev; 1 -> onionNext = !onionNext; else -> overlays = !overlays } },
    )
    SectionLabel("ANIMAÇÃO")
    ChipRow(NpcAnimation.entries.map { it.name }, animation.ordinal, { animation = NpcAnimation.entries[it]; elapsed = 0L })
    SectionLabel("FACING")
    ChipRow(listOf("FRONT", "SIDE", "BACK"), facing, { facing = it })
    SectionLabel("ESCALA")
    ChipRow(scales.map { "$it" }, scaleIndex, { scaleIndex = it })
    SectionLabel("AMBIENTE")
    ChipRow(listOf("Escuro", "Escritório", "Transporte", "Parque"), environment, { environment = it })
    SectionLabel("ROUPA")
    ChipRow(outfitLabels, outfitIndex + 1, { outfitIndex = it - 1 })
    SectionLabel("PALETA")
    ChipRow(listOf("Original", "Quente", "Noturna"), paletteIndex, { paletteIndex = it })
    SectionLabel("POSE · POSTURA")
    ChipRow(listOf("Automática", "Em pé", "Sentado"), posture, { posture = it })
    SectionLabel("EXPRESSÃO · OLHOS")
    ChipRow(listOf("Auto") + Eyes.entries.map { it.name }, eyesIndex + 1, { eyesIndex = it - 1 })
    SectionLabel("EXPRESSÃO · BOCA")
    ChipRow(listOf("Auto") + Mouth.entries.map { it.name }, mouthIndex + 1, { mouthIndex = it - 1 })
    Text("Canvas 48×72 · ${style.artProfile.proportions} · roupa ${style.outfit}", color = HoodieColors.Muted, style = MaterialTheme.typography.bodySmall)
}

private const val FRAME_STEP_MS = 100L
private const val ONION_PREV = 0xFFE86A6A.toInt()
private const val ONION_NEXT = 0xFF6AD0E8.toInt()

/** Caixas de cabeça/corpo e trajetória dos pés para o overlay do Pixel Lab. */
private class LabSkeleton(val head: IntArray, val body: IntArray, val motionPath: List<com.hoodie.app.pixel.sprite.Point>)

@Composable
private fun CharacterPreview(
    title: String, pixels: IntArray, width: Int, height: Int,
    anchors: com.hoodie.app.pixel.character.CharacterAnchors, overlays: Boolean, modifier: Modifier = Modifier,
    background: Int = 0, onion: List<Pair<IntArray, Int>> = emptyList(), skeleton: LabSkeleton? = null,
) {
    val image = remember(pixels, overlays, anchors, background, onion, skeleton) {
        val buffer = PixelBuffer(width, height).also { it.fill(background) }
        // Onion skin: silhuetas vizinhas tingidas e translúcidas por baixo do quadro atual.
        onion.forEach { (ghost, tint) ->
            ghost.indices.forEach { i -> if (ghost[i] ushr 24 != 0) buffer.pixels[i] = PixelBuffer.mix(buffer.pixels[i], tint, 0.45f) }
        }
        pixels.indices.forEach { i -> if (pixels[i] ushr 24 != 0) buffer.pixels[i] = pixels[i] }
        if (overlays) {
            val occupied = pixels.indices.filter { pixels[it] ushr 24 != 0 }
            if (occupied.isNotEmpty()) {
                val minX = occupied.minOf { it % width }; val maxX = occupied.maxOf { it % width }
                val minY = occupied.minOf { it / width }; val maxY = occupied.maxOf { it / width }
                val bounds = 0xFFFF66C4.toInt(); val feet = 0xFF62D3CF.toInt(); val head = 0xFFE8B84A.toInt()
                buffer.hline(minX, maxX, minY, bounds); buffer.hline(minX, maxX, maxY, bounds)
                buffer.vline(minX, minY, maxY, bounds); buffer.vline(maxX, minY, maxY, bounds)
                buffer.hline(0, width - 1, anchors.feet.y, feet)
                listOf(anchors.leftHand, anchors.rightHand).forEach { buffer.box(it.x - 1, it.y - 1, it.x + 1, it.y + 1, 0xFF62D3CF.toInt()) }
                buffer.box(anchors.head.x - 1, anchors.head.y - 1, anchors.head.x + 1, anchors.head.y + 1, head)
                buffer.box(anchors.mouth.x, anchors.mouth.y, anchors.mouth.x, anchors.mouth.y, 0xFFFF8B72.toInt())
            }
            skeleton?.let { sk ->
                fun rect(r: IntArray, c: Int) {
                    val x0 = minOf(r[0], r[2]); val x1 = maxOf(r[0], r[2])
                    buffer.hline(x0, x1, r[1], c); buffer.hline(x0, x1, r[3], c); buffer.vline(x0, r[1], r[3], c); buffer.vline(x1, r[1], r[3], c)
                }
                rect(sk.head, 0xFFE8B84A.toInt())
                rect(sk.body, 0xFF8BE88B.toInt())
                sk.motionPath.forEach { buffer.set(it.x, it.y, 0xFFFFFFFF.toInt()) }
            }
        }
        createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888).also { it.setPixels(buffer.pixels, 0, width, 0, 0, width, height) }.asImageBitmap()
    }
    PixelPanel(modifier.testTag("npc-preview-$title")) {
        Text(title, style = MaterialTheme.typography.labelSmall, color = HoodieColors.Gold)
        Box(Modifier.fillMaxWidth().aspectRatio(width.toFloat() / height), contentAlignment = Alignment.Center) {
            PixelImage(image, width, height, Modifier.fillMaxSize())
        }
        if (overlays) {
            Text("feet ${anchors.feet} · head ${anchors.head}", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
            Text("hands ${anchors.leftHand} / ${anchors.rightHand}", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
            Text("mouth ${anchors.mouth}", style = MaterialTheme.typography.labelSmall, color = HoodieColors.Muted)
        }
    }
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
