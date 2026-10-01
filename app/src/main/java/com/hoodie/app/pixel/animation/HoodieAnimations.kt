package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.HoodiePose
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth

/**
 * Catálogo de animações (equivalente ao metadata JSON de sprite sheet): id, fps,
 * loop e frames. Os frames são poses, rasterizadas e cacheadas pelo HoodiePainter.
 */
enum class AnimationId(val label: String, val fps: Int, val loop: Boolean, val frames: List<HoodiePose>) {
    IDLE("Idle", 3, true, breathing(HoodiePose())),
    IDLE_SIT("Idle sentado", 3, true, breathing(HoodiePose(legs = Legs.SIT))),
    WALK("Caminhar", 8, true, walk(backpack = false)),
    WALK_BACKPACK("Caminhar de mochila", 8, true, walk(backpack = true)),
    SLEEP("Dormir", 2, true, listOf(
        HoodiePose(headOnly = true, eyes = Eyes.CLOSED),
        HoodiePose(headOnly = true, eyes = Eyes.CLOSED),
        HoodiePose(headOnly = true, eyes = Eyes.CLOSED, bob = 1),
        HoodiePose(headOnly = true, eyes = Eyes.CLOSED, bob = 1),
    )),
    WAKE_UP("Acordar/espreguiçar", 4, false, listOf(
        HoodiePose(eyes = Eyes.SLEEPY),
        HoodiePose(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, leftArm = Arm.UP, rightArm = Arm.UP),
        HoodiePose(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, leftArm = Arm.UP, rightArm = Arm.UP, bob = -1),
        HoodiePose(eyes = Eyes.CLOSED, mouth = Mouth.OPEN, leftArm = Arm.UP, rightArm = Arm.UP, bob = -1),
        HoodiePose(eyes = Eyes.HAPPY),
        HoodiePose(),
    )),
    STRETCH("Alongar", 4, false, listOf(
        HoodiePose(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED),
        HoodiePose(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED, bob = -1),
        HoodiePose(leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY, bob = -1),
        HoodiePose(eyes = Eyes.HAPPY),
    )),
    STRETCH_SIT("Alongar sentado", 4, false, listOf(
        HoodiePose(legs = Legs.SIT, leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.CLOSED, mouth = Mouth.OPEN),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY),
        HoodiePose(legs = Legs.SIT, eyes = Eyes.HAPPY),
    )),
    WORK_TYPING("Digitando", 6, true, listOf(
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, eyes = Eyes.FOCUSED),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.FOCUSED),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT),
    )),
    WORK_MOUSE("Usando mouse", 3, true, listOf(
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, eyes = Eyes.LOOK_RIGHT),
    )),
    WORK_READ("Lendo a tela", 2, true, listOf(
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, eyes = Eyes.LOOK_RIGHT, bob = 1),
    )),
    THINK("Pensando", 2, true, listOf(
        HoodiePose(legs = Legs.SIT, rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT),
        HoodiePose(legs = Legs.SIT, rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT, bob = 1),
    )),
    THINK_STAND("Pensando em pé", 2, true, listOf(
        HoodiePose(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT),
        HoodiePose(rightArm = Arm.CHIN, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT, bob = 1),
    )),
    COFFEE_SIT("Café sentado", 3, true, coffee(Legs.SIT)),
    COFFEE_STAND("Café em pé", 3, true, coffee(Legs.STAND)),
    PHONE_SIT("Celular sentado", 3, true, phone(Legs.SIT)),
    PHONE_STAND("Celular em pé", 3, true, phone(Legs.STAND)),
    EAT("Comer", 4, true, listOf(
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_CHEST, item = Item.FORK),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_MOUTH, item = Item.FORK, mouth = Mouth.OPEN),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_CHEST, item = Item.FORK, eyes = Eyes.HAPPY, mouth = Mouth.CHEW),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.FORWARD_DOWN, rightArm = Arm.HOLD_CHEST, item = Item.FORK, eyes = Eyes.HAPPY, mouth = Mouth.SMILE),
    )),
    GAMING("Videogame", 6, true, listOf(
        HoodiePose(legs = Legs.SIT, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.CONTROLLER, eyes = Eyes.LOOK_RIGHT),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.CONTROLLER, eyes = Eyes.LOOK_RIGHT, bob = 1),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.CONTROLLER, eyes = Eyes.FOCUSED),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.CONTROLLER, eyes = Eyes.LOOK_RIGHT, bob = 1),
    )),
    GAMING_EXCITED("Videogame animado", 8, false, listOf(
        HoodiePose(legs = Legs.SIT, leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.WIDE, mouth = Mouth.OPEN, bob = -1),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.UP, rightArm = Arm.UP, eyes = Eyes.WIDE, mouth = Mouth.OPEN, bob = -1),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.CONTROLLER, eyes = Eyes.HAPPY),
    )),
    READING("Ler", 1, true, listOf(
        HoodiePose(legs = Legs.SIT, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.BOOK, eyes = Eyes.LOOK_DOWN),
        HoodiePose(legs = Legs.SIT, leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.BOOK, eyes = Eyes.LOOK_DOWN, bob = 1),
    )),
    WATCH_TV("Ver TV", 2, true, listOf(
        HoodiePose(legs = Legs.SIT, eyes = Eyes.LOOK_RIGHT),
        HoodiePose(legs = Legs.SIT, eyes = Eyes.LOOK_RIGHT, bob = 1),
    )),
    NAP_SIT("Cochilo", 2, true, listOf(
        HoodiePose(legs = Legs.SIT, eyes = Eyes.CLOSED),
        HoodiePose(legs = Legs.SIT, eyes = Eyes.CLOSED, bob = 1),
    )),
    COOK("Cozinhar", 4, true, listOf(
        HoodiePose(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN, item = Item.PAN, eyes = Eyes.FOCUSED),
        HoodiePose(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, item = Item.PAN, eyes = Eyes.FOCUSED),
        HoodiePose(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_DOWN, item = Item.PAN, eyes = Eyes.HAPPY),
        HoodiePose(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_UP, item = Item.PAN, eyes = Eyes.FOCUSED),
    )),
    CLEAN("Varrer", 4, true, listOf(
        HoodiePose(leftArm = Arm.FORWARD_DOWN, rightArm = Arm.FORWARD_DOWN, item = Item.BROOM, eyes = Eyes.LOOK_DOWN),
        HoodiePose(leftArm = Arm.FORWARD_UP, rightArm = Arm.FORWARD_UP, item = Item.BROOM, eyes = Eyes.LOOK_DOWN, bob = 1),
    )),
    RUN("Esteira", 10, true, listOf(
        HoodiePose(legs = Legs.RUN_A, leftArm = Arm.SWING_FRONT, rightArm = Arm.SWING_BACK, eyes = Eyes.FOCUSED, mouth = Mouth.OPEN, bob = -1),
        HoodiePose(legs = Legs.STAND, eyes = Eyes.FOCUSED, mouth = Mouth.OPEN),
        HoodiePose(legs = Legs.RUN_B, leftArm = Arm.SWING_BACK, rightArm = Arm.SWING_FRONT, eyes = Eyes.FOCUSED, mouth = Mouth.OPEN, bob = -1),
        HoodiePose(legs = Legs.STAND, eyes = Eyes.FOCUSED, mouth = Mouth.OPEN),
    )),
    LIFT("Levantar peso", 2, true, listOf(
        HoodiePose(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.FOCUSED, mouth = Mouth.FLAT),
        HoodiePose(leftArm = Arm.UP, rightArm = Arm.UP, item = Item.DUMBBELL, itemInBothHands = true, eyes = Eyes.CLOSED, mouth = Mouth.FLAT, bob = -1),
    )),
    WATER("Beber água", 3, true, listOf(
        HoodiePose(rightArm = Arm.HOLD_CHEST, item = Item.BOTTLE),
        HoodiePose(rightArm = Arm.HOLD_MOUTH, item = Item.BOTTLE, eyes = Eyes.CLOSED),
        HoodiePose(rightArm = Arm.HOLD_MOUTH, item = Item.BOTTLE, eyes = Eyes.CLOSED),
        HoodiePose(rightArm = Arm.HOLD_CHEST, item = Item.BOTTLE, eyes = Eyes.HAPPY),
    )),
    WAVE("Acenar", 6, false, listOf(
        HoodiePose(rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN),
        HoodiePose(rightArm = Arm.WAVE, eyes = Eyes.HAPPY, mouth = Mouth.OPEN),
        HoodiePose(rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN),
        HoodiePose(rightArm = Arm.WAVE, eyes = Eyes.HAPPY, mouth = Mouth.OPEN),
        HoodiePose(rightArm = Arm.UP, eyes = Eyes.HAPPY),
        HoodiePose(eyes = Eyes.HAPPY),
    )),
    WAVE_SIT("Acenar sentado", 6, false, listOf(
        HoodiePose(legs = Legs.SIT, rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN),
        HoodiePose(legs = Legs.SIT, rightArm = Arm.WAVE, eyes = Eyes.HAPPY, mouth = Mouth.OPEN),
        HoodiePose(legs = Legs.SIT, rightArm = Arm.UP, eyes = Eyes.HAPPY, mouth = Mouth.OPEN),
        HoodiePose(legs = Legs.SIT, rightArm = Arm.WAVE, eyes = Eyes.HAPPY, mouth = Mouth.OPEN),
        HoodiePose(legs = Legs.SIT, eyes = Eyes.HAPPY),
    )),
    HAPPY("Feliz", 6, false, listOf(
        HoodiePose(eyes = Eyes.HAPPY, mouth = Mouth.OPEN, bob = -1),
        HoodiePose(eyes = Eyes.HAPPY, mouth = Mouth.OPEN),
        HoodiePose(eyes = Eyes.HAPPY, mouth = Mouth.OPEN, bob = -1),
        HoodiePose(eyes = Eyes.HAPPY),
    )),
    SURPRISED("Surpreso", 6, false, listOf(
        HoodiePose(eyes = Eyes.WIDE, mouth = Mouth.OPEN, bob = -1),
        HoodiePose(eyes = Eyes.WIDE, mouth = Mouth.OPEN),
        HoodiePose(eyes = Eyes.WIDE, mouth = Mouth.OPEN),
        HoodiePose(),
    )),
    SHRUG("Dar de ombros", 4, false, listOf(
        HoodiePose(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT),
        HoodiePose(leftArm = Arm.HOLD_CHEST, rightArm = Arm.HOLD_CHEST, eyes = Eyes.LOOK_UP, mouth = Mouth.FLAT, bob = -1),
        HoodiePose(eyes = Eyes.OPEN),
    )),
    LOOK_AROUND("Olhar em volta", 2, true, listOf(
        HoodiePose(eyes = Eyes.LOOK_LEFT), HoodiePose(eyes = Eyes.OPEN),
        HoodiePose(eyes = Eyes.LOOK_RIGHT), HoodiePose(eyes = Eyes.OPEN, earTwitch = true),
    )),
    BUS_SIT("Sentado no ônibus", 2, true, listOf(
        HoodiePose(legs = Legs.SIT, backpack = true, eyes = Eyes.LOOK_LEFT),
        HoodiePose(legs = Legs.SIT, backpack = true, eyes = Eyes.LOOK_LEFT, bob = 1),
        HoodiePose(legs = Legs.SIT, backpack = true, eyes = Eyes.LOOK_LEFT),
        HoodiePose(legs = Legs.SIT, backpack = true, eyes = Eyes.OPEN, bob = 1),
    )),
    ;

    val frameDurationMs: Long get() = 1000L / fps
    val durationMs: Long get() = frameDurationMs * frames.size

    fun frameAt(elapsedMs: Long): HoodiePose {
        val index = (elapsedMs / frameDurationMs).toInt()
        return if (loop) frames[index % frames.size] else frames[index.coerceAtMost(frames.size - 1)]
    }
}

