package com.hoodie.app.pixel.scene

import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.animation.AnimationId.*
import com.hoodie.app.pixel.renderer.EffectKind
import com.hoodie.app.pixel.sprite.Expression

/** Efeito ancorado nos pés do Hoodie (dx, dy relativos). */
data class EffectSpec(val kind: EffectKind, val dx: Int, val dy: Int)

/**
 * Microação visual: o estado real dura horas, mas na tela o gato alterna pequenas
 * ações (digitar, mouse, café...) para nunca parecer uma tela estática.
 */
data class MicroAction(
    val anim: AnimationId,
    val weight: Int,
    val minMs: Long = 4_000,
    val maxMs: Long = 9_000,
    val spot: SpotId? = null,
    val effects: List<EffectSpec> = emptyList(),
)

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
)

/** HoodieActivity + contexto do usuário → cena, âncora e microações. */
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
    ): VisualState {
        val scene = sceneFor(activity, context, homeOffice, commute, variant)
        val tired = if (energy < 20) Expression.TIRED else null
        val base = forScene(scene, activity, variant)
        return base.copy(variant = variant, expression = base.expression ?: tired)
    }

    private fun one(anim: AnimationId) = listOf(MicroAction(anim, 1, 8_000, 15_000))

    private val zzz = listOf(EffectSpec(EffectKind.SLEEP_Z, 12, -60))

    private fun forScene(scene: SceneId, a: HoodieActivity, variant: Int): VisualState = when (scene) {
        SceneId.HOME -> when (a) {
            HoodieActivity.SLEEPING -> VisualState(scene, SpotId.BED, one(SLEEP), zzz, screenOn = false)
            HoodieActivity.WAKING_UP -> VisualState(scene, SpotId.CENTER, listOf(
                MicroAction(WAKE_UP, 3, 1_500, 1_500), MicroAction(STRETCH, 2, 1_000, 1_000), MicroAction(IDLE, 2),
            ), screenOn = false, expression = Expression.SLEEPY)
            HoodieActivity.BREAKFAST -> VisualState(scene, SpotId.KITCHEN, listOf(
                MicroAction(COFFEE_STAND, 50, effects = listOf(EffectSpec(EffectKind.STEAM, 8, -46))), MicroAction(IDLE, 20), MicroAction(COOK, 30),
            ), screenOn = false)
            HoodieActivity.WORKING -> VisualState(scene, SpotId.DESK, workActions())
            HoodieActivity.EATING -> VisualState(scene, SpotId.DESK, listOf(MicroAction(EAT, 70), MicroAction(IDLE_SIT, 30)), screenOn = false)
            HoodieActivity.COFFEE -> VisualState(scene, SpotId.WINDOW, listOf(
                MicroAction(COFFEE_STAND, 70, effects = listOf(EffectSpec(EffectKind.STEAM, 8, -46))), MicroAction(IDLE, 30),
            ), screenOn = false)
            HoodieActivity.RESTING -> VisualState(scene, SpotId.SOFA, listOf(MicroAction(NAP_SIT, 60), MicroAction(IDLE_SIT, 40)), screenOn = false)
            HoodieActivity.GAMING -> VisualState(scene, SpotId.SOFA, listOf(
                MicroAction(GAMING, 85, 5_000, 12_000), MicroAction(GAMING_EXCITED, 15, 600, 600),
            ), listOf(EffectSpec(EffectKind.MUSIC, 34, -78)), tvOn = true, screenOn = false)
            HoodieActivity.READING -> VisualState(scene, SpotId.SOFA, one(READING), screenOn = false)
            HoodieActivity.WATCHING_TV -> VisualState(scene, SpotId.SOFA, listOf(MicroAction(WATCH_TV, 80), MicroAction(IDLE_SIT, 20)), tvOn = true, screenOn = false)
            HoodieActivity.COOKING -> VisualState(scene, SpotId.KITCHEN, one(COOK), listOf(EffectSpec(EffectKind.STEAM, -20, -36)), screenOn = false)
            HoodieActivity.CLEANING -> VisualState(scene, SpotId.CENTER, one(CLEAN), listOf(EffectSpec(EffectKind.DUST, 10, -2)), screenOn = false)
            HoodieActivity.PHONE -> VisualState(scene, SpotId.SOFA, one(PHONE_SIT), screenOn = false)
            HoodieActivity.TRAINING -> VisualState(scene, SpotId.CENTER, listOf(MicroAction(STRETCH, 1, 1_000, 1_000), MicroAction(IDLE, 1, 1_500, 2_500)), screenOn = false)
            else -> VisualState(scene, SpotId.CENTER, idleActions(SpotId.WINDOW), screenOn = false)
        }
        SceneId.OFFICE -> when (a) {
            HoodieActivity.COFFEE -> VisualState(scene, SpotId.COFFEE, listOf(
                MicroAction(COFFEE_STAND, 70, effects = listOf(EffectSpec(EffectKind.STEAM, 8, -46))), MicroAction(IDLE, 30),
            ))
            HoodieActivity.RESTING, HoodieActivity.IDLE -> VisualState(scene, SpotId.WINDOW, listOf(
                MicroAction(IDLE, 40), MicroAction(STRETCH, 20, 1_000, 1_000), MicroAction(LOOK_AROUND, 20), MicroAction(THINK_STAND, 20),
            ))
            HoodieActivity.PHONE -> VisualState(scene, SpotId.WINDOW, one(PHONE_STAND))
            HoodieActivity.EATING -> VisualState(scene, SpotId.DESK, listOf(MicroAction(EAT, 70), MicroAction(WORK_READ, 30)))
            else -> VisualState(scene, SpotId.DESK, workActions())
        }
        SceneId.STREET -> VisualState(scene, SpotId.WALK, one(WALK_BACKPACK), backpackWalk = true)
        SceneId.TRANSIT -> VisualState(scene, SpotId.SEAT, listOf(MicroAction(BUS_SIT, 85), MicroAction(IDLE_SIT, 15)), backpackWalk = true)
        SceneId.RESTAURANT -> when (a) {
            HoodieActivity.EATING -> VisualState(scene, SpotId.TABLE, listOf(
                MicroAction(EAT, 60), MicroAction(IDLE_SIT, 15, 2_000, 4_000), MicroAction(COFFEE_SIT, 10), MicroAction(PHONE_SIT, 15),
            ), listOf(EffectSpec(EffectKind.STEAM, -4, -32)))
            HoodieActivity.COFFEE -> VisualState(scene, SpotId.TABLE, one(COFFEE_SIT), listOf(EffectSpec(EffectKind.STEAM, 8, -46)))
            HoodieActivity.PHONE -> VisualState(scene, SpotId.TABLE, one(PHONE_SIT))
            else -> VisualState(scene, SpotId.TABLE, listOf(MicroAction(IDLE_SIT, 1), MicroAction(PHONE_SIT, 1)))
        }
        SceneId.GYM -> when (a) {
            HoodieActivity.TRAINING -> VisualState(scene, SpotId.TREADMILL, listOf(
                MicroAction(RUN, 50, 8_000, 15_000, SpotId.TREADMILL, listOf(EffectSpec(EffectKind.SWEAT, 18, -66))),
                MicroAction(LIFT, 30, 6_000, 10_000, SpotId.WEIGHTS, listOf(EffectSpec(EffectKind.SWEAT, 18, -66))),
                MicroAction(STRETCH, 20, 1_000, 1_000, SpotId.MAT),
                MicroAction(WATER, 10, 3_000, 4_000, SpotId.WATER),
            ))
            else -> VisualState(scene, SpotId.WATER, listOf(MicroAction(WATER, 40, spot = SpotId.WATER), MicroAction(IDLE, 60, spot = SpotId.CENTER)))
        }
        SceneId.UNKNOWN -> if (a == HoodieActivity.SLEEPING) {
            VisualState(scene, SpotId.CENTER, one(IDLE), expression = Expression.SLEEPY)
        } else VisualState(scene, SpotId.CENTER, listOf(
            MicroAction(LOOK_AROUND, 40), MicroAction(PHONE_STAND, 30), MicroAction(THINK_STAND, 30),
        ))
        SceneId.GENERIC_INDOOR -> when (a) {
            HoodieActivity.EATING -> VisualState(scene, SpotId.SOFA, one(EAT))
            HoodieActivity.WALKING -> VisualState(scene, SpotId.CENTER, listOf(MicroAction(LOOK_AROUND, 50), MicroAction(IDLE, 50, spot = SpotId.SOFA)))
            else -> VisualState(scene, SpotId.SOFA, listOf(MicroAction(IDLE_SIT, 40), MicroAction(PHONE_SIT, 30), MicroAction(COFFEE_SIT, 30)))
        }
        SceneId.GENERIC_OUTDOOR -> when (a) {
            HoodieActivity.PHONE -> VisualState(scene, SpotId.CENTER, one(PHONE_STAND))
            HoodieActivity.COFFEE -> VisualState(scene, SpotId.CENTER, one(COFFEE_STAND))
            else -> VisualState(scene, SpotId.PATH_A, listOf(
                MicroAction(IDLE, 30, 2_000, 4_000, SpotId.PATH_A), MicroAction(LOOK_AROUND, 30, 3_000, 5_000, SpotId.PATH_B),
                MicroAction(IDLE, 20, 2_000, 4_000, SpotId.CENTER), MicroAction(HAPPY, 20, 700, 700, SpotId.PATH_B),
            ))
        }
    }

    /** Sistema de trabalho procedural (pesos da especificação). */
    private fun workActions() = listOf(
        MicroAction(WORK_TYPING, 45, 5_000, 12_000),
        MicroAction(WORK_MOUSE, 15, 3_000, 6_000),
        MicroAction(WORK_READ, 15, 3_000, 6_000),
        MicroAction(COFFEE_SIT, 8, 4_000, 6_000, effects = listOf(EffectSpec(EffectKind.STEAM, 8, -42))),
        MicroAction(THINK, 7, 3_000, 5_000),
        MicroAction(PHONE_SIT, 5, 3_000, 5_000),
        MicroAction(STRETCH_SIT, 5, 1_000, 1_000),
    )

    private fun idleActions(wander: SpotId) = listOf(
        MicroAction(IDLE, 50), MicroAction(LOOK_AROUND, 20), MicroAction(STRETCH, 10, 1_000, 1_000), MicroAction(IDLE, 20, spot = wander),
    )

    /** Spot físico de uma atividade com sentar/em pé coerente com a animação. */
    fun isSitting(anim: AnimationId): Boolean = anim.frames.first().legs == com.hoodie.app.pixel.sprite.Legs.SIT
}
