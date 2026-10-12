package com.hoodie.app.pixel.npc.shopping

import com.hoodie.app.core.time.DayPeriod

/**
 * Loja de roupas ([com.hoodie.app.pixel.scene.StoreScene]): o mesmo comprador do
 * mercado, com os pontos dele reposicionados — corredor A/B = araras de camisetas e
 * de vestidos, "promoção" = etiqueta de liquidação ao lado do provador, "carrinho" =
 * espelho de chão, fila e balcão = caixa. O caminho passa pelo piso livre à frente
 * das araras (baseline 214), nunca por dentro delas.
 */
object StoreNavigationGraph : GraphFloorPlan(
    spots = mapOf(
        ShoppingNpcSpot.OFFSCREEN to ShoppingNpcPoint(-26, 160),
        ShoppingNpcSpot.DOOR to ShoppingNpcPoint(26, 160),
        ShoppingNpcSpot.AISLE_A_START to ShoppingNpcPoint(52, 226),
        ShoppingNpcSpot.AISLE_A_MIDDLE to ShoppingNpcPoint(80, 226),
        ShoppingNpcSpot.AISLE_A_END to ShoppingNpcPoint(106, 226),
        ShoppingNpcSpot.CENTER to ShoppingNpcPoint(116, 244),
        ShoppingNpcSpot.AISLE_B_START to ShoppingNpcPoint(126, 226, facesRight = false),
        ShoppingNpcSpot.AISLE_B_MIDDLE to ShoppingNpcPoint(150, 226, facesRight = false),
        ShoppingNpcSpot.AISLE_B_END to ShoppingNpcPoint(176, 226, facesRight = false),
        ShoppingNpcSpot.PROMOTION_SIGN to ShoppingNpcPoint(190, 222),
        ShoppingNpcSpot.CART_AREA to ShoppingNpcPoint(54, 280, facesRight = false),
        ShoppingNpcSpot.CHECKOUT_QUEUE to ShoppingNpcPoint(126, 282),
        ShoppingNpcSpot.CHECKOUT_COUNTER to ShoppingNpcPoint(158, 290),
    ),
    edges = mapOf(
        ShoppingNpcSpot.OFFSCREEN to listOf(ShoppingNpcSpot.DOOR),
        ShoppingNpcSpot.DOOR to listOf(ShoppingNpcSpot.OFFSCREEN, ShoppingNpcSpot.AISLE_A_START),
        ShoppingNpcSpot.AISLE_A_START to listOf(ShoppingNpcSpot.DOOR, ShoppingNpcSpot.AISLE_A_MIDDLE),
        ShoppingNpcSpot.AISLE_A_MIDDLE to listOf(ShoppingNpcSpot.AISLE_A_START, ShoppingNpcSpot.AISLE_A_END),
        ShoppingNpcSpot.AISLE_A_END to listOf(ShoppingNpcSpot.AISLE_A_MIDDLE, ShoppingNpcSpot.CENTER),
        ShoppingNpcSpot.CENTER to listOf(ShoppingNpcSpot.AISLE_A_END, ShoppingNpcSpot.AISLE_B_START, ShoppingNpcSpot.CART_AREA),
        ShoppingNpcSpot.AISLE_B_START to listOf(ShoppingNpcSpot.CENTER, ShoppingNpcSpot.AISLE_B_MIDDLE),
        ShoppingNpcSpot.AISLE_B_MIDDLE to listOf(ShoppingNpcSpot.AISLE_B_START, ShoppingNpcSpot.AISLE_B_END),
        ShoppingNpcSpot.AISLE_B_END to listOf(ShoppingNpcSpot.AISLE_B_MIDDLE, ShoppingNpcSpot.PROMOTION_SIGN),
        ShoppingNpcSpot.PROMOTION_SIGN to listOf(ShoppingNpcSpot.AISLE_B_END),
        ShoppingNpcSpot.CART_AREA to listOf(ShoppingNpcSpot.CENTER, ShoppingNpcSpot.CHECKOUT_QUEUE),
        ShoppingNpcSpot.CHECKOUT_QUEUE to listOf(ShoppingNpcSpot.CART_AREA, ShoppingNpcSpot.CHECKOUT_COUNTER),
        ShoppingNpcSpot.CHECKOUT_COUNTER to listOf(ShoppingNpcSpot.CHECKOUT_QUEUE),
    ),
) {
    /** Linha de profundidade do comprador na loja: à frente das araras. */
    const val BASELINE = 226
}

/** Falas do comprador na loja de roupas (mesmas intenções do mercado, outro assunto). */
object StoreSpeechLibrary : ShoppingSpeech {
    override fun lines(intent: ShoppingNpcIntent, period: DayPeriod): List<String> = when (intent) {
        ShoppingNpcIntent.LOOK_PRODUCT -> listOf("Será que tem no meu tamanho?", "Que cor bonita.")
        ShoppingNpcIntent.COMPARE_PRODUCTS -> listOf("Azul ou vermelho?", "Esse caimento...")
        ShoppingNpcIntent.LOOK_PROMOTION -> listOf("Tá na liquidação!", "Metade do preço.")
        ShoppingNpcIntent.CHECK_LIST -> listOf("Precisava de um casaco.", "Só vim olhar...")
        ShoppingNpcIntent.CHECK_PHONE -> listOf("Vou mandar foto.", "Combina com aquela calça?")
        ShoppingNpcIntent.WAIT_CHECKOUT -> listOf("Vou levar.", "Já volto pro provador... não.")
        ShoppingNpcIntent.PAY -> listOf("Pode embrulhar.", "Obrigado!")
        ShoppingNpcIntent.IDLE -> if (period == DayPeriod.EVENING || period == DayPeriod.NIGHT) listOf("A loja já vai fechar.") else emptyList()
        else -> emptyList()
    }
}
