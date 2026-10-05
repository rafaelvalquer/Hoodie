package com.hoodie.app.pixel.review

import com.hoodie.app.pixel.character.CharacterFrame
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.npc.NpcGait
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.review.VisualReviewPose
import com.hoodie.app.pixel.sprite.HoodiePalette
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Point
import kotlin.math.hypot
import kotlin.math.pow

object NpcVisualQualityRules {
    private val sharedColors = setOf(
        HoodiePalette.EYE, HoodiePalette.WHITE, HoodiePalette.NOSE, HoodiePalette.TONGUE,
        HoodiePalette.BLUSH, 0xFFD0D4DE.toInt(), 0xFF9FE3F0.toInt(), 0xFFF4F1EA.toInt(),
        0xFF6B3E26.toInt(), 0xFFE07A93.toInt(), 0xFF2E3350.toInt(), 0xFFC8484A.toInt(),
        0xFF8E2E33.toInt(), 0xFFF4EBD8.toInt(), 0xFF4F7FC9.toInt(), 0xFFF6F3EA.toInt(), 0xFFF2CF5B.toInt(),
        0xFF2F4A3C.toInt(), 0xFFDDE7E0.toInt(), 0xFF8B5A2B.toInt(), 0xFFE5C25A.toInt(),
        0xFFC49A35.toInt(), 0xFF3A3D4A.toInt(), 0xFF8A8F9E.toInt(), 0xFFD9A83A.toInt(),
        0xFF6B3E26.toInt(), 0xFFD9A15A.toInt(), 0xFF3B3F55.toInt(), 0xFFDDE2F0.toInt(),
        0xFFE05A5A.toInt(), 0xFF5AC8E0.toInt(), 0xFF8AD6F2.toInt(), 0xFF2F6FD0.toInt(),
    )

    fun inspect(style: CharacterStyle, checkpoint: VisualReviewPose): NpcVisualQualityReport {
        val frameData = NpcVisualReviewFrames.resolve(style, checkpoint)
        val frame = CharacterPainter.paint(style, frameData.pose, frameData.motion)
        return inspect(style, checkpoint.name, frame)
    }

    fun inspect(style: CharacterStyle, poseName: String, frame: CharacterFrame, item: Item = Item.NONE): NpcVisualQualityReport {
        val image = frame.image
        val occupied = image.pixels.indices.filter { image.pixels[it] ushr 24 != 0 }
        val minX = occupied.minOfOrNull { it % image.width } ?: 0
        val maxX = occupied.maxOfOrNull { it % image.width } ?: 0
        val minY = occupied.minOfOrNull { it / image.width } ?: 0
        val maxY = occupied.maxOfOrNull { it / image.width } ?: 0
        val clipped = occupied.isNotEmpty() && (minX == 0 || maxX == image.width - 1 || minY == 0)
        val anchors = frame.anchors.let { listOf(it.feet, it.head, it.leftHand, it.rightHand, it.mouth, it.back) }
        val anchorsValid = anchors.all { it.x in 0 until image.width && it.y in 0 until image.height }
        val p = style.palette
        val declared = listOf(p.outline, p.furLight, p.fur, p.furDark, p.inner, p.outfitLight, p.outfit, p.outfitDark, p.shirt, p.accent).toSet()
        val colors = occupied.map { image.pixels[it] }.toSet()
        val speciesColors = colors.intersect(declared)
        val outside = colors - declared - sharedColors
        val outlinePixels = occupied.count { image.pixels[it] == p.outline }
        val outlineRatio = if (occupied.isEmpty()) 0f else outlinePixels.toFloat() / occupied.size
        val contrast = contrastRatio(p.fur, p.outfit)
        val itemDistance = if (item == Item.NONE) null else handPropDistance(image, frame.anchors.rightHand, frame.anchors.leftHand, item)
        val footValid = footContactValid(style)
        val hard = buildList {
            if (clipped) add("CLIPPING")
            if (!anchorsValid) add("ANCHOR_OUT_OF_CANVAS")
            if (outside.isNotEmpty()) add("COLOR_OUTSIDE_PALETTE:${outside.size}")
            if (!footValid) add("FOOT_CONTACT")
            if (item != Item.NONE && (itemDistance == null || itemDistance > 10)) add("PROP_DETACHED")
        }
        val warnings = buildList {
            if (maxX - minX + 1 < 23) add("SILHOUETTE_NARROW")
            if (maxY - minY + 1 < 45) add("CHARACTER_SMALL")
            if (outlineRatio !in 0.035f..0.30f) add("OUTLINE_RATIO")
            if (declared.size > 10) add("DECLARED_PALETTE_OVER_10")
            if (contrast < 1.5f) add("LOW_FUR_OUTFIT_CONTRAST")
        }
        return NpcVisualQualityReport(
            character = style.id,
            pose = poseName,
            clipped = clipped,
            paletteSize = speciesColors.size,
            declaredPaletteSize = declared.size,
            renderedPaletteSize = colors.size,
            outsidePaletteColors = outside.size,
            outlineRatio = outlineRatio,
            furOutfitContrast = contrast,
            height = if (occupied.isEmpty()) 0 else maxY - minY + 1,
            width = if (occupied.isEmpty()) 0 else maxX - minX + 1,
            handPropDistance = itemDistance,
            footContactValid = footValid,
            anchorValid = anchorsValid,
            hardIssues = hard,
            softWarnings = warnings,
        )
    }

