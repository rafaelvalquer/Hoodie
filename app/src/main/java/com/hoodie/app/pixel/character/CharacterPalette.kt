package com.hoodie.app.pixel.character

/** Até dez cores chapadas para manter contorno e leitura limpa em pixel art. */
data class CharacterPalette(
    val outline: Int,
    val furLight: Int,
    val fur: Int,
    val furDark: Int,
    val inner: Int,
    val outfitLight: Int,
    val outfit: Int,
    val outfitDark: Int,
    val shirt: Int,
    val accent: Int,
)
