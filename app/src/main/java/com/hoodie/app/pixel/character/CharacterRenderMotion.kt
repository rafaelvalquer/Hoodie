package com.hoodie.app.pixel.character

/** Movimento secundário fornecido pelo personagem semântico, separado do estado da pose. */
data class CharacterRenderMotion(
    val stepAmplitude: Int = 2,
    val armSwing: Int = 2,
    /** null usa a oscilação corporal padrão; zero mantém a cauda parada. */
    val tailAmplitude: Int? = null,
)
