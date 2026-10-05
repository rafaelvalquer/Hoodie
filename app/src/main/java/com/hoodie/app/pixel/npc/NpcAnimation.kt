package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterRenderMotion
import com.hoodie.app.pixel.character.FootContact
import com.hoodie.app.pixel.character.GaitSample
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Ears
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Conjunto universal de animações de NPC. Todas são poses CALCULADAS a partir do tempo
 * (e, na caminhada, da distância percorrida) — não sprites desenhados à mão.
 */
enum class NpcAnimation {
    IDLE, WALK, LOOK, LOOK_WINDOW, TALK,
    SIT, SIT_PHONE, SIT_EAT, SIT_SLEEP, SIT_HEAD_DROP, SIT_WAKE,
    STAND, STAND_PHONE, STAND_COFFEE,
    TURN_LEFT, TURN_RIGHT, ENTER, EXIT, REACTION;

    val seated: Boolean get() = name.startsWith("SIT")
}

/** Reações raras, sobrepostas ao comportamento. */
enum class NpcReaction { LOOK_AT_HOODIE, SMILE, SURPRISED, WAVE, NOD }

/** Pose + movimento secundário de um instante. */
data class NpcFrame(
    val pose: CharacterPose,
    val motion: CharacterRenderMotion,
    val contact: FootContact = FootContact.BOTH,
)

/**
 * Passada de 8 fases (CONTACT, DOWN, PASS, UP ×2) dirigida pela DISTÂNCIA percorrida:
 * o pé de apoio recua no quadro exatamente o que o personagem avança no mundo, então
 * fica plantado (sem foot sliding). [scale] é a escala ambiente do sprite na cena.
 */
object NpcGait {
    data class Sample(val gait: GaitSample, val phase: Float) {
        /** 0..7: CONTACT, DOWN, PASS, UP do pé da frente, depois do de trás. */
        val stride: Int get() = floor(phase * 4f).toInt().mod(8)
        val quarter: Int get() = stride % 4
        /** Pé da frente (near) em apoio na primeira metade do ciclo; o de trás na segunda. */
        val nearPlanted: Boolean get() = phase < 1f
        val farPlanted: Boolean get() = phase >= 1f
    }

    fun sample(distancePx: Float, motion: SpeciesMotionProfile, scale: Float = AmbientScale.DEFAULT): Sample {
        val step = motion.stepLength.coerceAtLeast(2)
        val worldStep = step * scale
        val u = ((distancePx / worldStep) % 2f).let { if (it < 0) it + 2f else it }
        fun foot(v: Float): Pair<Int, Int> = if (v < 1f) {
            // Apoio: do meio-passo à frente até o meio-passo atrás, no ritmo do mundo.
            (step / 2f - v * step).roundToInt() to 0
        } else {
            // Balanço: o pé sai do chão logo no início e só pousa no fim (arco em seno).
            val f = v - 1f
            val lift = if (f < 0.06f || f > 0.94f) 0 else maxOf(1, (sin(PI * f) * motion.footLift).roundToInt())
            (-step / 2f + f * step).roundToInt() to lift
        }
        val (nearX, nearLift) = foot(u)
        val (farX, farLift) = foot((u + 1f) % 2f)
        val contact = when {
            nearLift == 0 && farLift == 0 -> FootContact.BOTH
            u < 1f -> FootContact.LEFT
            else -> FootContact.RIGHT
        }
        return Sample(GaitSample(nearX, farX, nearLift, farLift, contact), u)
    }
}

/** Biblioteca de poses V3. */
object NpcPoseLibrary {
    private val BOB = intArrayOf(0, 1, 0, -1)
    private val TAIL_WAVE = intArrayOf(0, 1, 2, 1, 0, -1, -2, -1)

