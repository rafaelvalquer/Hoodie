package com.hoodie.app.pixel.npc.restaurant

/** Falas curtas, sem repetição imediata e coerentes com a refeição e o relógio do ambiente. */
object RestaurantSpeechLibrary {
    fun line(mealState: RestaurantMealState, clockMinute: Int, seed: Int, decisionIndex: Long): String {
        val minute = Math.floorMod(clockMinute, 1_440)
        val timeChoices = when {
            minute >= 22 * 60 || minute < 5 * 60 -> listOf("ÚLTIMA REFEIÇÃO DO DIA.", "TÁ QUIETO AGORA.")
            minute in 11 * 60..14 * 60 -> listOf("HORA DO ALMOÇO.", "TÁ CHEIO HOJE.")
            minute >= 17 * 60 -> listOf("BOA NOITE.", "HOJE EU MEREÇO.")
            else -> emptyList()
        }
        val mealChoices = when (mealState) {
            RestaurantMealState.WAITING -> listOf("VOU PEDIR O DE SEMPRE.", "DEMOROU HOJE.", "ESSE PARECE BOM.")
            RestaurantMealState.SERVED -> listOf("FINALMENTE CHEGOU.", "CHEIRO BOM.")
            RestaurantMealState.EATING -> listOf("CHEIRO BOM.", "TÁ MUITO BOM.", "MAIS UM POUCO.")
            RestaurantMealState.FINISHED -> listOf("VALEU A PENA.", "FINALMENTE CHEGOU.")
        }
        val choices = mealChoices + timeChoices
        val choice = ((seed.toLong() * 31 + decisionIndex).mod(choices.size.toLong())).toInt()
        return choices[choice]
    }
}
