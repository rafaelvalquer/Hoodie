package com.hoodie.app.pixel.scene

import com.hoodie.app.core.mobility.MovementMode
import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.model.PlaceType
import com.hoodie.app.core.mobility.MobilityVisualSnapshot
import com.hoodie.app.pixel.transport.TransportVisualResolution
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationId.*
import com.hoodie.app.pixel.renderer.EffectKind
import com.hoodie.app.pixel.sprite.Anchor
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Expression
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.transport.TransportVisualProfile
import com.hoodie.app.pixel.transport.TransportVisualRegistry
import com.hoodie.app.pixel.transport.TransportLighting
import com.hoodie.app.pixel.diary.journey.JourneyVehicle

/**
 * Efeito visual. Com [anchor] ele segue uma âncora do frame (Zzz na cabeça,
 * vapor na mão com a caneca); sem âncora fica relativo aos pés/spot.
 * [requires] liga o efeito só quando um prop está presente (vapor da comida servida).
 */
data class EffectSpec(
    val kind: EffectKind,
    val dx: Int,
    val dy: Int,
    val anchor: Anchor? = null,
    val requires: SceneFlag? = null,
)

/**
 * Microação visual: o estado real dura horas, mas na tela o gato alterna pequenas
 * ações. Cada microação pode ter transições próprias: [enter] antes do loop
 * (pegar a caneca) e [exit] depois (pôr a caneca na mesa).
 */
data class MicroAction(
    val anim: AnimationId,
    val weight: Int,
    val minMs: Long = 4_000,
    val maxMs: Long = 9_000,
    val spot: SpotId? = null,
    val effects: List<EffectSpec> = emptyList(),
    val enter: List<AnimationId> = emptyList(),
    val exit: List<AnimationId> = emptyList(),
    /** Direção da animação no loop (caminhar de lado na rua). */
    val direction: Direction = Direction.FRONT,
    /** Toca uma vez e volta a sortear (eventos como GAME_WIN). */
    val once: Boolean = false,
)

/** Um passo do roteiro do olhar: monitor → teclado → monitor → mouse… */
data class GazeStep(val eyes: Eyes, val minMs: Long, val maxMs: Long)

/**
 * Estado visual = cena + âncora + loop de microações, com a sequência
 * ENTER (sentar, deitar…) → LOOP → EXIT (levantar, acordar…).
 */
data class VisualState(
    val scene: SceneId,
    val spot: SpotId,
    val actions: List<MicroAction>,
    val effects: List<EffectSpec> = emptyList(),
    val tvOn: Boolean = false,
    val screenOn: Boolean = true,
    val variant: Int = 0,
    val expression: Expression? = null,
    val backpackWalk: Boolean = false,
    /** Antecipação antes de ir até o spot (olhar a cama, bocejar). */
    val approach: List<AnimationId> = emptyList(),
    val enter: List<AnimationId> = emptyList(),
    val exit: List<AnimationId> = emptyList(),
    val gaze: List<GazeStep> = emptyList(),
    /** Props já "no estado" quando a cena abre direto no loop (sem tocar o enter). */
    val steadyFlags: Set<SceneFlag> = emptySet(),
    val transportAmbient: com.hoodie.app.pixel.transport.TransportAmbientProfile? = null,
    val shoppingVenue: PlaceType? = null,
    val manualTransition: Boolean = false,
    val mobilityVisual: TransportVisualResolution? = null,
)

/** HoodieActivity + contexto do usuário → cena, âncora, sequências e microações. */
object VisualDirector {

    /**
     * Cena do deslocamento. Prioridade: sessão real de mobilidade → preferência
     * [CommuteStyle] → variação. Assim a preferência continua valendo com a detecção desligada.
     */
    fun commuteScene(mobilityMode: MovementMode?, commute: CommuteStyle, variant: Int): SceneId =
        TransportVisualRegistry.profileFor(mobilityMode, commute, variant).scene

    fun sceneFor(activity: HoodieActivity, context: UserContextType, homeOffice: Boolean, commute: CommuteStyle, variant: Int, mobilityMode: MovementMode? = null): SceneId =
        when (context) {
            UserContextType.HOME -> if (activity == HoodieActivity.WALKING) SceneId.GENERIC_OUTDOOR else SceneId.HOME
            UserContextType.WORK -> if (homeOffice) SceneId.HOME else SceneId.OFFICE
            UserContextType.COMMUTING -> commuteScene(mobilityMode, commute, variant)
            UserContextType.LUNCH -> SceneId.RESTAURANT
            UserContextType.DINING -> SceneId.RESTAURANT
            UserContextType.GYM -> SceneId.GYM
            UserContextType.STUDY -> SceneId.SCHOOL
            UserContextType.SHOPPING -> SceneId.SHOPPING
            UserContextType.VISITING -> SceneId.FAMILY
            UserContextType.LEISURE -> SceneId.LEISURE
            // Viagem ainda não tem cena própria: o exterior genérico é o fallback.
            UserContextType.TRAVEL -> SceneId.GENERIC_OUTDOOR
            UserContextType.UNKNOWN -> SceneId.UNKNOWN
        }

