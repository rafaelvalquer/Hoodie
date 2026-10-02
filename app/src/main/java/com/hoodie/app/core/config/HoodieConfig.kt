package com.hoodie.app.core.config

import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS

/**
 * Todos os números de comportamento do Hoodie num lugar só. Regras de negócio
 * referenciam estas constantes em vez de repetir valores soltos.
 */
object HoodieConfig {
    // ── Geofence ──
    /** Raio padrão: 100–200 m absorve a imprecisão do GPS sem pegar o quarteirão inteiro. */
    const val DEFAULT_GEOFENCE_RADIUS_M = 150f
    /** Limite do sistema é 100 por app; deixamos margem. */
    const val MAX_ACTIVE_GEOFENCES = 95
    const val GEOFENCE_LOITERING_MS = (5 * MINUTE_MS).toInt()

    // ── Context Engine ──
    const val GPS_FLAP_MINUTES = 5
    /** Saída + reentrada no mesmo lugar dentro desta janela = oscilação de GPS. */
    const val GPS_FLAP_MS = GPS_FLAP_MINUTES * MINUTE_MS
    /** Sem localização, o modo manual vale por este tempo antes de voltar à rotina provável. */
    const val MANUAL_HOLD_MS = 4 * HOUR_MS
    /** Janela de confirmações usada pelo score. */
    const val CONFIRMATION_LOOKBACK_MS = 30 * DAY_MS

    // ── Perguntas ──
    const val MAX_QUESTIONS_PER_DAY = 4
    const val QUESTION_COOLDOWN_MINUTES = 60

    // ── Checagens agendadas ──
    const val LUNCH_CHECK_DELAY_MIN = 15L
    const val COMMUTE_CHECK_DELAY_MIN = 40L
    const val RECONCILE_INTERVAL_MIN = 15L

    // ── Simulação ──
    /** Acima disso não vale simular minuto a minuto: o gato "recomeça" o dia. */
    const val MAX_CATCH_UP_MS = 3 * DAY_MS

    // ── Retenção ──
    const val LOCATION_EVENT_RETENTION_MS = 30 * DAY_MS

    // ── Phone Insights (Diário Digital) ──
    /** Lê eventos um pouco antes da meia-noite para saber o que já estava aberto às 00:00. */
    const val USAGE_LOOKBACK_MS = 2 * HOUR_MS
    /** Pausa/retomada do mesmo app dentro disso é a mesma sessão (troca de tela interna). */
    const val APP_SESSION_MERGE_GAP_MS = 5_000L
    /** Abaixo disso o app só "piscou" na tela: soma tempo, mas não conta como sessão. */
    const val MIN_APP_SESSION_MS = 2_000L
    /** Sem eventos de tela (API < 28): uso de apps separado por menos que isso é uma sessão de tela. */
    const val SCREEN_SESSION_FALLBACK_GAP_MS = 30_000L
    /** Linha do tempo digital: funde usos do mesmo app separados por menos que isso... */
    const val PHONE_TIMELINE_MERGE_GAP_MS = 2 * MINUTE_MS
    /** ...e só mostra blocos a partir desta duração. */
    const val PHONE_TIMELINE_MIN_MS = 3 * MINUTE_MS
    /** No Diário geral, só blocos maiores entram (para não poluir a história do dia). */
    const val DIARY_PHONE_ITEM_MIN_MS = 5 * MINUTE_MS
    const val PHONE_TIMELINE_MAX_ITEMS = 80
    const val TOP_APPS_SHOWN = 10
    const val CONTEXT_TOP_APPS = 5
    const val PHONE_INSIGHTS_REFRESH_HOURS = 3L
    const val PHONE_SESSION_RETENTION_DAYS = 365L
}
