package com.hoodie.app.core.deviceusage

/**
 * Evento bruto de uso, já traduzido dos códigos do UsageStatsManager. Só
 * "qual app" + "o que aconteceu" + "quando": nada de conteúdo, texto ou mídia.
 */
data class RawUsageEvent(
    val type: RawUsageEventType,
    val timestamp: Long,
    val packageName: String? = null,
)

enum class RawUsageEventType {
    APP_FOREGROUND,
    APP_BACKGROUND,
    SCREEN_INTERACTIVE,
    SCREEN_NON_INTERACTIVE,
    KEYGUARD_SHOWN,
    KEYGUARD_HIDDEN,
    DEVICE_STARTUP,
    DEVICE_SHUTDOWN,
}

/** Fonte dos eventos (fake nos testes). */
interface UsageStatsSource {
    /** Eventos em [from, to), ordenados por tempo. Lista vazia sem permissão. */
    suspend fun events(from: Long, to: Long): List<RawUsageEvent>
}
