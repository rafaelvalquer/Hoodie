package com.hoodie.app.pixel.character.species

import com.hoodie.app.pixel.character.CharacterPalette
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.procedural.R
import com.hoodie.app.pixel.sprite.Ears
import com.hoodie.app.pixel.sprite.Facing
import kotlin.math.max
import kotlin.math.roundToInt

/** Silhuetas e feições próprias; membros, pose e roupa continuam no pintor comum. */
internal object CharacterSpeciesPainter {
    fun drawEars(b: PixelBuffer, species: SpeciesStyle, pose: CharacterPose, p: CharacterPalette, cx: Int, top: Int, scale: Float) {
        val top = top + pose.headDy
        val down = if (pose.ears == Ears.DOWN || pose.ears == Ears.RELAXED) 2 else 0
        // Long ears may extend above the head; keep their tips one logical pixel inside the canvas.
        val longEarTop = max(top, 16 - down)
        val safeEarTop = top.coerceAtLeast(2)
        if (pose.facing == Facing.SIDE) {
            when (species.earStyle) {
                EarStyle.POINTED -> pointedEar(b, cx - 8, top + 8 + down, p)
                EarStyle.FLOPPY -> ellipse(b, cx - 10, top + 12 + down, 5, 8, p.outline).also {
                    ellipse(b, cx - 10, top + 12 + down, 3, 6, p.furDark)
                }
                EarStyle.LONG -> {
                    ellipse(b, cx - 5, longEarTop - 1 + down, 5, 14, p.outline)
                    ellipse(b, cx - 5, longEarTop - 1 + down, 3, 12, p.fur)
                    ellipse(b, cx - 5, longEarTop - 1 + down, 1, 9, p.inner)
                }
                EarStyle.ROUND -> {
                    ellipse(b, cx - 8, safeEarTop + 6 + down, 7, 7, p.outline)
                    ellipse(b, cx - 8, safeEarTop + 6 + down, 5, 5, p.fur)
                    ellipse(b, cx - 8, safeEarTop + 6 + down, 3, 3, p.inner)
                }
                EarStyle.WING -> ellipse(b, cx - 10, top + 12, 4, 6, p.outline).also {
                    ellipse(b, cx - 10, top + 12, 2, 4, p.furDark)
                }
                EarStyle.NONE -> Unit
            }
            return
        }
        when (species.earStyle) {
            EarStyle.POINTED -> {
                pointedEar(b, cx - 10, safeEarTop + 7 + down, p)
                pointedEar(b, cx + 10, safeEarTop + 7 + down, p)
            }
            EarStyle.FLOPPY -> {
                if (species.headShape == HeadShape.BULLDOG) {
                    ellipse(b, cx - 15, top + 12 + down, 5, 7, p.outline)
                    ellipse(b, cx - 15, top + 12 + down, 3, 5, p.furDark)
                    ellipse(b, cx + 15, top + 12 + down, 5, 7, p.outline)
                    ellipse(b, cx + 15, top + 12 + down, 3, 5, p.furDark)
                } else {
                    ellipse(b, cx - 12, top + 10 + down, 5, 9, p.outline)
                    ellipse(b, cx - 12, top + 10 + down, 3, 7, p.furDark)
                    ellipse(b, cx + 12, top + 10 + down, 5, 9, p.outline)
                    ellipse(b, cx + 12, top + 10 + down, 3, 7, p.furDark)
                }
            }
            EarStyle.LONG -> {
                ellipse(b, cx - 6, longEarTop - 1 + down, 5, 14, p.outline)
                ellipse(b, cx - 6, longEarTop - 1 + down, 3, 12, p.fur)
                ellipse(b, cx - 6, longEarTop - 1 + down, 1, 9, p.inner)
                ellipse(b, cx + 6, longEarTop - 1 + down, 5, 14, p.outline)
                ellipse(b, cx + 6, longEarTop - 1 + down, 3, 12, p.fur)
                ellipse(b, cx + 6, longEarTop - 1 + down, 1, 9, p.inner)
            }
            EarStyle.ROUND -> {
                ellipse(b, cx - 9, safeEarTop + 6 + down, 7, 7, p.outline)
                ellipse(b, cx - 9, safeEarTop + 6 + down, 5, 5, p.fur)
                ellipse(b, cx - 9, safeEarTop + 6 + down, 3, 3, p.inner)
                ellipse(b, cx + 9, safeEarTop + 6 + down, 7, 7, p.outline)
                ellipse(b, cx + 9, safeEarTop + 6 + down, 5, 5, p.fur)
                ellipse(b, cx + 9, safeEarTop + 6 + down, 3, 3, p.inner)
            }
            EarStyle.WING -> {
                ellipse(b, cx - 12, top + 12, 4, 6, p.outline)
                ellipse(b, cx - 12, top + 12, 2, 4, p.furDark)
                ellipse(b, cx + 12, top + 12, 4, 6, p.outline)
                ellipse(b, cx + 12, top + 12, 2, 4, p.furDark)
            }
            EarStyle.NONE -> Unit
        }
    }

