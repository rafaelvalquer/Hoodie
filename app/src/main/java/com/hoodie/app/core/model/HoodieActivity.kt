package com.hoodie.app.core.model

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
    WALKING("🌳", "passeando", "saiu para passear"),
    WATCHING_TV("📺", "vendo TV", "ligou a TV"),
    PHONE("📱", "no celular", "pegou o celular"),
    CLEANING("🧹", "arrumando a casa", "arrumou a casa"),
    IDLE("🐱", "de bobeira", "ficou de bobeira");

    /** Atividades que gastam energia por muito tempo (não iniciar com energia muito baixa). */
    val isLong: Boolean
        get() = this in setOf(WORKING, TRAINING, GAMING, WALKING, CLEANING, COOKING)
}