    private fun contrastRatio(a: Int, b: Int): Float {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val value = ((color ushr shift) and 0xFF) / 255.0
                return if (value <= 0.04045) value / 12.92 else ((value + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val first = luminance(a)
        val second = luminance(b)
        return ((maxOf(first, second) + 0.05) / (minOf(first, second) + 0.05)).toFloat()
    }

    /** Confere os oito checkpoints: pé apoiado compensa avanço de mundo e nenhum quadro perde contato. */
    fun footContactValid(style: CharacterStyle): Boolean {
        val motion = SpeciesMotionProfiles.forCharacter(style)
        var nearOrigin: Float? = null
        var farOrigin: Float? = null
        for (phase in 0 until 8) {
            val distance = motion.stepLength * phase / 4f
            val gait = NpcGait.sample(distance, motion, 1f)
            if (gait.gait.contact.name == "NONE") return false
            if (phase < 4) {
                val planted = distance + gait.gait.nearX
                if (nearOrigin == null) nearOrigin = planted
                else if (kotlin.math.abs(planted - nearOrigin) > 1.01f) return false
            } else {
                val planted = distance + gait.gait.farX
                if (farOrigin == null) farOrigin = planted
                else if (kotlin.math.abs(planted - farOrigin) > 1.01f) return false
            }
        }
        return true
    }

    private fun handPropDistance(image: PixelBuffer, right: Point, left: Point, item: Item): Int? {
        val targetColors = when (item) {
            Item.PHONE -> setOf(0xFF9FE3F0.toInt())
            Item.MUG -> setOf(0xFFE07A93.toInt(), 0xFFF4F1EA.toInt())
            Item.FORK -> setOf(0xFFD0D4DE.toInt())
            Item.BOOK -> setOf(0xFFC8484A.toInt())
            Item.PRODUCT -> setOf(0xFF4F7FC9.toInt())
            else -> emptySet()
        }
        val points = image.pixels.indices.filter { image.pixels[it] in targetColors }
            .map { Point(it % image.width, it / image.width) }
        if (points.isEmpty()) return null
        val center = Point(points.map { it.x }.average().toInt(), points.map { it.y }.average().toInt())
        val toRight = hypot((center.x - right.x).toDouble(), (center.y - right.y).toDouble())
        val toLeft = hypot((center.x - left.x).toDouble(), (center.y - left.y).toDouble())
        return minOf(toRight, toLeft).toInt()
    }
}
