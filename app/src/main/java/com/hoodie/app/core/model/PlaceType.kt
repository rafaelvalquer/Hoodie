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
    OTHER("📍", "Outro"),

    // Lugares com cena própria. Sempre no fim: o nome é persistido e a ordem histórica não muda.
    BAKERY("🥐", "Padaria ou cafeteria"),
    MALL("🛍", "Shopping"),
    BEACH("🏖", "Praia"),
    CINEMA("🎬", "Cinema"),
    PHARMACY("➕", "Farmácia"),
    HOTEL("🏨", "Hotel");

    /** Contexto natural de um lugar conhecido. */
    fun toContext(): UserContextType = when (this) {
        HOME -> UserContextType.HOME
        WORK -> UserContextType.WORK
        GYM -> UserContextType.GYM
        SCHOOL -> UserContextType.STUDY
        RESTAURANT -> UserContextType.DINING
        MARKET -> UserContextType.SHOPPING
        STORE -> UserContextType.SHOPPING
        FAMILY -> UserContextType.VISITING
        LEISURE -> UserContextType.LEISURE
        // "Outro" pode ser médico, igreja, mecânico, pet shop… — não é passeio por padrão.
        OTHER -> UserContextType.UNKNOWN
        BAKERY -> UserContextType.DINING
        MALL -> UserContextType.SHOPPING
        BEACH -> UserContextType.LEISURE
        CINEMA -> UserContextType.LEISURE
        PHARMACY -> UserContextType.SHOPPING
        HOTEL -> UserContextType.TRAVEL
    }

    /**
     * Contextos em que o tipo deste lugar escolhe uma cena própria (e por isso fica guardado no
     * evento de contexto). Uma padaria também serve o almoço; um shopping também é passeio.
     * Lugares antigos (exceto Mercado/Loja, que já dividiam o contexto de compras) não guardam.
     */
    val venueContexts: Set<UserContextType>
        get() = when (this) {
            MARKET, STORE, PHARMACY -> setOf(UserContextType.SHOPPING)
            BAKERY -> setOf(UserContextType.DINING, UserContextType.LUNCH)
            MALL -> setOf(UserContextType.SHOPPING, UserContextType.LEISURE)
            BEACH, CINEMA -> setOf(UserContextType.LEISURE)
            HOTEL -> setOf(UserContextType.TRAVEL)
            else -> emptySet()
        }

    companion object {
        /**
         * Catálogo físico canônico compartilhado por "O que estou fazendo?" e "Novo lugar".
         * "Outro" fica por último na lista, mesmo com os novos tipos depois dele no enum.
         */
        val physicalPlaceOptions: List<PlaceType> = entries.filter { it != OTHER } + OTHER

        /** Tipo do lugar que vale guardar no evento de [context] (null = este contexto não usa o tipo). */
        fun venueFor(context: UserContextType, venue: PlaceType?): PlaceType? =
            venue?.takeIf { context in it.venueContexts || (context == UserContextType.SHOPPING) }

        /** Compatibilidade semântica para as perguntas de descoberta de um lugar novo. */
        val newPlaceOptions: List<PlaceType> get() = physicalPlaceOptions
    }
}
