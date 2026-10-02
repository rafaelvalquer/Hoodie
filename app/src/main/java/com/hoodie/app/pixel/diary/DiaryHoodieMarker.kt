package com.hoodie.app.pixel.diary

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.HoodiePalette
import kotlin.math.abs

enum class MarkerDirection { FRONT, BACK, LEFT, RIGHT }
enum class MarkerPose { IDLE, WALK_FRONT, WALK_BACK, WALK_SIDE }

/** Onde e como o mini Hoodie está no mapa num instante do replay. */
data class MarkerState(val position: MapPoint, val direction: MarkerDirection, val walking: Boolean)

/**
 * Mini Hoodie 16×24 que anda pelas ruas do mapa no replay. Desenhado à mão em
 * grade (mesma paleta do Hoodie grande); a vista da direita é o espelho da
 * esquerda, como no sprite principal.
 */
object DiaryHoodieMarker {
    const val WIDTH = 16
    const val HEIGHT = 24
    /** Pés: o ponto do sprite que fica sobre o caminho. */
    const val FEET_X = 8
    const val FEET_Y = 23
    const val WALK_FRAMES = 4

    /** Regra de direção: o eixo dominante decide; vertical para baixo é a vista de frente. */
    fun direction(dx: Float, dy: Float): MarkerDirection = when {
        abs(dx) > abs(dy) -> if (dx > 0) MarkerDirection.RIGHT else MarkerDirection.LEFT
        dy < 0 -> MarkerDirection.BACK
        else -> MarkerDirection.FRONT
    }

    fun poseFor(direction: MarkerDirection, walking: Boolean): MarkerPose = when {
        !walking -> MarkerPose.IDLE
        direction == MarkerDirection.BACK -> MarkerPose.WALK_BACK
        direction == MarkerDirection.FRONT -> MarkerPose.WALK_FRONT
        else -> MarkerPose.WALK_SIDE
    }

    /** Estado no meio de um deslocamento ([progress] 0..1) ou parado na porta de um lugar. */
    fun onTrip(trip: DiaryMapTrip, progress: Float): MarkerState {
        val p = DiaryRoadBuilder.pointAt(trip.path, progress)
        val (dx, dy) = DiaryRoadBuilder.stepAt(trip.path, progress)
        val walking = trip.path.size > 1 && progress > 0f && progress < 1f
        return MarkerState(p, direction(dx.toFloat(), dy.toFloat()), walking)
    }

    fun atNode(node: DiaryMapPlaceNode) = MarkerState(node.entrance, MarkerDirection.FRONT, walking = false)

    private data class Key(val pose: MarkerPose, val back: Boolean, val frame: Int, val blink: Boolean)
    private val cache = HashMap<Key, PixelBuffer>()

    /** Parado, pisca por ~160 ms a cada 3,2 s. */
    fun isBlinking(walking: Boolean, timeMs: Long) = !walking && timeMs % 3_200 < 160

    fun sprite(direction: MarkerDirection, walking: Boolean, timeMs: Long): PixelBuffer {
        val pose = poseFor(direction, walking)
        val frame = if (walking) ((timeMs / 140) % WALK_FRAMES).toInt() else ((timeMs / 700) % 2).toInt()
        val blink = isBlinking(walking, timeMs) && direction != MarkerDirection.BACK
        val base = synchronized(cache) {
            cache.getOrPut(Key(pose, direction == MarkerDirection.BACK, frame, blink)) { draw(pose, direction, frame, blink) }
        }
        return if (direction == MarkerDirection.RIGHT) mirror(base) else base
    }

    fun mirror(src: PixelBuffer) = PixelBuffer(src.width, src.height).also { it.blit(src, 0, 0, flipX = true) }

    /** Desenha o marcador com os pés em [state].position. */
    fun paint(b: PixelBuffer, state: MarkerState, timeMs: Long) {
        val s = sprite(state.direction, state.walking, timeMs)
        val x = state.position.x.toInt() - FEET_X
        val y = state.position.y.toInt() + 3 - FEET_Y
        // Sombra.
        b.hline(x + 4, x + 11, y + FEET_Y + 1, 0x66000000)
        b.blit(s, x, y)
    }

    // ───────────── Desenho ─────────────

    private val COLORS = mapOf(
        'k' to HoodiePalette.OUTLINE, 'f' to HoodiePalette.FUR, 'F' to HoodiePalette.FUR_SHADE, 'l' to HoodiePalette.FUR_LIGHT,
        'i' to HoodiePalette.INNER_EAR, 'h' to HoodiePalette.HOOD, 'H' to HoodiePalette.HOOD_SHADE, 'd' to HoodiePalette.HOOD_DARK,
        'e' to HoodiePalette.EYE, 'n' to HoodiePalette.NOSE, 's' to HoodiePalette.STRING,
    )

