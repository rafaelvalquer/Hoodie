package com.hoodie.app.pixel.character

/**
 * Proporções V3 em pixels lógicos do canvas 48×72. Larguras são totais (com contorno);
 * alturas descrevem cada bloco da anatomia, empilhados de baixo (chão) para cima:
 *
 *     pé (FOOT_HEIGHT) → perna [legLength] → quadril/tronco [torsoHeight] → cabeça [headHeight]
 *
 * A cabeça sobrepõe a gola em [CHIN_OVERLAP] px: o pescoço aparece só como sombra sob o queixo.
 */
data class ProportionProfile(
    val headWidth: Int,
    val headHeight: Int,
    val shoulderWidth: Int,
    val torsoHeight: Int,
    val hipWidth: Int,
    val legLength: Int,
    val footWidth: Int,
    /** Largura de cada perna (com contorno). */
    val legWidth: Int = 7,
    /** Espessura da manga/braço (com contorno). */
    val armWidth: Int = 6,
) {
    init {
        require(headWidth in 20..40) { "headWidth $headWidth" }
        require(headHeight in 18..34) { "headHeight $headHeight" }
        require(shoulderWidth in 14..32) { "shoulderWidth $shoulderWidth" }
        require(hipWidth in 12..32) { "hipWidth $hipWidth" }
        require(torsoHeight in 12..26) { "torsoHeight $torsoHeight" }
        require(legLength in 4..14) { "legLength $legLength" }
        require(footWidth in 6..14) { "footWidth $footWidth" }
    }

    companion object {
        const val FOOT_HEIGHT = 5
        const val CHIN_OVERLAP = 3

        /** Referência próxima ao Hoodie. */
        val CAT = ProportionProfile(headWidth = 32, headHeight = 26, shoulderWidth = 23, torsoHeight = 25, hipWidth = 21, legLength = 11, footWidth = 10)
        /** Cabeça muito larga, ombro largo, perna curta, pé grande. */
        val BULLDOG = ProportionProfile(headWidth = 38, headHeight = 33, shoulderWidth = 30, torsoHeight = 24, hipWidth = 25, legLength = 8, footWidth = 12, legWidth = 9, armWidth = 7)
        /** Cabeça média, corpo leve. */
        val DOG = ProportionProfile(headWidth = 30, headHeight = 27, shoulderWidth = 23, torsoHeight = 24, hipWidth = 20, legLength = 11, footWidth = 10)
        /** Orelhas altas (fora da cabeça), tronco estreito, pernas mais longas, pés grandes. */
        val RABBIT = ProportionProfile(headWidth = 28, headHeight = 24, shoulderWidth = 20, torsoHeight = 21, hipWidth = 19, legLength = 12, footWidth = 11, legWidth = 7, armWidth = 5)
        /** Cabeça grande, tronco pequeno, pernas curtas (escala pequena sem ficar minúsculo). */
        val MOUSE = ProportionProfile(headWidth = 28, headHeight = 24, shoulderWidth = 19, torsoHeight = 19, hipWidth = 18, legLength = 9, footWidth = 9, legWidth = 6, armWidth = 5)
        /** Cabeça larga e redonda, tronco arredondado, pernas curtas e finas, pés largos. */
        val DUCK = ProportionProfile(headWidth = 32, headHeight = 28, shoulderWidth = 22, torsoHeight = 22, hipWidth = 24, legLength = 8, footWidth = 13, legWidth = 4, armWidth = 6)
        /** Cabeça média; a cauda volumosa é o elemento forte. */
        val RACCOON = ProportionProfile(headWidth = 32, headHeight = 26, shoulderWidth = 23, torsoHeight = 23, hipWidth = 21, legLength = 11, footWidth = 10)
    }
}
