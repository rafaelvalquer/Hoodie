package com.hoodie.app.domain.diary.journey

/**
 * Visualização do mapa do Diário: [JORNADA] overworld ou [RELÓGIO] anel de 24 h.
 * Salva no DataStore (`diary_map_mode`); o mapa clássico vive só no Diary Lab.
 */
enum class DiaryMapMode {
    JOURNEY, CLOCK;

    companion object {
        fun parse(value: String?): DiaryMapMode = entries.firstOrNull { it.name == value } ?: JOURNEY
    }
}
