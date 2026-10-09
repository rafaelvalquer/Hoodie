package com.hoodie.app.pixel.sprite

import com.hoodie.app.pixel.animation.AnimationId

/**
 * Âncoras que a arte final PRECISA desenhar (marcador na camada `anchors` ou slice).
 * Sem elas o item cai num ponto padrão e "flutua" longe da mão. O runtime tolera
 * (o clip ainda carrega), mas o ShippedSheetsValidationTest reprova.
 */
object RequiredAnchors {

    private val HAND = setOf(Anchor.RIGHT_HAND)
    private val BOTH_HANDS = setOf(Anchor.RIGHT_HAND, Anchor.LEFT_HAND)

    val requirements: Map<AnimationId, Set<Anchor>> = buildMap {
        // Todo clip precisa dos pés: é o ponto alinhado ao chão.
        AnimationId.entries.forEach { put(it, setOf(Anchor.FEET)) }
        // Itens na mão.
        listOf(
            AnimationId.REACH_MUG, AnimationId.DRINK, AnimationId.PUT_MUG,
            AnimationId.COFFEE_SIT, AnimationId.COFFEE_STAND, AnimationId.COFFEE_TIRED, AnimationId.COFFEE_HAPPY,
            AnimationId.PHONE_TAKE, AnimationId.PHONE_READ, AnimationId.PHONE_SCROLL, AnimationId.PHONE_TYPE,
            AnimationId.PHONE_REACT_SMILE, AnimationId.PHONE_REACT_MEH, AnimationId.PHONE_REACT_WOW, AnimationId.PHONE_PUT,
            AnimationId.PHONE_SIT, AnimationId.PHONE_STAND, AnimationId.IDLE_PHONE,
            AnimationId.EAT, AnimationId.WATER, AnimationId.COOK, AnimationId.CLEAN, AnimationId.WORK_NOTES,
            AnimationId.REACH_MOUSE, AnimationId.WORK_MOUSE,
            // Lápis, produto, celular no caixa, petisco e celular-câmera.
            AnimationId.STUDY_WRITE, AnimationId.SHOP_PICK, AnimationId.SHOP_PAY, AnimationId.VISIT_SNACK, AnimationId.LEISURE_PHOTO,
            // Loja: cabide na mão e sacola.
            AnimationId.STORE_BROWSE_RACK, AnimationId.STORE_HOLD_GARMENT, AnimationId.STORE_FITTING_ROOM, AnimationId.STORE_BAG_EXIT,
        ).forEach { put(it, setOf(Anchor.FEET) + HAND) }
        // Halteres nas duas mãos.
        listOf(AnimationId.LIFT_PICK, AnimationId.LIFT, AnimationId.LIFT_PUT).forEach { put(it, setOf(Anchor.FEET) + BOTH_HANDS) }
        // Mochila nas costas.
        put(AnimationId.WALK_BACKPACK, setOf(Anchor.FEET, Anchor.BACK))
        put(AnimationId.BUS_SIT, setOf(Anchor.FEET, Anchor.BACK))
        put(AnimationId.BUS_SIT_FRONT, setOf(Anchor.SEAT_HIP, Anchor.BACK))
        // Efeitos na cabeça (Zzz, suor, brilho).
        listOf(AnimationId.SLEEP, AnimationId.SLEEP_TURN, AnimationId.RUN, AnimationId.GYM_REST).forEach {
            put(it, setOf(Anchor.FEET, Anchor.HEAD))
        }
    }

    fun required(animation: AnimationId): Set<Anchor> = requirements[animation] ?: setOf(Anchor.FEET)

    /** Âncoras obrigatórias que não foram desenhadas em algum frame. */
    fun missing(animation: AnimationId, frames: List<SheetFrame>): Set<Anchor> =
        required(animation).filter { a -> frames.any { a !in it.explicitAnchors } }.toSet()
}
