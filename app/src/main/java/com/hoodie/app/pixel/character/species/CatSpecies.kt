package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterScale

object CatSpecies : SpeciesStyle {
    override val id = "cat"
    override val headWidth = 29
    override val headHeight = 23
    override val bodyScale = CharacterScale.STANDARD
    override val earStyle = EarStyle.POINTED
    override val muzzleStyle = MuzzleStyle.SHORT
    override val tailStyle = TailStyle.CAT
    override val headShape = HeadShape.CAT
}
