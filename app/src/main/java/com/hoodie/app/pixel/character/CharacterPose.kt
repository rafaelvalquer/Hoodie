package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Ears
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth
import com.hoodie.app.pixel.sprite.Posture

/**
 * Estado corporal comum a Hoodie e NPCs. Os campos de item e roupa permanecem
 * no tipo compartilhado durante a migração dos clips legados.
 */
data class CharacterPose(
    val eyes: Eyes = Eyes.OPEN,
    val mouth: Mouth = Mouth.SMILE,
    val leftArm: Arm = Arm.DOWN,
    val rightArm: Arm = Arm.DOWN,
    val legs: Legs = Legs.STAND,
    val facing: Facing = Facing.FRONT,
    val stride: Int = 0,
    val bob: Int = 0,
    val headDy: Int = 0,
    val lift: Int = 0,
    val ears: Ears = Ears.NORMAL,
    val stringSwing: Int = 0,
    val backpack: Boolean = false,
    val backpackDy: Int = 0,
    val item: Item = Item.NONE,
    val itemInBothHands: Boolean = false,
    val headOnly: Boolean = false,
    val headTilt: Int = 0,
    val blush: Boolean = false,
    /** Postura de composição; SIT_FRONT ancora no quadril e usa pernas de banco. */
    val posture: Posture = Posture.STANDING,
) {
    /** Preserva a impressão textual antiga, incluída no SHA dos clips aprovados. */
    override fun toString(): String = "HoodiePose(" +
        "eyes=$eyes, mouth=$mouth, leftArm=$leftArm, rightArm=$rightArm, legs=$legs, facing=$facing, " +
        "stride=$stride, bob=$bob, headDy=$headDy, lift=$lift, ears=$ears, stringSwing=$stringSwing, " +
        "backpack=$backpack, backpackDy=$backpackDy, item=$item, itemInBothHands=$itemInBothHands, " +
        "headOnly=$headOnly, headTilt=$headTilt, blush=$blush)"

    /** Overlay seguro para integrar estado corporal sem descartar campos legados. */
    fun overlay(
        eyes: Eyes? = null,
        mouth: Mouth? = null,
        leftArm: Arm? = null,
        rightArm: Arm? = null,
        legs: Legs? = null,
        facing: Facing? = null,
        stride: Int? = null,
        bob: Int? = null,
        headDy: Int? = null,
        lift: Int? = null,
        ears: Ears? = null,
        stringSwing: Int? = null,
        headTilt: Int? = null,
    ) = copy(
        eyes = eyes ?: this.eyes, mouth = mouth ?: this.mouth,
        leftArm = leftArm ?: this.leftArm, rightArm = rightArm ?: this.rightArm,
        legs = legs ?: this.legs, facing = facing ?: this.facing,
        stride = stride ?: this.stride, bob = bob ?: this.bob,
        headDy = headDy ?: this.headDy, lift = lift ?: this.lift,
        ears = ears ?: this.ears, stringSwing = stringSwing ?: this.stringSwing,
        headTilt = headTilt ?: this.headTilt,
    )

    /** Override de postura usado por ferramentas de comparação e composição de cenas. */
    fun withPosture(posture: Posture): CharacterPose = copy(
        legs = if (posture == Posture.SITTING || posture == Posture.SIT_FRONT) Legs.SIT else Legs.STAND,
        facing = if (posture == Posture.SIT_FRONT) Facing.FRONT else facing,
        posture = posture,
    )
}
