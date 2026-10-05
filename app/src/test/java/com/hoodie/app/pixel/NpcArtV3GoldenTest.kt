package com.hoodie.app.pixel

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import java.io.File
import java.security.MessageDigest
import java.util.HexFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.json.JSONObject

/**
 * Goldens artísticos V3 (PNGs para revisão manual + SHA para regressão):
 *
 *     npc-v3/<espécie>_<animação>.png   personagem isolado
 *     npc-v3/<espécie>-walk-sheet.png   folha de contato da passada (8 fases)
 *     npc-v3/scene_<cena>.png           personagem no cenário real
 *
 * Gravar após revisar: RECORD_SCENE_GOLDENS=true.
 */
class NpcArtV3GoldenTest {
    private val bg = 0xFF2B2E4A.toInt()

    private fun frame(style: CharacterStyle, animation: NpcAnimation, t: Long): PixelBuffer {
        val f = NpcPoseLibrary.frame(animation, t, 0, SpeciesMotionProfiles.forCharacter(style))
        return PixelBuffer(48, 72).also { it.fill(bg); it.blit(CharacterPainter.paint(style, f.pose, f.motion).image, 0, 0) }
    }

    private fun walkSheet(style: CharacterStyle): PixelBuffer {
        val motion = SpeciesMotionProfiles.forCharacter(style)
        val cycleMs = motion.stepLength * 2 * motion.msPerPixel
        val sheet = PixelBuffer(8 * 50 + 2, 76).also { it.fill(bg) }
        (0 until 8).forEach { i ->
            val f = NpcPoseLibrary.frame(NpcAnimation.WALK, i * cycleMs / 8, 0, motion)
            sheet.blit(CharacterPainter.paint(style, f.pose, f.motion).image, 2 + i * 50, 2)
            sheet.hline(2 + i * 50, 49 + i * 50, 73, 0xFF62D3CF.toInt())
        }
        return sheet
    }

    private fun scene(id: SceneId, t: Long): PixelBuffer {
        val s = SceneRegistry[id]
        return PixelBuffer(240, 320).also { it.copyFrom(SceneRenderer().renderEmpty(s, SceneEnv(DayPeriod.DAY, 600, variant = 0), t)) }
    }

    private fun digest(b: PixelBuffer): String {
        val md = MessageDigest.getInstance("SHA-256")
        b.pixels.forEach { p -> md.update(byteArrayOf((p ushr 24).toByte(), (p ushr 16).toByte(), (p ushr 8).toByte(), p.toByte())) }
        return HexFormat.of().formatHex(md.digest())
    }

    @Test fun npcV3ArtGoldens() {
        val r = NpcCharacterRegistry
        val images = linkedMapOf(
            "bulldog_idle" to frame(r.BULLDOG_EXEC, NpcAnimation.IDLE, 400),
            "bulldog_walk" to frame(r.BULLDOG_EXEC, NpcAnimation.WALK, 300),
            "bulldog_talk" to frame(r.BULLDOG_EXEC, NpcAnimation.TALK, 900),
            "dog_idle" to frame(r.DOG_WORKER, NpcAnimation.IDLE, 400),
            "rabbit_idle" to frame(r.RABBIT_ANALYST, NpcAnimation.IDLE, 400),
            "mouse_idle" to frame(r.MOUSE_COMMUTER, NpcAnimation.IDLE, 400),
            "duck_idle" to frame(r.DUCK_SLEEPY, NpcAnimation.IDLE, 400),
            "raccoon_idle" to frame(r.RACCOON_COMMUTER, NpcAnimation.IDLE, 400),
            "cat_idle" to frame(r.CAT_COLLEAGUE, NpcAnimation.IDLE, 400),
            "bulldog-walk-sheet" to walkSheet(r.BULLDOG_EXEC),
            "rabbit-walk-sheet" to walkSheet(r.RABBIT_ANALYST),
            "mouse-walk-sheet" to walkSheet(r.MOUSE_COMMUTER),
            "duck-walk-sheet" to walkSheet(r.DUCK_SLEEPY),
            "scene_office" to scene(SceneId.OFFICE, 4_800),
            "scene_bus" to scene(SceneId.BUS, 2_000),
            "scene_train" to scene(SceneId.TRAIN, 2_000),
            "scene_metro" to scene(SceneId.METRO, 2_000),
            "scene_restaurant" to scene(SceneId.RESTAURANT, 2_000),
            "scene_shopping" to scene(SceneId.SHOPPING, 3_000),
            "scene_leisure" to scene(SceneId.LEISURE, 3_000),
        )
        images.forEach { (name, img) -> PreviewExport.save("npc-v3/$name", img, scale = if (name.startsWith("scene")) 2 else 5) }
        PreviewExport.sheet("npc-v3/scenes", images.filterKeys { it.startsWith("scene") }.values.toList(), columns = 4, scale = 2)

        val actual = images.mapValues { digest(it.value) }
        val lines = actual.map { (k, v) -> "$k\t$v" }
        if (System.getProperty("approveNpcV3Goldens") == "true") {
            assertTrue(
                "goldens V3 só podem ser atualizados depois de toda a matriz receber aprovação humana em npc-art-v3-review-manifest.json",
                manualReviewApproved(),
            )
            File("src/test/resources/npc-art-v3.sha256").writeText(lines.joinToString("\n", postfix = "\n"))
            return
        }
        val expected = requireNotNull(javaClass.getResourceAsStream("/npc-art-v3.sha256")) { "revise build/pixel-preview/npc-v3 e grave com RECORD_SCENE_GOLDENS=true" }
            .bufferedReader().readLines().filter { it.isNotBlank() }.associate { it.substringBefore('\t') to it.substringAfter('\t') }
        assertEquals(expected.keys, actual.keys)
        actual.forEach { (k, v) -> assertEquals("golden NPC V3 mudou: $k", expected.getValue(k), v) }
    }

    private fun manualReviewApproved(): Boolean {
        val stream = javaClass.getResourceAsStream("/npc-art-v3-review-manifest.json") ?: return false
        val manifest = stream.bufferedReader().use { JSONObject(it.readText()) }
        if (manifest.optString("approval_state") != "APPROVED") return false
        val required = listOf(
            "matrices", "walk_all_species", "turn_sit_talk_sheets", "expressions_and_head_crops",
            "outfits", "props", "scale_legibility_090_to_100", "public_scenes", "day_night_contrast",
            "hoodie_bulldog_gate", "hoodie_cat_gate",
        )
        if (required.any { manifest.optString(it) != "APPROVED" }) return false
        val characters = manifest.optJSONObject("characters") ?: return false
        val gates = listOf("idle", "walk", "talk", "sit")
        val keys = characters.keys()
        while (keys.hasNext()) {
            val character = characters.optJSONObject(keys.next()) ?: return false
            if (gates.any { character.optString(it) != "APPROVED" }) return false
        }
        return true
    }
}