    fun resolve(
        activity: HoodieActivity,
        context: UserContextType,
        homeOffice: Boolean = false,
        commute: CommuteStyle = CommuteStyle.RANDOM,
        mobilityMode: MovementMode? = null,
        variant: Int = 0,
        energy: Int = 70,
        mood: Int = 70,
        social: Int = 50,
        hunger: Int = 30,
        focus: Int = 60,
        shoppingVenue: PlaceType? = null,
        manualTransition: Boolean = false,
        mobilitySnapshot: MobilityVisualSnapshot? = null,
        preferredMode: MovementMode? = null,
        now: Long = System.currentTimeMillis(),
    ): VisualState {
        val mobilityVisual = if (context == UserContextType.COMMUTING) mobilitySnapshot?.let {
            TransportVisualRegistry.resolve(it, preferredMode, now, variant)
        } else null
        val scene = mobilityVisual?.profile?.scene ?: sceneFor(activity, context, homeOffice, commute, variant, mobilityMode)
        val tired = if (energy < 20) Expression.TIRED else null
        val currentMood = Mood(energy, mood, social, hunger, focus)
        val base = if (context == UserContextType.COMMUTING) {
            val profile = mobilityVisual?.profile ?: TransportVisualRegistry.profileFor(mobilityMode, commute, variant)
            transportState(profile, currentMood).let { state ->
                if (mobilityVisual?.profile?.scene == SceneId.GENERIC_OUTDOOR) state.copy(spot = SceneRegistry[SceneId.GENERIC_OUTDOOR].defaultSpot)
                else state
            }
        } else forScene(scene, activity, currentMood)
        return base.copy(
            variant = variant,
            expression = base.expression ?: tired,
            shoppingVenue = shoppingVenue,
            manualTransition = manualTransition,
            mobilityVisual = mobilityVisual,
        )
    }

    private fun transportState(profile: TransportVisualProfile, mood: Mood): VisualState {
        val actions = profile.animationSet.primary.mapIndexed { index, animation ->
            val baseWeight = when (profile.scene) {
                SceneId.CAR -> listOf(44, 24, 22, 10).getOrElse(index) { 10 }
                SceneId.BUS -> listOf(40, 25, 15, 10).getOrElse(index) { 10 }
                SceneId.TRANSIT -> listOf(55, 35, 10).getOrElse(index) { 10 }
                SceneId.TRAIN -> listOf(40, 25, 15, 12).getOrElse(index) { 10 }
                SceneId.METRO -> listOf(36, 28, 18, 12).getOrElse(index) { 10 }
                else -> listOf(55, 30, 15).getOrElse(index) { 10 }
            }
            val seatedAnimation = if (profile.scene == SceneId.BUS && animation == BUS_SIT)
                com.hoodie.app.pixel.animation.AnimationId.BUS_SIT_FRONT else animation
            MicroAction(seatedAnimation, baseWeight, 4_000, 9_000, once = animation in setOf(CAR_BUMP, BUS_BUMP, TRAIN_BRAKE, METRO_BRAKE, BIKE_LOOK, OTHER_RIDE_LOOK, TRANSIT_BUMP))
        }.toMutableList()
        if (mood.tired && profile.journey.vehicle in setOf(JourneyVehicle.BUS, JourneyVehicle.TRAIN, JourneyVehicle.METRO, JourneyVehicle.GENERIC_TRANSIT)) {
            actions += MicroAction(NAP_SIT, 8, 4_000, 8_000)
        }
        val streetWalk = profile.scene == SceneId.STREET && profile.mode == null
        val exteriorRide = profile.scene in setOf(SceneId.STREET, SceneId.BICYCLE, SceneId.GENERIC_RIDE)
        val spot = if (exteriorRide) SpotId.WALK else SpotId.SEAT
        if (streetWalk) actions.clear().also { actions += MicroAction(WALK_BACKPACK, 100, direction = Direction.RIGHT) }
        return VisualState(
            scene = profile.scene,
            spot = spot,
            actions = actions,
            variant = 0,
            enter = profile.animationSet.enter,
            exit = profile.animationSet.exit,
            gaze = if (profile.ambient.lighting != TransportLighting.OPEN_AIR) windowGaze else emptyList(),
            backpackWalk = exteriorRide,
            transportAmbient = profile.ambient,
        )
    }

    /** Animação de deslocamento entre spots: mochila no trajeto, passo lento no passeio. */
    fun locomotion(scene: SceneId?, backpack: Boolean): AnimationId = when {
        backpack || scene == SceneId.STREET -> WALK_BACKPACK
        scene == SceneId.LEISURE -> LEISURE_WALK
        else -> WALK
    }

