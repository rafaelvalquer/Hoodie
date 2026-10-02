package com.hoodie.app.pixel.art

import com.hoodie.app.pixel.animation.AnimGroup
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.sprite.Anchor
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter.Part
import com.hoodie.app.pixel.sprite.HoodiePalette
import com.hoodie.app.pixel.sprite.Point
import com.hoodie.app.pixel.sprite.RequiredAnchors

/**
 * Passes de arte por grupo, pixel a pixel, sobre o bootstrap. É o registro do que
 * foi refinado em código; a revisão humana no Aseprite continua pendente em
 * assets-source/hoodie/art-status.json (manualReview).
 *
 * WALK  — orelhas com micro movimento nos frames de passagem (2 e 6).
 * IDLE  — uma orelha de cada vez se mexe durante a respiração (frames 3 e 6).
 * SLEEP — (transição de deitar refeita no clip: apoia a pata → inclina → deita).
 * WORK  — mãos com brilho no toque da tecla; âncora da mão encaixada na pata.
 * Todos — âncoras de mão obrigatórias sempre em cima de um pixel da pata.
 */
object ArtPasses {

    fun apply(anim: AnimationId, facing: Facing, i: Int, f: ArtBootstrapStudio.ArtFrame) {
        when (anim) {
            AnimationId.WALK, AnimationId.WALK_BACKPACK -> walk(facing, i, f)
            AnimationId.IDLE, AnimationId.IDLE_SIT -> when (i) { 3 -> stretchEar(f, left = true, facing); 6 -> stretchEar(f, left = false, facing) }
            else -> Unit
        }
        if (anim.group == AnimGroup.WORK) work(anim, i, f)
        snapHandAnchors(anim, f)
    }

    private fun walk(facing: Facing, i: Int, f: ArtBootstrapStudio.ArtFrame) {
        when (i) {
            2 -> stretchEar(f, left = true, facing)
            6 -> stretchEar(f, left = false, facing)
        }
    }

    /**
     * Orelha 1 px mais alta (a ponta sobe, a base continua presa na cabeça).
     * De lado só a orelha da frente (perto do focinho) se mexe.
     */
    fun stretchEar(f: ArtBootstrapStudio.ArtFrame, left: Boolean, facing: Facing) {
        val w = f.image.width
        val split = if (facing == Facing.SIDE) 26 else w / 2
        val columns = (0 until w).filter { x -> if (facing == Facing.SIDE || left) x < split else x >= split }
        for (x in columns) {
            val ys = (0 until f.image.height).filter { y -> f.partAt(x, y) == Part.EARS }
            if (ys.isEmpty()) continue
            // De cima para baixo: cada pixel sobe 1; o de baixo fica (estica sem abrir buraco).
            for (y in ys.sorted()) f.set(x, y - 1, f[x, y], Part.EARS)
        }
    }

    /** No trabalho: a pata que bate na tecla ganha um ponto de luz (lê melhor o toque). */
    private fun work(anim: AnimationId, i: Int, f: ArtBootstrapStudio.ArtFrame) {
        if (anim != AnimationId.WORK_TYPING) return
        for (part in listOf(Part.HAND_LEFT, Part.HAND_RIGHT)) {
            val px = pixelsOf(f, part)
            val top = px.minByOrNull { (x, y) -> y * 100 + x } ?: continue
            // Primeiro pixel de pelo abaixo do contorno superior da pata.
            val (x, y) = top
            if (f[x + 1, y + 1] == HoodiePalette.FUR) f.set(x + 1, y + 1, HoodiePalette.FUR_LIGHT, part)
        }
    }

    /** Âncora de mão obrigatória sempre encostada na pata desenhada (o item não "flutua"). */
    private fun snapHandAnchors(anim: AnimationId, f: ArtBootstrapStudio.ArtFrame) {
        val required = RequiredAnchors.required(anim)
        if (Anchor.RIGHT_HAND in required) snap(f, Part.HAND_RIGHT, f.anchors.rightHand)?.let { f.anchors = f.anchors.copy(rightHand = it) }
        if (Anchor.LEFT_HAND in required) snap(f, Part.HAND_LEFT, f.anchors.leftHand)?.let { f.anchors = f.anchors.copy(leftHand = it) }
    }

    private fun snap(f: ArtBootstrapStudio.ArtFrame, part: Part, at: Point): Point? {
        val px = pixelsOf(f, part).filter { (x, y) -> f[x, y] != HoodiePalette.OUTLINE }
        if (px.isEmpty() || px.any { (x, y) -> x == at.x && y == at.y }) return null
        val (x, y) = px.minBy { (x, y) -> (x - at.x) * (x - at.x) + (y - at.y) * (y - at.y) }
        return Point(x, y)
    }

    fun pixelsOf(f: ArtBootstrapStudio.ArtFrame, part: Part): List<Pair<Int, Int>> =
        (0 until f.image.height).flatMap { y -> (0 until f.image.width).filter { x -> f.partAt(x, y) == part }.map { it to y } }
}
