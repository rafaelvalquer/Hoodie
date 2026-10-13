package com.hoodie.app.pixel.npc.leisure

/**
 * Pontos do Passeio em coordenadas lógicas 240×320 (pés no chão). Os pontos de circulação
 * ficam fora das faixas que o Hoodie usa no caminho (x 52, 120 e 188) para os visitantes
 * não pararem em cima dele.
 */
enum class LeisureNpcSpot {
    ENTRANCE_LEFT, ENTRANCE_RIGHT,
    PATH_LEFT, PATH_CENTER, PATH_RIGHT,
    BENCH_APPROACH, BENCH_SEAT,
    VIEWPOINT, NATURE_VIEW,
    SOCIAL_LEFT, SOCIAL_RIGHT,
    /** Cruzamentos internos: só servem de passagem, ninguém para neles. */
    JUNCTION_WEST, JUNCTION_BENCH,
}

/**
 * @property attractionX para onde o visitante olha ao parar aqui (lago, chafariz, vista);
 * null = olha para o lado de onde veio.
 * @property capacity quantos visitantes podem ocupar o ponto ao mesmo tempo.
 */
data class LeisureSpot(
    val x: Int,
    val floorY: Int,
    val attractionX: Int? = null,
    val capacity: Int = 1,
    val offstage: Boolean = false,
)
