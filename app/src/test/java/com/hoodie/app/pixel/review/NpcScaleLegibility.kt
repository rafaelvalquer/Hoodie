package com.hoodie.app.pixel.review

import com.hoodie.app.pixel.character.BodyLayout
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.npc.AmbientScale
import com.hoodie.app.pixel.npc.NpcRenderer
import com.hoodie.app.pixel.npc.NpcScalePolicy
import com.hoodie.app.pixel.npc.NpcDepth
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import kotlin.math.roundToInt

/** Métricas técnicas repetíveis; não substituem a revisão visual humana. */
data class NpcScaleLegibilityReport(
    val character: String,
    val requestedScale: Float,
    val effectiveScale: Float,
    val productionScale: Float,
    val eyePixels: Int,
    val outlineCoverage: Float,
    val characterHeight: Int,
    val headPixels: Int,
    val feetPixels: Int,
    val visiblePixelRetention: Float,
) {
    fun json() = """{"character":"$character","requestedScale":${fmt(requestedScale)},"effectiveScale":${fmt(effectiveScale)},"productionScale":${fmt(productionScale)},"eyePixels":$eyePixels,"outlineCoverage":${fmt(outlineCoverage)},"characterHeight":$characterHeight,"headPixels":$headPixels,"feetPixels":$feetPixels,"visiblePixelRetention":${fmt(visiblePixelRetention)}}"""

    private fun fmt(value: Float) = "%.4f".format(java.util.Locale.ROOT, value)
}

object NpcScaleLegibility {
    val reviewScales = listOf(.75f, .80f, .85f, .90f, .95f, 1.00f)

    fun inspect(style: CharacterStyle, requestedScale: Float): NpcScaleLegibilityReport {
        require(requestedScale in reviewScales)
        val effective = requestedScale
        val productionScale = maxOf(requestedScale, style.minimumAmbientScale)
        val pose = CharacterPose(facing = Facing.FRONT, eyes = Eyes.OPEN)
        val frame = CharacterPainter.paint(style, pose)
        val scaled = NpcRenderer.scaleFrameForAmbient(frame, effective)
        val layout = BodyLayout.resolve(style, pose)
        val image = scaled.image
        val eyesY = scaleY(layout.eyeY + eyeYOffset(style), frame.anchors.feet.y, effective)
        val eyeCenters = eyeCenters(style, layout.headCx)
        val eyePixels = eyeCenters.sumOf { centerX ->
            val x = scaleX(centerX, frame.anchors.feet.x, effective)
            count(image, x - 2, eyesY - 2, x + 2, eyesY + 3) { it == style.palette.outline }
        }
        val visible = image.pixels.count { it ushr 24 != 0 }
        val baseVisible = frame.image.pixels.count { it ushr 24 != 0 }.coerceAtLeast(1)
        val minX = image.pixels.indices.filter { image.pixels[it] ushr 24 != 0 }.minOfOrNull { it % image.width } ?: 0
        val maxX = image.pixels.indices.filter { image.pixels[it] ushr 24 != 0 }.maxOfOrNull { it % image.width } ?: 0
        val minY = image.pixels.indices.filter { image.pixels[it] ushr 24 != 0 }.minOfOrNull { it / image.width } ?: 0
        val maxY = image.pixels.indices.filter { image.pixels[it] ushr 24 != 0 }.maxOfOrNull { it / image.width } ?: 0
        val occupied = image.pixels.count { it ushr 24 != 0 }.coerceAtLeast(1)
        val outline = image.pixels.count { it == style.palette.outline }
        val headLeft = scaleX(layout.headLeft, frame.anchors.feet.x, effective)
        val headRight = scaleX(layout.headRight, frame.anchors.feet.x, effective)
        val headTop = scaleY(layout.headTop, frame.anchors.feet.y, effective)
        val headBottom = scaleY(layout.headBottom, frame.anchors.feet.y, effective)
        val feetY = frame.anchors.feet.y
        return NpcScaleLegibilityReport(
            character = style.id,
            requestedScale = requestedScale,
            effectiveScale = effective,
            productionScale = productionScale,
            eyePixels = eyePixels,
            outlineCoverage = outline.toFloat() / occupied,
            characterHeight = if (visible == 0) 0 else maxY - minY + 1,
            headPixels = count(image, headLeft, headTop, headRight, headBottom) { it ushr 24 != 0 },
            feetPixels = count(image, 14, feetY - 5, 33, feetY) { it ushr 24 != 0 },
            visiblePixelRetention = visible.toFloat() / baseVisible,
        )
    }