    /** Personalidade nas escolhas visuais: a mesma atividade muda conforme as necessidades. */
    private data class Mood(val energy: Int, val mood: Int, val social: Int = 50, val hunger: Int = 30, val focus: Int = 60) {
        val coffee: AnimationId get() = when { energy < 20 -> COFFEE_TIRED; mood >= 85 -> COFFEE_HAPPY; else -> DRINK }
        val tired: Boolean get() = energy < 25
        val hungry: Boolean get() = hunger > 65
        val distracted: Boolean get() = focus < 30
        val lonely: Boolean get() = social < 35
        val cheerful: Boolean get() = mood >= 70
    }

    // ───── Peças reutilizáveis ─────

    private val zzz = listOf(EffectSpec(EffectKind.SLEEP_Z, 10, -4, Anchor.HEAD))
    private val sweat = listOf(EffectSpec(EffectKind.SWEAT, 14, 6, Anchor.HEAD))
    private val mugSteam = listOf(EffectSpec(EffectKind.STEAM, 2, -8, Anchor.RIGHT_HAND))
    private val sitDown = listOf(SIT_DOWN)
    private val standUp = listOf(STAND_UP)

    private fun one(anim: AnimationId, direction: Direction = Direction.FRONT) = listOf(MicroAction(anim, 1, 8_000, 15_000, direction = direction))

    private fun coffee(m: Mood, weight: Int) =
        MicroAction(m.coffee, weight, 4_000, 7_000, effects = mugSteam, enter = listOf(REACH_MUG), exit = listOf(PUT_MUG))

    private fun phone(weight: Int) = listOf(
        MicroAction(PHONE_READ, weight * 4, 3_000, 6_000, enter = listOf(PHONE_TAKE), exit = listOf(PHONE_PUT)),
        MicroAction(PHONE_SCROLL, weight * 3, 3_000, 5_000, enter = listOf(PHONE_TAKE), exit = listOf(PHONE_PUT)),
        MicroAction(PHONE_TYPE, weight * 2, 2_000, 4_000, enter = listOf(PHONE_TAKE), exit = listOf(PHONE_PUT)),
        MicroAction(PHONE_REACT_SMILE, weight, enter = listOf(PHONE_TAKE), exit = listOf(PHONE_PUT), once = true),
        MicroAction(PHONE_REACT_WOW, weight, enter = listOf(PHONE_TAKE), exit = listOf(PHONE_PUT), once = true),
    )

    /** Idle com variações (respiração, olhar, orelha, coçar, celular). */
    private fun idleActions(wander: SpotId?) = buildList {
        add(MicroAction(IDLE, 45, 4_000, 9_000))
        add(MicroAction(IDLE_LOOK, 15, once = true))
        add(MicroAction(IDLE_EAR, 10, once = true))
        add(MicroAction(IDLE_SCRATCH, 10, once = true))
        add(MicroAction(IDLE_PHONE, 10, 3_000, 6_000, enter = listOf(PHONE_TAKE), exit = listOf(PHONE_PUT)))
        if (wander != null) add(MicroAction(IDLE, 10, spot = wander))
    }

    /** Sistema de trabalho procedural (pesos da especificação) com transições entre microações. */
    private fun workActions(m: Mood) = listOf(
        MicroAction(if (m.tired) WORK_TIRED else WORK_TYPING, 45, 8_000, 20_000, exit = listOf(STOP_TYPING)),
        MicroAction(WORK_MOUSE, 15, 3_000, 8_000, enter = listOf(REACH_MOUSE)),
        MicroAction(WORK_READ, 15, 4_000, 12_000),
        coffee(m, 8),
        MicroAction(THINK, 7, 3_000, 5_000),
        MicroAction(PHONE_READ, 5, 3_000, 5_000, enter = listOf(PHONE_TAKE), exit = listOf(PHONE_PUT)),
        MicroAction(STRETCH_SIT, 5, once = true),
        MicroAction(WORK_NOTES, 4, 3_000, 6_000),
    )

