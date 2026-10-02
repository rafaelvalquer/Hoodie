package com.hoodie.app.pixel.art

import com.hoodie.app.pixel.SheetBaker
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.sprite.AnchorMarkers
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.SpriteRequest
import com.hoodie.app.pixel.sprite.SpriteSheetProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Paridade `.aseprite` ↔ o que o app carrega: para cada grupo, frames, tags,
 * durações, âncoras, dimensões, paleta, PNG e JSON de runtime.
 */
class AsepriteRuntimeParityTest {

    private val sourceDir = File("../assets-source/hoodie")
    private val runtime by lazy { SpriteSheetProvider.load(SheetBaker.assetSource(File("src/main/assets")), SheetBaker.decoder) }

    private fun tagKey(name: String): Pair<AnimationId, Facing> {
        val facing = Facing.entries.first { name.endsWith("_" + it.name.lowercase()) }
        return AnimationId.valueOf(name.removeSuffix("_" + facing.name.lowercase()).uppercase()) to facing
    }

    @Test
    fun `cada grupo bate com o runtime`() {
        val (provider, report) = runtime
        assertTrue(report.errors.toString(), report.errors.isEmpty())
        ArtBootstrapStudio.GROUPS.keys.forEach { group ->
            val doc = AsepriteFile.read(File(sourceDir, "$group.aseprite"))
            assertEquals("$group dimensões", HoodiePainter.WIDTH to HoodiePainter.HEIGHT, doc.width to doc.height)
            val anchorsLayer = doc.layers.indexOfFirst { it.name == AsepriteSourceCompiler.ANCHORS_LAYER }
            assertTrue("$group sem camada anchors", anchorsLayer >= 0)
            assertTrue("$group sem tags", doc.tags.isNotEmpty())
            doc.tags.forEach { tag ->
                val (anim, facing) = tagKey(tag.name)
                val dir = SheetBaker.directionOf(facing)
                assertTrue("${tag.name} não carregou no runtime", provider.supports(anim, facing))
                assertEquals("${tag.name} frames", anim.frames.size, tag.to - tag.from + 1)
                assertEquals("${tag.name} frames no runtime", tag.to - tag.from + 1, provider.frameCount(anim, dir))
                for (i in tag.from..tag.to) {
                    val k = i - tag.from
                    val rt = provider.frame(SpriteRequest(anim, dir, k))
                    assertEquals("${tag.name}/$k duração", doc.frames[i].durationMs.toLong(), rt.durationMs)
                    assertTrue("${tag.name}/$k PNG", doc.flatten(i, setOf(AsepriteSourceCompiler.ANCHORS_LAYER)).pixels.contentEquals(rt.image.pixels))
                    // Âncoras: o marcador pintado no .aseprite é a âncora do runtime.
                    val markers = doc.frames[i].cels.filter { it.layer == anchorsLayer }
                    fun marker(color: Int) = markers.firstNotNullOfOrNull { c ->
                        (0 until c.image.width * c.image.height).firstOrNull { c.image.pixels[it] == color }?.let { (c.x + it % c.image.width) to (c.y + it / c.image.width) }
                    }
                    marker(AnchorMarkers.FEET)?.let { assertEquals("${tag.name}/$k pés", it, rt.anchors.feet.x to rt.anchors.feet.y) }
                    marker(AnchorMarkers.HEAD)?.let { assertEquals("${tag.name}/$k cabeça", it, rt.anchors.head.x to rt.anchors.head.y) }
                    marker(AnchorMarkers.RIGHT_HAND)?.let { assertEquals("${tag.name}/$k mão", it, rt.anchors.rightHand.x to rt.anchors.rightHand.y) }
                }
            }
            // Paleta do arquivo cobre todas as cores usadas.
            val used = doc.frames.indices.flatMap { doc.flatten(it, setOf(AsepriteSourceCompiler.ANCHORS_LAYER)).pixels.filter { p -> p ushr 24 != 0 } }.toSet()
            assertTrue("$group usa cores fora da paleta do arquivo: ${(used - doc.palette.toSet()).map { Integer.toHexString(it) }}", doc.palette.toSet().containsAll(used))
            // JSON e anchors.png do APK são exatamente a compilação do arquivo.
            val compiled = AsepriteSourceCompiler.compile(doc)
            assertEquals("$group.json", compiled.json, File("src/main/assets/${SpriteSheetProvider.DIR}/$group.json").readText().replace("\r\n", "\n"))
        }
    }
}