    fun drawHead(b: PixelBuffer, species: SpeciesStyle, pose: CharacterPose, p: CharacterPalette, cx: Int, top: Int, scale: Float) {
        if (pose.facing == Facing.SIDE) {
            drawSideHead(b, species, pose, p, cx, top, scale)
            return
        }
        when (species.headShape) {
            HeadShape.BULLDOG -> drawBulldogHead(b, pose, p, cx, top, scale)
            HeadShape.DUCK -> {
                val faceTop = top + pose.headDy
                ellipse(b, cx, faceTop + 15, 16, 14, p.outline)
                ellipse(b, cx, faceTop + 14, 14, 12, p.fur)
                ellipse(b, cx - 6, faceTop + 9, 5, 3, p.furLight)
            }
            HeadShape.CAT, HeadShape.DOG, HeadShape.RACCOON -> drawRoundedMammalHead(b, species, pose, p, cx, top, scale)
            HeadShape.RABBIT, HeadShape.MOUSE -> {
                val faceTop = top + pose.headDy
                val hw = max(11, (species.headWidth * scale / 1.9f).toInt())
                val hh = max(10, (species.headHeight * scale / 1.9f).toInt())
                ellipse(b, cx, faceTop + hh, hw, hh, p.outline)
                ellipse(b, cx, faceTop + hh - 1, hw - 2, hh - 2, p.fur)
                ellipse(b, cx - hw / 3, faceTop + hh - 5, max(3, hw / 4), 4, p.furLight)
                ellipse(b, cx + hw / 2, faceTop + hh + 2, max(3, hw / 5), hh / 2, p.furDark)
            }
        }
    }

    fun drawBulldogHead(b: PixelBuffer, pose: CharacterPose, p: CharacterPalette, cx: Int, top: Int, scale: Float) {
        if (pose.facing == Facing.SIDE) {
            drawSideHead(b, BulldogSpecies, pose, p, cx, top, scale)
            return
        }
        drawRoundedMammalHead(b, BulldogSpecies, pose, p, cx, top, scale, bulldog = true)
    }

    fun drawBulldogEars(b: PixelBuffer, pose: CharacterPose, p: CharacterPalette, cx: Int, top: Int, scale: Float) =
        drawEars(b, BulldogSpecies, pose, p, cx, top, scale)

    fun drawMuzzle(b: PixelBuffer, species: SpeciesStyle, pose: CharacterPose, p: CharacterPalette, cx: Int, top: Int, scale: Float) {
        if (pose.facing == Facing.SIDE) {
            drawSideMuzzle(b, species, pose, p, cx, top)
            return
        }
        when (species.muzzleStyle) {
            MuzzleStyle.BROAD -> drawBulldogMuzzle(b, pose, p, cx, top, scale)
            MuzzleStyle.BEAK -> {
                val y = top + 15 + pose.headDy
                b.box(cx - 6, y, cx + 6, y + 1, p.outline)
                b.box(cx - 5, y + 2, cx + 5, y + 4, p.accent)
                b.box(cx - 3, y + 5, cx + 3, y + 5, p.outline)
                b.set(cx - 3, y + 2, p.outfitLight)
            }
            MuzzleStyle.LONG -> {
                val y = top + 15 + pose.headDy
                ellipse(b, cx + 1, y, 8, 5, p.outline)
                ellipse(b, cx + 1, y - 1, 6, 3, p.furLight)
                noseAndMouth(b, cx + 5, y - 2, p)
            }
            MuzzleStyle.FINE -> {
                val y = top + 16 + pose.headDy
                ellipse(b, cx, y, 5, 4, p.outline)
                ellipse(b, cx, y - 1, 3, 2, p.furLight)
                noseAndMouth(b, cx + 1, y - 2, p)
                b.line(cx - 2, y + 1, cx - 9, y, p.outline)
                b.line(cx - 2, y + 3, cx - 9, y + 4, p.outline)
                b.line(cx + 2, y + 1, cx + 9, y, p.outline)
                b.line(cx + 2, y + 3, cx + 9, y + 4, p.outline)
            }
            MuzzleStyle.MASKED -> {
                val y = top + 12 + pose.headDy
                b.line(cx - 13, y - 2, cx - 3, y + 3, p.outline)
                b.line(cx + 13, y - 2, cx + 3, y + 3, p.outline)
                ellipse(b, cx, top + 16 + pose.headDy, 5, 3, p.furLight)
                noseAndMouth(b, cx + 1, top + 14 + pose.headDy, p)
            }
            MuzzleStyle.SHORT -> {
                val y = top + 16 + pose.headDy
                ellipse(b, cx - 3, y, 4, 3, p.outline)
                ellipse(b, cx + 3, y, 4, 3, p.outline)
                ellipse(b, cx - 3, y - 1, 3, 2, p.furLight)
                ellipse(b, cx + 3, y - 1, 3, 2, p.furLight)
                noseAndMouth(b, cx, y - 2, p)
            }
        }
    }

