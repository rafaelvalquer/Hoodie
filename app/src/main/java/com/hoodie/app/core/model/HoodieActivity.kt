package com.hoodie.app.core.model

/**
 * O que o Hoodie está fazendo. Novas atividades entram SEMPRE no fim: o ordinal
 * participa da seed da simulação determinística e da ordem estável do sorteio;
 * reordenar mudaria a vida já reconstruída do gato.
 */
enum class HoodieActivity(val emoji: String, val label: String, val pastTense: String) {
    SLEEPING("🛏", "dormindo", "foi dormir"),
    WAKING_UP("🥱", "acordando", "acordou"),
    BREAKFAST("🥐", "tomando café da manhã", "tomou café da manhã"),
    WORKING("💻", "trabalhando", "começou a trabalhar"),
    COMMUTING("🎒", "a caminho", "saiu"),
    EATING("🍜", "comendo", "comeu"),
    COFFEE("☕", "tomando café", "tomou café"),
    RESTING("😌", "descansando", "descansou"),
    GAMING("🎮", "jogando", "foi jogar"),
    READING("📖", "lendo", "foi ler"),
    TRAINING("🏃", "treinando", "começou a treinar"),
    COOKING("🍳", "cozinhando", "foi cozinhar"),
    WALKING("🚶", "caminhando", "saiu para caminhar"),
    WATCHING_TV("📺", "vendo TV", "ligou a TV"),
    PHONE("📱", "no celular", "pegou o celular"),
    CLEANING("🧹", "arrumando a casa", "arrumou a casa"),
    IDLE("🐱", "de bobeira", "ficou de bobeira"),

    // Atividades próprias dos lugares (Escola, Compras, Família, Passeio).
    STUDYING("📚", "estudando", "começou a estudar"),
    SHOPPING("🛒", "fazendo compras", "foi às compras"),
    SOCIALIZING("👪", "socializando", "foi visitar a família"),
    SIGHTSEEING("🌳", "aproveitando o passeio", "saiu para passear");

    /** Atividades que gastam energia por muito tempo (não iniciar com energia muito baixa). */
    val isLong: Boolean
        get() = this in LONG

    private companion object {
        val LONG = setOf(WORKING, TRAINING, GAMING, WALKING, CLEANING, COOKING, STUDYING, SHOPPING, SIGHTSEEING)
    }
}
