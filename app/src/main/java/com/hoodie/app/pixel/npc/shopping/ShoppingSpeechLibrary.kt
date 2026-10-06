package com.hoodie.app.pixel.npc.shopping

import com.hoodie.app.core.time.DayPeriod

object ShoppingSpeechLibrary {
    fun lines(intent: ShoppingNpcIntent, period: DayPeriod): List<String> = when (intent) {
        ShoppingNpcIntent.LOOK_PRODUCT -> listOf("Parece bom.", "Será que preciso?")
        ShoppingNpcIntent.COMPARE_PRODUCTS -> listOf("Qual dos dois?", "Esse tá caro.")
        ShoppingNpcIntent.LOOK_PROMOTION -> listOf("Tá na promoção.", "Boa.")
        ShoppingNpcIntent.CHECK_LIST -> listOf("Falta alguma coisa...", "Esqueci a lista.")
        ShoppingNpcIntent.CHECK_PHONE -> listOf("Deixa eu conferir.", "Será que era esse?")
        ShoppingNpcIntent.WAIT_CHECKOUT -> listOf("Tem fila hoje.", "Já vou.")
        ShoppingNpcIntent.PAY -> listOf("Pronto.", "Era só isso.")
        ShoppingNpcIntent.IDLE -> if (period == DayPeriod.EVENING || period == DayPeriod.NIGHT) listOf("Quero ir pra casa.") else emptyList()
        else -> emptyList()
    }
}