    fun frame(
        animation: NpcAnimation,
        elapsedMs: Long,
        seed: Int,
        motion: SpeciesMotionProfile,
        walkedPx: Float? = null,
        scale: Float = AmbientScale.DEFAULT,
        seated: Boolean = animation.seated,
        facing: Facing? = null,
        reaction: NpcReaction? = null,
    ): NpcFrame {
        val t = elapsedMs.coerceAtLeast(0)
        val base = motion.renderMotion()
        val f = when (animation) {
            NpcAnimation.WALK, NpcAnimation.ENTER, NpcAnimation.EXIT ->
                walk(walkedPx ?: (t / motion.msPerPixel.toFloat()), seed, motion, scale)
            NpcAnimation.IDLE -> NpcFrame(idle(t, seed, seated), base.copy(breath = breath(t, seed)))
            NpcAnimation.LOOK -> NpcFrame(look(t, seed, seated), base.copy(breath = breath(t, seed)))
            NpcAnimation.LOOK_WINDOW -> NpcFrame(
                look(t, seed, seated).copy(facing = Facing.SIDE, eyes = if ((t / 1_400) % 3 == 2L) Eyes.LOOK_UP else Eyes.OPEN, headTilt = 0),
                base.copy(breath = breath(t, seed)),
            )
            NpcAnimation.TALK -> NpcFrame(talk(t, seed, seated), base.copy(breath = breath(t, seed)))
            NpcAnimation.SIT -> NpcFrame(sitDown(t, seed), base)
            NpcAnimation.SIT_PHONE -> NpcFrame(sitPhone(t, seed), base.copy(breath = breath(t, seed)))
            NpcAnimation.SIT_EAT -> NpcFrame(sitEat(t, seed), base)
            NpcAnimation.SIT_SLEEP -> NpcFrame(sitSleep(t, seed), base.copy(breath = breath(t, seed, 1_300)))
            NpcAnimation.SIT_HEAD_DROP -> NpcFrame(headDrop(t), base.copy(breath = 1))
            NpcAnimation.SIT_WAKE -> NpcFrame(wake(t), base)
            NpcAnimation.STAND -> NpcFrame(stand(t, seed), base.copy(breath = breath(t, seed)))
            NpcAnimation.STAND_PHONE -> NpcFrame(standPhone(t, seed), base.copy(breath = breath(t, seed)))
            NpcAnimation.STAND_COFFEE -> NpcFrame(standCoffee(t, seed), base)
            NpcAnimation.TURN_LEFT, NpcAnimation.TURN_RIGHT -> NpcFrame(turn(t), base)
            NpcAnimation.REACTION -> NpcFrame(reaction(reaction ?: NpcReaction.LOOK_AT_HOODIE, t, seated), base)
        }
        return if (facing != null && animation != NpcAnimation.TURN_LEFT && animation != NpcAnimation.TURN_RIGHT)
            f.copy(pose = f.pose.copy(facing = facing)) else f
    }

    // ───── Locomoção ─────

    /** Caminhada: braço oposto à perna, bob de contato, cabeça e orelhas atrasadas, cauda que segue. */
    fun walk(walkedPx: Float, seed: Int, motion: SpeciesMotionProfile, scale: Float = AmbientScale.DEFAULT): NpcFrame {
        val s = NpcGait.sample(walkedPx, motion, scale)
        val q = s.quarter
        val bob = (BOB[q] * motion.walkBob).coerceIn(-2, 2)
        // A cabeça "fica para trás" do sobe-e-desce do corpo.
        val headDy = if (motion.headLag > 0) (-BOB[q]).coerceIn(-1, 1) else 0
        val nearForward = s.gait.nearX > 0
        val pose = CharacterPose(
            legs = Legs.WALK, stride = s.stride, facing = Facing.SIDE,
            bob = bob, headDy = headDy,
            eyes = if (s.stride == 7 && seed.mod(2) == 0) Eyes.LOOK_UP else Eyes.OPEN,
            mouth = Mouth.SMILE,
            ears = if (q == 2) Ears.ALERT else Ears.NORMAL,
            rightArm = if (motion.wingArms) Arm.SWING_BACK else if (nearForward) Arm.SWING_BACK else Arm.SWING_FRONT,
            leftArm = if (motion.wingArms) Arm.SWING_BACK else if (nearForward) Arm.SWING_FRONT else Arm.SWING_BACK,
            stringSwing = TAIL_WAVE[(s.stride + 8 - motion.tailLag.coerceIn(0, 4)) % 8].coerceIn(-1, 1),
        )
        val m = motion.renderMotion().copy(
            gait = s.gait,
            earLag = if (q == 3) motion.earLag else if (q == 1) -motion.earLag / 2 else 0,
            sway = if (motion.sway) (if (s.phase < 1f) 1 else -1) else 0,
        )
        return NpcFrame(pose, m, s.gait.contact)
    }

    // ───── Em pé / sentado ─────

    private fun breath(t: Long, seed: Int, period: Long = 1_100): Int = (((t + seed * 97L) / period) % 2).toInt()

    fun shouldBlink(t: Long, seed: Int): Boolean {
        val k = Math.floorMod(t + seed * 257L, 3_900L)
        // Piscada simples; a cada ~4 ciclos, piscada dupla.
        return k < 130L || (Math.floorMod(seed, 4) == 0 && k in 260L..360L)
    }

    private fun seatedBase(seated: Boolean) = CharacterPose(legs = if (seated) Legs.SIT else Legs.STAND)

