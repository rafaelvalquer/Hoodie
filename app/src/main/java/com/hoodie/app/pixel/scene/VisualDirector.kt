package com.hoodie.app.pixel.scene

import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationId.*
import com.hoodie.app.pixel.renderer.EffectKind
import com.hoodie.app.pixel.sprite.Anchor
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Expression
import com.hoodie.app.pixel.sprite.Eyes

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
)

/** HoodieActivity + contexto do usuário → cena, âncora, sequências e microações. */
object VisualDirector {

    fun sceneFor(activity: HoodieActivity, context: UserContextType, homeOffice: Boolean, commute: CommuteStyle, variant: Int): SceneId =
        when (context) {
            UserContextType.HOME -> if (activity == HoodieActivity.WALKING) SceneId.GENERIC_OUTDOOR else SceneId.HOME
            UserContextType.WORK -> if (homeOffice) SceneId.HOME else SceneId.OFFICE
            UserContextType.COMMUTING -> when (commute) {
                CommuteStyle.WALK -> SceneId.STREET
                CommuteStyle.BUS -> SceneId.TRANSIT
                CommuteStyle.RANDOM -> if (variant % 2 == 0) SceneId.STREET else SceneId.TRANSIT
            }
            UserContextType.LUNCH -> SceneId.RESTAURANT
            UserContextType.GYM -> SceneId.GYM
            UserContextType.STUDY, UserContextType.SHOPPING, UserContextType.VISITING -> SceneId.GENERIC_INDOOR
            UserContextType.LEISURE, UserContextType.TRAVEL -> SceneId.GENERIC_OUTDOOR
            UserContextType.UNKNOWN -> SceneId.UNKNOWN
        }

    fun resolve(
        activity: HoodieActivity,
        context: UserContextType,
        homeOffice: Boolean = false,
        commute: CommuteStyle = CommuteStyle.RANDOM,
        variant: Int = 0,
        energy: Int = 70,
        mood: Int = 70,
    ): VisualState {
        val scene = sceneFor(activity, context, homeOffice, commute, variant)
        val tired = if (energy < 20) Expression.TIRED else null
        val base = forScene(scene, activity, Mood(energy, mood))
        return base.copy(variant = variant, expression = base.expression ?: tired)
    }

    /** Personalidade nas escolhas visuais: a mesma atividade muda conforme energia e humor. */
    private data class Mood(val energy: Int, val mood: Int) {
        val coffee: AnimationId get() = when { energy < 20 -> COFFEE_TIRED; mood >= 85 -> COFFEE_HAPPY; else -> DRINK }
        val tired: Boolean get() = energy < 25
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
        SceneId.TRANSIT -> VisualState(
            scene, SpotId.SEAT, listOf(MicroAction(BUS_SIT, 75), MicroAction(IDLE_SIT, 10)) + phone(3).take(1),
            backpackWalk = true, gaze = windowGaze,
        )
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
