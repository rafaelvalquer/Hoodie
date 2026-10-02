package com.hoodie.app.pixel.art

import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.SheetBaker
import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.HoodiePainter
import com.hoodie.app.pixel.sprite.RequiredShippedAnimations
import com.hoodie.app.pixel.sprite.SpriteSheetProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * `.aseprite` é a fonte da verdade da arte:
 *
 *     .aseprite ──AsepriteSourceCompiler──▶ PNG + JSON + anchors.png (APK)
 *
 * - `-PexportArt=true` compila os `.aseprite` para app/src/main/assets/pixel/hoodie/.
 * - `-PartBootstrap=<grupo>[,<grupo>…]` (ou `all`) cria o `.aseprite` pelo [ArtBootstrapStudio] —
 *   só quando o arquivo não existe, a não ser que o grupo seja nomeado explicitamente.
 * - Sem flags, os testes conferem que o APK está em dia com os `.aseprite`.
 */
class FinalArtExportTest {

    private val export = System.getProperty("exportArt") == "true"
    private val bootstrap = System.getProperty("artBootstrap").orEmpty()
    private val sourceDir = File("../assets-source/hoodie")
    private val assetsDir = File("src/main/assets/${SpriteSheetProvider.DIR}")

    private fun source(group: String) = File(sourceDir, "$group.aseprite")

    @Test
    fun `os grupos cobrem os arquivos e clips obrigatorios`() {
        assertEquals(RequiredShippedAnimations.files.toSet(), ArtBootstrapStudio.GROUPS.keys)
        assertTrue(RequiredShippedAnimations.missing(ArtBootstrapStudio.GROUPS.values.flatten().toSet()).isEmpty())
    }

    @Test
    fun `bootstrap so cria arquivo novo ou o grupo nomeado`() {
        ArtBootstrapStudio.GROUPS.keys.forEach { group ->
            val named = group in bootstrap.split(',').map { it.trim() }
            if (named || (bootstrap == "all" && !source(group).exists())) {
                AsepriteFile.write(source(group), ArtBootstrapStudio.document(group))
                SheetBaker.write(File(sourceDir, "baseline"), group, SheetBaker.bake(ArtBootstrapStudio.GROUPS.getValue(group)))
            }
            assertTrue("${source(group)} ausente — rode com -PartBootstrap=$group", source(group).exists())
        }
    }

    @Test
    fun `apk compilado dos aseprite e esta em dia`() {
        ArtBootstrapStudio.GROUPS.keys.forEach { group ->
            val compiled = AsepriteSourceCompiler.compile(AsepriteFile.read(source(group)))
            if (export) SheetBaker.write(assetsDir, group, SheetBaker.Baked(compiled.image, compiled.json, compiled.anchors))
            val hint = "desatualizado em relação a $group.aseprite — rode com -PexportArt=true"
            assertArrayEquals("$group.png $hint", compiled.image.pixels, SheetBaker.decodePng(File(assetsDir, "$group.png").inputStream())!!.pixels)
            assertEquals("$group.json $hint", compiled.json, File(assetsDir, "$group.json").readText().replace("\r\n", "\n"))
            assertArrayEquals("$group.anchors.png $hint", compiled.anchors.pixels, SheetBaker.decodePng(File(assetsDir, "$group.anchors.png").inputStream())!!.pixels)
        }
    }

    @Test
    fun `bootstrap tem camadas semanticas e ida e volta preserva tudo`() {
        val doc = ArtBootstrapStudio.document("hoodie_walk")
        val back = AsepriteFile.decode(AsepriteFile.encode(doc))
        assertEquals(ArtBootstrapStudio.LAYERS, back.layers.map { it.name })
        assertEquals(doc.tags, back.tags)
        assertEquals(doc.frames.map { it.durationMs }, back.frames.map { it.durationMs })
        for (i in doc.frames.indices) assertArrayEquals(doc.flatten(i).pixels, back.flatten(i).pixels)
        val baseline = back.layers.first { it.name == ArtBootstrapStudio.BASELINE }
        assertTrue("referência escondida e travada", baseline.reference && !baseline.visible && baseline.flags and AsepriteFile.LAYER_EDITABLE == 0)
        // Partes do corpo de verdade: cada uma dessas camadas tem pixels em algum frame.
        val used = back.frames.flatMap { f -> f.cels.map { back.layers[it.layer].name } }.toSet()
        listOf("outline", "head", "ears", "face", "hoodie", "hoodie_shadow", "arm_left", "arm_right", "hand_left", "hand_right", "leg_left", "leg_right", "tail", "strings", "backpack")
            .forEach { assertTrue("camada $it vazia", it in used) }
    }

    @Test
    fun `retoque e passes nao mudam pes nem contagem de frames`() {
        ArtBootstrapStudio.GROUPS.values.flatten().forEach { (anim, facing) ->
            for (i in anim.frames.indices) {
                val f = ArtBootstrapStudio.frame(anim, facing, i)
                assertEquals(HoodiePainter.FEET, f.anchors.feet)
                assertEquals(anim.clip.frames[i].durationMs, f.durationMs)
                assertEquals(HoodiePainter.WIDTH, f.image.width)
            }
        }
    }

    @Test
    fun `ancoras de mao obrigatorias ficam na pata desenhada`() {
        ArtBootstrapStudio.GROUPS.values.flatten().forEach { (anim, facing) ->
            val required = com.hoodie.app.pixel.sprite.RequiredAnchors.required(anim)
            for (i in anim.frames.indices) {
                val f = ArtBootstrapStudio.frame(anim, facing, i)
                if (com.hoodie.app.pixel.sprite.Anchor.RIGHT_HAND in required)
                    assertEquals("$anim/$i mão direita", HoodiePainter.Part.HAND_RIGHT, f.partAt(f.anchors.rightHand.x, f.anchors.rightHand.y))
            }
        }
    }

    @Test
    fun `preview procedural x final`() {
        val pairs = listOf(AnimationId.WALK to Facing.SIDE, AnimationId.WALK to Facing.FRONT, AnimationId.WALK to Facing.BACK, AnimationId.IDLE to Facing.FRONT, AnimationId.SLEEP to Facing.FRONT, AnimationId.WORK_TYPING to Facing.FRONT)
        val buffers = pairs.flatMap { (a, f) -> listOf(ArtBootstrapStudio.procedural(a, f, 0).image, ArtBootstrapStudio.frame(a, f, 0).image) }
        PreviewExport.sheet("final_vs_procedural", buffers, columns = 2, scale = 4)
        ArtBootstrapStudio.GROUPS.forEach { (group, clips) ->
            val frames = clips.flatMap { (a, f) -> a.frames.indices.map { ArtBootstrapStudio.frame(a, f, it).image } }
            PreviewExport.sheet("art_$group", frames, columns = 8, scale = 3)
        }
    }
}