    /** Respiração → piscar → micro movimento de cabeça → orelha → cauda (ciclo de 4,8 s). */
    fun idle(t: Long, seed: Int, seated: Boolean = false): CharacterPose {
        val k = Math.floorMod(t + seed * 331L, 4_800L)
        return seatedBase(seated).copy(
            eyes = if (shouldBlink(t, seed)) Eyes.CLOSED else Eyes.OPEN,
            mouth = Mouth.SMILE,
            headTilt = if (k in 2_400L..3_000L) 1 else 0,
            headDy = if (k in 3_000L..3_300L) 1 else 0,
            ears = when (k) { in 3_600L..3_760L -> Ears.TWITCH_LEFT; in 3_760L..3_900L -> Ears.TWITCH_RIGHT; else -> Ears.NORMAL },
            stringSwing = TAIL_WAVE[((k / 300) % 8).toInt()].coerceIn(-1, 1),
        )
    }

    /** Olhos → cabeça → orelhas; o corpo não se mexe. */
    fun look(t: Long, seed: Int, seated: Boolean = false): CharacterPose {
        val k = Math.floorMod(t + seed * 211L, 3_600L)
        val (eyes, tilt) = when (k) {
            in 0L..899L -> Eyes.LOOK_LEFT to -1
            in 900L..1_499L -> Eyes.OPEN to 0
            in 1_500L..2_399L -> Eyes.LOOK_RIGHT to 1
            in 2_400L..2_999L -> Eyes.LOOK_UP to 0
            else -> Eyes.OPEN to 0
        }
        return seatedBase(seated).copy(
            eyes = if (shouldBlink(t, seed)) Eyes.CLOSED else eyes,
            headTilt = tilt,
            ears = if (k in 0L..200L || k in 1_500L..1_700L) Ears.ALERT else Ears.NORMAL,
            mouth = Mouth.SMILE,
        )
    }

    /** Fala com o corpo: boca, aceno de cabeça, olhar para o Hoodie e gesto de mão. */
    fun talk(t: Long, seed: Int, seated: Boolean = false): CharacterPose {
        val k = Math.floorMod(t + seed * 53L, 2_400L)
        val open = k in 0L..179L || k in 360L..539L || k in 900L..1_079L || k in 1_400L..1_599L
        return seatedBase(seated).copy(
            facing = Facing.FRONT,
            mouth = if (open) Mouth.OPEN else Mouth.SMILE,
            headDy = if (k in 600L..799L) 1 else 0,
            headTilt = if (k in 1_600L..2_000L) 1 else 0,
            rightArm = if (k in 700L..1_500L) Arm.FORWARD_UP else Arm.DOWN,
            eyes = if (k in 2_000L..2_119L) Eyes.CLOSED else Eyes.OPEN,
            ears = if (k in 700L..900L) Ears.ALERT else Ears.NORMAL,
        )
    }

    /** STAND → BEND → LOWER → CONTACT → SIT (640 ms), nunca um teleporte. */
    fun sitDown(t: Long, seed: Int): CharacterPose = when {
        t < 160 -> CharacterPose(legs = Legs.STAND)
        t < 320 -> CharacterPose(legs = Legs.STAND, bob = 2, headDy = 1)
        t < 480 -> CharacterPose(legs = Legs.SIT, bob = -2)
        t < 560 -> CharacterPose(legs = Legs.SIT, bob = -1, ears = Ears.ALERT)
        else -> idle(t, seed, seated = true)
    }

    /** Celular na mão, cabeça inclinada, piscar e o dedo de vez em quando. */
    fun sitPhone(t: Long, seed: Int): CharacterPose {
        val k = Math.floorMod(t + seed * 173L, 3_200L)
        return CharacterPose(
            legs = Legs.SIT, facing = Facing.SIDE, item = Item.PHONE,
            rightArm = Arm.HOLD_CHEST,
            leftArm = if (k in 1_500L..1_699L || k in 2_600L..2_699L) Arm.HOLD_CHEST else Arm.DOWN,
            eyes = if (shouldBlink(t, seed)) Eyes.CLOSED else Eyes.LOOK_DOWN,
            headDy = 1, mouth = if (k in 2_000L..2_500L) Mouth.FLAT else Mouth.SMILE,
        )
    }

    /** Garfo parado → pega → levanta → boca → mastiga → abaixa. */
    fun sitEat(t: Long, seed: Int): CharacterPose {
        val k = Math.floorMod(t + seed * 113L, 3_000L)
        val base = CharacterPose(legs = Legs.SIT, facing = Facing.SIDE, item = Item.FORK, eyes = Eyes.LOOK_DOWN)
        return when (k) {
            in 0L..599L -> base.copy(rightArm = Arm.HOLD_CHEST)
            in 600L..899L -> base.copy(rightArm = Arm.FORWARD_DOWN)
            in 900L..1_199L -> base.copy(rightArm = Arm.HOLD_CHEST, eyes = Eyes.OPEN)
            in 1_200L..1_499L -> base.copy(rightArm = Arm.HOLD_MOUTH, mouth = Mouth.OPEN, eyes = Eyes.OPEN)
            in 1_500L..2_299L -> base.copy(rightArm = Arm.HOLD_CHEST, eyes = Eyes.HAPPY, mouth = if ((k / 200) % 2 == 0L) Mouth.CHEW else Mouth.SMILE)
            else -> base.copy(rightArm = Arm.FORWARD_DOWN)
        }
    }

