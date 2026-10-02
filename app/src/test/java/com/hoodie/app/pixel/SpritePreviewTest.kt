package com.hoodie.app.pixel

import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.HoodiePose
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth
import org.junit.Test

class SpritePreviewTest {
    @Test
    fun exportModelSheet() {
        val poses = listOf(
            HoodiePose(),
            HoodiePose(eyes = Eyes.CLOSED, bob = 1),
            HoodiePose(eyes = Eyes.HAPPY, rightArm = Arm.WAVE, mouth = Mouth.OPEN),
            HoodiePose(legs = Legs.STEP_LEFT, leftArm = Arm.SWING_FRONT, rightArm = Arm.SWING_BACK, backpack = true, eyes = Eyes.LOOK_LEFT),
            HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED),
            HoodiePose(legs = Legs.SIT, rightArm = Arm.HOLD_MOUTH, item = Item.MUG, eyes = Eyes.CLOSED),
            HoodiePose(legs = Legs.SIT, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.CONTROLLER, eyes = Eyes.WIDE),
            HoodiePose(legs = Legs.SIT, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.BOOK, eyes = Eyes.LOOK_DOWN),
            HoodiePose(rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN),
            HoodiePose(leftArm = Arm.UP, rightArm = Arm.UP, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.CLOSED, mouth = Mouth.FLAT),
            HoodiePose(rightArm = Arm.FORWARD_DOWN, item = Item.BROOM),
            HoodiePose(headOnly = true, eyes = Eyes.CLOSED),
        )
        PreviewExport.sheet("hoodie_model_sheet", poses.map { HoodiePainter.sprite(it) }, columns = 6, scale = 5)
    }

    /** Ciclos de caminhada nas três vistas + espelho, para revisar no PNG. */
    @Test
    fun exportWalkCycles() {
        val p = com.hoodie.app.pixel.sprite.ProceduralSpriteProvider
        val rows = listOf(
            com.hoodie.app.pixel.sprite.Direction.LEFT, com.hoodie.app.pixel.sprite.Direction.RIGHT,
            com.hoodie.app.pixel.sprite.Direction.FRONT, com.hoodie.app.pixel.sprite.Direction.BACK,
        ).flatMap { d -> (0 until 8).map { i -> p.frame(com.hoodie.app.pixel.sprite.SpriteRequest(com.hoodie.app.pixel.animation.AnimationId.WALK_BACKPACK, d, i)).image } }
        PreviewExport.sheet("hoodie_walk_cycles", rows, columns = 8, scale = 4)
        // Perfil sem mochila em escala maior: é onde braço, perna e rabo aparecem melhor.
        val side = (0 until 8).map { i -> p.frame(com.hoodie.app.pixel.sprite.SpriteRequest(com.hoodie.app.pixel.animation.AnimationId.WALK, com.hoodie.app.pixel.sprite.Direction.LEFT, i)).image }
        PreviewExport.sheet("hoodie_walk_side", side, columns = 4, scale = 6)
    }

    /** Orelhas, piscada e expressões. */
    @Test
    fun exportPersonality() {
        val poses = com.hoodie.app.pixel.sprite.Ears.entries.map { HoodiePose(ears = it) } +
            listOf(Eyes.HALF, Eyes.CLOSED, Eyes.SLEEPY, Eyes.FOCUSED).map { HoodiePose(eyes = it) } +
            listOf(HoodiePose(rightArm = Arm.HEAD, eyes = Eyes.CLOSED), HoodiePose(lift = 2, eyes = Eyes.HAPPY, mouth = Mouth.OPEN), HoodiePose(headOnly = true, eyes = Eyes.CLOSED, headTilt = 1))
        PreviewExport.sheet("hoodie_personality", poses.map { HoodiePainter.sprite(it) }, columns = 7, scale = 4)
    }
}
