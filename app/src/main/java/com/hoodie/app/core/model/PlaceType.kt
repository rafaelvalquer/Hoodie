package com.hoodie.app.core.model

enum class PlaceType(val emoji: String, val label: String) {
    HOME("🏠", "Casa"),
    WORK("🏢", "Trabalho"),
    GYM("🏋", "Academia"),
    SCHOOL("🎓", "Escola"),
    RESTAURANT("🍽", "Restaurante"),
    MARKET("🛒", "Compras"),
    FAMILY("👪", "Família"),
    LEISURE("🎉", "Passeio"),
    OTHER("📍", "Outro");

    /** Contexto natural de um lugar conhecido. */
    fun toContext(): UserContextType = when (this) {
        HOME -> UserContextType.HOME
        WORK -> UserContextType.WORK
        GYM -> UserContextType.GYM
        SCHOOL -> UserContextType.STUDY
        RESTAURANT -> UserContextType.LUNCH
        MARKET -> UserContextType.SHOPPING
        FAMILY -> UserContextType.VISITING
        LEISURE -> UserContextType.LEISURE
        OTHER -> UserContextType.LEISURE
    }

    companion object {
        /** Opções para "Parece que você está em um lugar novo. O que é?" */
        val newPlaceOptions = listOf(RESTAURANT, GYM, FAMILY, MARKET, LEISURE, OTHER)
    }
}
