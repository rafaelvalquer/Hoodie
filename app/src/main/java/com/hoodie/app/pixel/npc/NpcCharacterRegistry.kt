package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.character.EyeStyle
import com.hoodie.app.pixel.character.MouthStyle
import com.hoodie.app.pixel.character.outfit.OutfitStyle
import com.hoodie.app.pixel.character.species.BulldogSpecies
import com.hoodie.app.pixel.character.species.CatSpecies
import com.hoodie.app.pixel.character.species.DogSpecies
import com.hoodie.app.pixel.character.species.DuckSpecies
import com.hoodie.app.pixel.character.species.MouseSpecies
import com.hoodie.app.pixel.character.species.RabbitSpecies
import com.hoodie.app.pixel.character.species.RaccoonSpecies

/** Catálogo estável de identidades; comportamento e fala são registrados à parte. */
object NpcCharacterRegistry {
    private const val INK = 0xFF1A1C33.toInt()

    val BULLDOG_EXEC = CharacterStyle(
        id = "bulldog_exec", species = BulldogSpecies, outfit = OutfitStyle.Suit,
        palette = CharacterPalette(
            outline = INK, furLight = 0xFFE0C7AD.toInt(), fur = 0xFFC9A58A.toInt(), furDark = 0xFF8D6555.toInt(),
            inner = 0xFF9F6D65.toInt(), outfitLight = 0xFF65728F.toInt(), outfit = 0xFF35415C.toInt(),
            outfitDark = 0xFF222A40.toInt(), shirt = 0xFFF3EBD9.toInt(), accent = 0xFFC9544F.toInt(),
        ), eyeStyle = EyeStyle.HEAVY, mouthStyle = MouthStyle.MUZZLE,
    )

    val DOG_WORKER = CharacterStyle(
        id = "dog_worker", species = DogSpecies, outfit = OutfitStyle.Casual,
        palette = palette(0xFFB78361.toInt(), 0xFFE3C09C.toInt(), 0xFF83563F.toInt(), 0xFF687C72.toInt(), 0xFFD7DCE3.toInt()),
        mouthStyle = MouthStyle.MUZZLE,
    )
    val DOG_SHOPPER = DOG_WORKER.copy(
        id = "dog_shopper", outfit = OutfitStyle.Commuter,
        palette = palette(0xFFBE8E68.toInt(), 0xFFE6C5A1.toInt(), 0xFF855B42.toInt(), 0xFF677C9B.toInt(), 0xFFE8B84A.toInt()),
    )
    val RABBIT_ANALYST = CharacterStyle(
        id = "rabbit_analyst", species = RabbitSpecies, outfit = OutfitStyle.Student,
        palette = palette(0xFFF3EBD9.toInt(), 0xFFFFF8E9.toInt(), 0xFFB9A68F.toInt(), 0xFF5B83B3.toInt(), 0xFFE8B84A.toInt()),
        eyeStyle = EyeStyle.SOFT,
    )
    val RABBIT_READER = RABBIT_ANALYST.copy(
        id = "rabbit_reader", outfit = OutfitStyle.Casual,
        palette = palette(0xFFE2D8C8.toInt(), 0xFFFFF4E4.toInt(), 0xFFBBA58E.toInt(), 0xFFB65F65.toInt(), 0xFF7AB6B0.toInt()),
    )
    val RABBIT_WALKER = RABBIT_READER.copy(
        id = "rabbit_walker", outfit = OutfitStyle.Sport,
        palette = palette(0xFFD7C6B0.toInt(), 0xFFF1E4D5.toInt(), 0xFFAA927B.toInt(), 0xFF728B65.toInt(), 0xFFEFA3C8.toInt()),
    )
    val MOUSE_COMMUTER = CharacterStyle(
        id = "mouse_commuter", species = MouseSpecies, outfit = OutfitStyle.Commuter,
        palette = palette(0xFFB78B76.toInt(), 0xFFE0B5A2.toInt(), 0xFF865D51.toInt(), 0xFF547A91.toInt(), 0xFF62D3CF.toInt()),
        eyeStyle = EyeStyle.ROUND, mouthStyle = MouthStyle.WHISKERS,
    )
    val DUCK_SLEEPY = CharacterStyle(
        id = "duck_sleepy", species = DuckSpecies, outfit = OutfitStyle.Casual,
        palette = palette(0xFFE9C95A.toInt(), 0xFFFFE89A.toInt(), 0xFFB18B35.toInt(), 0xFF7B739D.toInt(), 0xFFE9854B.toInt()),
        eyeStyle = EyeStyle.SOFT, mouthStyle = MouthStyle.BEAK,
    )
    val RACCOON_COMMUTER = CharacterStyle(
        id = "raccoon_window", species = RaccoonSpecies, outfit = OutfitStyle.Commuter,
        palette = palette(0xFF9295A4.toInt(), 0xFFC5C8D0.toInt(), 0xFF737887.toInt(), 0xFF4D6686.toInt(), 0xFFE2B973.toInt()),
        eyeStyle = EyeStyle.MASKED, mouthStyle = MouthStyle.MUZZLE,
    )
    val CAT_COLLEAGUE = cat("cat_colleague", 0xFFB7A2C8.toInt(), 0xFF586987.toInt(), 0xFFB7A2C8.toInt())
    val CAT_GUEST = cat("cat_guest", 0xFFCEA6B8.toInt(), 0xFF597D73.toInt(), 0xFFEFA3C8.toInt())

    val all: List<CharacterStyle> = listOf(
        BULLDOG_EXEC, DOG_WORKER, DOG_SHOPPER, RABBIT_ANALYST, RABBIT_READER, RABBIT_WALKER,
        MOUSE_COMMUTER, DUCK_SLEEPY, RACCOON_COMMUTER, CAT_COLLEAGUE, CAT_GUEST,
    )

    private fun cat(id: String, fur: Int, outfit: Int, accent: Int) = CharacterStyle(
        id = id, species = CatSpecies, outfit = OutfitStyle.Casual,
        palette = palette(fur, 0xFFE4CEDF.toInt(), 0xFF786781.toInt(), outfit, accent),
    )

    private fun palette(fur: Int, light: Int, dark: Int, outfit: Int, accent: Int) = CharacterPalette(
        outline = INK, furLight = light, fur = fur, furDark = dark, inner = 0xFFEFA3C8.toInt(),
        outfitLight = 0xFFD9E5F1.toInt(), outfit = outfit, outfitDark = 0xFF343A50.toInt(),
        shirt = 0xFFF3EBD9.toInt(), accent = accent,
    )
}
