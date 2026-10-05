package com.hoodie.app.pixel.character

/** Forma geral: quão "quadrada" é a cabeça e quanto o tronco afunila dos ombros ao quadril. */
data class SilhouetteProfile(
    /** Raio dos cantos da cabeça (0 = quadrada, grande = redonda). */
    val headRoundness: Int = 8,
    /** Raio dos ombros. */
    val shoulderRoundness: Int = 3,
    /** Pixels visíveis de pescoço/sombra sob o queixo. */
    val neckShadow: Int = 1,
)

/** Rosto: posição e peso dos olhos, focinho e marcas. */
data class FaceProfile(
    /** Distância de cada olho ao centro da cabeça. */
    val eyeSpacing: Int = 7,
    /** Altura dos olhos como fração da altura da cabeça (0 topo .. 1 queixo). */
    val eyeLine: Float = 0.48f,
    /** Sobrancelha pesada (Bulldog) desenhada sobre o olho. */
    val heavyBrow: Boolean = false,
    /** Brilho branco no olho (vida no olhar, como nas referências). */
    val eyeGlint: Boolean = true,
)

/** Quanto detalhe a roupa pode receber sem estourar o orçamento de cores/pixels. */
enum class GarmentDetailLevel { MINIMAL, STANDARD, RICH }

/**
 * Camada artística acima do [CharacterStyle]: concentra as regras visuais de cada
 * espécie para que o painter não precise de `if (bulldog)` espalhados.
 */
data class CharacterArtProfile(
    val silhouette: SilhouetteProfile = SilhouetteProfile(),
    val face: FaceProfile = FaceProfile(),
    val proportions: ProportionProfile = ProportionProfile.CAT,
    val shading: ShadingProfile = ShadingProfile(),
    val garmentDetail: GarmentDetailLevel = GarmentDetailLevel.STANDARD,
)
