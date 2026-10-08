package com.hoodie.app.pixel.npc.office

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcMovement
import com.hoodie.app.pixel.npc.brain.NpcBrainState
import com.hoodie.app.pixel.sprite.Facing

/** Single render-ready snapshot shared by the sprite, speech overlay and diagnostics. */
data class OfficeNpcFrameState(
    val npcId: String,
    val x: Int,
    val floorY: Int,
    val phase: OfficeActionPhase,
    val animation: NpcAnimation,
    val facing: Facing,
    val seated: Boolean,
    val actionId: String,
    val speech: OfficeSpeechEvent?,
    val movement: NpcMovement,
    val brainState: NpcBrainState,
)
