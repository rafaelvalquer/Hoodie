package com.hoodie.app.pixel.animation

import com.hoodie.app.pixel.scene.GazeStep
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.VisualState
import com.hoodie.app.pixel.sprite.Ears
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.PoseOverlay
import kotlin.random.Random

/**
 * Vida entre as animações: piscadas em intervalos irregulares (às vezes duplas),
 * olhadas, orelhas e um alongamento de vez em quando. Frequências:
 *
 *     piscar        3–8 s  (20% piscada dupla)
 *     olhar         8–20 s (ou o roteiro de olhar da atividade)
 *     orelha        10–30 s
 *     alongar       2–6 min
 */
class IdleDirector(private val random: Random) {

    private var nextBlinkAt = -1L
    private var blinkStart = -1L
    private var doubleBlink = false

    private var nextLookAt = -1L
    private var lookUntil = -1L
    private var lookEyes: Eyes? = null

    private var gazeScript: List<GazeStep> = emptyList()
    private var gazeIndex = 0
    private var gazeUntil = -1L

    private var nextEarAt = -1L
    private var earUntil = -1L
    private var earPose: Ears? = null

    private var nextStretchAt = -1L

    /** Sequência da piscada: meio-fechado → fechado → meio-fechado. */
    private val blinkFrames = listOf(Eyes.HALF to 60L, Eyes.CLOSED to 90L, Eyes.HALF to 60L)
    private val blinkMs = blinkFrames.sumOf { it.second }

    fun reset(now: Long) {
        nextBlinkAt = now + random.nextLong(BLINK_MIN, BLINK_MAX)
        nextLookAt = now + random.nextLong(LOOK_MIN, LOOK_MAX)
        nextEarAt = now + random.nextLong(EAR_MIN, EAR_MAX)
        nextStretchAt = now + random.nextLong(STRETCH_MIN, STRETCH_MAX)
        blinkStart = -1; lookEyes = null; earPose = null
    }

    fun setGaze(script: List<GazeStep>, now: Long) {
        if (script == gazeScript) return
        gazeScript = script; gazeIndex = 0
        gazeUntil = if (script.isEmpty()) -1 else now + duration(script[0])
    }

    private fun duration(s: GazeStep) = s.minMs + random.nextLong((s.maxMs - s.minMs).coerceAtLeast(0) + 1)

    /** Overlay do instante [now]. [allowLook] = falso durante caminhadas e sequências. */
    fun overlay(now: Long, allowLook: Boolean): PoseOverlay {
        if (nextBlinkAt < 0) reset(now)
        return PoseOverlay(blink = blink(now), gaze = if (allowLook) gaze(now) else null, ears = ears(now))
    }

    fun blink(now: Long): Eyes? {
        if (blinkStart < 0 && now >= nextBlinkAt) {
            blinkStart = now
            doubleBlink = random.nextInt(100) < DOUBLE_BLINK_PERCENT
        }
        if (blinkStart < 0) return null
        var t = now - blinkStart
        if (doubleBlink && t >= blinkMs + DOUBLE_GAP) t -= blinkMs + DOUBLE_GAP
        else if (doubleBlink && t >= blinkMs) return null
        if (t >= blinkMs) {
            blinkStart = -1
            nextBlinkAt = now + random.nextLong(BLINK_MIN, BLINK_MAX)
            return null
        }
        var acc = 0L
        for ((eyes, ms) in blinkFrames) { acc += ms; if (t < acc) return eyes }
        return null
    }

    private fun gaze(now: Long): Eyes? {
        if (gazeScript.isNotEmpty()) {
            if (now >= gazeUntil) {
                gazeIndex = (gazeIndex + 1) % gazeScript.size
                gazeUntil = now + duration(gazeScript[gazeIndex])
            }
            return gazeScript[gazeIndex].eyes.takeIf { it != Eyes.OPEN }
        }
        if (lookEyes != null && now >= lookUntil) { lookEyes = null; nextLookAt = now + random.nextLong(LOOK_MIN, LOOK_MAX) }
        if (lookEyes == null && now >= nextLookAt) {
            lookEyes = listOf(Eyes.LOOK_LEFT, Eyes.LOOK_RIGHT, Eyes.LOOK_UP)[random.nextInt(3)]
            lookUntil = now + random.nextLong(600, 1_200)
        }
        return lookEyes
    }

