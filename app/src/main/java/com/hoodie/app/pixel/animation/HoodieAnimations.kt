package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.animation.AnimationEvent.*
import com.hoodie.app.pixel.animation.InterruptPolicy.*
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Ears
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.HoodiePose
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth

/** Grupos só para organizar o Pixel Lab. */
enum class AnimGroup { LOCOMOTION, IDLE, POSTURE, SLEEP, WORK, COFFEE, PHONE, FOOD, GAME, GYM, HOME, REACTION }

/**
 * Identificador estável de animação. É o contrato entre o motor (VisualDirector,
 * máquina de estados) e os sprites: um sprite sheet do Aseprite com a tag
 * `walk_side` substitui o clip procedural de [WALK] sem mudar mais nada.
 */
enum class AnimationId(val label: String, val group: AnimGroup) {
    // Locomoção
    WALK("Caminhar", AnimGroup.LOCOMOTION),
    WALK_BACKPACK("Caminhar de mochila", AnimGroup.LOCOMOTION),
    WALK_STOP("Parar de andar", AnimGroup.LOCOMOTION),
    TURN("Virar (antecipação)", AnimGroup.LOCOMOTION),
    RUN("Correr", AnimGroup.LOCOMOTION),
    RUN_START("Começar a correr", AnimGroup.LOCOMOTION),
    RUN_STOP("Parar de correr", AnimGroup.LOCOMOTION),

    // Idle e personalidade
    IDLE("Idle A · respiração", AnimGroup.IDLE),
    IDLE_SIT("Idle sentado", AnimGroup.IDLE),
    IDLE_LOOK("Idle B · olha ao lado", AnimGroup.IDLE),
    IDLE_EAR("Idle C · orelhas", AnimGroup.IDLE),
    IDLE_SCRATCH("Idle D · coça a cabeça", AnimGroup.IDLE),
    IDLE_PHONE("Idle E · olha o celular", AnimGroup.IDLE),
    LOOK_AROUND("Olhar em volta", AnimGroup.IDLE),
    YAWN("Bocejar", AnimGroup.IDLE),
    STRETCH("Alongar", AnimGroup.IDLE),
    STRETCH_SIT("Alongar sentado", AnimGroup.IDLE),
    THINK("Pensar sentado", AnimGroup.IDLE),
    THINK_STAND("Pensar em pé", AnimGroup.IDLE),

    // Postura
    SIT_DOWN("Sentar", AnimGroup.POSTURE),
    STAND_UP("Levantar", AnimGroup.POSTURE),
    SIT_TABLE("Sentar à mesa", AnimGroup.POSTURE),
    STAND_TABLE("Levantar da mesa", AnimGroup.POSTURE),

    // Sono
    BED_SIT("Sentar na cama", AnimGroup.SLEEP),
    BED_LIE_DOWN("Deitar", AnimGroup.SLEEP),
    SLEEP("Dormir", AnimGroup.SLEEP),
    SLEEP_TURN("Virar no sono", AnimGroup.SLEEP),
    WAKE_EYES("Abrir os olhos", AnimGroup.SLEEP),
    BED_EXIT("Sair da cama", AnimGroup.SLEEP),
    WAKE_UP("Acordar e espreguiçar", AnimGroup.SLEEP),
    NAP_SIT("Cochilo", AnimGroup.SLEEP),

    // Trabalho
    WORK_TYPING("Digitando", AnimGroup.WORK),
    STOP_TYPING("Parar de digitar", AnimGroup.WORK),
    REACH_MOUSE("Pegar o mouse", AnimGroup.WORK),
    WORK_MOUSE("Usando mouse", AnimGroup.WORK),
    WORK_READ("Lendo a tela", AnimGroup.WORK),
    WORK_NOTES("Anotando", AnimGroup.WORK),
    WORK_TIRED("Cansado no trabalho", AnimGroup.WORK),

    // Café
    REACH_MUG("Pegar a caneca", AnimGroup.COFFEE),
    DRINK("Beber", AnimGroup.COFFEE),
    PUT_MUG("Pôr a caneca", AnimGroup.COFFEE),
    COFFEE_SIT("Café sentado", AnimGroup.COFFEE),
    COFFEE_STAND("Café em pé", AnimGroup.COFFEE),
    COFFEE_TIRED("Café cansado", AnimGroup.COFFEE),
    COFFEE_HAPPY("Café feliz", AnimGroup.COFFEE),

    // Celular
    PHONE_TAKE("Pegar celular", AnimGroup.PHONE),
    PHONE_READ("Ler no celular", AnimGroup.PHONE),
    PHONE_SCROLL("Rolar a tela", AnimGroup.PHONE),
    PHONE_TYPE("Digitar no celular", AnimGroup.PHONE),
    PHONE_REACT_SMILE("Reação 🙂", AnimGroup.PHONE),
    PHONE_REACT_MEH("Reação 😐", AnimGroup.PHONE),
    PHONE_REACT_WOW("Reação 😮", AnimGroup.PHONE),
    PHONE_PUT("Guardar celular", AnimGroup.PHONE),
    PHONE_SIT("Celular sentado", AnimGroup.PHONE),
    PHONE_STAND("Celular em pé", AnimGroup.PHONE),

