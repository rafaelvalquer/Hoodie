package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.character.EyeStyle
import com.hoodie.app.pixel.character.MouthStyle
import com.hoodie.app.pixel.character.outfit.BackAccessory
import com.hoodie.app.pixel.character.outfit.OutfitStyle
import com.hoodie.app.pixel.character.species.BulldogSpecies
import com.hoodie.app.pixel.character.species.CatSpecies
import com.hoodie.app.pixel.character.species.DogSpecies
import com.hoodie.app.pixel.character.species.DuckSpecies
import com.hoodie.app.pixel.character.species.MouseSpecies
import com.hoodie.app.pixel.character.species.RabbitSpecies
import com.hoodie.app.pixel.character.species.RaccoonSpecies

/**
 * Catálogo estável de identidades; comportamento e fala são registrados à parte.
 * Paletas V3 seguem as referências geradas (10 cores cada: contorno, 3 tons de pelo,
 * interno, 3 tons de roupa, camisa/brilho e acento).
 */
object NpcCharacterRegistry {
    private const val INK = 0xFF1A1C33.toInt()

    /** Benchmark: Bulldog de terno azul-marinho, camisa creme e gravata vermelha. */
    val BULLDOG_EXEC = CharacterStyle(
        id = "bulldog_exec", species = BulldogSpecies, outfit = OutfitStyle.Suit,
        palette = CharacterPalette(
            outline = INK, furLight = 0xFFF2D9B0.toInt(), fur = 0xFFC8843F.toInt(), furDark = 0xFF8A4F2A.toInt(),
            inner = 0xFF9E3B35.toInt(), outfitLight = 0xFF4E5E92.toInt(), outfit = 0xFF2E3B66.toInt(),
            outfitDark = 0xFF1C2340.toInt(), shirt = 0xFFF1E6CF.toInt(), accent = 0xFFC0453C.toInt(),
        ), eyeStyle = EyeStyle.HEAVY, mouthStyle = MouthStyle.MUZZLE,
    )

