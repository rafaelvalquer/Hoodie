package com.hoodie.app.pixel

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.AsepriteSheetParser
import com.hoodie.app.pixel.sprite.CompositeSpriteProvider
import com.hoodie.app.pixel.sprite.Direction
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.ProceduralSpriteProvider
import com.hoodie.app.pixel.sprite.SpriteSheetProvider
import com.hoodie.app.pixel.sprite.SpriteRequest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

/** Aseprite JSON → SpriteSheetProvider → mesmo resultado do procedural. */
class SpriteSheetPipelineTest {

    private val baselineClips = listOf(
        AnimationId.WALK to Facing.SIDE, AnimationId.WALK to Facing.FRONT, AnimationId.WALK to Facing.BACK,
        AnimationId.IDLE to Facing.FRONT,
    )

    private fun loadBaked(): Pair<SpriteSheetProvider, com.hoodie.app.pixel.sprite.SheetLoadReport> {
        val root = Files.createTempDirectory("hoodie-sheets").toFile()
        SheetBaker.write(File(root, SpriteSheetProvider.DIR), "hoodie_baseline", SheetBaker.bake(baselineClips))
        return SpriteSheetProvider.load(SheetBaker.assetSource(root), SheetBaker.decoder)
    }

    @Test
    fun `tags com direcao sao entendidas`() {
        assertEquals(AnimationId.WALK to Facing.SIDE, AsepriteSheetParser.tagKey("walk_side"))
        assertEquals(AnimationId.WORK_TYPING to Facing.FRONT, AsepriteSheetParser.tagKey("work_typing"))
        assertEquals(AnimationId.SLEEP to Facing.BACK, AsepriteSheetParser.tagKey("SLEEP_back"))
        assertEquals(null, AsepriteSheetParser.tagKey("coffe"))
    }

    @Test
    fun `sheet exportado e lido de volta reproduz frames, duracoes e ancoras`() {
        val (sheets, report) = loadBaked()
        assertTrue(report.errors.toString(), report.errors.isEmpty())
        assertEquals(baselineClips.toSet(), sheets.available)
        for ((anim, facing) in baselineClips) {
            val dir = when (facing) { Facing.FRONT -> Direction.FRONT; Facing.BACK -> Direction.BACK; Facing.SIDE -> Direction.LEFT }
            assertArrayEquals(ProceduralSpriteProvider.durations(anim, dir), sheets.durations(anim, dir))
            anim.frames.indices.forEach { i ->
                val expected = ProceduralSpriteProvider.frame(SpriteRequest(anim, dir, i))
                val actual = sheets.frame(SpriteRequest(anim, dir, i))
                assertArrayEquals("$anim/$facing/$i", expected.image.pixels, actual.image.pixels)
                assertEquals(expected.anchors, actual.anchors)
                assertEquals(expected.events, actual.events)
                assertEquals("spritesheet", actual.source)
            }
        }
        // RIGHT = espelho da vista lateral do sheet.
        val right = sheets.frame(SpriteRequest(AnimationId.WALK, Direction.RIGHT, 2))
        assertArrayEquals(ProceduralSpriteProvider.frame(SpriteRequest(AnimationId.WALK, Direction.RIGHT, 2)).image.pixels, right.image.pixels)
    }

    @Test
    fun `composite usa o sheet onde existe e o procedural no resto`() {
        val composite = CompositeSpriteProvider(loadBaked().first)
        assertEquals("spritesheet", composite.frame(SpriteRequest(AnimationId.WALK, Direction.LEFT, 0)).source)
        assertEquals("procedural", composite.frame(SpriteRequest(AnimationId.WORK_TYPING, Direction.FRONT, 0)).source)
        // Item na mão de um sheet vira overlay desenhado na âncora.
        assertFalse(composite.supports(AnimationId.DRINK, Facing.FRONT).not())
    }

    @Test
    fun `problemas de importacao viram relatorio, nao crash`() {
        val baked = SheetBaker.bake(listOf(AnimationId.IDLE to Facing.FRONT))
        val badJson = baked.json.replace("\"idle_front\"", "\"idel_front\"")
        val problems = mutableListOf<String>()
        val parsed = AsepriteSheetParser.parse(badJson, baked.image, problems)
        assertTrue(parsed.isEmpty())
        assertTrue(problems.any { "idel_front" in it })

        val root = Files.createTempDirectory("hoodie-bad").toFile()
        val small = SheetBaker.Baked(PixelBuffer(32, 32), baked.json.replace("\"w\": 48", "\"w\": 32").replace("\"h\": 72", "\"h\": 32"))
        SheetBaker.write(File(root, SpriteSheetProvider.DIR), "small", small)
        val (provider, report) = SpriteSheetProvider.load(SheetBaker.assetSource(root), SheetBaker.decoder)
        assertTrue(provider.available.isEmpty())
        assertTrue(report.problems.toString(), report.problems.any { "48×72" in it })
    }

