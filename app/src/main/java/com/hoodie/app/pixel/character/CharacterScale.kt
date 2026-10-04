package com.hoodie.app.pixel.character

/** Medidas em pixels lógicos no canvas fixo de 48×72. */
data class CharacterScale(
    val bodyWidth: Int = 26,
    val bodyHeight: Int = 27,
    val headScale: Float = 1f,
    val armLength: Int = 18,
    val legLength: Int = 12,
) {
    companion object {
        val SMALL = CharacterScale(bodyWidth = 25, bodyHeight = 28, headScale = .92f, armLength = 18, legLength = 11)
        val STANDARD = CharacterScale(bodyWidth = 29, bodyHeight = 29, headScale = 1f, armLength = 19, legLength = 12)
        val WIDE = CharacterScale(bodyWidth = 30, bodyHeight = 29, headScale = 1.12f, armLength = 19, legLength = 11)
        val TALL = CharacterScale(bodyWidth = 26, bodyHeight = 30, headScale = .96f, armLength = 20, legLength = 14)
    }
}