    /** Monitor → teclado → monitor → mouse → monitor. */
    private val workGaze = listOf(
        GazeStep(Eyes.LOOK_RIGHT, 2_000, 4_000), GazeStep(Eyes.LOOK_DOWN, 600, 1_200),
        GazeStep(Eyes.LOOK_RIGHT, 1_500, 3_000), GazeStep(Eyes.OPEN, 500, 1_000),
    )
    /** Comida → celular → comida. */
    private val foodGaze = listOf(GazeStep(Eyes.LOOK_DOWN, 2_000, 3_500), GazeStep(Eyes.OPEN, 800, 1_500), GazeStep(Eyes.LOOK_LEFT, 600, 1_000))
    /** Lugar desconhecido: esquerda, direita, cima, celular. */
    private val unknownGaze = listOf(
        GazeStep(Eyes.LOOK_LEFT, 800, 1_500), GazeStep(Eyes.LOOK_RIGHT, 800, 1_500), GazeStep(Eyes.LOOK_UP, 600, 1_200), GazeStep(Eyes.LOOK_DOWN, 800, 1_400),
    )
    private val tvGaze = listOf(GazeStep(Eyes.LOOK_RIGHT, 4_000, 8_000), GazeStep(Eyes.OPEN, 600, 1_200))
    /** Livro → caderno → quadro → livro. */
    private val studyGaze = listOf(
        GazeStep(Eyes.LOOK_DOWN, 2_500, 5_000), GazeStep(Eyes.LOOK_UP, 600, 1_200),
        GazeStep(Eyes.LOOK_DOWN, 1_500, 3_000), GazeStep(Eyes.OPEN, 500, 1_000),
    )
    /** Prateleira de cima → de baixo → carrinho. */
    private val shelfGaze = listOf(GazeStep(Eyes.LOOK_UP, 1_200, 2_500), GazeStep(Eyes.OPEN, 800, 1_500), GazeStep(Eyes.LOOK_DOWN, 600, 1_200))
    /** A outra pessoa está à esquerda; às vezes o prato. */
    private val visitGaze = listOf(GazeStep(Eyes.LOOK_LEFT, 3_000, 6_000), GazeStep(Eyes.OPEN, 600, 1_200), GazeStep(Eyes.LOOK_DOWN, 500, 900))
    /** Paisagem: horizonte, céu, caminho. */
    private val leisureGaze = listOf(GazeStep(Eyes.LOOK_RIGHT, 2_000, 4_000), GazeStep(Eyes.LOOK_UP, 1_000, 2_000), GazeStep(Eyes.OPEN, 800, 1_500))

    // ───── Escola, compras, família e passeio ─────

    private val chairSteady = setOf(SceneFlag.CHAIR_OCCUPIED)

    /** Estudo sentado: foco baixo distrai (celular, pensar); cansaço puxa café e cochilo. */
    private fun studyActions(m: Mood) = buildList {
        add(MicroAction(STUDY_READ, if (m.distracted) 22 else 35, 5_000, 12_000))
        add(MicroAction(STUDY_WRITE, if (m.distracted) 12 else 25, 4_000, 10_000))
        add(MicroAction(STUDY_THINK, if (m.distracted) 20 else 12, once = true))
        add(MicroAction(STUDY_PAGE_TURN, 10, once = true))
        add(coffee(m, if (m.tired) 14 else 5))
        add(MicroAction(STRETCH_SIT, 5, once = true))
        if (m.distracted) add(MicroAction(PHONE_READ, 14, 3_000, 5_000, enter = listOf(PHONE_TAKE), exit = listOf(PHONE_PUT)))
        if (m.tired) add(MicroAction(NAP_SIT, 10, 3_000, 6_000))
    }

    /** Compras em pé: prateleira A/B, pegar, carrinho e caixa. Fome puxa mais produtos; cansaço, o caixa. */
    private fun shopActions(m: Mood) = buildList {
        add(MicroAction(SHOP_LOOK, 25, 3_000, 6_000, SpotId.AISLE_A))
        add(MicroAction(SHOP_LOOK, 18, 3_000, 6_000, SpotId.AISLE_B))
        add(MicroAction(SHOP_PICK, if (m.hungry) 30 else 20, spot = SpotId.AISLE_A, once = true))
        add(MicroAction(SHOP_CART, 14, 2_500, 4_500, SpotId.CART))
        add(MicroAction(SHOP_PAY, if (m.tired) 20 else 10, spot = SpotId.CHECKOUT, once = true))
        add(MicroAction(THINK_STAND, 6, 2_000, 4_000, SpotId.AISLE_B))
        add(MicroAction(IDLE_LOOK, 5, spot = SpotId.CENTER, once = true))
    }

    /** Visita no sofá: social baixo = mais conversa; fome = petiscos; humor alto = risadas. */
    private fun visitActions(m: Mood) = buildList {
        add(MicroAction(VISIT_CHAT, if (m.lonely) 45 else 32, 4_000, 9_000))
        add(MicroAction(VISIT_LISTEN, 25, 4_000, 8_000))
        add(MicroAction(VISIT_LAUGH, if (m.cheerful) 15 else 7, once = true))
        add(MicroAction(VISIT_SNACK, if (m.hungry) 25 else 10, once = true))
        add(coffee(m, 8))
        add(MicroAction(IDLE_SIT, 7, 3_000, 6_000))
    }