    @Test
    fun `ancoras por frame vem da camada anchors mesmo sem slices`() {
        val baked = SheetBaker.bake(listOf(AnimationId.DRINK to Facing.FRONT))
        // Sem slices no JSON: só a camada anchors informa onde está a mão (caneca).
        val noSlices = org.json.JSONObject(baked.json).also { it.getJSONObject("meta").remove("slices") }.toString()
        val parsed = AsepriteSheetParser.parse(noSlices, baked.image, mutableListOf(), baked.anchors)
        val frames = parsed.getValue(AnimationId.DRINK to Facing.FRONT)
        frames.forEachIndexed { i, f ->
            val expected = ProceduralSpriteProvider.frame(SpriteRequest(AnimationId.DRINK, Direction.FRONT, i)).anchors
            assertEquals("frame $i", expected, f.anchors)
        }
        // A mão se move entre frames (caneca até a boca): âncora animada de verdade.
        assertTrue(frames.map { it.anchors.rightHand }.distinct().size > 1)
    }

    @Test
    fun `overlay vira remendo sobre a arte final e clip herdado sentado usa o procedural`() {
        val root = Files.createTempDirectory("hoodie-overlay").toFile()
        SheetBaker.write(File(root, SpriteSheetProvider.DIR), "idle", SheetBaker.bake(listOf(AnimationId.IDLE to Facing.FRONT, AnimationId.IDLE_LOOK to Facing.FRONT)))
        val composite = CompositeSpriteProvider(SpriteSheetProvider.load(SheetBaker.assetSource(root), SheetBaker.decoder).first)
        val plain = SpriteRequest(AnimationId.IDLE, Direction.FRONT, 0)
        val blink = plain.copy(overlay = com.hoodie.app.pixel.sprite.PoseOverlay(blink = com.hoodie.app.pixel.sprite.Eyes.CLOSED))
        val f = composite.frame(blink)
        assertEquals("spritesheet", f.source)
        // Mesmos pixels do procedural com os olhos fechados (o sheet aqui é o próprio procedural).
        assertTrue(f.image.pixels.contentEquals(ProceduralSpriteProvider.frame(blink).image.pixels))
        assertFalse(f.image.pixels.contentEquals(composite.frame(plain).image.pixels))
        // IDLE_LOOK herda a postura: desenhado em pé, sentado cai no procedural.
        assertEquals("spritesheet", composite.frame(SpriteRequest(AnimationId.IDLE_LOOK)).source)
        assertEquals("procedural", composite.frame(SpriteRequest(AnimationId.IDLE_LOOK, posture = com.hoodie.app.pixel.sprite.Posture.SITTING)).source)
    }

    @Test
    fun `pixel lab compara procedural, final e overlay`() {
        val (sheets, _) = loadBaked()
        val active = CompositeSpriteProvider(sheets)
        val req = SpriteRequest(AnimationId.WALK, Direction.LEFT, 2)
        val m = com.hoodie.app.pixel.debug.SourceCompare
        assertEquals("procedural", m.provider(com.hoodie.app.pixel.debug.CompareMode.PROCEDURAL, active).frame(req).source)
        assertEquals("spritesheet", m.provider(com.hoodie.app.pixel.debug.CompareMode.FINAL, active).frame(req).source)
        val over = m.provider(com.hoodie.app.pixel.debug.CompareMode.OVERLAY, active).frame(req)
        assertEquals("spritesheet+procedural", over.source)
        assertEquals(active.frame(req).anchors, over.anchors)
    }

    @Test
    fun `sheet com contagem de frames errada e rejeitado e o procedural assume`() {
        val root = Files.createTempDirectory("hoodie-count").toFile()
        val baked = SheetBaker.bake(listOf(AnimationId.IDLE to Facing.FRONT, AnimationId.WALK to Facing.SIDE))
        val json = org.json.JSONObject(baked.json)
        json.getJSONObject("meta").getJSONArray("frameTags").getJSONObject(0).put("to", 1) // idle com 2 de 8 frames
        SheetBaker.write(File(root, SpriteSheetProvider.DIR), "short", baked.copy(json = json.toString()))
        val (sheets, report) = SpriteSheetProvider.load(SheetBaker.assetSource(root), SheetBaker.decoder)
        assertTrue(report.problems.toString(), report.problems.any { "o clip tem 8" in it && "rejeitado" in it })
        assertFalse(sheets.supports(AnimationId.IDLE, Facing.FRONT))
        assertTrue("o resto do arquivo continua valendo", sheets.supports(AnimationId.WALK, Facing.SIDE))
        assertEquals("procedural", CompositeSpriteProvider(sheets).frame(SpriteRequest(AnimationId.IDLE, Direction.FRONT, 0)).source)
    }

