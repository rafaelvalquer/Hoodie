package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.sprite.Facing

enum class OfficeNpcSpot { DESK_LEFT, DESK_RIGHT, COFFEE, WINDOW, WHITEBOARD, PRINTER, CENTER, CENTER_LEFT, CENTER_RIGHT, DOOR }

data class OfficeSpot(
    val x: Int,
    val floorY: Int,
    val facing: Facing,
    val capacity: Int = 1,
)
