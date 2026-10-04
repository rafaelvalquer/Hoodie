package com.hoodie.app.pixel.sprite

import com.hoodie.app.pixel.character.CharacterPose

enum class Eyes { OPEN, HALF, CLOSED, HAPPY, WIDE, FOCUSED, SLEEPY, LOOK_LEFT, LOOK_RIGHT, LOOK_UP, LOOK_DOWN }

enum class Mouth { SMILE, OPEN, FLAT, CHEW }

/** HEAD = pata sobre a cabeça (coçar). */
enum class Arm { DOWN, SWING_FRONT, SWING_BACK, FORWARD_UP, FORWARD_DOWN, HOLD_CHEST, HOLD_MOUTH, CHIN, HEAD, UP, WAVE }

/**
 * WALK usa [HoodiePose.stride] (0..7) — ciclo de 8 poses: contato, descida,
 * passagem, subida, para cada pé. INHERIT herda a postura atual (em pé ou
 * sentado), o que permite reações/ações sem duplicar clips.
 */
enum class Legs { STAND, STEP_LEFT, STEP_RIGHT, RUN_A, RUN_B, SIT, WALK, INHERIT }

/** Orelhas transmitem personalidade com 2–3 pixels. */
enum class Ears { NORMAL, ALERT, RELAXED, TWITCH_LEFT, TWITCH_RIGHT, DOWN }

/** Vista desenhada. LEFT/RIGHT usam SIDE (RIGHT = espelho). */
enum class Facing { FRONT, BACK, SIDE }

/** Pontos de encaixe: o item é desenhado na âncora da mão, não em um sprite por combinação. */
enum class Item { NONE, MUG, PHONE, BOOK, CONTROLLER, DUMBBELL, BROOM, PAN, BOTTLE, FORK, MENU, PENCIL, PRODUCT, SNACK }

/** Expressões aplicáveis por cima de qualquer animação. */
enum class Expression(val label: String, val eyes: Eyes?, val ears: Ears?) {
    NORMAL("Normal", null, null),
    HAPPY("Feliz", Eyes.HAPPY, Ears.ALERT),
    SLEEPY("Sonolento", Eyes.SLEEPY, Ears.RELAXED),
    FOCUSED("Focado", Eyes.FOCUSED, null),
    SURPRISED("Surpreso", Eyes.WIDE, Ears.ALERT),
    TIRED("Cansado", Eyes.SLEEPY, Ears.DOWN),
    EXCITED("Animado", Eyes.WIDE, Ears.ALERT),
}

/**
 * Uma pose completa do Hoodie. O sprite procedural é gerado a partir dela, então
 * proporção, rosto e moletom são idênticos em todos os frames por construção.
 */
typealias HoodiePose = CharacterPose