    private fun drawSideHead(b: PixelBuffer, species: SpeciesStyle, pose: CharacterPose, p: CharacterPalette, cx: Int, top: Int, scale: Float) {
        val y = top + pose.headDy
        when (species.headShape) {
            HeadShape.BULLDOG -> drawRoundedMammalSideHead(b, species, p, cx, y, scale, bulldog = true)
            HeadShape.DUCK -> {
                ellipse(b, cx, y + 15, 14, 13, p.outline)
                ellipse(b, cx, y + 14, 12, 11, p.fur)
                ellipse(b, cx - 5, y + 9, 5, 3, p.furLight)
            }
            HeadShape.CAT, HeadShape.DOG, HeadShape.RACCOON -> drawRoundedMammalSideHead(b, species, p, cx, y, scale)
            HeadShape.RABBIT, HeadShape.MOUSE -> {
                val hw = max(11, (species.headWidth * scale / 2.15f).toInt())
                val hh = max(10, (species.headHeight * scale / 1.9f).toInt())
                ellipse(b, cx + 2, y + hh, hw, hh, p.outline)
                ellipse(b, cx + 2, y + hh - 1, hw - 2, hh - 2, p.fur)
                ellipse(b, cx - 3, y + hh - 5, max(3, hw / 4), 4, p.furLight)
                ellipse(b, cx - 8, y + hh + 1, max(2, hw / 5), hh / 2, p.furDark)
            }
        }
    }

    /** Mammal faces borrow the Hoodie’s broad, chamfered rectangle and one-pixel ink edge. */
    private fun drawRoundedMammalHead(
        b: PixelBuffer,
        species: SpeciesStyle,
        pose: CharacterPose,
        p: CharacterPalette,
        cx: Int,
        top: Int,
        scale: Float,
        bulldog: Boolean = false,
    ) {
        val faceTop = top + pose.headDy
        val width = if (bulldog) (species.headWidth * scale).roundToInt().coerceIn(31, 35)
        else ((species.headWidth * scale / 1.9f) * 2f).roundToInt().coerceIn(29, 34)
        val height = if (bulldog) (species.headHeight * scale).roundToInt().coerceIn(25, 28)
        else ((species.headHeight * scale / 1.9f) * 2f).roundToInt().coerceIn(23, 25)
        val left = cx - width / 2
        val right = left + width - 1
        val bottom = faceTop + height - 1
        val radius = minOf(8, width / 4, height / 3)
        drawRoundedHeadShape(b, left, faceTop, right, bottom, radius, p)
        // Shallow lower/side shading echoes the hoodie’s blocky fur shadows.
        for (y in bottom - 2..bottom - 1) {
            for (x in left + 2..right - 2) if (b[x, y] == p.fur) b.set(x, y, p.furDark)
        }
        b.hline(left + radius, left + radius + 4, faceTop + 2, p.furLight)
        if (species.headShape == HeadShape.RACCOON) {
            b.line(left + 2, faceTop + height / 2, cx - 2, faceTop + height / 2 + 3, p.furDark)
            b.line(right - 2, faceTop + height / 2, cx + 2, faceTop + height / 2 + 3, p.furDark)
        }
        if (bulldog) {
            b.hline(cx - 5, cx + 1, faceTop + 5, p.furLight)
            b.box(left + 3, faceTop + 12, left + 6, faceTop + 17, p.furDark)
            b.box(right - 6, faceTop + 12, right - 3, faceTop + 17, p.furDark)
        }
    }