    /** Jaqueta verde de zíper sobre camiseta escura. */
    val DOG_WORKER = CharacterStyle(
        id = "dog_worker", species = DogSpecies, outfit = OutfitStyle.Casual,
        palette = palette(
            fur = 0xFFC27A45, light = 0xFFF1D6A8, dark = 0xFF8C4E2A, inner = 0xFFD98C8C,
            outfitLight = 0xFF8FAA7E, outfit = 0xFF6B8A5E, outfitDark = 0xFF3B3F4E, accent = 0xFFE8B84A,
        ),
        eyeStyle = EyeStyle.ROUND, mouthStyle = MouthStyle.MUZZLE,
    )
    /** Suéter azul com bolsa transversal de fivela amarela. */
    val DOG_SHOPPER = DOG_WORKER.copy(
        id = "dog_shopper", outfit = OutfitStyle.Student, backAccessory = BackAccessory.ShoulderBag,
        palette = palette(
            fur = 0xFFD08A4C, light = 0xFFF4DFB8, dark = 0xFF9A5A30, inner = 0xFFD98C8C,
            outfitLight = 0xFF6676A3, outfit = 0xFF46557F, outfitDark = 0xFF2C3350, accent = 0xFFE8B84A,
        ),
    )
    /** Coelho de uniforme: suéter azul, gola branca, mochila de alças marrons. */
    val RABBIT_ANALYST = CharacterStyle(
        id = "rabbit_analyst", species = RabbitSpecies, outfit = OutfitStyle.Student, backAccessory = BackAccessory.Backpack,
        palette = palette(
            fur = 0xFFF1E4CC, light = 0xFFFFF8EA, dark = 0xFFC9B394, inner = 0xFFEFA3A3,
            outfitLight = 0xFF5C78B0, outfit = 0xFF3D5390, outfitDark = 0xFF4A4250, accent = 0xFF9A5B33,
        ),
        eyeStyle = EyeStyle.SOFT,
    )
    /** Moletom vermelho de cordões verdes e mochila. */
    val RABBIT_READER = RABBIT_ANALYST.copy(
        id = "rabbit_reader", outfit = OutfitStyle.Sport, backAccessory = BackAccessory.Backpack,
        palette = palette(
            fur = 0xFFE3C29A, light = 0xFFF6E3C8, dark = 0xFFB98E68, inner = 0xFFE88080,
            outfitLight = 0xFFEC8A7E, outfit = 0xFFD9605A, outfitDark = 0xFF3E4A66, accent = 0xFF6CC4A8,
        ),
    )
    /** Suéter verde e bermuda escura para o passeio. */
    val RABBIT_WALKER = RABBIT_READER.copy(
        id = "rabbit_walker", outfit = OutfitStyle.Casual, backAccessory = BackAccessory.None,
        palette = palette(
            fur = 0xFFEAD3B6, light = 0xFFFBEFDF, dark = 0xFFC2A486, inner = 0xFFF09A9A,
            outfitLight = 0xFF7E9A66, outfit = 0xFF5E7A4A, outfitDark = 0xFF2C2C34, accent = 0xFFE88C8C,
        ),
    )
    /** Rato de jaqueta azul aberta, camiseta branca e bolsa transversal. */
    val MOUSE_COMMUTER = CharacterStyle(
        id = "mouse_commuter", species = MouseSpecies, outfit = OutfitStyle.Commuter, backAccessory = BackAccessory.ShoulderBag,
        palette = palette(
            fur = 0xFFB97A50, light = 0xFFF0D2B0, dark = 0xFF7E4A2E, inner = 0xFFEFA0A8,
            outfitLight = 0xFF5A78B0, outfit = 0xFF34508A, outfitDark = 0xFF2A2F45, accent = 0xFF5CC8D8,
        ),
        eyeStyle = EyeStyle.ROUND, mouthStyle = MouthStyle.WHISKERS,
    )
    /** Pato branco de moletom roxo, sempre com sono. */
    val DUCK_SLEEPY = CharacterStyle(
        id = "duck_sleepy", species = DuckSpecies, outfit = OutfitStyle.Sport,
        palette = palette(
            fur = 0xFFF4EEE2, light = 0xFFFFFDF7, dark = 0xFFC9C0B2, inner = 0xFFF0A8A0,
            outfitLight = 0xFF9A7CC2, outfit = 0xFF7B5AA6, outfitDark = 0xFF4E3A70, accent = 0xFFF0A030,
        ),
        eyeStyle = EyeStyle.HOODIE, mouthStyle = MouthStyle.BEAK,
    )
    /** Guaxinim de jaqueta azul e mochila de alças mostarda. */
    val RACCOON_COMMUTER = CharacterStyle(
        id = "raccoon_window", species = RaccoonSpecies, outfit = OutfitStyle.Commuter, backAccessory = BackAccessory.Backpack,
        palette = palette(
            fur = 0xFF8C8580, light = 0xFFE8E0D6, dark = 0xFF4E4648, inner = 0xFFE8A0A8,
            outfitLight = 0xFF5A78B0, outfit = 0xFF34508A, outfitDark = 0xFF2A2A30, accent = 0xFFD8A040,
        ),
        eyeStyle = EyeStyle.MASKED, mouthStyle = MouthStyle.MUZZLE,
    )
    /** Gato colega: lilás, jaqueta azul aberta, camiseta clara — "mesma espécie" do Hoodie. */
    val CAT_COLLEAGUE = cat(
        "cat_colleague", OutfitStyle.Commuter, BackAccessory.None,
        fur = 0xFFB9A3CF, light = 0xFFE6DCEF, dark = 0xFF8A72A6,
        outfitLight = 0xFF6C8AC0, outfit = 0xFF46649A, outfitDark = 0xFF2E3046, accent = 0xFFC8B6E2,
    )
    /** Gata visitante: suéter verde de uniforme e mochila. */
    val CAT_GUEST = cat(
        "cat_guest", OutfitStyle.Student, BackAccessory.Backpack,
        fur = 0xFFC8A8B8, light = 0xFFF0E2EA, dark = 0xFF9A7E8E,
        outfitLight = 0xFF80B898, outfit = 0xFF5E9A78, outfitDark = 0xFF34405E, accent = 0xFFE88FA6,
    )

    val all: List<CharacterStyle> = listOf(
        BULLDOG_EXEC, DOG_WORKER, DOG_SHOPPER, RABBIT_ANALYST, RABBIT_READER, RABBIT_WALKER,
        MOUSE_COMMUTER, DUCK_SLEEPY, RACCOON_COMMUTER, CAT_COLLEAGUE, CAT_GUEST,
    )

    private fun cat(
        id: String, outfitStyle: OutfitStyle, accessory: BackAccessory,
        fur: Long, light: Long, dark: Long, outfitLight: Long, outfit: Long, outfitDark: Long, accent: Long,
    ) = CharacterStyle(
        id = id, species = CatSpecies, outfit = outfitStyle, backAccessory = accessory,
        palette = palette(fur, light, dark, 0xFFF2B0C0, outfitLight, outfit, outfitDark, accent),
    )

    private fun palette(
        fur: Long, light: Long, dark: Long, inner: Long,
        outfitLight: Long, outfit: Long, outfitDark: Long, accent: Long,
    ) = CharacterPalette(
        outline = INK, furLight = light.toInt(), fur = fur.toInt(), furDark = dark.toInt(), inner = inner.toInt(),
        outfitLight = outfitLight.toInt(), outfit = outfit.toInt(), outfitDark = outfitDark.toInt(),
        shirt = 0xFFF3EBD9.toInt(), accent = accent.toInt(),
    )
}
