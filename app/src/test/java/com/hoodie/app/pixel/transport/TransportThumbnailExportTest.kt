package com.hoodie.app.pixel.transport

import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Posture
import com.hoodie.app.pixel.sprite.ProceduralSpriteProvider
import com.hoodie.app.pixel.sprite.SpriteFrame
import com.hoodie.app.pixel.sprite.SpriteRequest
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Thumbnails de enquadramento das cenas de transporte V3 (docs/transport-art-bible.md §Thumbnails).
 * Só blocos de valor em 3 cinzas + o sprite real do Hoodie em 1:1 — para escolher composição,
 * não arte. Só exporta; não aprova nada.
 *
 * `./gradlew :app:testDebugUnitTest -PtransportThumbs=true --tests "*TransportThumbnailExportTest*"`
 */
class TransportThumbnailExportTest {
    private val out = File(System.getProperty("transportThumbsOutput") ?: "build/pixel-preview/transport-thumbnails")

    private companion object {
        const val W = 240
        const val H = 320
        const val LIGHT = 0xFFD2D2D2.toInt()
        const val MID = 0xFF8E8E8E.toInt()
        const val DARK = 0xFF3A3A3A.toInt()
    }

    // ───────────── Pincel de valor ─────────────

    /** Retângulo recortado no canvas (o `PixelBuffer.rect` estoura o array com retângulos fora da borda direita na última linha). */
    private fun PixelBuffer.r(x0: Int, y0: Int, x1: Int, y1: Int, c: Int) {
        val a = x0.coerceAtLeast(0); val b = y0.coerceAtLeast(0)
        val e = x1.coerceAtMost(width - 1); val f = y1.coerceAtMost(height - 1)
        if (a <= e && b <= f) box(a, b, e, f, c)
    }

    private fun PixelBuffer.ring(cx: Int, cy: Int, r: Int, width: Int, c: Int) {
        for (dy in -r..r) for (dx in -r..r) {
            val d2 = dx * dx + dy * dy
            if (d2 <= r * r && d2 >= (r - width) * (r - width)) set(cx + dx, cy + dy, c)
        }
    }

    /** Silhueta neutra de NPC (cabeça + corpo), 1:1 com um sprite 48×72. */
    private fun PixelBuffer.npcSeated(hipX: Int, hipY: Int, c: Int) {
        disc(hipX, hipY - 46, 11, c)
        r(hipX - 13, hipY - 34, hipX + 13, hipY, c)
        r(hipX - 13, hipY, hipX - 4, hipY + 14, c); r(hipX + 4, hipY, hipX + 13, hipY + 14, c)
    }

    private fun PixelBuffer.npcStanding(feetX: Int, feetY: Int, c: Int, armUp: Int? = null) {
        disc(feetX, feetY - 60, 11, c)
        r(feetX - 13, feetY - 48, feetX + 13, feetY - 20, c)
        r(feetX - 12, feetY - 20, feetX - 3, feetY, c); r(feetX + 3, feetY - 20, feetX + 12, feetY, c)
        armUp?.let { ax -> r(minOf(ax, feetX) , feetY - 86, maxOf(ax, feetX) + 3, feetY - 82, c); r(ax, feetY - 86, ax + 3, feetY - 44, c) }
    }

    private fun frame(anim: AnimationId, dir: Direction, posture: Posture): SpriteFrame =
        ProceduralSpriteProvider.frame(SpriteRequest(anim, dir, 0, posture))

    /** Cola o Hoodie 1:1 com [anchor] do sprite em ([x], [y]). */
    private fun PixelBuffer.hoodie(f: SpriteFrame, x: Int, y: Int, anchor: (SpriteFrame) -> com.hoodie.app.pixel.sprite.Point) {
        val a = anchor(f)
        blit(f.image, x - a.x, y - a.y)
    }

    private val car = frame(AnimationId.CAR_IDLE, Direction.RIGHT, Posture.SITTING)
    private val sitFront = frame(AnimationId.TRAIN_SIT, Direction.FRONT, Posture.SIT_FRONT)
    private val metroSitFront = frame(AnimationId.METRO_SIT, Direction.FRONT, Posture.SIT_FRONT)
    private val handle = frame(AnimationId.METRO_HANDLE, Direction.FRONT, Posture.STANDING)
    private fun SpriteFrame.hip() = anchors.seatHip ?: anchors.feet

    // ───────────── Carro ─────────────

