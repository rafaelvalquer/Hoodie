package com.hoodie.app.domain.notification

/** Quanto o Hoodie pode interromper. O interruptor geral continua mandando: o modo nunca o reativa. */
enum class NotificationMode(val label: String, val description: String) {
    SILENT("Silencioso", "Sem notificações novas; as pendências ficam dentro do app."),
    BALANCED("Equilibrado", "Só o importante, com limite por dia e respeito ao seu contexto."),
    PARTICIPATIVE("Participativo", "Mais interações, ainda com limites contra excesso."),
    ;

    companion object {
        fun parse(value: String?): NotificationMode = entries.firstOrNull { it.name == value } ?: BALANCED
    }
}