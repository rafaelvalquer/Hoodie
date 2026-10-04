package com.hoodie.app.pixel

import com.hoodie.app.core.model.CommuteStyle
import com.hoodie.app.core.model.HoodieActivity
import com.hoodie.app.core.model.UserContextType
import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.animation.AnimationStateMachine
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.renderer.SceneRenderer
import com.hoodie.app.pixel.scene.VisualDirector
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId
import com.hoodie.app.pixel.scene.SceneRegistry
import com.hoodie.app.pixel.sprite.Facing
import java.io.File
import java.nio.ByteBuffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/** Gera a folha comparativa atual sem aprovar nem regravar os hashes de referência. */
class NpcArtReviewPreviewTest {
    @Test fun exportAmbientNpcScenesAtProductionScaleAndLayering() {
        val cases = listOf(
            Triple(SceneId.OFFICE, 0, DayPeriod.DAY),
            Triple(SceneId.OFFICE, 1, DayPeriod.DAY),
            Triple(SceneId.BUS, 0, DayPeriod.DAY),
            Triple(SceneId.TRAIN, 0, DayPeriod.DAY),
            Triple(SceneId.METRO, 0, DayPeriod.DAY),
            Triple(SceneId.RESTAURANT, 0, DayPeriod.DAY),
            Triple(SceneId.SHOPPING, 0, DayPeriod.DAY),
            Triple(SceneId.LEISURE, 0, DayPeriod.DAY),
            Triple(SceneId.OFFICE, 0, DayPeriod.NIGHT),
            Triple(SceneId.BUS, 0, DayPeriod.NIGHT),
            Triple(SceneId.METRO, 0, DayPeriod.NIGHT),
            Triple(SceneId.RESTAURANT, 0, DayPeriod.NIGHT),
        )
        val images = cases.map { (sceneId, variant, period) ->
            val scene = SceneRegistry[sceneId]
            val env = SceneEnv(period = period, clockMinute = if (period == DayPeriod.NIGHT) 22 * 60 else 10 * 60, variant = variant)
            assertTrue("$sceneId variant $variant at $period must contain at least one ambient NPC", scene.ambientNpcs(env).isNotEmpty())
            PixelBuffer(scene.width, scene.height).also { buffer ->
                buffer.copyFrom(SceneRenderer().renderEmpty(scene, env, timeMs = 4_700L))
            }.also { image ->
                PreviewExport.save("npc-art-review/scene-${sceneId.name.lowercase()}-${period.name.lowercase()}-v$variant", image, scale = 1)
            }
        }
        PreviewExport.sheet("npc-art-review/scenes-with-npcs", images, columns = 3, scale = 1)
        cases.forEach { (sceneId, variant, period) ->
            assertPngDimensions("scene-${sceneId.name.lowercase()}-${period.name.lowercase()}-v$variant", 240, 320)
        }
        assertEquals("day and night scene contexts must be represented", 12, images.size)
    }

    @Test fun exportReferenceSpeciesAcrossUniversalAnimationsAndViews() {
        val styles = NpcArtReviewFixture.characterIds.map { id ->
            NpcCharacterRegistry.all.first { it.id == id }
        }
        val animations = listOf(
            NpcAnimation.IDLE, NpcAnimation.WALK, NpcAnimation.LOOK, NpcAnimation.TALK,
            NpcAnimation.SIT_PHONE, NpcAnimation.SIT_EAT, NpcAnimation.SIT_SLEEP,
        )
        val facings = listOf(Facing.FRONT, Facing.SIDE, Facing.BACK)

        animations.forEach { animation ->
            val comparisons = styles.flatMap { style ->
                facings.map { facing ->
                    val pose = NpcMotionController.pose(
                        animation, 460, 0, SpeciesMotionProfiles.forCharacter(style),
                    ).copy(facing = facing)
                    NpcArtReviewFixture.comparison(style, pose)
                }
            }
            val name = "species-${animation.name.lowercase()}"
            PreviewExport.sheet("npc-art-review/$name", comparisons, columns = facings.size, scale = 2)
            assertPngDimensions(name, 612, 1036)
        }
    }

    @Test fun exportAdditionalRegisteredIdentitiesAcrossUniversalAnimationsAndViews() {
        val styles = NpcArtReviewFixture.additionalIdentityIds.map { id ->
            NpcCharacterRegistry.all.first { it.id == id }
        }
        val animations = listOf(
            NpcAnimation.IDLE, NpcAnimation.WALK, NpcAnimation.LOOK, NpcAnimation.TALK,
            NpcAnimation.SIT_PHONE, NpcAnimation.SIT_EAT, NpcAnimation.SIT_SLEEP,
        )
        val facings = listOf(Facing.FRONT, Facing.SIDE, Facing.BACK)

        animations.forEach { animation ->
            val comparisons = styles.flatMap { style ->
                facings.map { facing ->
                    val pose = NpcMotionController.pose(
                        animation, 460, 0, SpeciesMotionProfiles.forCharacter(style),
                    ).copy(facing = facing)
                    NpcArtReviewFixture.comparison(style, pose)
                }
            }
            val name = "identities-${animation.name.lowercase()}"
            PreviewExport.sheet("npc-art-review/$name", comparisons, columns = facings.size, scale = 2)
            assertPngDimensions(name, 612, 592)
        }
        assertEquals("every additional catalog identity must be reviewed", 4, styles.size)
    }

