package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterScale

object RabbitSpecies : SpeciesStyle {
    override val id = "rabbit"
    override val headWidth = 25
    override val headHeight = 22
    override val bodyScale = CharacterScale.STANDARD
    override val earStyle = EarStyle.LONG
    override val muzzleStyle = MuzzleStyle.SHORT
    override val tailStyle = TailStyle.SHORT
    override val headShape = HeadShape.RABBIT
}