    /** Passeio em pé entre caminho, mirante e banco. Cansaço senta; humor alto fotografa. */
    private fun leisureActions(m: Mood) = buildList {
        add(MicroAction(LEISURE_LOOK, 22, spot = SpotId.VIEWPOINT, once = true))
        add(MicroAction(LEISURE_PHOTO, if (m.cheerful) 20 else 12, spot = SpotId.VIEWPOINT, once = true))
        add(MicroAction(LEISURE_BENCH, if (m.tired) 35 else 14, 5_000, 10_000, SpotId.BENCH, enter = sitDown, exit = standUp))
        add(MicroAction(IDLE, 14, 2_000, 4_000, SpotId.PATH_B))
        add(MicroAction(IDLE_LOOK, 10, spot = SpotId.PATH_A, once = true))
        add(MicroAction(HAPPY, if (m.cheerful) 8 else 4, spot = SpotId.WALK, once = true))
    }

    /** Sentado no banco do passeio. */
    private fun bench(scene: SceneId, actions: List<MicroAction>, effects: List<EffectSpec> = emptyList(), expression: Expression? = null) =
        VisualState(scene, SpotId.BENCH, actions, effects, expression = expression, enter = sitDown, exit = standUp, gaze = leisureGaze)

    /** Sentado no sofá da família. */
    private fun familySofa(scene: SceneId, actions: List<MicroAction>, tvOn: Boolean = false, effects: List<EffectSpec> = emptyList(), expression: Expression? = null) =
        VisualState(
            scene, SpotId.FAMILY_SOFA, actions, effects, tvOn = tvOn, expression = expression,
            enter = sitDown, exit = standUp, gaze = if (tvOn) tvGaze else visitGaze, steadyFlags = chairSteady,
        )

    /** Sentado na carteira da escola. */
    private fun schoolDesk(scene: SceneId, actions: List<MicroAction>, effects: List<EffectSpec> = emptyList(), expression: Expression? = null) =
        VisualState(scene, SpotId.DESK, actions, effects, expression = expression, enter = sitDown, exit = standUp, steadyFlags = chairSteady)
    private val windowGaze = listOf(GazeStep(Eyes.LOOK_LEFT, 3_000, 6_000), GazeStep(Eyes.OPEN, 800, 1_500))