    @Test fun exportCurrentHoodieAndNpcReferenceArtwork() {
        val styles = NpcCharacterRegistry.all.associateBy { it.id }
        val pairs = NpcArtReviewFixture.characterIds.map { id ->
            val style = styles.getValue(id)
            NpcArtReviewFixture.idleComparison(style).also { pair ->
                PreviewExport.save("npc-art-review/hoodie_vs_$id", pair, scale = 4)
            }
        }
        assertEquals("all approved species must be represented", 7, pairs.size)
        PreviewExport.sheet("npc-art-review-current", pairs, columns = 2, scale = 4)
    }

    @Test fun exportBulldogBenchmarkStatesAndOfficeScene() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val comparisons = listOf(
            "bulldog_idle_front" to NpcArtReviewFixture.idlePose(style),
            "bulldog_walk_side" to NpcMotionController.pose(NpcAnimation.WALK, 460, 0, SpeciesMotionProfiles.forCharacter(style)).copy(facing = Facing.SIDE),
            "bulldog_talk" to NpcMotionController.pose(NpcAnimation.TALK, 930, 0, SpeciesMotionProfiles.forCharacter(style)).copy(facing = Facing.SIDE),
        ).map { (name, pose) ->
            val pair = NpcArtReviewFixture.comparison(style, pose)
            PreviewExport.save("npc-art-review/$name", pair, scale = 4)
            pair
        }
        assertEquals(3, comparisons.size)
        PreviewExport.sheet("npc-art-review/bulldog-benchmark", comparisons, columns = 1, scale = 3)

        val suitDayPose = NpcMotionController.pose(
            NpcAnimation.IDLE, 400, 0, SpeciesMotionProfiles.forCharacter(style),
        ).copy(facing = Facing.FRONT)
        val suitDay = PixelBuffer(64, 88).also { canvas ->
            canvas.fill(0xFFDCEAF1.toInt())
            canvas.hline(0, 63, 78, 0xFF8194A8.toInt())
            canvas.box(0, 79, 63, 87, 0xFFAEBCC6.toInt())
            canvas.blit(CharacterPainter.paint(style, suitDayPose, SpeciesMotionProfiles.forCharacter(style).renderMotion()).image, 8, 7)
        }
        PreviewExport.save("npc-art-review/bulldog_suit_day", suitDay, scale = 3)

        val phoneStyle = NpcCharacterRegistry.MOUSE_COMMUTER
        val phonePose = NpcMotionController.pose(
            NpcAnimation.SIT_PHONE, 460, 5, SpeciesMotionProfiles.forCharacter(phoneStyle),
        )
        val phoneComparison = NpcArtReviewFixture.comparison(phoneStyle, phonePose)
        PreviewExport.save("npc-art-review/mouse-sit-phone", phoneComparison, scale = 4)

        val machine = AnimationStateMachine(Random(44))
        val visual = VisualDirector.resolve(
            activity = HoodieActivity.WORKING,
            context = UserContextType.WORK,
            homeOffice = false,
            commute = CommuteStyle.WALK,
            variant = 0,
        )
        machine.setVisual(visual, 1)
        var time = 1L
        var frame = machine.frame(time, 10 * 60 + 8, DayPeriod.DAY)!!
        while (time < 4_800L) {
            time += 33
            frame = machine.frame(time, 10 * 60 + 8, DayPeriod.DAY)!!
        }
        val office = PixelBuffer(240, 320).also { it.copyFrom(SceneRenderer().render(frame, time)) }
        PreviewExport.save("npc-art-review/bulldog-office-scene", office, scale = 2)

        val goldenMaster = PixelBuffer(304, 392).also { canvas ->
            canvas.fill(0xFF2B2E4A.toInt())
            comparisons.forEachIndexed { index, comparison -> canvas.blit(comparison, index * 102, 0) }
            canvas.blit(suitDay, 0, 72)
            canvas.blit(office, 64, 72)
        }
        PreviewExport.save("npc-art-review/bulldog-golden-master", goldenMaster, scale = 2)

        assertPngDimensions("bulldog_idle_front", 400, 288)
        assertPngDimensions("bulldog_walk_side", 400, 288)
        assertPngDimensions("bulldog_talk", 400, 288)
        assertPngDimensions("bulldog_suit_day", 192, 264)
        assertPngDimensions("bulldog-office-scene", 480, 640)
        assertPngDimensions("bulldog-golden-master", 608, 784)
    }

    private fun assertPngDimensions(name: String, width: Int, height: Int) {
        val file = File(PreviewExport.dir, "npc-art-review/$name.png")
        assertTrue("missing review screenshot: ${file.absolutePath}", file.isFile && file.length() > 32)
        val header = file.inputStream().use { input -> ByteArray(24).also { bytes -> assertEquals(24, input.read(bytes)) } }
        assertEquals("$name png width", width, ByteBuffer.wrap(header, 16, 4).int)
        assertEquals("$name png height", height, ByteBuffer.wrap(header, 20, 4).int)
    }
}