    /** A — close da porta do motorista: carro maior que o canvas, roda dianteira cortada à direita. */
    private fun carA() = PixelBuffer(W, H).apply {
        fill(LIGHT)
        listOf(0 to 120, 34 to 104, 70 to 126, 108 to 98, 150 to 118, 196 to 108).forEach { (x, top) -> r(x, top, x + 30, 150, MID) }
        r(0, 296, W - 1, H - 1, DARK)
        r(0, 116, W - 1, 290, MID)                         // corpo cortado nas duas bordas
        r(0, 112, 200, 118, MID)                           // teto
        r(78, 126, 152, 182, DARK)                         // janela 74×56 (interior escuro)
        hoodie(car, 116, 182 + 18, { it.hip() })       // quadril abaixo do peitoril
        r(0, 183, W - 1, 290, MID)                         // porta: oclusor que corta no peito
        ring(146, 172, 12, 3, DARK)                        // volante na frente do peito
        r(0, 186, W - 1, 187, DARK); r(150, 186, 151, 290, DARK); r(100, 214, 116, 217, DARK)
        disc(232, 290, 34, DARK); disc(232, 290, 12, MID)  // roda dianteira cortada
        r(204, 240, W - 1, 252, DARK)                      // para-lama
    }

    /** B — três quartos: carro inteiro na largura, capô à direita, mais cidade. */
    private fun carB() = PixelBuffer(W, H).apply {
        fill(LIGHT)
        listOf(0 to 70, 40 to 96, 88 to 60, 130 to 90, 176 to 74, 214 to 100).forEach { (x, top) -> r(x, top, x + 34, 180, MID) }
        r(0, 290, W - 1, H - 1, DARK)
        r(14, 150, 176, 160, MID)                          // teto
        r(30, 160, 92, 204, DARK)                          // janela traseira
        r(100, 160, 172, 204, DARK)                        // janela do motorista
        hoodie(car, 138, 204 + 18, { it.hip() })
        r(10, 204, 236, 274, MID); r(172, 192, 236, 206, MID)   // porta + capô
        ring(166, 194, 10, 3, DARK)
        disc(60, 280, 22, DARK); disc(60, 280, 8, MID); disc(196, 280, 22, DARK); disc(196, 280, 8, MID)
    }

    /** C — janela grande: porta enche a base, retrovisor como oclusor, interior com a janela oposta. */
    private fun carC() = PixelBuffer(W, H).apply {
        fill(LIGHT)
        listOf(0 to 30, 50 to 18, 110 to 34, 170 to 14).forEach { (x, top) -> r(x, top, x + 40, 56, MID) }
        r(0, 56, W - 1, 70, MID)                           // teto
        r(30, 70, 206, 176, DARK)                          // janela 176×106
        r(42, 82, 112, 128, LIGHT)                         // janela do outro lado (profundidade)
        hoodie(car, 120, 176 + 6, { it.hip() })
        ring(176, 150, 22, 4, MID)                         // volante grande
        r(0, 176, W - 1, H - 1, MID)                       // porta
        r(0, 176, W - 1, 179, DARK); r(150, 222, 178, 228, DARK)
        r(206, 70, 214, 176, MID)                          // coluna A
        r(212, 118, W - 1, 152, DARK)                      // retrovisor cortado pela borda
    }

    // ───────────── Trem ─────────────

    private fun PixelBuffer.trainRoom(winTop: Int, winBottom: Int, benchX0: Int, benchX1: Int, tunnel: Boolean = false) {
        fill(MID)
        r(0, 0, W - 1, 24, DARK); r(20, 10, 220, 14, LIGHT)       // teto + luminária
        r(0, winTop, W - 1, winBottom, if (tunnel) DARK else LIGHT)
        if (tunnel) for (k in 0 until 3) r(0, winTop + 14 + k * 18, W - 1, winTop + 15 + k * 18, LIGHT)
        else listOf(10 to 22, 60 to 10, 120 to 26, 180 to 14).forEach { (x, d) -> r(x, winBottom - 30 + d, x + 36, winBottom, MID) }
        for (x in listOf(78, 158)) r(x, winTop, x + 3, winBottom, DARK)  // montantes
        r(benchX0, winBottom + 10, benchX1, 224, DARK)                    // encosto
        r(benchX0, 224, benchX1, 236, MID)                                // assento
        r(benchX0, 236, benchX1, 262, DARK)
        r(0, 262, W - 1, H - 1, MID)
    }

    /** A — banco longitudinal, 3 lugares, Hoodie no meio, barra + NPC em pé cortado à esquerda. */
    private fun trainA() = PixelBuffer(W, H).apply {
        trainRoom(60, 140, 24, 216)
        npcSeated(60, 226, MID)
        hoodie(sitFront, 120, 226, { it.hip() })
        r(26, 0, 30, H - 1, DARK)
        npcStanding(12, 318, DARK, armUp = 27)
    }