    /** Metade esquerda (8 colunas) espelhada: corpo de frente, linhas 0..18. */
    private val FRONT_HALF = listOf(
        "..kk....",
        ".kik....",
        ".kifkkkk",
        "kflfllll",
        "kfffffff",
        "kffeffff",
        "kffeffff",
        "kfffffkn",
        "kFfffffk",
        ".kFFFFFF",
        "..kkkkkk",
        ".khhhhhh",
        "khhhhhsh",
        "khhhhhsh",
        "kfhhhhhh",
        "kfhhhhhh",
        "kkHHHHHH",
        ".kHdHdHd",
        "..kkkkkk",
    )

    private val BACK_HALF = listOf(
        "..kk....",
        ".kfk....",
        ".kffkkkk",
        "kflfllll",
        "kfffffff",
        "kfffffff",
        "kfffffff",
        "kffffFFF",
        "kFfkkkkk",
        ".kkhhhhh",
        "..khhhhh",
        ".khhhhhh",
        "khhhhhhh",
        "khhhhHHh",
        "kfhhhhhh",
        "kfhhhhhh",
        "kkHHHHHH",
        ".kHdHdHd",
        "..kkkkkk",
    )

    /** Perfil virado para a esquerda, 16 colunas, linhas 0..17. */
    private val SIDE = listOf(
        "....kk..kk......",
        "...kik.kfk......",
        "..kkifkkfkkk....",
        ".kflllllfffk....",
        ".kfffffffffFk...",
        "kffefffffffFk...",
        "kffefffffffFk...",
        "knffffffffFFk...",
        ".kkfffffffFFk...",
        "..kkkkkkkkkk....",
        "...khhhhhhhk....",
        "..kshhhhhhhHk...",
        "..kshhhhhhhHkk..",
        "...kfhhhhhHHkfk.",
        "...kfhhhhhHHkfk.",
        "...kHHHHHHHHklk.",
        "....kdHdHdHdkk..",
        ".....kkkkkkkk...",
    )

    private fun draw(pose: MarkerPose, direction: MarkerDirection, frame: Int, blink: Boolean = false): PixelBuffer {
        val b = PixelBuffer(WIDTH, HEIGHT)
        val walking = pose != MarkerPose.IDLE
        // Respiração (parado) ou balanço do passo (andando).
        val bob = if (walking) (if (frame % 2 == 1) 1 else 0) else frame
        val side = pose == MarkerPose.WALK_SIDE
        if (side) {
            // Pernas: a da frente e a de trás trocam de lugar a cada passo.
            val near = if (walking) intArrayOf(-2, 0, 2, 0)[frame] else 0
            val far = if (walking) -near else 2
            val lift = if (walking && frame % 2 == 1) 1 else 0
            leg(b, 7 + far, 17, 23, HoodiePalette.FUR_SHADE)
            leg(b, 5 + near, 17, 23 - lift, HoodiePalette.FUR)
            grid(b, SIDE, 0, bob)
            if (blink) for (y in 5..6) if (b[3, y + bob] == HoodiePalette.EYE) b.set(3, y + bob, if (y == 6) HoodiePalette.OUTLINE else HoodiePalette.FUR)
            // Rabo acompanha a passada: a ponta sobe e desce.
            val tip = if (frame % 2 == 1) 12 else 13
            b.set(14, tip + bob, HoodiePalette.OUTLINE); b.set(13, tip + bob + 1, HoodiePalette.FUR)
        } else {
            // Rabo aparecendo ao lado da perna, balançando (pernas e corpo ficam por cima).
            val sway = if (pose == MarkerPose.IDLE) frame else intArrayOf(0, 1, 0, -1)[frame]
            b.box(13 + sway, 17, 14 + sway, 21, HoodiePalette.OUTLINE)
            b.vline(13 + sway, 18, 20, HoodiePalette.FUR_SHADE)
            val (l, r) = if (walking) when (frame) { 1 -> 1 to 0; 3 -> 0 to 1; else -> 0 to 0 } else 0 to 0
            leg(b, 3, 17, 23 - l, HoodiePalette.FUR)
            leg(b, 9, 17, 23 - r, HoodiePalette.FUR)
            val half = if (direction == MarkerDirection.BACK) BACK_HALF else FRONT_HALF
            grid(b, half.map { it + it.reversed() }, 0, bob)
            if (blink) for (x in 0 until WIDTH) for (y in 5..6) if (b[x, y + bob] == HoodiePalette.EYE) b.set(x, y + bob, if (y == 6) HoodiePalette.OUTLINE else HoodiePalette.FUR)
            // Braços balançam opostos às pernas.
            if (walking && l + r > 0) {
                val armY = 14 + bob
                b.set(if (l > 0) 14 else 1, armY + 1, HoodiePalette.FUR)
            }
        }
        return b
    }

    private fun leg(b: PixelBuffer, x: Int, top: Int, bottom: Int, fill: Int) {
        b.box(x, top, x + 3, bottom, HoodiePalette.OUTLINE)
        b.box(x + 1, top, x + 2, bottom - 1, fill)
    }

    private fun grid(b: PixelBuffer, rows: List<String>, dx: Int, dy: Int) {
        rows.forEachIndexed { y, line ->
            line.forEachIndexed { x, ch -> COLORS[ch]?.let { b.set(x + dx, y + dy, it) } }
        }
    }
}
