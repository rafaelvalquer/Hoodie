package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterScale

object MouseSpecies : SpeciesStyle {
    override val id = "mouse"
    override val headWidth = 23
    override val headHeight = 21
    override val bodyScale = CharacterScale.SMALL
    override val earStyle = EarStyle.ROUND
    override val muzzleStyle = MuzzleStyle.FINE
    override val tailStyle = TailStyle.LONG
    override val headShape = HeadShape.MOUSE
}