    /** Profile keeps the same squared crown and soft corners, with room for the muzzle. */
    private fun drawRoundedMammalSideHead(
        b: PixelBuffer,
        species: SpeciesStyle,
        p: CharacterPalette,
        cx: Int,
        top: Int,
        scale: Float,
        bulldog: Boolean = false,
    ) {
        val width = if (bulldog) (species.headWidth * scale).roundToInt().coerceIn(31, 35)
        else ((species.headWidth * scale / 2.15f) * 2f).roundToInt().coerceIn(27, 31)
        val height = if (bulldog) (species.headHeight * scale).roundToInt().coerceIn(27, 30) + 2
        else ((species.headHeight * scale / 1.9f) * 2f).roundToInt().coerceIn(23, 25)
        val left = cx - width / 2 + 2
        val right = left + width - 1
        val y0 = if (bulldog) (top - 2).coerceAtLeast(1) else top + 2
        val bottom = y0 + height - 1
        val radius = minOf(8, width / 4, height / 3)
        drawRoundedHeadShape(b, left, y0, right, bottom, radius, p)
        for (y in bottom - 2..bottom - 1) {
            for (x in left + 2..right - 2) if (b[x, y] == p.fur) b.set(x, y, p.furDark)
        }
        b.hline(left + radius, left + radius + 4, y0 + 2, p.furLight)
        if (species.headShape == HeadShape.RACCOON) b.hline(left + 3, left + 9, y0 + height / 2 + 2, p.furDark)
        if (bulldog) {
            b.box(left + 2, y0 + 13, left + 5, y0 + 18, p.furDark)
            b.hline(left + 8, left + 13, y0 + 5, p.furLight)
        }
    }

    /** Same rounded-rectangle mask as Hoodie, with its one-pixel outline inside the bounds. */
    private fun drawRoundedHeadShape(b: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, radius: Int, p: CharacterPalette) {
        val head = R(x0, y0, x1, y1, radius, round = true)
        for (y in y0..y1) for (x in x0..x1) {
            if (!head.inside(x, y)) continue
            val edge = !head.inside(x - 1, y) || !head.inside(x + 1, y) ||
                !head.inside(x, y - 1) || !head.inside(x, y + 1)
            b.set(x, y, if (edge) p.outline else p.fur)
        }
    }

    private fun drawSideMuzzle(b: PixelBuffer, species: SpeciesStyle, pose: CharacterPose, p: CharacterPalette, cx: Int, top: Int) {
        val y = top + pose.headDy
        when (species.muzzleStyle) {
            MuzzleStyle.BROAD -> {
                ellipse(b, cx + 10, y + 20, 10, 7, p.outline)
                ellipse(b, cx + 9, y + 20, 8, 5, p.furLight)
                b.box(cx + 14, y + 15, cx + 21, y + 18, p.outline)
                b.box(cx + 15, y + 16, cx + 19, y + 17, p.furDark)
                b.vline(cx + 8, y + 22, y + 26, p.furDark)
                b.hline(cx + 8, cx + 13, y + 27, p.outline)
            }
            MuzzleStyle.LONG -> {
                ellipse(b, cx + 10, y + 17, 10, 5, p.outline)
                ellipse(b, cx + 11, y + 16, 8, 3, p.furLight)
                noseAndMouth(b, cx + 18, y + 15, p)
            }
            MuzzleStyle.SHORT -> {
                ellipse(b, cx + 8, y + 18, 6, 4, p.outline)
                ellipse(b, cx + 8, y + 17, 4, 2, p.furLight)
                noseAndMouth(b, cx + 11, y + 16, p)
            }
            MuzzleStyle.FINE -> {
                ellipse(b, cx + 9, y + 18, 5, 3, p.outline)
                ellipse(b, cx + 9, y + 17, 3, 2, p.furLight)
                noseAndMouth(b, cx + 12, y + 16, p)
                b.line(cx + 7, y + 18, cx + 15, y + 17, p.outline)
                b.line(cx + 7, y + 20, cx + 15, y + 21, p.outline)
            }
            MuzzleStyle.BEAK -> {
                b.box(cx + 8, y + 14, cx + 17, y + 15, p.outline)
                b.box(cx + 9, y + 16, cx + 20, y + 19, p.accent)
                b.line(cx + 10, y + 20, cx + 17, y + 20, p.outline)
                b.set(cx + 10, y + 16, p.outfitLight)
            }
            MuzzleStyle.MASKED -> {
                b.box(cx + 3, y + 11, cx + 14, y + 14, p.outline)
                ellipse(b, cx + 11, y + 18, 5, 3, p.furLight)
                noseAndMouth(b, cx + 14, y + 16, p)
            }
        }
    }