    /** Respiração lenta, cabeça caindo aos poucos, orelha baixa e uma pequena correção. */
    fun sitSleep(t: Long, seed: Int): CharacterPose {
        val k = Math.floorMod(t + seed * 389L, 4_000L)
        return CharacterPose(
            legs = Legs.SIT, eyes = Eyes.CLOSED, mouth = Mouth.FLAT, ears = Ears.DOWN,
            headDy = when (k) { in 2_000L..3_199L -> 2; in 3_200L..3_399L -> 0; else -> 1 },
        )
    }

    /** A cabeça despenca e dá um tranco de volta. */
    fun headDrop(t: Long): CharacterPose = CharacterPose(
        legs = Legs.SIT, eyes = Eyes.CLOSED, mouth = Mouth.FLAT, ears = Ears.DOWN,
        headDy = when { t < 500 -> 2; t < 1_000 -> 3; t < 1_150 -> 0; else -> 1 },
    )

    /** Acorda de leve: meio olho, bocejo, volta a fechar. */
    fun wake(t: Long): CharacterPose = CharacterPose(
        legs = Legs.SIT, ears = Ears.RELAXED,
        eyes = when { t < 300 -> Eyes.HALF; t < 800 -> Eyes.OPEN; else -> Eyes.HALF },
        mouth = if (t in 300L..700L) Mouth.OPEN else Mouth.FLAT,
    )

    /** Em pé segurando a barra do transporte, olhando em volta. */
    fun stand(t: Long, seed: Int): CharacterPose = idle(t, seed).copy(
        rightArm = Arm.UP,
        eyes = if (shouldBlink(t, seed)) Eyes.CLOSED else if ((t / 1_800) % 3 == 1L) Eyes.LOOK_LEFT else Eyes.OPEN,
    )

    fun standPhone(t: Long, seed: Int): CharacterPose = CharacterPose(
        item = Item.PHONE, rightArm = Arm.HOLD_CHEST,
        eyes = if (shouldBlink(t, seed)) Eyes.CLOSED else Eyes.LOOK_DOWN, headDy = 1,
        leftArm = if (Math.floorMod(t + seed * 7L, 2_400L) in 1_200L..1_400L) Arm.HOLD_CHEST else Arm.DOWN,
    )

    fun standCoffee(t: Long, seed: Int): CharacterPose {
        val k = Math.floorMod(t + seed * 31L, 3_000L)
        val sip = k in 1_200L..1_799L
        return CharacterPose(
            item = Item.MUG, rightArm = if (sip) Arm.HOLD_MOUTH else Arm.HOLD_CHEST,
            mouth = if (sip) Mouth.OPEN else Mouth.SMILE,
            eyes = if (sip) Eyes.CLOSED else if (shouldBlink(t, seed)) Eyes.CLOSED else Eyes.OPEN,
        )
    }

    /** Virar: perfil → frente → perfil (o controlador espelha o último quadro). */
    fun turn(t: Long): CharacterPose = CharacterPose(facing = if (t in TURN_MS / 3..TURN_MS * 2 / 3) Facing.FRONT else Facing.SIDE)

    fun reaction(r: NpcReaction, t: Long, seated: Boolean = false): CharacterPose {
        val base = seatedBase(seated).copy(facing = Facing.FRONT)
        return when (r) {
            NpcReaction.LOOK_AT_HOODIE -> base.copy(ears = Ears.ALERT, eyes = if (t in 600L..720L) Eyes.CLOSED else Eyes.OPEN)
            NpcReaction.SMILE -> base.copy(eyes = Eyes.HAPPY, blush = true)
            NpcReaction.SURPRISED -> base.copy(eyes = Eyes.WIDE, mouth = Mouth.OPEN, ears = Ears.ALERT, lift = if (t < 200 && !seated) 1 else 0)
            NpcReaction.WAVE -> base.copy(rightArm = if ((t / 200) % 2 == 0L) Arm.WAVE else Arm.UP, eyes = Eyes.HAPPY)
            NpcReaction.NOD -> base.copy(headDy = if ((t / 200) % 2 == 0L) 1 else 0, eyes = Eyes.HAPPY)
        }
    }

    const val TURN_MS = 360L
    const val REACTION_MS = 1_600L
}
