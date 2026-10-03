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
    UNKNOWN("📍", "Lugar desconhecido");

    companion object {
        /** Opções exibidas no modo manual "O que estou fazendo?". */
        val manualOptions = listOf(
            HOME, WORK, LUNCH, GYM, STUDY, SHOPPING,
            COMMUTING, LEISURE, VISITING, TRAVEL, UNKNOWN,
        )
    }
}
