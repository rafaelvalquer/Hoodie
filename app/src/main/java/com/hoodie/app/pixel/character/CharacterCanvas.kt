package com.hoodie.app.pixel.character

import com.hoodie.app.pixel.sprite.Point

/** Canvas lógico único para o Hoodie e todos os personagens ambientais. */
object CharacterCanvas {
    const val WIDTH = 48
    const val HEIGHT = 72
    const val CENTER_X = WIDTH / 2
    const val GROUND_Y = HEIGHT - 1

    val FEET = Point(CENTER_X, GROUND_Y)

    fun contains(point: Point): Boolean = point.x in 0 until WIDTH && point.y in 0 until HEIGHT
}