    // Comida
    LOOK_MENU("Olhar o cardápio", AnimGroup.FOOD),
    WAIT_FOOD("Esperar a comida", AnimGroup.FOOD),
    EAT("Comer", AnimGroup.FOOD),
    FINISH_FOOD("Terminar a comida", AnimGroup.FOOD),

    // Videogame
    GAMING("Videogame", AnimGroup.GAME),
    GAME_PRESS("Apertando botões", AnimGroup.GAME),
    GAME_FOCUSED("Concentrado", AnimGroup.GAME),
    GAME_WIN("Ganhou!", AnimGroup.GAME),
    GAME_LOSE("Perdeu...", AnimGroup.GAME),

    // Academia
    GYM_WARMUP("Aquecimento", AnimGroup.GYM),
    LIFT_PICK("Pegar o peso", AnimGroup.GYM),
    LIFT("Levantar peso", AnimGroup.GYM),
    LIFT_PUT("Largar o peso", AnimGroup.GYM),
    GYM_REST("Descansar (academia)", AnimGroup.GYM),
    WATER("Beber água", AnimGroup.GYM),

    // Casa e lugares
    READING("Ler", AnimGroup.HOME),
    WATCH_TV("Ver TV", AnimGroup.HOME),
    COOK("Cozinhar", AnimGroup.HOME),
    CLEAN("Varrer", AnimGroup.HOME),
    BUS_SIT("Sentado no ônibus", AnimGroup.HOME),

    // Reações
    WAVE("Acenar", AnimGroup.REACTION),
    HAPPY("Feliz (pulinho)", AnimGroup.REACTION),
    SMILE("Sorrir", AnimGroup.REACTION),
    SURPRISED("Surpreso", AnimGroup.REACTION),
    SHRUG("Dar de ombros", AnimGroup.REACTION),
    NOTICE("Perceber o usuário", AnimGroup.REACTION),
    GLANCE("Olhar rápido", AnimGroup.REACTION),
    EAR_FLICK("Mexer a orelha", AnimGroup.REACTION),
    NOD("Concordar", AnimGroup.REACTION),
    ;

    val clip: AnimationClip get() = HoodieClips[this]
    val loop: Boolean get() = clip.loop
    val durationMs: Long get() = clip.totalMs
    val frames: List<AnimationFrame> get() = clip.frames

    /** Pose do clip procedural em [elapsedMs] (sem overlays). */
    fun frameAt(elapsedMs: Long): HoodiePose = frames[ClipTiming.indexAt(clip.durations, elapsedMs, loop)].pose

    /** Postura "natural" do clip (null = herda a atual). */
    val posture: Legs? get() = frames.first().pose.legs.takeIf { it != Legs.INHERIT && it != Legs.WALK }
}

/** Catálogo procedural (fallback). Sprite sheets podem substituir clip a clip. */
object HoodieClips {
    private val S = HoodiePose()
    private val SIT = HoodiePose(legs = Legs.SIT)
    private val I = HoodiePose(legs = Legs.INHERIT)

    private class Builder {
        val frames = mutableListOf<AnimationFrame>()
        fun f(ms: Long, pose: HoodiePose, vararg events: AnimationEvent) { frames += AnimationFrame(pose, ms, events.toSet()) }
    }

    private val clips = HashMap<AnimationId, AnimationClip>()

    private fun clip(id: AnimationId, loop: Boolean = true, policy: InterruptPolicy = FINISH_FRAME, directional: Boolean = false, build: Builder.() -> Unit) {
        clips[id] = AnimationClip(id, Builder().apply(build).frames, loop, policy, directional)
    }

    operator fun get(id: AnimationId): AnimationClip = clips[id] ?: error("Clip ausente: $id")

    val all: Map<AnimationId, AnimationClip> get() = clips

    // Caminhada: contato, descida, passagem, subida — para cada pé.
    private val WALK_MS = longArrayOf(100, 80, 80, 100, 100, 80, 80, 100)
    private val WALK_BOB = intArrayOf(0, 1, 0, -1, 0, 1, 0, -1)
    private val WALK_STRINGS = intArrayOf(0, 1, 1, 0, 0, -1, -1, 0)

    private fun walkFrames(b: Builder, backpack: Boolean) {
        for (i in 0 until 8) {
            val prev = (i + 7) % 8
            b.f(
                WALK_MS[i],
                HoodiePose(
                    legs = Legs.WALK, stride = i, bob = WALK_BOB[i],
                    // A cabeça e a mochila chegam 1 frame atrasadas (movimento secundário).
                    headDy = WALK_BOB[prev] - WALK_BOB[i],
                    stringSwing = WALK_STRINGS[i],
                    leftArm = if (i < 4) Arm.SWING_BACK else Arm.SWING_FRONT,
                    rightArm = if (i < 4) Arm.SWING_FRONT else Arm.SWING_BACK,
                    backpack = backpack, backpackDy = if (backpack) WALK_BOB[prev] - WALK_BOB[i] else 0,
                ),
                *(if (i == 0 || i == 4) arrayOf(FOOTSTEP) else emptyArray<AnimationEvent>()),
            )
        }
    }