    private fun forScene(scene: SceneId, a: HoodieActivity, m: Mood): VisualState = when (scene) {
        SceneId.HOME -> when (a) {
            HoodieActivity.SLEEPING -> VisualState(
                scene, SpotId.BED,
                listOf(MicroAction(SLEEP, 85, 10_000, 25_000), MicroAction(SLEEP_TURN, 15, once = true)),
                zzz, screenOn = false,
                approach = listOf(YAWN), enter = listOf(BED_SIT, BED_LIE_DOWN),
                exit = listOf(WAKE_EYES, BED_SIT, YAWN, BED_EXIT),
                steadyFlags = setOf(SceneFlag.IN_BED),
            )
            HoodieActivity.WAKING_UP -> VisualState(
                scene, SpotId.CENTER,
                listOf(MicroAction(IDLE, 50), MicroAction(YAWN, 25, once = true), MicroAction(IDLE_SCRATCH, 25, once = true)),
                screenOn = false, expression = Expression.SLEEPY, enter = listOf(STRETCH),
            )
            HoodieActivity.BREAKFAST -> VisualState(
                scene, SpotId.KITCHEN,
                listOf(coffee(m, 50), MicroAction(IDLE, 20), MicroAction(COOK, 30, effects = listOf(EffectSpec(EffectKind.STEAM, -20, -36)))),
                screenOn = false,
            )
            HoodieActivity.WORKING -> VisualState(
                scene, SpotId.DESK, workActions(m), enter = sitDown, exit = standUp, gaze = workGaze,
                steadyFlags = setOf(SceneFlag.CHAIR_OCCUPIED),
            )
            HoodieActivity.EATING -> VisualState(
                scene, SpotId.DESK, listOf(MicroAction(EAT, 70), MicroAction(IDLE_SIT, 30)), screenOn = false,
                enter = sitDown, exit = standUp, gaze = foodGaze, steadyFlags = setOf(SceneFlag.CHAIR_OCCUPIED),
            )
            HoodieActivity.COFFEE -> VisualState(
                scene, SpotId.WINDOW, listOf(coffee(m, 70), MicroAction(IDLE_LOOK, 30, once = true)), screenOn = false, gaze = windowGaze,
            )
            HoodieActivity.RESTING -> VisualState(
                scene, SpotId.SOFA, listOf(MicroAction(NAP_SIT, 60), MicroAction(IDLE_SIT, 40)), screenOn = false, enter = sitDown, exit = standUp,
            )
            HoodieActivity.GAMING -> VisualState(
                scene, SpotId.SOFA,
                listOf(
                    MicroAction(GAMING, 40, 4_000, 10_000), MicroAction(GAME_PRESS, 25, 2_000, 5_000), MicroAction(GAME_FOCUSED, 20, 3_000, 6_000),
                    MicroAction(GAME_WIN, 8, once = true), MicroAction(GAME_LOSE, 7, once = true),
                ),
                listOf(EffectSpec(EffectKind.MUSIC, -34, -6, Anchor.HEAD)), tvOn = true, screenOn = false, enter = sitDown, exit = standUp,
            )
            HoodieActivity.READING -> VisualState(scene, SpotId.SOFA, one(READING), screenOn = false, enter = sitDown, exit = standUp)
            HoodieActivity.WATCHING_TV -> VisualState(
                scene, SpotId.SOFA, listOf(MicroAction(WATCH_TV, 80), MicroAction(IDLE_SIT, 20)), tvOn = true, screenOn = false,
                enter = sitDown, exit = standUp, gaze = tvGaze,
            )
            HoodieActivity.COOKING -> VisualState(scene, SpotId.KITCHEN, one(COOK), listOf(EffectSpec(EffectKind.STEAM, -20, -36)), screenOn = false)
            HoodieActivity.CLEANING -> VisualState(scene, SpotId.CENTER, one(CLEAN), listOf(EffectSpec(EffectKind.DUST, 10, -2)), screenOn = false)
            HoodieActivity.PHONE -> VisualState(scene, SpotId.SOFA, phone(1), screenOn = false, enter = sitDown, exit = standUp)
            HoodieActivity.TRAINING -> VisualState(scene, SpotId.CENTER, listOf(MicroAction(GYM_WARMUP, 50, 3_000, 6_000), MicroAction(STRETCH, 50, once = true)), sweat, screenOn = false)
            else -> VisualState(scene, SpotId.CENTER, idleActions(SpotId.WINDOW), screenOn = false)
        }
        SceneId.OFFICE -> when (a) {
            HoodieActivity.COFFEE -> VisualState(scene, SpotId.COFFEE, listOf(coffee(m, 70), MicroAction(IDLE_LOOK, 30, once = true)))
            HoodieActivity.RESTING, HoodieActivity.IDLE -> VisualState(
                scene, SpotId.WINDOW,
                idleActions(null) + listOf(MicroAction(STRETCH, 15, once = true), MicroAction(THINK_STAND, 15)), gaze = windowGaze,
            )
            HoodieActivity.PHONE -> VisualState(scene, SpotId.WINDOW, phone(1))
            HoodieActivity.EATING -> VisualState(
                scene, SpotId.DESK, listOf(MicroAction(EAT, 70), MicroAction(WORK_READ, 30)),
                enter = sitDown, exit = standUp, steadyFlags = setOf(SceneFlag.CHAIR_OCCUPIED),
            )
            else -> VisualState(
                scene, SpotId.DESK, workActions(m), enter = sitDown, exit = standUp, gaze = workGaze,
                steadyFlags = setOf(SceneFlag.CHAIR_OCCUPIED),
            )
        }
        // Na rua o mundo corre para a esquerda: o Hoodie anda de lado, para a direita.
        SceneId.STREET -> VisualState(scene, SpotId.WALK, one(WALK_BACKPACK, Direction.RIGHT), backpackWalk = true)
        SceneId.CAR -> transportState(TransportVisualRegistry.profileFor(MovementMode.CAR, CommuteStyle.WALK), m)
        SceneId.TRANSIT -> VisualState(
            scene, SpotId.SEAT, listOf(MicroAction(BUS_SIT, 75), MicroAction(IDLE_SIT, 10)) + phone(3).take(1),
            backpackWalk = true, gaze = windowGaze,
        )
        SceneId.BUS, SceneId.TRAIN, SceneId.METRO, SceneId.BICYCLE, SceneId.GENERIC_RIDE ->
            transportState(TransportVisualRegistry.profileFor(
                when (scene) {
                    SceneId.BUS -> MovementMode.BUS
                    SceneId.TRAIN -> MovementMode.TRAIN
                    SceneId.METRO -> MovementMode.METRO
                    SceneId.BICYCLE -> MovementMode.BICYCLE
                    else -> MovementMode.OTHER
                }, CommuteStyle.WALK,
            ), m)
        SceneId.RESTAURANT -> {
            val foodSteam = listOf(EffectSpec(EffectKind.STEAM, -4, -32, requires = SceneFlag.FOOD_SERVED))
            val meal = listOf(SIT_TABLE, LOOK_MENU, WAIT_FOOD)
            val leave = listOf(FINISH_FOOD, STAND_TABLE)
            when (a) {
                HoodieActivity.EATING -> VisualState(
                    scene, SpotId.TABLE,
                    listOf(MicroAction(EAT, 60), MicroAction(IDLE_SIT, 15, 2_000, 4_000), MicroAction(DRINK, 10)) + phone(3).take(1),
                    foodSteam, enter = meal, exit = leave, gaze = foodGaze, steadyFlags = setOf(SceneFlag.FOOD_SERVED),
                )
                HoodieActivity.COFFEE -> VisualState(scene, SpotId.TABLE, listOf(MicroAction(m.coffee, 1)), mugSteam, enter = listOf(SIT_TABLE), exit = listOf(STAND_TABLE))
                HoodieActivity.PHONE -> VisualState(scene, SpotId.TABLE, phone(1), enter = listOf(SIT_TABLE), exit = listOf(STAND_TABLE))
                else -> VisualState(scene, SpotId.TABLE, listOf(MicroAction(IDLE_SIT, 1)) + phone(1).take(1), enter = listOf(SIT_TABLE), exit = listOf(STAND_TABLE))
            }
        }
        SceneId.GYM -> when (a) {
            HoodieActivity.TRAINING -> VisualState(
                scene, SpotId.MAT,
                listOf(
                    MicroAction(RUN, 45, 8_000, 15_000, SpotId.TREADMILL, sweat, enter = listOf(RUN_START), exit = listOf(RUN_STOP)),
                    MicroAction(LIFT, 30, 6_000, 10_000, SpotId.WEIGHTS, sweat, enter = listOf(LIFT_PICK), exit = listOf(LIFT_PUT)),
                    MicroAction(STRETCH, 10, spot = SpotId.MAT, once = true),
                    MicroAction(GYM_WARMUP, 10, 3_000, 5_000, SpotId.MAT),
                    MicroAction(WATER, 10, 3_000, 4_000, SpotId.WATER),
                    MicroAction(GYM_REST, 5, 3_000, 5_000, SpotId.CENTER, sweat),
                ),
                enter = listOf(GYM_WARMUP),
            )
            else -> VisualState(scene, SpotId.WATER, listOf(MicroAction(WATER, 40, spot = SpotId.WATER), MicroAction(GYM_REST, 30, spot = SpotId.CENTER), MicroAction(IDLE, 30, spot = SpotId.CENTER)))
        }
        SceneId.UNKNOWN -> if (a == HoodieActivity.SLEEPING) {
            VisualState(scene, SpotId.CENTER, one(IDLE), expression = Expression.SLEEPY)
        } else VisualState(
            scene, SpotId.CENTER,
            listOf(MicroAction(LOOK_AROUND, 35), MicroAction(THINK_STAND, 20), MicroAction(IDLE_EAR, 10, once = true), MicroAction(IDLE_LOOK, 10, once = true)) + phone(5).take(1),
            gaze = unknownGaze,
        )
        SceneId.SCHOOL -> when (a) {
            HoodieActivity.STUDYING -> VisualState(
                scene, SpotId.DESK, studyActions(m),
                approach = listOf(GLANCE), enter = sitDown, exit = standUp, gaze = studyGaze,
                steadyFlags = setOf(SceneFlag.CHAIR_OCCUPIED, SceneFlag.BOOK_OPEN),
            )
            HoodieActivity.READING -> schoolDesk(
                scene, listOf(MicroAction(STUDY_READ, 50, 5_000, 12_000), MicroAction(READING, 30), MicroAction(STUDY_PAGE_TURN, 20, once = true)),
            ).copy(gaze = studyGaze, steadyFlags = setOf(SceneFlag.CHAIR_OCCUPIED, SceneFlag.BOOK_OPEN))
            HoodieActivity.COFFEE -> VisualState(scene, SpotId.WINDOW, listOf(coffee(m, 70), MicroAction(IDLE_LOOK, 30, once = true)), gaze = windowGaze)
            HoodieActivity.PHONE -> VisualState(scene, SpotId.WINDOW, phone(1))
            HoodieActivity.RESTING -> schoolDesk(scene, listOf(MicroAction(NAP_SIT, 60), MicroAction(IDLE_SIT, 40)))
            HoodieActivity.SLEEPING -> schoolDesk(scene, one(NAP_SIT), zzz, Expression.SLEEPY)
            HoodieActivity.EATING -> schoolDesk(scene, listOf(MicroAction(EAT, 70), MicroAction(STUDY_READ, 30)))
            else -> VisualState(
                scene, SpotId.BOARD,
                listOf(
                    MicroAction(THINK_STAND, 30, 3_000, 6_000, SpotId.BOARD), MicroAction(IDLE, 25, spot = SpotId.BOOKS),
                    MicroAction(LOOK_AROUND, 25, 3_000, 5_000, SpotId.CENTER), MicroAction(IDLE_LOOK, 20, spot = SpotId.WINDOW, once = true),
                ),
            )
        }
        SceneId.SHOPPING -> when (a) {
            HoodieActivity.PHONE -> VisualState(scene, SpotId.CENTER, phone(1))
            HoodieActivity.COFFEE, HoodieActivity.EATING -> VisualState(
                scene, SpotId.CENTER, listOf(coffee(m, 60), MicroAction(SHOP_LOOK, 40, 3_000, 5_000, SpotId.AISLE_B)), gaze = shelfGaze,
            )
            HoodieActivity.SLEEPING, HoodieActivity.RESTING -> VisualState(
                scene, SpotId.CENTER,
                listOf(MicroAction(IDLE, 60, spot = SpotId.CENTER), MicroAction(YAWN, 20, spot = SpotId.CENTER, once = true), MicroAction(SHOP_CART, 20, 2_500, 4_000, SpotId.CART)),
                expression = Expression.SLEEPY,
            )
            else -> VisualState(scene, SpotId.AISLE_A, shopActions(m), approach = listOf(LOOK_AROUND), gaze = shelfGaze)
        }
        SceneId.FAMILY -> when (a) {
            HoodieActivity.WATCHING_TV -> familySofa(
                scene, listOf(MicroAction(WATCH_TV, 60), MicroAction(VISIT_LAUGH, 15, once = true), MicroAction(VISIT_CHAT, 25, 3_000, 6_000)), tvOn = true,
            )
            HoodieActivity.EATING -> familySofa(scene, listOf(MicroAction(VISIT_SNACK, 50, once = true), MicroAction(VISIT_CHAT, 30, 3_000, 6_000), MicroAction(VISIT_LISTEN, 20)))
            HoodieActivity.COFFEE -> familySofa(scene, listOf(coffee(m, 55), MicroAction(VISIT_LISTEN, 45)))
            HoodieActivity.PHONE -> familySofa(scene, phone(2) + MicroAction(VISIT_LISTEN, 4))
            HoodieActivity.RESTING -> familySofa(scene, listOf(MicroAction(NAP_SIT, 50), MicroAction(VISIT_LISTEN, 30), MicroAction(IDLE_SIT, 20)))
            HoodieActivity.SLEEPING -> familySofa(scene, one(NAP_SIT), effects = zzz, expression = Expression.SLEEPY)
            HoodieActivity.SOCIALIZING -> familySofa(scene, visitActions(m)).copy(approach = listOf(WAVE), exit = listOf(STAND_UP, WAVE))
            else -> VisualState(
                scene, SpotId.CENTER,
                listOf(
                    MicroAction(LOOK_AROUND, 35, 3_000, 5_000, SpotId.CENTER), MicroAction(IDLE, 30, spot = SpotId.WINDOW),
                    MicroAction(IDLE_LOOK, 20, spot = SpotId.FAMILY_TABLE, once = true), MicroAction(WAVE, 15, spot = SpotId.CENTER, once = true),
                ),
                gaze = visitGaze,
            )
        }
        SceneId.LEISURE -> when (a) {
            HoodieActivity.PHONE -> bench(scene, phone(2) + MicroAction(LEISURE_BENCH, 4))
            HoodieActivity.COFFEE -> bench(scene, listOf(coffee(m, 60), MicroAction(LEISURE_BENCH, 40)))
            HoodieActivity.EATING -> bench(scene, listOf(MicroAction(VISIT_SNACK, 60, once = true), MicroAction(LEISURE_BENCH, 40)))
            HoodieActivity.RESTING -> bench(scene, listOf(MicroAction(LEISURE_BENCH, 55), MicroAction(NAP_SIT, 45)))
            HoodieActivity.SLEEPING -> bench(scene, one(NAP_SIT), zzz, Expression.SLEEPY)
            else -> VisualState(
                scene, SpotId.PATH_A, leisureActions(m), approach = listOf(LEISURE_LOOK), gaze = leisureGaze,
            )
        }
        SceneId.GENERIC_INDOOR -> when (a) {
            HoodieActivity.EATING -> VisualState(scene, SpotId.SOFA, one(EAT), enter = sitDown, exit = standUp)
            HoodieActivity.WALKING -> VisualState(scene, SpotId.CENTER, listOf(MicroAction(LOOK_AROUND, 50), MicroAction(IDLE, 50, spot = SpotId.SOFA)))
            else -> VisualState(
                scene, SpotId.SOFA, listOf(MicroAction(IDLE_SIT, 40), coffee(m, 30)) + phone(6).take(1), enter = sitDown, exit = standUp,
            )
        }
        SceneId.GENERIC_OUTDOOR -> when (a) {
            HoodieActivity.PHONE -> VisualState(scene, SpotId.CENTER, phone(1))
            HoodieActivity.COFFEE -> VisualState(scene, SpotId.CENTER, listOf(coffee(m, 1)))
            else -> VisualState(
                scene, SpotId.PATH_A,
                listOf(
                    MicroAction(IDLE, 30, 2_000, 4_000, SpotId.PATH_A), MicroAction(LOOK_AROUND, 30, 3_000, 5_000, SpotId.PATH_B),
                    MicroAction(IDLE_LOOK, 20, spot = SpotId.CENTER, once = true), MicroAction(HAPPY, 20, spot = SpotId.PATH_B, once = true),
                ),
            )
        }
    }
}
