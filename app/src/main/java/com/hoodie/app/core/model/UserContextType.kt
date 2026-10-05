package com.hoodie.app.core.model

/** Onde/como o usuário provavelmente está. Separado do que o Hoodie faz. */
enum class UserContextType(val emoji: String, val label: String) {
    HOME("🏠", "Casa"),
    WORK("🏢", "Trabalho"),
    COMMUTING("🚌", "Deslocamento"),
    LUNCH("🍜", "Almoço"),
    GYM("🏋", "Academia"),
    STUDY("📚", "Estudo"),
    SHOPPING("🛒", "Compras"),
    LEISURE("🎉", "Passeio"),
    VISITING("👪", "Visita"),
    TRAVEL("🧳", "Viagem"),
    UNKNOWN("📍", "Lugar desconhecido"),
    /** Presença conhecida em restaurante; diferente do almoço inferido pela rotina. */
    DINING("🍽", "Restaurante");

    companion object {
        /** Opções físicas; atividade/transporte têm fluxos próprios. */
        val manualOptions = listOf(HOME, WORK, STUDY, SHOPPING, GYM, LEISURE, VISITING, LUNCH, DINING, UNKNOWN)
    }
}
