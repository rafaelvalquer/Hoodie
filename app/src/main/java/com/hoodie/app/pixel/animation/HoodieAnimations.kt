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
    operator fun get(id: AnimationId): AnimationClip = AnimationRegistry.clips.getValue(id)
    val all: Map<AnimationId, AnimationClip> get() = AnimationRegistry.clips
}
