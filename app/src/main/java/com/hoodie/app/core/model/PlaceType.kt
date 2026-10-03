package com.hoodie.app.core.model

enum class PlaceType(val emoji: String, val label: String) {
    HOME("🏠", "Casa"),
    WORK("🏢", "Trabalho"),
    GYM("🏋", "Academia"),
    SCHOOL("🎓", "Escola"),
    RESTAURANT("🍽", "Restaurante"),
    MARKET("🛒", "Mercado"),
    STORE("🏬", "Loja"),
    FAMILY("👪", "Casa de amigos ou familiares"),
    LEISURE("🎉", "Lazer"),
    OTHER("📍", "Outro");

    /** Contexto natural de um lugar conhecido. */
    fun toContext(): UserContextType = when (this) {
        HOME -> UserContextType.HOME
        WORK -> UserContextType.WORK
        GYM -> UserContextType.GYM
        SCHOOL -> UserContextType.STUDY
        RESTAURANT -> UserContextType.LUNCH
        MARKET -> UserContextType.SHOPPING
        STORE -> UserContextType.SHOPPING
        FAMILY -> UserContextType.VISITING
        LEISURE -> UserContextType.LEISURE
        // "Outro" pode ser médico, igreja, mecânico, pet shop… — não é passeio por padrão.
        OTHER -> UserContextType.UNKNOWN
    }

    companion object {
        /** Opções para "Parece que você está em um lugar novo. O que é?" */
        val newPlaceOptions = entries.toList()
    }
}