    private fun ears(now: Long): Ears? {
        if (earPose != null && now >= earUntil) { earPose = null; nextEarAt = now + random.nextLong(EAR_MIN, EAR_MAX) }
        if (earPose == null && now >= nextEarAt) {
            val roll = random.nextInt(100)
            earPose = when { roll < 40 -> Ears.TWITCH_LEFT; roll < 80 -> Ears.TWITCH_RIGHT; else -> Ears.ALERT }
            earUntil = now + if (earPose == Ears.ALERT) 500 else 220
        }
        return earPose
    }

    /** O alongamento esporádico é uma ação, não overlay: a máquina consulta e enfileira. */
    fun wantsStretch(now: Long): Boolean {
        if (nextStretchAt < 0) reset(now)
        if (now < nextStretchAt) return false
        nextStretchAt = now + random.nextLong(STRETCH_MIN, STRETCH_MAX)
        return true
    }

    companion object {
        const val BLINK_MIN = 3_000L
        const val BLINK_MAX = 8_000L
        const val DOUBLE_BLINK_PERCENT = 20
        const val DOUBLE_GAP = 150L
        const val LOOK_MIN = 8_000L
        const val LOOK_MAX = 20_000L
        const val EAR_MIN = 10_000L
        const val EAR_MAX = 30_000L
        const val STRETCH_MIN = 2 * 60_000L
        const val STRETCH_MAX = 6 * 60_000L
    }
}

/**
 * Reação quando o usuário abre o app:
 *
 *     55% continua o que está fazendo     15% olha para o usuário
 *     10% acena                            8% sorri
 *      5% olha e volta                     4% mexe a orelha
 *      3% reação do contexto (trabalho: olha + acena; jogo: olhada rápida; estudo, compras,
 *         visita e passeio têm a sua)
 */
object ReactionDirector {
    val WEIGHTS = listOf(55, 15, 10, 8, 5, 4, 3)

    fun onAppOpened(visual: VisualState, current: AnimationId?, random: Random): List<AnimationId> {
        // Dormindo ele não é acordado pela abertura do app.
        if (current == AnimationId.SLEEP || current == AnimationId.SLEEP_TURN || visual.scene == SceneId.STREET) return emptyList()
        var roll = random.nextInt(WEIGHTS.sum())
        val bucket = WEIGHTS.indexOfFirst { w -> (roll < w).also { roll -= w } }
        return when (bucket) {
            0 -> emptyList()
            1 -> listOf(AnimationId.NOTICE)
            2 -> listOf(AnimationId.WAVE)
            3 -> listOf(AnimationId.SMILE)
            4 -> listOf(AnimationId.GLANCE)
            5 -> listOf(AnimationId.EAR_FLICK)
            else -> contextual(current)
        }
    }

    fun contextual(current: AnimationId?): List<AnimationId> = when (current?.group) {
        AnimGroup.WORK -> listOf(AnimationId.NOTICE, AnimationId.WAVE)
        AnimGroup.GAME -> listOf(AnimationId.GLANCE)
        AnimGroup.FOOD -> listOf(AnimationId.SMILE)
        // Estudando: tira os olhos do livro e concorda de leve.
        AnimGroup.STUDY -> listOf(AnimationId.GLANCE, AnimationId.NOD)
        // Compras: percebe o usuário entre as prateleiras.
        AnimGroup.SHOPPING -> listOf(AnimationId.NOTICE, AnimationId.SMILE)
        // Loja: mostra a roupa escolhida, todo animado.
        AnimGroup.STORE -> listOf(AnimationId.SMILE, AnimationId.HAPPY)
        // Visita: não larga a conversa, só acena.
        AnimGroup.VISIT -> listOf(AnimationId.WAVE)
        // Passeio: chama para ver a paisagem.
        AnimGroup.LEISURE -> listOf(AnimationId.WAVE, AnimationId.HAPPY)
        else -> listOf(AnimationId.HAPPY)
    }
}
