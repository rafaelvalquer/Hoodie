package com.hoodie.app.pixel.npc.leisure

/** Estado visível de um visitante do Passeio num instante (o que ele está fazendo). */
enum class LeisureNpcIntent {
    IDLE, WALKING, OBSERVING, PHOTOGRAPHING, RELAXING, SITTING, READING, USING_PHONE,
    SOCIALIZING, RETURNING, EXITING,
}

/**
 * Atividade escolhida pelo cérebro. Cada uma vira uma pequena sequência de segmentos
 * (andar até o ponto → virar → agir), executada por [LeisureNpcSession].
 */
enum class LeisureActivity {
    /** Caminha até outro ponto do caminho e olha em volta. */
    EXPLORE,
    /** Para diante da atração da variante (lago, chafariz, flores) ou do mirante. */
    OBSERVE,
    /** Enquadra e fotografa a atração. */
    PHOTO,
    /** Alonga-se no caminho. */
    STRETCH,
    /** Encontro com outro visitante ([LeisureSocialCoordinator]). */
    SOCIAL,
    /** Lê no banco. */
    READ,
    /** Consulta o celular (em pé ou sentado). */
    PHONE,
    /** Descansa no banco olhando em volta ou bebendo algo. */
    BENCH_REST,
    /** Passeio curto sem destino especial. */
    STROLL,
    /** Sai pela borda da cena e volta algum tempo depois. */
    EXIT,
}