    @Test
    fun `ancora critica nao desenhada rejeita o clip e o procedural assume`() {
        val root = Files.createTempDirectory("hoodie-anchor").toFile()
        val baked = SheetBaker.bake(listOf(AnimationId.DRINK to Facing.FRONT))
        // Sem slices e sem camada anchors: a mão (caneca) cai no ponto padrão.
        val noSlices = org.json.JSONObject(baked.json).also { it.getJSONObject("meta").remove("slices") }.toString()
        SheetBaker.write(File(root, SpriteSheetProvider.DIR), "drink", baked.copy(json = noSlices, anchors = null))
        val (sheets, report) = SpriteSheetProvider.load(SheetBaker.assetSource(root), SheetBaker.decoder)
        assertFalse(sheets.supports(AnimationId.DRINK, Facing.FRONT))
        val err = report.errors.single()
        assertEquals(AnimationId.DRINK, err.clip)
        assertTrue(err.message, "RIGHT_HAND" in err.message && "FEET" in err.message && "rejeitado" in err.message)
        assertEquals("procedural", CompositeSpriteProvider(sheets).frame(SpriteRequest(AnimationId.DRINK, Direction.FRONT, 0)).source)
    }

    @Test
    fun `cores fora da paleta sao aviso, nao erro`() {
        val root = Files.createTempDirectory("hoodie-palette").toFile()
        val baked = SheetBaker.bake(listOf(AnimationId.IDLE to Facing.FRONT))
        baked.image.pixels.indexOfFirst { it ushr 24 != 0 }.let { baked.image.pixels[it] = 0xFF123456.toInt() }
        SheetBaker.write(File(root, SpriteSheetProvider.DIR), "idle", baked)
        val (sheets, report) = SpriteSheetProvider.load(SheetBaker.assetSource(root), SheetBaker.decoder)
        assertTrue(sheets.supports(AnimationId.IDLE, Facing.FRONT))
        assertTrue(report.errors.isEmpty())
        assertEquals(com.hoodie.app.pixel.sprite.SheetProblemSeverity.WARNING, report.warnings.single().severity)
        assertTrue("paleta possui 1 cores extras" in report.warnings.single().message)
    }

    @Test
    fun `cobertura visual por grupo`() {
        val all = AnimationId.entries.flatMap { id -> Facing.entries.map { id to it } }.toSet()
        assertEquals(100, com.hoodie.app.pixel.sprite.RequiredShippedAnimations.coverage(all).percent)
        assertEquals(0, com.hoodie.app.pixel.sprite.RequiredShippedAnimations.coverage(emptySet()).percent)
        val walkOnly = Facing.entries.map { AnimationId.WALK to it }.toSet()
        val loco = com.hoodie.app.pixel.sprite.RequiredShippedAnimations.coverage(walkOnly, com.hoodie.app.pixel.animation.AnimGroup.LOCOMOTION)
        assertEquals(1, loco.finalCount)
    }

    /** Gera o baseline que vai para assets-source/hoodie/baseline (rodado a cada build de teste). */
    @Test
    fun `exporta baseline para o artista`() {
        val out = File(PreviewExport.dir, "baseline")
        SheetBaker.write(out, "hoodie_walk", SheetBaker.bake(listOf(AnimationId.WALK to Facing.SIDE, AnimationId.WALK to Facing.FRONT, AnimationId.WALK to Facing.BACK, AnimationId.WALK_BACKPACK to Facing.SIDE)))
        SheetBaker.write(out, "hoodie_idle", SheetBaker.bake(listOf(AnimationId.IDLE to Facing.FRONT, AnimationId.IDLE_SIT to Facing.FRONT, AnimationId.IDLE_LOOK to Facing.FRONT, AnimationId.IDLE_EAR to Facing.FRONT, AnimationId.IDLE_SCRATCH to Facing.FRONT)))
        SheetBaker.write(out, "hoodie_work", SheetBaker.bake(listOf(AnimationId.SIT_DOWN, AnimationId.WORK_TYPING, AnimationId.STOP_TYPING, AnimationId.REACH_MOUSE, AnimationId.WORK_MOUSE, AnimationId.WORK_READ, AnimationId.STAND_UP).map { it to Facing.FRONT }))
        SheetBaker.write(out, "hoodie_sleep", SheetBaker.bake(listOf(AnimationId.BED_SIT, AnimationId.BED_LIE_DOWN, AnimationId.SLEEP, AnimationId.SLEEP_TURN, AnimationId.WAKE_EYES, AnimationId.BED_EXIT).map { it to Facing.FRONT }))
        assertTrue(File(out, "hoodie_walk.json").exists())
    }
}