    /** B — mais perto: 2 lugares, Hoodie à direita do centro, barra e NPC em pé cortados à direita. */
    private fun trainB() = PixelBuffer(W, H).apply {
        trainRoom(46, 126, 0, W - 1)
        npcSeated(70, 226, MID)
        hoodie(sitFront, 150, 226, { it.hip() })
        r(214, 0, 218, H - 1, DARK)
        npcStanding(230, 318, DARK, armUp = 214)
    }

    /** C — janela grande, alças em primeiro plano no topo, barra central. */
    private fun trainC() = PixelBuffer(W, H).apply {
        trainRoom(40, 150, 0, W - 1)
        hoodie(sitFront, 76, 226, { it.hip() })
        npcSeated(168, 226, MID)
        r(118, 0, 121, H - 1, DARK)
        for (x in listOf(20, 70, 170, 220)) { r(x, 0, x + 1, 30, DARK); ring(x, 38, 7, 2, DARK) }
    }

    // ───────────── Metrô ─────────────

    /** A — sentado no meio, túnel na janela, NPC em pé segurando a alça cortado à direita. */
    private fun metroA() = PixelBuffer(W, H).apply {
        trainRoom(56, 136, 18, 222, tunnel = true)
        npcSeated(60, 226, MID)
        hoodie(metroSitFront, 120, 226, { it.hip() })
        for (x in listOf(40, 100, 160)) { r(x, 0, x + 1, 26, DARK); ring(x, 33, 6, 2, DARK) }
        npcStanding(228, 318, DARK, armUp = 212)
    }

    /** B — Hoodie em pé segurando a barra; porta com a estação ao fundo; banco atrás. */
    private fun metroB() = PixelBuffer(W, H).apply {
        fill(MID)
        r(0, 0, W - 1, 24, DARK); r(20, 10, 220, 14, LIGHT)
        r(0, 56, 70, 136, DARK); r(170, 56, W - 1, 136, DARK)              // janelas de túnel
        r(0, 92, 70, 93, LIGHT); r(170, 104, W - 1, 105, LIGHT)
        r(80, 40, 160, 262, DARK); r(92, 56, 148, 150, LIGHT)              // porta com janela (estação)
        r(119, 40, 120, 262, MID)
        npcSeated(36, 226, MID); r(0, 224, 70, 236, MID); r(170, 224, W - 1, 236, MID)
        r(0, 262, W - 1, H - 1, MID)
        val left = 126 - handle.anchors.feet.x
        val hand = handle.anchors.leftHand
        r(left + hand.x - 1, 0, left + hand.x + 1, H - 1, DARK)            // barra (atrás da mão)
        hoodie(handle, 126, 300) { it.anchors.feet }
        r(4, 0, 8, H - 1, DARK)                                            // barra cortada à esquerda
    }

    /** C — sentado ao lado da porta central (estação), NPC do outro lado, alças no topo. */
    private fun metroC() = PixelBuffer(W, H).apply {
        trainRoom(56, 136, 0, W - 1, tunnel = true)
        r(96, 40, 150, 262, DARK); r(104, 56, 142, 150, LIGHT)             // porta central
        r(122, 40, 123, 262, MID)
        npcSeated(52, 226, MID)
        hoodie(metroSitFront, 192, 226, { it.hip() })
        for (x in listOf(24, 76, 172, 220)) { r(x, 0, x + 1, 28, DARK); ring(x, 35, 6, 2, DARK) }
    }

    @Test fun exportFramingThumbnails() {
        assumeTrue(System.getProperty("transportThumbs") == "true")
        val all = linkedMapOf(
            "car-a" to carA(), "car-b" to carB(), "car-c" to carC(),
            "train-a" to trainA(), "train-b" to trainB(), "train-c" to trainC(),
            "metro-a" to metroA(), "metro-b" to metroB(), "metro-c" to metroC(),
        )
        all.forEach { (name, img) -> PreviewExport.write(File(out, "$name.png"), img, 2, null) }
        // Folha 3×3 para comparar lado a lado (linhas: carro, trem, metrô).
        val sheet = PixelBuffer(W * 3 + 16, H * 3 + 16).apply { fill(0xFF2B2E4A.toInt()) }
        all.values.forEachIndexed { i, img -> sheet.blit(img, (i % 3) * (W + 8), (i / 3) * (H + 8)) }
        PreviewExport.write(File(out, "contact-sheet.png"), sheet, 1, 0xFF2B2E4A.toInt())
    }
}
