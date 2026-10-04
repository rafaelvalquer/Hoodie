package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.sprite.Facing

/** Renderiza os dois lados do Pixel Lab com a mesma pose e a mesma regra de orientação. */
object CharacterComparisonRenderer {
    fun hoodie(pose: CharacterPose, facingRight: Boolean = false): CharacterFrame =
        orient(CharacterPainter.paint(CharacterStyle.HOODIE, pose), pose, facingRight)

    fun character(
        style: CharacterStyle,
        pose: CharacterPose,
        facingRight: Boolean = false,
        motion: CharacterRenderMotion = CharacterRenderMotion(),
    ): CharacterFrame = orient(CharacterPainter.paint(style, pose, motion), pose, facingRight)

    private fun orient(frame: CharacterFrame, pose: CharacterPose, facingRight: Boolean) =
        if (pose.facing == Facing.SIDE && facingRight) frame.mirrored() else frame
}
