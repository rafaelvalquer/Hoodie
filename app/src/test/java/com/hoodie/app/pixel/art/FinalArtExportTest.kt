package com.hoodie.app.pixel.art

import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.SheetBaker
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.RequiredAnchors
import com.hoodie.app.pixel.sprite.RequiredShippedAnimations
import com.hoodie.app.pixel.sprite.SpriteRequest
import com.hoodie.app.pixel.sprite.SpriteSheetProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Arte final v1: .aseprite fonte → PNG/JSON no APK.
 *
 * `./gradlew :app:testDebugUnitTest -PexportArt=true --tests "*FinalArtExportTest*"`
 * regrava assets-source/hoodie/ e app/src/main/assets/pixel/hoodie/. Sem a flag,
 * os testes só conferem que o que está commitado bate com o estúdio (drift).
 */
class FinalArtExportTest {

    private val export = System.getProperty("exportArt") == "true"
    private val sourceDir = File("../assets-source/hoodie")
    private val assetsDir = File("src/main/assets/${SpriteSheetProvider.DIR}")

    private fun exportAll() {
        FinalArtStudio.GROUPS.keys.forEach { group ->
            AsepriteFile.write(File(sourceDir, "$group.aseprite"), FinalArtStudio.document(group))
            SheetBaker.write(assetsDir, group, FinalArtStudio.bake(group))
            // Baseline procedural com a mesma divisão (referência para comparar no Aseprite).
            SheetBaker.write(File(sourceDir, "baseline"), group, SheetBaker.bake(FinalArtStudio.GROUPS.getValue(group)))
        }
    }

    @Test
    fun `os grupos cobrem os arquivos e clips obrigatorios`() {
        assertEquals(RequiredShippedAnimations.files.toSet(), FinalArtStudio.GROUPS.keys)
        val all = FinalArtStudio.GROUPS.values.flatten().toSet()
        assertTrue(RequiredShippedAnimations.missing(all).isEmpty())
    }

    @Test
    fun `retoque nao muda silhueta, pes nem ancoras`() {
        FinalArtStudio.GROUPS.values.flatten().forEach { (anim, facing) ->
            for (i in anim.frames.indices) {
                val p = FinalArtStudio.procedural(anim, facing, i)
                val f = FinalArtStudio.frame(anim, facing, i)
                assertEquals(p.anchors, f.anchors)
                assertEquals(p.durationMs, f.durationMs)
                for (k in p.image.pixels.indices) assertEquals("$anim/$facing/$i silhueta", p.image.pixels[k] ushr 24, f.image.pixels[k] ushr 24)
            }
        }
        // E muda de fato a arte (não é o procedural de novo).
        val idle = FinalArtStudio.frame(AnimationId.IDLE, Facing.FRONT, 0).image.pixels
        assertTrue(!idle.contentEquals(FinalArtStudio.procedural(AnimationId.IDLE, Facing.FRONT, 0).image.pixels))
    }

    @Test
    fun `aseprite ida e volta preserva frames, tags, duracoes e camadas`() {
        FinalArtStudio.GROUPS.forEach { (group, clips) ->
            val doc = AsepriteFile.decode(AsepriteFile.encode(FinalArtStudio.document(group)))
            assertEquals(HoodiePainter.WIDTH, doc.width); assertEquals(HoodiePainter.HEIGHT, doc.height)
            assertEquals(FinalArtStudio.LAYERS, doc.layers.map { it.name })
            val baseline = doc.layers.first { it.name == FinalArtStudio.BASELINE }
            assertTrue("referência travada", baseline.reference && baseline.flags and AsepriteFile.LAYER_EDITABLE == 0)
            assertEquals(clips.map { (a, f) -> "${a.name.lowercase()}_${f.name.lowercase()}" }, doc.tags.map { it.name })
            var index = 0
            clips.forEach { (anim, facing) ->
                for (i in anim.frames.indices) {
                    val expected = FinalArtStudio.frame(anim, facing, i)
                    assertEquals(expected.durationMs.toInt(), doc.frames[index].durationMs)
                    // Exportar ignorando a camada de âncoras (como o export.sh) devolve a arte final.
                    assertArrayEquals("$group/$anim/$i", expected.image.pixels, doc.flatten(index, setOf(FinalArtStudio.ANCHORS)).pixels)
                    index++
                }
            }
        }
    }

    @Test
    fun `assets do APK e fontes aseprite estao em dia com o estudio`() {
        if (export) exportAll()
        FinalArtStudio.GROUPS.keys.forEach { group ->
            val baked = FinalArtStudio.bake(group)
            val png = File(assetsDir, "$group.png")
            assertTrue("$png ausente — rode com -PexportArt=true", png.exists())
            assertArrayEquals("$group.png desatualizado — rode com -PexportArt=true", baked.image.pixels, SheetBaker.decodePng(png.inputStream())!!.pixels)
            assertEquals("$group.json desatualizado", baked.json, File(assetsDir, "$group.json").readText())
            assertArrayEquals(baked.anchors!!.pixels, SheetBaker.decodePng(File(assetsDir, "$group.anchors.png").inputStream())!!.pixels)

            val src = AsepriteFile.read(File(sourceDir, "$group.aseprite"))
            val fresh = FinalArtStudio.document(group)
            assertEquals("$group.aseprite desatualizado", fresh.frames.size, src.frames.size)
            for (i in fresh.frames.indices) assertArrayEquals("$group.aseprite frame $i", fresh.flatten(i).pixels, src.flatten(i).pixels)
        }
    }

    @Test
    fun `sheets finais carregam sem problemas e com as ancoras obrigatorias`() {
        val (provider, report) = SpriteSheetProvider.load(SheetBaker.assetSource(File("src/main/assets")), SheetBaker.decoder)
        assertTrue(report.problems.toString(), report.problems.isEmpty())
        assertTrue(RequiredShippedAnimations.missing(provider.available).isEmpty())
        FinalArtStudio.GROUPS.values.flatten().forEach { (anim, facing) ->
            val f = provider.frame(SpriteRequest(anim, SheetBaker.directionOf(facing), 0))
            assertEquals("spritesheet", f.source)
            assertEquals(HoodiePainter.FEET, f.anchors.feet)
            assertTrue(RequiredAnchors.required(anim).isNotEmpty())
        }
    }

    @Test
    fun `preview lado a lado procedural x final`() {
        val pairs = listOf(AnimationId.WALK to Facing.SIDE, AnimationId.WALK to Facing.FRONT, AnimationId.IDLE to Facing.FRONT, AnimationId.SLEEP to Facing.FRONT, AnimationId.WORK_TYPING to Facing.FRONT)
        val buffers = pairs.flatMap { (a, f) -> listOf(FinalArtStudio.procedural(a, f, 0).image, FinalArtStudio.frame(a, f, 0).image) }
        PreviewExport.sheet("final_vs_procedural", buffers, columns = 2, scale = 4)
        assertNotEquals(0, buffers.size)
        assertEquals(Direction.LEFT, SheetBaker.directionOf(Facing.SIDE))
    }
}
