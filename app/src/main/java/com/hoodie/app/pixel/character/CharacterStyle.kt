package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.character.outfit.BackAccessory
import com.hoodie.app.pixel.character.outfit.OutfitStyle
import com.hoodie.app.pixel.character.species.CatSpecies
import com.hoodie.app.pixel.character.species.SpeciesStyle
import com.hoodie.app.pixel.sprite.HoodiePalette

/**
 * Aparência composta:
 *
 *     CharacterStyle ├── SpeciesStyle ├── OutfitStyle ├── Palette ├── Scale └── ArtProfile
 */
data class CharacterStyle(
    val id: String,
    val species: SpeciesStyle,
    val outfit: OutfitStyle,
    val palette: CharacterPalette,
    val scale: CharacterScale = species.bodyScale,
    val eyeStyle: EyeStyle = EyeStyle.HOODIE,
    val mouthStyle: MouthStyle = MouthStyle.HOODIE,
    val hasTail: Boolean = species.tailStyle != com.hoodie.app.pixel.character.species.TailStyle.NONE,
    val artProfile: CharacterArtProfile = species.artProfile,
    val backAccessory: BackAccessory = BackAccessory.None,
) {
    companion object {
        /** Mantém o caminho legado do Hoodie no painter para garantir identidade pixel a pixel. */
        val HOODIE = CharacterStyle(
            id = "hoodie",
            species = CatSpecies,
            outfit = OutfitStyle.Hoodie,
            palette = CharacterPalette(
                HoodiePalette.OUTLINE, HoodiePalette.FUR_LIGHT, HoodiePalette.FUR, HoodiePalette.FUR_SHADE,
                HoodiePalette.INNER_EAR, HoodiePalette.HOOD_LIGHT, HoodiePalette.HOOD, HoodiePalette.HOOD_SHADE,
                HoodiePalette.HOOD_LIGHT, HoodiePalette.STRING,
            ),
        )
    }
}

enum class EyeStyle { HOODIE, HEAVY, SOFT, ROUND, MASKED }
enum class MouthStyle { HOODIE, MUZZLE, BEAK, WHISKERS }
