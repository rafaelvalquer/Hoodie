package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterScale

object DogSpecies : SpeciesStyle {
    override val id = "dog"
    override val headWidth = 30
    override val headHeight = 24
    override val bodyScale = CharacterScale.STANDARD
    override val earStyle = EarStyle.FLOPPY
    override val muzzleStyle = MuzzleStyle.LONG
    override val tailStyle = TailStyle.SHORT
    override val headShape = HeadShape.DOG
}
