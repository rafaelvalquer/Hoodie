package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Mouth
import org.junit.Assert.assertArrayEquals
import org.junit.Test

class CharacterMouthRenderingTest {
    @Test fun rearFacingExpressionDoesNotDrawOnTheBackOfAnyCharactersHead() {
        NpcCharacterRegistry.all.forEach { style ->
            val neutralBack = CharacterPainter.paint(
                style, CharacterPose(facing = Facing.BACK, mouth = Mouth.SMILE),
            ).image
            val expressiveBack = CharacterPainter.paint(
                style,
                CharacterPose(facing = Facing.BACK, eyes = Eyes.HAPPY, mouth = Mouth.OPEN, blush = true),
            ).image

            assertArrayEquals("${style.id} should not show a mouth from behind", neutralBack.pixels, expressiveBack.pixels)
        }
    }
}
