package com.hoodie.app.pixel.character

enum class LightDirection { TOP_LEFT, TOP_RIGHT }

/**
 * Luz comum a todos os personagens: um tom de luz e um de sombra, sem dithering.
 * Padrão TOP_LEFT: realce na borda superior/esquerda, sombra na borda inferior/direita.
 */
data class ShadingProfile(
    val lightDirection: LightDirection = LightDirection.TOP_LEFT,
    val useFurShadow: Boolean = true,
    val useGarmentShadow: Boolean = true,
    /** Espessura da faixa de sombra interna (px). */
    val shadowDepth: Int = 2,
)

/** Três tons + contorno de uma superfície (pelo, roupa, calça…). */
data class Tones(val light: Int, val base: Int, val dark: Int, val outline: Int) {
    companion object {
        fun fur(p: CharacterPalette) = Tones(p.furLight, p.fur, p.furDark, p.outline)
        fun outfit(p: CharacterPalette) = Tones(p.outfitLight, p.outfit, p.outfitDark, p.outline)
        /** Superfície escura (calça, sapato, jaqueta fechada): sem luz própria, base = tom escuro. */
        fun deep(p: CharacterPalette) = Tones(p.outfit, p.outfitDark, p.outline, p.outline)
        fun shirt(p: CharacterPalette) = Tones(p.shirt, p.shirt, p.furLight, p.outline)
    }
}
