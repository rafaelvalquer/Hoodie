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
        /** Catálogo físico canônico compartilhado por "O que estou fazendo?" e "Novo lugar". */
        val physicalPlaceOptions: List<PlaceType> = entries.toList()

        /** Compatibilidade semântica para as perguntas de descoberta de um lugar novo. */
        val newPlaceOptions: List<PlaceType> get() = physicalPlaceOptions
    }
}
