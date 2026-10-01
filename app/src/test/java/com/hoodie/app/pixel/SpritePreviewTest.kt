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
}