    fun drawBulldogMuzzle(b: PixelBuffer, pose: CharacterPose, p: CharacterPalette, cx: Int, top: Int, scale: Float) {
        if (pose.facing == Facing.SIDE) {
            drawSideMuzzle(b, BulldogSpecies, pose, p, cx, top)
            return
        }
        val y = top + 19 + pose.headDy
        ellipse(b, cx, y, 13, 7, p.outline)
        ellipse(b, cx - 5, y + 1, 7, 5, p.furLight)
        ellipse(b, cx + 5, y + 1, 7, 5, p.furLight)
        // Nariz largo e achatado, duas narinas e a dobra central do focinho.
        b.box(cx - 5, y - 4, cx + 5, y - 1, p.outline)
        b.box(cx - 4, y - 3, cx + 4, y - 2, p.furDark)
        b.set(cx - 3, y - 3, p.outline)
        b.set(cx + 3, y - 3, p.outline)
        b.vline(cx, y - 1, y + 3, p.furDark)
        b.line(cx - 1, y + 4, cx - 5, y + 5, p.outline)
        b.line(cx + 1, y + 4, cx + 5, y + 5, p.outline)
    }

    fun drawTail(b: PixelBuffer, species: SpeciesStyle, pose: CharacterPose, p: CharacterPalette, x: Int, y: Int, amplitude: Int) {
        val wag = if (pose.facing == Facing.BACK || pose.facing == Facing.SIDE) amplitude else 0
        // Side poses are authored facing right, then mirrored to face left; their tail extends left
        // before the mirror so it lands behind the character instead of beside the muzzle.
        val direction = if (pose.facing == Facing.SIDE) -1 else 1
        when (species.tailStyle) {
            TailStyle.NONE, TailStyle.DUCK -> Unit
            TailStyle.LONG -> {
                b.line(x, y, x + direction * (7 + wag), y + 7, p.outline)
                b.line(x + direction, y - 1, x + direction * (8 + wag), y + 6, p.fur)
                b.line(x + direction * (6 + wag), y + 7, x + direction * (12 + wag), y + 6, p.outline)
                b.line(x + direction * (7 + wag), y + 6, x + direction * (11 + wag), y + 5, p.furDark)
            }
            TailStyle.CAT -> {
                b.line(x, y, x + direction * (4 + wag), y - 6, p.outline)
                b.line(x + direction * (4 + wag), y - 6, x + direction * (10 + wag), y - 8, p.outline)
                b.line(x + direction, y, x + direction * (5 + wag), y - 5, p.fur)
                b.line(x + direction * (5 + wag), y - 5, x + direction * (9 + wag), y - 7, p.fur)
            }
            TailStyle.RINGED -> {
                b.line(x, y, x + direction * (6 + wag), y - 5, p.outline)
                b.line(x + direction * (6 + wag), y - 5, x + direction * (12 + wag), y - 3, p.outline)
                b.line(x + direction, y, x + direction * (6 + wag), y - 4, p.fur)
                b.line(x + direction * (7 + wag), y - 4, x + direction * (11 + wag), y - 3, p.furDark)
            }
            TailStyle.SHORT -> {
                ellipse(b, x + direction * (2 + wag), y + 1, 4, 4, p.outline)
                ellipse(b, x + direction * (2 + wag), y, 2, 2, p.furLight)
            }
        }
    }

    private fun noseAndMouth(b: PixelBuffer, x: Int, y: Int, p: CharacterPalette) {
        b.set(x, y, p.outline)
        if (p.accent ushr 24 != 0) b.set(x, y + 1, p.accent)
    }

    private fun pointedEar(b: PixelBuffer, cx: Int, baseY: Int, p: CharacterPalette) {
        for (row in 0..8) {
            val half = max(1, row * 2 / 3)
            b.hline(cx - half, cx + half, baseY - row, p.outline)
            if (row < 7) b.hline(cx - max(0, half - 1), cx + max(0, half - 1), baseY - row, p.fur)
        }
        for (row in 2..5) b.vline(cx, baseY - row, baseY - row, p.inner)
    }

    private fun ellipse(b: PixelBuffer, cx: Int, cy: Int, rx: Int, ry: Int, color: Int) {
        for (dy in -ry..ry) for (dx in -rx..rx) {
            if (dx * dx * ry * ry + dy * dy * rx * rx <= rx * rx * ry * ry) b.set(cx + dx, cy + dy, color)
        }
    }
}