private fun breathing(base: HoodiePose) = listOf(base, base, base.copy(bob = 1), base.copy(bob = 1))

private fun walk(backpack: Boolean) = listOf(
    HoodiePose(legs = Legs.STEP_LEFT, leftArm = Arm.SWING_BACK, rightArm = Arm.SWING_FRONT, backpack = backpack, bob = -1),
    HoodiePose(legs = Legs.STAND, backpack = backpack),
    HoodiePose(legs = Legs.STEP_RIGHT, leftArm = Arm.SWING_FRONT, rightArm = Arm.SWING_BACK, backpack = backpack, bob = -1),
    HoodiePose(legs = Legs.STAND, backpack = backpack),
)

private fun coffee(legs: Legs) = listOf(
    HoodiePose(legs = legs, rightArm = Arm.HOLD_CHEST, item = Item.MUG),
    HoodiePose(legs = legs, rightArm = Arm.HOLD_CHEST, item = Item.MUG, bob = 1),
    HoodiePose(legs = legs, rightArm = Arm.HOLD_MOUTH, item = Item.MUG, eyes = Eyes.CLOSED),
    HoodiePose(legs = legs, rightArm = Arm.HOLD_MOUTH, item = Item.MUG, eyes = Eyes.CLOSED),
    HoodiePose(legs = legs, rightArm = Arm.HOLD_CHEST, item = Item.MUG, eyes = Eyes.HAPPY),
    HoodiePose(legs = legs, rightArm = Arm.HOLD_CHEST, item = Item.MUG, bob = 1),
)

private fun phone(legs: Legs) = listOf(
    HoodiePose(legs = legs, leftArm = Arm.DOWN, rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN),
    HoodiePose(legs = legs, leftArm = Arm.DOWN, rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN, bob = 1),
    HoodiePose(legs = legs, leftArm = Arm.DOWN, rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.LOOK_DOWN),
    HoodiePose(legs = legs, leftArm = Arm.DOWN, rightArm = Arm.HOLD_CHEST, item = Item.PHONE, eyes = Eyes.HAPPY),
)
