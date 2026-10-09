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
enum class AnimGroup { LOCOMOTION, IDLE, POSTURE, SLEEP, WORK, COFFEE, PHONE, FOOD, GAME, GYM, HOME, REACTION, STUDY, SHOPPING, STORE, VISIT, LEISURE, TRANSPORT }

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
    BUS_SIT("Sentado no ônibus", AnimGroup.TRANSPORT),
    BUS_SIT_FRONT("Sentado de frente no ônibus", AnimGroup.TRANSPORT),

    // Carro
    CAR_ENTER("Entrar no carro", AnimGroup.TRANSPORT),
    CAR_IDLE("Viajar de carro", AnimGroup.TRANSPORT),
    CAR_LOOK_WINDOW("Olhar a janela do carro", AnimGroup.TRANSPORT),
    CAR_LOOK_FRONT("Olhar para a frente no carro", AnimGroup.TRANSPORT),
    CAR_BUMP("Balançar com o carro", AnimGroup.TRANSPORT),
    CAR_EXIT("Sair do carro", AnimGroup.TRANSPORT),

    // Ônibus
    BUS_ENTER("Entrar no ônibus", AnimGroup.TRANSPORT),
    BUS_LOOK_WINDOW("Olhar a janela do ônibus", AnimGroup.TRANSPORT),
    BUS_PHONE("Olhar o celular no ônibus", AnimGroup.TRANSPORT),
    BUS_BUMP("Balançar no ônibus", AnimGroup.TRANSPORT),
    BUS_STAND("Levantar no ônibus", AnimGroup.TRANSPORT),
    BUS_EXIT("Sair do ônibus", AnimGroup.TRANSPORT),

    // Trem
    TRAIN_ENTER("Entrar no trem", AnimGroup.TRANSPORT),
    TRAIN_SIT("Sentar no trem", AnimGroup.TRANSPORT),
    TRAIN_WINDOW("Olhar a janela do trem", AnimGroup.TRANSPORT),
    TRAIN_PHONE("Olhar o celular no trem", AnimGroup.TRANSPORT),
    TRAIN_STAND("Levantar no trem", AnimGroup.TRANSPORT),
    TRAIN_BRAKE("Reagir à frenagem do trem", AnimGroup.TRANSPORT),
    TRAIN_EXIT("Sair do trem", AnimGroup.TRANSPORT),

    // Metrô
    METRO_ENTER("Entrar no metrô", AnimGroup.TRANSPORT),
    METRO_SIT("Sentar no metrô", AnimGroup.TRANSPORT),
    METRO_STAND("Levantar no metrô", AnimGroup.TRANSPORT),
    METRO_HANDLE("Segurar a barra do metrô", AnimGroup.TRANSPORT),
    METRO_LOOK_WINDOW("Olhar a janela do metrô", AnimGroup.TRANSPORT),
    METRO_PHONE("Olhar o celular no metrô", AnimGroup.TRANSPORT),
    METRO_BRAKE("Reagir à frenagem do metrô", AnimGroup.TRANSPORT),
    METRO_EXIT("Sair do metrô", AnimGroup.TRANSPORT),

    // Bicicleta
    BIKE_START("Começar a pedalar", AnimGroup.TRANSPORT),
    BIKE_PEDAL("Pedalar", AnimGroup.TRANSPORT),
    BIKE_COAST("Deslizar sem pedalar", AnimGroup.TRANSPORT),
    BIKE_LOOK("Olhar durante o pedal", AnimGroup.TRANSPORT),
    BIKE_BRAKE("Frear a bicicleta", AnimGroup.TRANSPORT),
    BIKE_STOP("Parar a bicicleta", AnimGroup.TRANSPORT),

    // Outro veículo pessoal
    OTHER_RIDE_START("Começar outro transporte", AnimGroup.TRANSPORT),
    OTHER_RIDE("Seguir em outro transporte", AnimGroup.TRANSPORT),
    OTHER_RIDE_LOOK("Olhar durante o trajeto", AnimGroup.TRANSPORT),
    OTHER_RIDE_STOP("Parar outro transporte", AnimGroup.TRANSPORT),

    // Transporte coletivo ou veículo ainda não identificado (sem marcação de modal).
    TRANSIT_ENTER("Entrar no transporte", AnimGroup.TRANSPORT),
    TRANSIT_SIT("Viajar sentado no transporte", AnimGroup.TRANSPORT),
    TRANSIT_LOOK_WINDOW("Olhar a janela do transporte", AnimGroup.TRANSPORT),
    TRANSIT_BUMP("Reagir ao movimento do transporte", AnimGroup.TRANSPORT),
    TRANSIT_EXIT("Sair do transporte", AnimGroup.TRANSPORT),

    // Escola
    STUDY_READ("Estudar lendo", AnimGroup.STUDY),
    STUDY_WRITE("Escrever no caderno", AnimGroup.STUDY),
    STUDY_THINK("Pensar no exercício", AnimGroup.STUDY),
    STUDY_PAGE_TURN("Virar a página", AnimGroup.STUDY),

    // Compras
    SHOP_LOOK("Olhar a prateleira", AnimGroup.SHOPPING),
    SHOP_PICK("Pegar um produto", AnimGroup.SHOPPING),
    SHOP_CART("Empurrar o carrinho", AnimGroup.SHOPPING),
    SHOP_PAY("Pagar no caixa", AnimGroup.SHOPPING),

    // Loja (roupas)
    STORE_BROWSE_RACK("Passar os cabides da arara", AnimGroup.STORE),
    STORE_HOLD_GARMENT("Provar a roupa no espelho", AnimGroup.STORE),
    STORE_FITTING_ROOM("Entrar no provador", AnimGroup.STORE),
    STORE_BAG_EXIT("Sair com a sacola", AnimGroup.STORE),

    // Família
    VISIT_CHAT("Conversar", AnimGroup.VISIT),
    VISIT_LISTEN("Escutar", AnimGroup.VISIT),
    VISIT_LAUGH("Rir junto", AnimGroup.VISIT),
    VISIT_SNACK("Beliscar um petisco", AnimGroup.VISIT),

    // Passeio
    LEISURE_WALK("Passear devagar", AnimGroup.LEISURE),
    LEISURE_BENCH("Sentar no banco", AnimGroup.LEISURE),
    LEISURE_PHOTO("Tirar foto", AnimGroup.LEISURE),
    LEISURE_LOOK("Admirar a paisagem", AnimGroup.LEISURE),

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
