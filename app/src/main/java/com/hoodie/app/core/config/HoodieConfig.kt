package com.hoodie.app.core.config

import com.hoodie.app.core.time.DAY_MS
import com.hoodie.app.core.time.HOUR_MS
import com.hoodie.app.core.time.MINUTE_MS

/**
 * Todos os números de comportamento do Hoodie num lugar só. Regras de negócio
 * referenciam estas constantes em vez de repetir valores soltos.
 */
object HoodieConfig {
    /** Nova composição da cena ferroviária; desligue para comparar com o vagão legado. */
    var TRAIN_SCENE_V2 = true
    /** Cenas de transporte V3 em camadas (docs/transport-art-bible.md), aprovadas em 07/10/2026 (scene-art-status.json). Sem a arte compilada, a cena antiga assume. */
    @Volatile var TRANSPORT_SCENES_V3 = true
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

    // ── Dia ativo / despertar inferido ──
    const val WAKE_PHONE_MIN_ACTIVE_MS = 2 * MINUTE_MS
    const val WAKE_PHONE_WINDOW_MS = 5 * MINUTE_MS
    const val WAKE_CORROBORATION_WINDOW_MS = 30 * MINUTE_MS
    const val SLEEP_INACTIVITY_MIN_MS = 3 * HOUR_MS
    const val SLEEP_ONSET_GRACE_MS = 8 * MINUTE_MS
    const val SLEEP_END_MIN_INACTIVITY_MS = 3 * HOUR_MS
    const val SLEEP_END_HOME_SETTLE_MS = 30 * MINUTE_MS
    const val SLEEP_END_SCHEDULE_WINDOW_MS = 3 * HOUR_MS
    const val SLEEP_END_CORROBORATION_WINDOW_MS = HOUR_MS
    const val SLEEP_END_LOOKAHEAD_MS = 4 * HOUR_MS
    const val WAKE_TRANSITION_REAL_MS = 1_200L
    const val WAKE_PRESENTATION_MS = MINUTE_MS
    const val DIARY_CLOCK_SLEEP_SEGMENTS = true
    const val ACTIVE_DAY_END_INFERENCE = true

    // ── Mobilidade (Activity Recognition + geofence) ──
    /** Caminhada só vale como deslocamento depois de sustentada por este tempo. */
    const val WALK_CONFIRM_MS = 2 * MINUTE_MS
    /** Veículo: o Android já filtra bastante, mas um semáforo não é uma viagem. */
    const val VEHICLE_CONFIRM_MS = 90_000L
    /** Parado por este tempo durante um deslocamento = provável chegada. */
    const val STILL_ARRIVAL_MS = 3 * MINUTE_MS
    /** Score de mobilidade: abaixo de ASK ignora, entre ASK e APPLY pergunta, acima aplica. */
    const val MOBILITY_ASK_SCORE = 40
    const val MOBILITY_APPLY_SCORE = 80
    const val MOBILITY_SCORE_MOVEMENT = 30
    const val MOBILITY_SCORE_GEOFENCE_EXIT = 40
    const val MOBILITY_SCORE_SUSTAINED = 15
    const val MOBILITY_SCORE_TIME_MATCH = 10
    const val MOBILITY_SCORE_HISTORY = 15
    /** Confirmações seguidas de chegada num lugar para passar a confirmar sozinho. */
    const val ARRIVAL_AUTO_CONFIRM_COUNT = 3
    /** Troca manual de contexto até este tempo depois de uma chegada automática = correção. */
    const val ARRIVAL_CORRECTION_WINDOW_MS = 30 * MINUTE_MS
    /** Mesmo modo escolhido para o mesmo trajeto este número de vezes = vira padrão aprendido. */
    const val TRANSPORT_LEARN_COUNT = 3
    /** Trajeto repetido este número de vezes = "histórico semelhante" no score. */
    const val TRIP_PATTERN_MIN_COUNT = 2
    /** Janela (± minutos) para dois deslocamentos contarem como "mesmo horário". */
    const val TRIP_PATTERN_WINDOW_MIN = 60
    /** Perguntas de mobilidade por sessão (a escolha do transporte pode ser uma segunda). */
    const val MOBILITY_MAX_QUESTIONS_PER_SESSION = 1
    /** Candidato a movimento que não virou deslocamento é descartado depois disso. */
    const val MOVEMENT_CANDIDATE_MAX_MS = 30 * MINUTE_MS
    /** Sessão aberta há mais que isso (app morto, evento perdido) é encerrada sem destino. */
    const val MOBILITY_SESSION_MAX_MS = 6 * HOUR_MS
    const val MOBILITY_RETENTION_DAYS = 365L

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

    // ── Diário: Mapa do Dia 2.0 ──
    /** Jornada Pixel como mapa padrão do Diário. false = volta ao mapa clássico (que segue disponível no seletor). */
    const val DIARY_JOURNEY_MAP_V2 = true

    // ── Diário: Jornada do Dia 3.0 (overworld em capítulos + relógio) ──
    /** Overworld em serpentina com capítulos. false = Jornada 2.0 (zigue-zague) durante a transição. */
    const val DIARY_JOURNEY_MAP_V3 = true
    /** Botão [RELÓGIO] no seletor do mapa. */
    const val DIARY_CLOCK_VIEW = true

    /** Interior do ônibus V2: bancos com slots, NPCs frontais e janelas com paralaxe recortada. */
    const val BUS_SCENE_V2 = true

    /** Relógio do Dia 2.0 (mostrador pixel 104×104). false = relógio legado (continua no Diary Lab). */
    const val DIARY_DAY_CLOCK_V2 = true
    /** Até quantas paradas o dia cabe num mapa só (3 linhas de 3). Acima disso: capítulos. */
    const val JOURNEY_SINGLE_MAP_MAX_STOPS = 9
    /** Acima disto um capítulo agrupa paradas rápidas consecutivas (4 linhas de 3). */
    const val JOURNEY_CHAPTER_MAX_STOPS = 12
    /** Visita mais curta que isto pode virar marco "×k". */
    const val JOURNEY_QUICK_STOP_MS = 10 * MINUTE_MS
    /** Início da Tarde e da Noite (hora local; Manhã inclui a madrugada). */
    val JOURNEY_AFTERNOON_START: java.time.LocalTime = java.time.LocalTime.of(12, 0)
    val JOURNEY_NIGHT_START: java.time.LocalTime = java.time.LocalTime.of(18, 0)
    /** Arco menor que isto vira tique no relógio (~8 min). */
    const val CLOCK_MIN_ARC_DEG = 2f
    /** Só arcos a partir disto ganham rótulo (~1h40). */
    const val CLOCK_MIN_LABEL_DEG = 25f
    /** Tiques mais próximos que isto se juntam num "×k". */
    const val CLOCK_TICK_MERGE_DEG = 3f
}