    fun reviewReports(style: CharacterStyle): List<NpcScaleLegibilityReport> = reviewScales.map { inspect(style, it) }

    private fun eyeCenters(style: CharacterStyle, headCx: Int): List<Int> = when (style.species.id) {
        "mouse" -> listOf(headCx - style.artProfile.face.eyeSpacing - 1, headCx + style.artProfile.face.eyeSpacing + 1)
        else -> listOf(headCx - style.artProfile.face.eyeSpacing, headCx + style.artProfile.face.eyeSpacing)
    }

    private fun eyeYOffset(style: CharacterStyle) = if (style.species.id == "bulldog") 0 else -1
    private fun scaleX(value: Int, pivot: Int, scale: Float) = (pivot + (value - pivot) * scale).roundToInt().coerceIn(0, CharacterPainter.WIDTH - 1)
    private fun scaleY(value: Int, pivot: Int, scale: Float) = (pivot + (value - pivot) * scale).roundToInt().coerceIn(0, CharacterPainter.HEIGHT - 1)

    private inline fun count(image: PixelBuffer, x0: Int, y0: Int, x1: Int, y1: Int, predicate: (Int) -> Boolean): Int {
        var total = 0
        for (y in y0.coerceAtLeast(0)..y1.coerceAtMost(image.height - 1)) {
            for (x in x0.coerceAtLeast(0)..x1.coerceAtMost(image.width - 1)) if (predicate(image[x, y])) total++
        }
        return total
    }
}

class NpcScaleLegibilityTest {
    @org.junit.Test fun requestedScalesPreserveTechnicalFaceAndSilhouettePixels() {
        NpcVisualReviewFrames.reviewSpecies.forEach { style ->
            val reports = NpcScaleLegibility.reviewReports(style)
            reports.forEach { report ->
                org.junit.Assert.assertTrue("${style.id}@${report.effectiveScale}: both eyes must remain distinguishable", report.eyePixels >= 2)
                org.junit.Assert.assertTrue("${style.id}@${report.effectiveScale}: outline must remain visible", report.outlineCoverage >= 0.015f)
                // Espécies compactas têm menos pixels verticais no desenho original;
                // em 75% o rato mantém 43 px e continua legível nos outros indicadores.
                org.junit.Assert.assertTrue("${style.id}@${report.effectiveScale}: character height collapsed", report.characterHeight >= 40)
                org.junit.Assert.assertTrue("${style.id}@${report.effectiveScale}: head area disappeared", report.headPixels >= 40)
                org.junit.Assert.assertTrue("${style.id}@${report.effectiveScale}: feet area disappeared", report.feetPixels >= 2)
                org.junit.Assert.assertTrue(
                    "${style.id}@${report.effectiveScale}: too many visible pixels were lost (${report.visiblePixelRetention})",
                    report.visiblePixelRetention >= report.effectiveScale * report.effectiveScale * 0.65f,
                )
            }
        }
    }

    @org.junit.Test fun speciesMinimumsAreRecordedSeparatelyFromReviewScale() {
        NpcVisualReviewFrames.reviewSpecies.forEach { style ->
            val report = NpcScaleLegibility.inspect(style, .75f)
            org.junit.Assert.assertEquals(style.id, NpcScalePolicy.scale(style, NpcDepth.BACKGROUND_FAR), report.productionScale, 0f)
            org.junit.Assert.assertEquals(style.id, .75f, report.effectiveScale, 0f)
        }
    }
}
