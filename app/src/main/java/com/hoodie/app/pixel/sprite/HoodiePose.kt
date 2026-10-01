package com.hoodie.app.pixel.sprite

enum class Eyes { OPEN, CLOSED, HAPPY, WIDE, FOCUSED, SLEEPY, LOOK_LEFT, LOOK_RIGHT, LOOK_UP, LOOK_DOWN }

enum class Mouth { SMILE, OPEN, FLAT, CHEW }

enum class Arm { DOWN, SWING_FRONT, SWING_BACK, FORWARD_UP, FORWARD_DOWN, HOLD_CHEST, HOLD_MOUTH, CHIN, UP, WAVE }

enum class Legs { STAND, STEP_LEFT, STEP_RIGHT, RUN_A, RUN_B, SIT }

/** Pontos de encaixe: o item é desenhado na mão, não em um sprite separado por combinação. */
enum class Item { NONE, MUG, PHONE, BOOK, CONTROLLER, DUMBBELL, BROOM, PAN, BOTTLE, FORK }

/** Expressões aplicáveis por cima de qualquer animação. */
enum class Expression(val label: String, val eyes: Eyes?) {
    NORMAL("Normal", null),
    HAPPY("Feliz", Eyes.HAPPY),
    SLEEPY("Sonolento", Eyes.SLEEPY),
    FOCUSED("Focado", Eyes.FOCUSED),
    SURPRISED("Surpreso", Eyes.WIDE),
    TIRED("Cansado", Eyes.SLEEPY),
    EXCITED("Animado", Eyes.WIDE),
}

/**
 * Uma pose completa do Hoodie. O sprite é gerado a partir dela, então proporção,
 * rosto e moletom são idênticos em todos os frames por construção.
 */
data class HoodiePose(
    val eyes: Eyes = Eyes.OPEN,
    val mouth: Mouth = Mouth.SMILE,
    val leftArm: Arm = Arm.DOWN,
    val rightArm: Arm = Arm.DOWN,
    val legs: Legs = Legs.STAND,
    /** Deslocamento vertical do tronco/cabeça (respiração, passos). */
    val bob: Int = 0,
    val earTwitch: Boolean = false,
    val backpack: Boolean = false,
    val item: Item = Item.NONE,
    val itemInBothHands: Boolean = false,
    /** Só a cabeça (deitado na cama, coberto). */
    val headOnly: Boolean = false,
    val blush: Boolean = false,
)