    private fun drink(b: Builder, base: HoodiePose, eyes: Eyes = Eyes.OPEN, ears: Ears = Ears.NORMAL, slow: Long = 0) {
        val p = base.copy(rightArm = Arm.HOLD_CHEST, item = Item.MUG, ears = ears)
        b.f(500 + slow, p.copy(eyes = eyes))
        b.f(400 + slow, p.copy(rightArm = Arm.HOLD_MOUTH, eyes = Eyes.CLOSED))
        b.f(300 + slow, p.copy(rightArm = Arm.HOLD_MOUTH, eyes = Eyes.CLOSED, bob = base.bob + 1))
        b.f(300, p.copy(eyes = if (eyes == Eyes.OPEN) Eyes.HAPPY else eyes))
        b.f(600 + slow, p.copy(eyes = eyes, bob = base.bob))
    }

    private fun phone(b: Builder, base: HoodiePose) {
        val p = base.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN)
        b.f(700, p); b.f(500, p.copy(bob = 1)); b.f(700, p); b.f(400, p.copy(eyes = Eyes.HAPPY))
    }

    init {
        // ───── Locomoção ─────
        clip(AnimationId.WALK, policy = IMMEDIATE, directional = true) { walkFrames(this, backpack = false) }
        clip(AnimationId.WALK_BACKPACK, policy = IMMEDIATE, directional = true) { walkFrames(this, backpack = true) }
        clip(AnimationId.WALK_STOP, loop = false, directional = true) {
            f(110, S.copy(stringSwing = 1, headDy = 1)); f(110, S.copy(stringSwing = -1)); f(90, S)
        }
        clip(AnimationId.TURN, loop = false, policy = IMMEDIATE, directional = true) {
            f(140, S.copy(ears = Ears.ALERT)); f(140, S.copy(bob = 1, stringSwing = 1))
        }
        clip(AnimationId.RUN) {
            f(100, HoodiePose(legs = Legs.RUN_A, leftArm = Arm.SWING_FRONT, rightArm = Arm.SWING_BACK, eyes = Eyes.FOCUSED, mouth = Mouth.OPEN, bob = -1, stringSwing = 2), FOOTSTEP)
            f(90, HoodiePose(eyes = Eyes.FOCUSED, mouth = Mouth.OPEN, stringSwing = 1))
            f(100, HoodiePose(legs = Legs.RUN_B, leftArm = Arm.SWING_BACK, rightArm = Arm.SWING_FRONT, eyes = Eyes.FOCUSED, mouth = Mouth.OPEN, bob = -1, stringSwing = -2), FOOTSTEP)
            f(90, HoodiePose(eyes = Eyes.FOCUSED, mouth = Mouth.OPEN, stringSwing = -1))
        }
        clip(AnimationId.RUN_START, loop = false) {
            f(160, S.copy(bob = 1, eyes = Eyes.FOCUSED)); f(120, HoodiePose(legs = Legs.STEP_LEFT, eyes = Eyes.FOCUSED, stringSwing = 1))
            f(110, HoodiePose(legs = Legs.RUN_A, leftArm = Arm.SWING_FRONT, rightArm = Arm.SWING_BACK, eyes = Eyes.FOCUSED, stringSwing = 2))
        }
        clip(AnimationId.RUN_STOP, loop = false) {
            f(110, HoodiePose(legs = Legs.RUN_B, leftArm = Arm.SWING_BACK, rightArm = Arm.SWING_FRONT, mouth = Mouth.OPEN, stringSwing = -2))
            f(130, HoodiePose(legs = Legs.STEP_RIGHT, mouth = Mouth.OPEN, stringSwing = 2, bob = 1))
            f(160, S.copy(mouth = Mouth.OPEN, stringSwing = -1, bob = 1)); f(200, S.copy(eyes = Eyes.CLOSED))
        }

        // ───── Idle ─────
        // Respiração lenta em 8 tempos (inspira, segura, expira) com cordões e cabeça
        // atrasados. A arte final usa os mesmos 8 tempos; a piscada vem do overlay.
        val breathe: Builder.(HoodiePose) -> Unit = { base ->
            f(650, base); f(450, base.copy(stringSwing = 1)); f(600, base.copy(bob = 1)); f(450, base.copy(bob = 1, headDy = -1))
            f(600, base.copy(bob = 1)); f(450, base.copy(stringSwing = -1)); f(500, base); f(400, base)
        }
        clip(AnimationId.IDLE, policy = IMMEDIATE) { breathe(S) }
        clip(AnimationId.IDLE_SIT, policy = IMMEDIATE) { breathe(SIT) }
        clip(AnimationId.IDLE_LOOK, loop = false, policy = IMMEDIATE) {
            f(900, I.copy(eyes = Eyes.LOOK_LEFT)); f(250, I); f(900, I.copy(eyes = Eyes.LOOK_RIGHT)); f(300, I)
        }
        clip(AnimationId.IDLE_EAR, loop = false, policy = IMMEDIATE) {
            f(200, I.copy(ears = Ears.TWITCH_LEFT)); f(250, I); f(200, I.copy(ears = Ears.TWITCH_RIGHT)); f(250, I); f(600, I.copy(ears = Ears.ALERT))
        }
        // Pata → cabeça, coça três vezes, pata desce (8 frames).
        clip(AnimationId.IDLE_SCRATCH, loop = false) {
            f(180, I.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP))
            for (k in 0 until 3) {
                f(160, I.copy(rightArm = Arm.HEAD, eyes = Eyes.CLOSED, ears = Ears.TWITCH_RIGHT))
                f(160, I.copy(rightArm = Arm.HEAD, eyes = Eyes.HAPPY, headDy = 1))
            }
            f(200, I)
        }
        clip(AnimationId.IDLE_PHONE) { phone(this, I) }
        clip(AnimationId.LOOK_AROUND, policy = IMMEDIATE) {
            f(700, S.copy(eyes = Eyes.LOOK_LEFT)); f(300, S); f(700, S.copy(eyes = Eyes.LOOK_RIGHT)); f(300, S.copy(ears = Ears.TWITCH_LEFT)); f(500, S.copy(eyes = Eyes.LOOK_UP))
        }
        clip(AnimationId.YAWN, loop = false) {
            f(200, I.copy(eyes = Eyes.HALF, mouth = Mouth.FLAT, ears = Ears.RELAXED))
            f(500, I.copy(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, bob = -1, headDy = -1, ears = Ears.RELAXED))
            f(400, I.copy(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, ears = Ears.RELAXED))
            f(200, I.copy(eyes = Eyes.HALF, ears = Ears.RELAXED)); f(150, I)
        }
        clip(AnimationId.STRETCH, loop = false) {
            f(160, S.copy(bob = 1)); f(250, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED))
            f(400, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED, bob = -1, lift = 1, stringSwing = 1))
            f(250, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY)); f(200, S.copy(eyes = Eyes.HAPPY, stringSwing = -1))
        }
        clip(AnimationId.STRETCH_SIT, loop = false) {
            f(250, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED))
            f(400, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED, mouth = Mouth.OPEN, bob = -1))
            f(250, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY)); f(200, SIT.copy(eyes = Eyes.HAPPY))
        }
        clip(AnimationId.THINK) { f(800, SIT.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT)); f(700, SIT.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT, bob = 1)) }
        clip(AnimationId.THINK_STAND) { f(800, S.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT)); f(700, S.copy(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT, bob = 1)) }

        // ───── Postura (squash discreto ao sentar/levantar) ─────
        val sitDown: Builder.() -> Unit = {
            f(120, S.copy(bob = 1, stringSwing = 1)); f(120, S.copy(bob = 2, eyes = Eyes.LOOK_DOWN))
            f(140, SIT.copy(bob = -1, stringSwing = 1), AnimationEvent.SIT); f(150, SIT.copy(bob = 1, stringSwing = -1)); f(120, SIT)
        }
        val standUp: Builder.() -> Unit = {
            f(120, SIT.copy(bob = 1)); f(120, SIT.copy(bob = -1, eyes = Eyes.LOOK_UP))
            f(140, S.copy(bob = 2, stringSwing = 1), STAND); f(120, S.copy(bob = -1, stringSwing = -1)); f(120, S)
        }
        clip(AnimationId.SIT_DOWN, loop = false, policy = FINISH_CYCLE, build = sitDown)
        clip(AnimationId.STAND_UP, loop = false, policy = FINISH_CYCLE, build = standUp)
        clip(AnimationId.SIT_TABLE, loop = false, policy = FINISH_CYCLE, build = sitDown)
        clip(AnimationId.STAND_TABLE, loop = false, policy = FINISH_CYCLE, build = standUp)

        // ───── Sono ─────
        clip(AnimationId.BED_SIT, loop = false, policy = FINISH_CYCLE) {
            f(500, SIT.copy(eyes = Eyes.SLEEPY, ears = Ears.RELAXED), BED_ENTER); f(500, SIT.copy(eyes = Eyes.SLEEPY, ears = Ears.RELAXED, bob = 1))
        }
        // Sentado → apoia a pata → inclina o corpo → afunda na cama → só a cabeça no travesseiro.
        clip(AnimationId.BED_LIE_DOWN, loop = false, policy = FINISH_CYCLE) {
            val s = SIT.copy(ears = Ears.RELAXED)
            f(240, s.copy(eyes = Eyes.SLEEPY, leftArm = Arm.FORWARD_DOWN))
            f(220, s.copy(eyes = Eyes.HALF, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, bob = 1))
            f(220, s.copy(eyes = Eyes.HALF, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, bob = 3, headDy = 1))
            f(220, s.copy(eyes = Eyes.CLOSED, bob = 6))
            f(250, HoodiePose(headOnly = true, eyes = Eyes.CLOSED, bob = -1, ears = Ears.RELAXED)); f(300, HoodiePose(headOnly = true, eyes = Eyes.CLOSED, ears = Ears.RELAXED))
        }
        clip(AnimationId.SLEEP, policy = PLAY_EXIT) {
            // Nunca totalmente parado: respiração + uma orelha que mexe no meio do sono.
            val z = HoodiePose(headOnly = true, eyes = Eyes.CLOSED, ears = Ears.RELAXED)
            f(900, z); f(800, z); f(900, z.copy(bob = 1)); f(250, z.copy(bob = 1, ears = Ears.TWITCH_LEFT)); f(650, z.copy(bob = 1)); f(900, z)
        }
        clip(AnimationId.SLEEP_TURN, loop = false, policy = PLAY_EXIT) {
            val z = HoodiePose(headOnly = true, eyes = Eyes.CLOSED, ears = Ears.RELAXED)
            f(300, z.copy(headTilt = 1)); f(250, z.copy(headTilt = 1, ears = Ears.TWITCH_RIGHT)); f(500, z.copy(headTilt = 1, bob = 1)); f(400, z)
        }
        clip(AnimationId.WAKE_EYES, loop = false, policy = FINISH_CYCLE) {
            val h = HoodiePose(headOnly = true)
            f(400, h.copy(eyes = Eyes.HALF, ears = Ears.RELAXED)); f(200, h.copy(eyes = Eyes.CLOSED)); f(300, h.copy(eyes = Eyes.HALF)); f(500, h.copy(eyes = Eyes.OPEN, ears = Ears.TWITCH_LEFT))
        }
        clip(AnimationId.BED_EXIT, loop = false, policy = FINISH_CYCLE) {
            f(200, SIT.copy(eyes = Eyes.HALF)); f(150, SIT.copy(bob = -1)); f(160, S.copy(bob = 2, eyes = Eyes.HALF), BED_EXIT, STAND); f(150, S.copy(bob = -1, stringSwing = 1)); f(120, S)
        }
        clip(AnimationId.WAKE_UP, loop = false) {
            f(300, S.copy(eyes = Eyes.SLEEPY)); f(250, S.copy(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, leftArm = Arm.UP, rightArm = Arm.UP))
            f(400, S.copy(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, leftArm = Arm.UP, rightArm = Arm.UP, bob = -1, lift = 1)); f(250, S.copy(eyes = Eyes.HAPPY)); f(200, S)
        }
        clip(AnimationId.NAP_SIT) { f(800, SIT.copy(eyes = Eyes.CLOSED, ears = Ears.RELAXED)); f(800, SIT.copy(eyes = Eyes.CLOSED, ears = Ears.RELAXED, bob = 1)) }

        // ───── Trabalho ─────
        clip(AnimationId.WORK_TYPING, policy = FINISH_CYCLE) {
            f(140, SIT.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN))
            f(130, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP))
            f(140, SIT.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN))
            f(160, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, headDy = 1))
            f(130, SIT.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN))
            f(260, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN))
        }
        clip(AnimationId.STOP_TYPING, loop = false) {
            f(150, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN)); f(150, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.DOWN, bob = 1))
        }
        clip(AnimationId.REACH_MOUSE, loop = false) {
            f(150, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.DOWN, eyes = Eyes.LOOK_RIGHT)); f(170, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, eyes = Eyes.LOOK_RIGHT))
        }
        clip(AnimationId.WORK_MOUSE, policy = FINISH_FRAME) {
            f(400, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT))
            f(250, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, eyes = Eyes.LOOK_RIGHT))
            f(600, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT))
        }
        clip(AnimationId.WORK_READ, policy = IMMEDIATE) {
            f(900, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT)); f(800, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT, bob = 1))
        }
        clip(AnimationId.WORK_NOTES, policy = FINISH_CYCLE) {
            val n = SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, item = Item.FORK, eyes = Eyes.LOOK_DOWN)
            f(220, n); f(180, n.copy(rightArm = Arm.FORWARD_UP)); f(220, n); f(400, n.copy(eyes = Eyes.LOOK_UP, rightArm = Arm.CHIN))
        }
        clip(AnimationId.WORK_TIRED, policy = FINISH_FRAME) {
            val t = SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.SLEEPY, ears = Ears.DOWN, mouth = Mouth.FLAT)
            // A cabeça cai e volta em degraus de até 2 px (sem "pulo" na volta do loop).
            f(700, t); f(700, t.copy(bob = 1, headDy = 1)); f(500, t.copy(eyes = Eyes.CLOSED, bob = 2, headDy = 1)); f(300, t.copy(bob = 1))
        }

        // ───── Café como ação completa ─────
        clip(AnimationId.REACH_MUG, loop = false) {
            f(160, I.copy(eyes = Eyes.LOOK_DOWN)); f(160, I.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_DOWN))
            f(200, I.copy(rightArm = Arm.HOLD_CHEST, item = Item.MUG), MUG_PICKUP)
        }
        clip(AnimationId.DRINK, policy = FINISH_CYCLE) { drink(this, I) }
        clip(AnimationId.PUT_MUG, loop = false) {
            f(200, I.copy(rightArm = Arm.FORWARD_DOWN, item = Item.MUG, eyes = Eyes.LOOK_DOWN))
            f(160, I.copy(rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_DOWN), MUG_PUT); f(150, I)
        }
        clip(AnimationId.COFFEE_SIT, policy = FINISH_CYCLE) { drink(this, SIT) }
        clip(AnimationId.COFFEE_STAND, policy = FINISH_CYCLE) { drink(this, S) }
        clip(AnimationId.COFFEE_TIRED, policy = FINISH_CYCLE) { drink(this, I.copy(bob = 1), eyes = Eyes.SLEEPY, ears = Ears.DOWN, slow = 250) }
        clip(AnimationId.COFFEE_HAPPY, policy = FINISH_CYCLE) { drink(this, I.copy(blush = true), eyes = Eyes.HAPPY, ears = Ears.ALERT) }

        // ───── Celular ─────
        clip(AnimationId.PHONE_TAKE, loop = false) {
            f(150, I.copy(eyes = Eyes.LOOK_DOWN)); f(150, I.copy(rightArm = Arm.FORWARD_DOWN)); f(200, I.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN), PHONE_PICK)
        }
        clip(AnimationId.PHONE_READ, policy = IMMEDIATE) { phone(this, I) }
        clip(AnimationId.PHONE_SCROLL, policy = FINISH_FRAME) {
            val p = I.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN)
            f(250, p.copy(leftArm = Arm.FORWARD_UP)); f(250, p.copy(leftArm = Arm.HOLD_CHEST)); f(500, p.copy(leftArm = Arm.FORWARD_UP)); f(300, p.copy(leftArm = Arm.HOLD_CHEST, bob = 1))
        }
        clip(AnimationId.PHONE_TYPE, policy = FINISH_FRAME) {
            val p = I.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN, mouth = Mouth.FLAT)
            f(120, p.copy(leftArm = Arm.FORWARD_UP)); f(120, p.copy(leftArm = Arm.HOLD_CHEST)); f(120, p.copy(leftArm = Arm.FORWARD_UP)); f(300, p.copy(leftArm = Arm.HOLD_CHEST))
        }
        val react: (Eyes, Mouth, Ears) -> Builder.() -> Unit = { eyes, mouth, ears ->
            {
                val p = I.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN)
                f(200, p); f(700, p.copy(eyes = eyes, mouth = mouth, ears = ears, lift = if (eyes == Eyes.WIDE) 1 else 0)); f(300, p)
            }
        }
        clip(AnimationId.PHONE_REACT_SMILE, loop = false, build = react(Eyes.HAPPY, Mouth.SMILE, Ears.ALERT))
        clip(AnimationId.PHONE_REACT_MEH, loop = false, build = react(Eyes.HALF, Mouth.FLAT, Ears.RELAXED))
        clip(AnimationId.PHONE_REACT_WOW, loop = false, build = react(Eyes.WIDE, Mouth.OPEN, Ears.ALERT))
        clip(AnimationId.PHONE_PUT, loop = false) {
            f(150, I.copy(rightArm = Arm.HOLD_CHEST, item = Item.PHONE)); f(150, I.copy(rightArm = Arm.FORWARD_DOWN), PHONE_PUT); f(150, I)
        }
        clip(AnimationId.PHONE_SIT, policy = IMMEDIATE) { phone(this, SIT) }
        clip(AnimationId.PHONE_STAND, policy = IMMEDIATE) { phone(this, S) }

        // ───── Comida ─────
        clip(AnimationId.LOOK_MENU, loop = false) {
            val m = SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.MENU, eyes = Eyes.LOOK_DOWN)
            f(700, m); f(500, m.copy(eyes = Eyes.LOOK_LEFT)); f(600, m); f(500, m.copy(eyes = Eyes.LOOK_RIGHT, ears = Ears.ALERT)); f(300, SIT)
        }
        clip(AnimationId.WAIT_FOOD, loop = false) {
            f(600, SIT.copy(eyes = Eyes.LOOK_LEFT)); f(400, SIT); f(600, SIT.copy(eyes = Eyes.LOOK_RIGHT))
            f(500, SIT.copy(eyes = Eyes.HAPPY, ears = Ears.ALERT, lift = 1), FOOD_SERVED); f(300, SIT)
        }
        clip(AnimationId.EAT, policy = FINISH_CYCLE) {
            f(250, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_CHEST, item = Item.FORK, eyes = Eyes.LOOK_DOWN))
            f(220, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_MOUTH, item = Item.FORK, mouth = Mouth.OPEN))
            f(260, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_CHEST, item = Item.FORK, eyes = Eyes.HAPPY, mouth = Mouth.CHEW))
            f(260, SIT.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_CHEST, item = Item.FORK, eyes = Eyes.HAPPY, mouth = Mouth.SMILE, headDy = 1))
        }
        clip(AnimationId.FINISH_FOOD, loop = false) {
            f(400, SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, eyes = Eyes.HAPPY, blush = true))
            f(300, SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, eyes = Eyes.HAPPY, blush = true, bob = 1))
            f(400, SIT.copy(eyes = Eyes.CLOSED), FOOD_DONE)
        }

        // ───── Videogame ─────
        val pad = SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.CONTROLLER)
        clip(AnimationId.GAMING, policy = IMMEDIATE) {
            f(170, pad.copy(eyes = Eyes.LOOK_RIGHT)); f(170, pad.copy(eyes = Eyes.LOOK_RIGHT, bob = 1)); f(200, pad.copy(eyes = Eyes.FOCUSED)); f(170, pad.copy(eyes = Eyes.LOOK_RIGHT, bob = 1))
        }
        clip(AnimationId.GAME_PRESS, policy = IMMEDIATE) {
            f(90, pad.copy(eyes = Eyes.FOCUSED)); f(90, pad.copy(eyes = Eyes.FOCUSED, bob = 1, leftArm = Arm.FORWARD_UP)); f(90, pad.copy(eyes = Eyes.FOCUSED)); f(90, pad.copy(eyes = Eyes.FOCUSED, bob = 1, rightArm = Arm.FORWARD_UP))
        }
        clip(AnimationId.GAME_FOCUSED, policy = IMMEDIATE) {
            f(500, pad.copy(eyes = Eyes.FOCUSED, ears = Ears.ALERT, headDy = 1)); f(400, pad.copy(eyes = Eyes.FOCUSED, ears = Ears.ALERT, headDy = 2, mouth = Mouth.FLAT))
        }
        clip(AnimationId.GAME_WIN, loop = false, policy = FINISH_CYCLE) {
            f(120, pad.copy(bob = 1, eyes = Eyes.WIDE))
            f(180, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.WIDE, mouth = Mouth.OPEN, lift = 2, ears = Ears.ALERT), SPARKLE)
            f(160, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, lift = 1))
            f(180, SIT.copy(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, lift = 2))
            f(300, pad.copy(eyes = Eyes.HAPPY, blush = true))
        }
        clip(AnimationId.GAME_LOSE, loop = false, policy = FINISH_CYCLE) {
            f(200, pad.copy(eyes = Eyes.WIDE)); f(600, pad.copy(eyes = Eyes.CLOSED, ears = Ears.DOWN, mouth = Mouth.FLAT, bob = 1, headDy = 1))
            f(500, pad.copy(eyes = Eyes.SLEEPY, ears = Ears.DOWN, mouth = Mouth.FLAT)); f(300, pad)
        }

        // ───── Academia ─────
        clip(AnimationId.GYM_WARMUP) {
            f(200, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, lift = 2, stringSwing = 1)); f(200, S.copy(bob = 1, stringSwing = -1), FOOTSTEP)
        }
        clip(AnimationId.LIFT_PICK, loop = false, policy = FINISH_CYCLE) {
            f(250, S.copy(eyes = Eyes.LOOK_DOWN)); f(200, S.copy(bob = 3, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_DOWN))
            f(220, S.copy(bob = 3, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.FOCUSED), ITEM_PICK)
            f(200, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.FOCUSED))
        }
        clip(AnimationId.LIFT, policy = FINISH_CYCLE) {
            f(600, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.FOCUSED, mouth = Mouth.FLAT))
            f(250, S.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.CLOSED, mouth = Mouth.FLAT, bob = 1))
            f(600, S.copy(leftArm = Arm.UP, rightArm = Arm.UP, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.CLOSED, mouth = Mouth.FLAT, bob = -1))
        }
        clip(AnimationId.LIFT_PUT, loop = false, policy = FINISH_CYCLE) {
            f(200, S.copy(bob = 2, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true))
            f(220, S.copy(bob = 3, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN), ITEM_PUT); f(200, S.copy(eyes = Eyes.HAPPY))
        }
        clip(AnimationId.GYM_REST) {
            val r = S.copy(bob = 2, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.CLOSED, mouth = Mouth.OPEN, ears = Ears.RELAXED)
            f(500, r); f(500, r.copy(bob = 3, headDy = 1))
        }
        clip(AnimationId.WATER, policy = FINISH_CYCLE) {
            f(300, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.BOTTLE)); f(350, S.copy(rightArm = Arm.HOLD_MOUTH, item = Item.BOTTLE, eyes = Eyes.CLOSED))
            f(350, S.copy(rightArm = Arm.HOLD_MOUTH, item = Item.BOTTLE, eyes = Eyes.CLOSED, headDy = -1)); f(300, S.copy(rightArm = Arm.HOLD_CHEST, item = Item.BOTTLE, eyes = Eyes.HAPPY))
        }

        // ───── Casa e lugares ─────
        clip(AnimationId.READING) {
            val r = SIT.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.BOOK, eyes = Eyes.LOOK_DOWN)
            f(1000, r); f(900, r.copy(bob = 1)); f(160, r.copy(rightArm = Arm.FORWARD_UP)); f(900, r)
        }
        clip(AnimationId.WATCH_TV, policy = IMMEDIATE) { f(700, SIT.copy(eyes = Eyes.LOOK_RIGHT)); f(700, SIT.copy(eyes = Eyes.LOOK_RIGHT, bob = 1)) }
        clip(AnimationId.COOK) {
            f(250, S.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN, item = Item.PAN, eyes = Eyes.FOCUSED))
            f(250, S.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, item = Item.PAN, eyes = Eyes.FOCUSED, stringSwing = 1))
            f(250, S.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN, item = Item.PAN, eyes = Eyes.HAPPY))
            f(250, S.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, item = Item.PAN, eyes = Eyes.FOCUSED, stringSwing = -1))
        }
        clip(AnimationId.CLEAN) {
            f(250, S.copy(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, item = Item.BROOM, eyes = Eyes.LOOK_DOWN, stringSwing = -1))
            f(250, S.copy(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_UP, item = Item.BROOM, eyes = Eyes.LOOK_DOWN, bob = 1, stringSwing = 1))
        }
        clip(AnimationId.BUS_SIT, policy = IMMEDIATE) {
            val b = SIT.copy(backpack = true, eyes = Eyes.LOOK_LEFT)
            f(500, b); f(500, b.copy(bob = 1, backpackDy = -1)); f(500, b.copy(backpackDy = 1)); f(500, b.copy(eyes = Eyes.OPEN, bob = 1))
        }

        // ───── Reações (herdam a postura atual) ─────
        clip(AnimationId.WAVE, loop = false, policy = FINISH_CYCLE) {
            f(160, I.copy(rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, ears = Ears.ALERT))
            f(160, I.copy(rightArm = Arm.WAVE, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, ears = Ears.ALERT))
            f(160, I.copy(rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN))
            f(160, I.copy(rightArm = Arm.WAVE, eyes = Eyes.HAPPY, mouth = Mouth.OPEN))
            f(200, I.copy(rightArm = Arm.UP, eyes = Eyes.HAPPY)); f(200, I.copy(eyes = Eyes.HAPPY))
        }
        clip(AnimationId.HAPPY, loop = false, policy = FINISH_CYCLE) {
            // Squash & stretch discreto: abaixa 1px, pula 2px, volta.
            f(120, I.copy(bob = 1, eyes = Eyes.HAPPY)); f(140, I.copy(lift = 2, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, stringSwing = 1, ears = Ears.ALERT), SPARKLE)
            f(100, I.copy(lift = 1, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, stringSwing = -1)); f(90, I.copy(bob = 1, eyes = Eyes.HAPPY)); f(250, I.copy(eyes = Eyes.HAPPY))
        }
        clip(AnimationId.SMILE, loop = false, policy = IMMEDIATE) { f(900, I.copy(eyes = Eyes.HAPPY, blush = true)); f(150, I) }
        clip(AnimationId.SURPRISED, loop = false) {
            f(150, I.copy(eyes = Eyes.WIDE, mouth = Mouth.OPEN, lift = 1, ears = Ears.ALERT)); f(350, I.copy(eyes = Eyes.WIDE, mouth = Mouth.OPEN, ears = Ears.ALERT)); f(300, I.copy(eyes = Eyes.WIDE)); f(150, I)
        }
        clip(AnimationId.SHRUG, loop = false) {
            f(250, I.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT))
            f(350, I.copy(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT, bob = -1, ears = Ears.RELAXED)); f(200, I)
        }
        clip(AnimationId.NOTICE, loop = false, policy = IMMEDIATE) {
            f(150, I.copy(ears = Ears.TWITCH_LEFT)); f(250, I.copy(ears = Ears.ALERT, eyes = Eyes.WIDE)); f(500, I.copy(ears = Ears.ALERT))
        }
        clip(AnimationId.GLANCE, loop = false, policy = IMMEDIATE) { f(150, I.copy(ears = Ears.TWITCH_RIGHT)); f(450, I); f(100, I.copy(eyes = Eyes.HALF)) }
        clip(AnimationId.EAR_FLICK, loop = false, policy = IMMEDIATE) {
            f(150, I.copy(ears = Ears.TWITCH_LEFT)); f(100, I); f(150, I.copy(ears = Ears.TWITCH_LEFT)); f(200, I)
        }
        clip(AnimationId.NOD, loop = false) { f(150, I.copy(headDy = 1, eyes = Eyes.HAPPY)); f(150, I); f(150, I.copy(headDy = 1, eyes = Eyes.HAPPY)); f(200, I) }
    }
}
