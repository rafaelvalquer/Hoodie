package com.hoodie.app.pixel

import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.AmbientNpcDefinition
import com.hoodie.app.pixel.npc.AmbientNpcSlot
import com.hoodie.app.pixel.npc.NpcBehaviorProfile
import com.hoodie.app.pixel.npc.NpcDirector
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcSpeechProfile
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.npc.SpeciesMotionProfile
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterCanvas
import com.hoodie.app.pixel.character.CharacterRenderMotion
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.npc.NpcRenderer
import com.hoodie.app.pixel.renderer.PixelBuffer
import org.junit.Assert.assertFalse
import com.hoodie.app.pixel.scene.SceneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.roundToInt

class NpcMovementTest {
    @Test fun ambientRenderScaleKeepsTheFeetAnchorAndUniformlyReducesCharacterPixels() {
        val style = NpcCharacterRegistry.CAT_COLLEAGUE
        val original = CharacterPainter.paint(style, CharacterPose())
        val scaled = NpcRenderer.scaleFrameForAmbient(original)
        fun bounds(frame: PixelBuffer): IntArray {
            val points = frame.pixels.indices.filter { frame.pixels[it] ushr 24 != 0 }
            val xs = points.map { it % frame.width }
            val ys = points.map { it / frame.width }
            return intArrayOf(xs.min(), ys.min(), xs.max(), ys.max())
        }
        val before = bounds(original.image)
        val after = bounds(scaled.image)
        val beforeWidth = before[2] - before[0] + 1
        val beforeHeight = before[3] - before[1] + 1
        val afterWidth = after[2] - after[0] + 1
        val afterHeight = after[3] - after[1] + 1

        assertEquals(CharacterCanvas.FEET, scaled.anchors.feet)
        assertTrue("NPC width should be about 80% (${beforeWidth}px → ${afterWidth}px)", kotlin.math.abs(afterWidth - (beforeWidth * NpcRenderer.AMBIENT_SCALE).toInt()) <= 2)
        assertTrue("NPC height should be about 80% (${beforeHeight}px → ${afterHeight}px)", kotlin.math.abs(afterHeight - (beforeHeight * NpcRenderer.AMBIENT_SCALE).toInt()) <= 2)
        assertEquals(
            (CharacterCanvas.FEET.y + (original.anchors.head.y - CharacterCanvas.FEET.y) * NpcRenderer.AMBIENT_SCALE).roundToInt(),
            scaled.anchors.head.y,
        )
        assertTrue("nearest-neighbor scaling should preserve source palette colors", scaled.image.pixels.filter { it ushr 24 != 0 }.all { color -> color in original.image.pixels })
    }

    @Test fun executivePathEntersLooksTalksContinuesAndLoops() {
        val executive = NpcDirector.plan(SceneId.OFFICE, variant = 0).first().copy(seed = 0)
        assertTrue(NpcMotionController.movement(executive, 0).x < 0)
        val look = NpcMotionController.movement(executive, 2_300)
        assertEquals(NpcAnimation.LOOK, look.animation)
        assertEquals(64, look.x)
        val talk = NpcMotionController.movement(executive, 4_700)
        assertEquals(NpcAnimation.TALK, talk.animation)
        assertEquals(122, talk.x)
        val exit = NpcMotionController.movement(executive, 9_000)
        assertEquals(NpcAnimation.WALK, exit.animation)
        assertTrue(exit.x > talk.x)
        assertTrue(NpcMotionController.movement(executive, 10_400).x < 0)
    }

    @Test fun executiveSpeechBubbleIsTiedToItsTalkStopInsteadOfTheWallClock() {
        val executive = NpcDirector.plan(SceneId.OFFICE, variant = 0).first().copy(seed = 0)
        val withSpeech = executive
        val withoutSpeech = executive.copy(definition = executive.definition.copy(speechProfile = null))
        fun render(slot: com.hoodie.app.pixel.npc.AmbientNpcSlot, timeMs: Long) =
            PixelBuffer(240, 320).also { it.fill(0xFF101010.toInt()) }.also {
                NpcRenderer.draw(it, slot, timeMs)
            }

        assertTrue("offscreen NPC must not show its future speech bubble", render(withSpeech, 0).pixels.contentEquals(render(withoutSpeech, 0).pixels))
        val speechFrame = render(withSpeech, 4_700)
        val plainFrame = render(withoutSpeech, 4_700)
        assertFalse("the bubble should appear during the TALK stop", speechFrame.pixels.contentEquals(plainFrame.pixels))
        val talkMovement = NpcMotionController.movement(withSpeech, 4_700)
        val profile = withSpeech.definition.behaviorProfile.motion
        val talkPose = NpcMotionController.pose(talkMovement.animation, talkMovement.localTimeMs, withSpeech.seed, profile)
        val scaledFrame = NpcRenderer.scaleFrameForAmbient(CharacterPainter.paint(
            withSpeech.definition.characterStyle, talkPose, profile.renderMotion(),
        ))
        val headAnchorY = scaledFrame.anchors.head.y
        val headTopY = talkMovement.floorY - CharacterCanvas.FEET.y + headAnchorY -
            (withSpeech.definition.characterStyle.species.headHeight * NpcRenderer.AMBIENT_SCALE).roundToInt() / 2
        val changedRows = speechFrame.pixels.indices.filter { speechFrame.pixels[it] != plainFrame.pixels[it] }.map { it / speechFrame.width }
        assertTrue("speech bubble tail must stop before the head silhouette", changedRows.max() < headTopY)
        assertTrue("the bubble paper should begin above the head", speechFrame.pixels.indices.any { index ->
            speechFrame.pixels[index] == 0xFFFFFEF8.toInt() && index / speechFrame.width < headTopY
        })
        assertTrue("the bubble should close before the TALK stop ends", render(withSpeech, 6_500).pixels.contentEquals(render(withoutSpeech, 6_500).pixels))
    }

    @Test fun speechBubbleTailClearsTheHeadForEveryRegisteredSpeciesAndIdentity() {
        NpcCharacterRegistry.all.forEach { style ->
            val motion = SpeciesMotionProfiles.forCharacter(style)
            val definition = AmbientNpcDefinition(
                id = style.id,
                characterStyle = style,
                behaviorProfile = NpcBehaviorProfile(NpcAnimation.TALK, motion),
                speechProfile = NpcSpeechProfile(listOf("OLA"), cycleMs = 2_000, visibleMs = 500),
            )
            val slot = AmbientNpcSlot(definition, x = 120, floorY = 230, baseline = 230, seed = 0)
            val withSpeech = PixelBuffer(240, 320).also { NpcRenderer.draw(it, slot, 0) }
            val withoutSpeech = PixelBuffer(240, 320).also {
                NpcRenderer.draw(it, slot.copy(definition = definition.copy(speechProfile = null)), 0)
            }
            val pose = NpcMotionController.pose(NpcAnimation.TALK, 0, 0, motion)
            val frame = NpcRenderer.scaleFrameForAmbient(CharacterPainter.paint(style, pose, motion.renderMotion()))
            val headTopY = slot.floorY - CharacterCanvas.FEET.y + frame.anchors.head.y -
                (style.species.headHeight * NpcRenderer.AMBIENT_SCALE).roundToInt() / 2
            val changedRows = withSpeech.pixels.indices
                .filter { withSpeech.pixels[it] != withoutSpeech.pixels[it] }
                .map { it / withSpeech.width }

            assertFalse("${style.id} speech profile should produce a visible bubble", changedRows.isEmpty())
            assertTrue(
                "${style.id} speech bubble tail must clear the species head at row $headTopY, pixels=${changedRows.max()}",
                changedRows.max() < headTopY,
            )
        }
    }

    @Test fun officeSceneSeedShowsTheExecutiveDuringTheExportedTalkMoment() {
        val executive = NpcDirector.plan(SceneId.OFFICE, variant = 0).first()
        assertEquals(NpcCharacterRegistry.BULLDOG_EXEC.id, executive.definition.characterStyle.id)
        assertEquals("the review scene uses the production NPC seed", 11, executive.seed)
        val withSpeech = PixelBuffer(240, 320).also { NpcRenderer.draw(it, executive, 4_800) }
        val withoutSpeech = PixelBuffer(240, 320).also {
            NpcRenderer.draw(it, executive.copy(definition = executive.definition.copy(speechProfile = null)), 4_800)
        }

        assertFalse("the production office preview time should show the Bulldog's speech bubble", withSpeech.pixels.contentEquals(withoutSpeech.pixels))
    }

    @Test fun speciesMotionProfileChangesNpcStrideArmsAndTailPose() {
        val bulldog = NpcMotionController.pose(
            NpcAnimation.WALK, 460, 5, SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.BULLDOG_EXEC),
        )
        val mouse = NpcMotionController.pose(
            NpcAnimation.WALK, 460, 5, SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.MOUSE_COMMUTER),
        )

        val bulldogMotion = SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.BULLDOG_EXEC)
        val mouseMotion = SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.MOUSE_COMMUTER)
        assertEquals(1, bulldogMotion.stepAmplitude)
        assertEquals(1, bulldogMotion.armSwing)
        assertEquals(0, bulldogMotion.tailAmplitude)
        assertEquals(2, mouseMotion.stepAmplitude)
        assertEquals(2, mouseMotion.armSwing)
        assertTrue(mouseMotion.tailAmplitude != bulldogMotion.tailAmplitude)
        val restrainedStride = CharacterPainter.paint(NpcCharacterRegistry.BULLDOG_EXEC, bulldog, bulldogMotion.renderMotion()).image.pixels
        val broadStride = CharacterPainter.paint(
            NpcCharacterRegistry.BULLDOG_EXEC,
            bulldog,
            CharacterRenderMotion(stepAmplitude = 2, armSwing = 2, tailAmplitude = 2),
        ).image.pixels
        assertFalse("species motion profile must affect rendered pixels", restrainedStride.contentEquals(broadStride))
    }

    @Test fun everySpeciesMotionProfileChangesTheRenderedWalkComparedWithSharedTiming() {
        val sharedTiming = SpeciesMotionProfile(
            walkBob = 1, stepAmplitude = 2, headLag = 0, tailAmplitude = 2, armSwing = 2,
        )

        NpcCharacterRegistry.all.forEach { style ->
            val speciesMotion = SpeciesMotionProfiles.forCharacter(style)
            if (speciesMotion != sharedTiming) {
                val speciesPose = NpcMotionController.pose(NpcAnimation.WALK, 460, 5, speciesMotion)
                val sharedPose = NpcMotionController.pose(NpcAnimation.WALK, 460, 5, sharedTiming)
                val speciesFrame = CharacterPainter.paint(style, speciesPose, speciesMotion.renderMotion()).image.pixels
                val sharedFrame = CharacterPainter.paint(style, sharedPose, CharacterRenderMotion()).image.pixels

                assertFalse(
                    "${style.id} declares a motion profile but renders the shared gait unchanged",
                    speciesFrame.contentEquals(sharedFrame),
                )
            }
        }
    }

    @Test fun headLagMovesTheRenderedHeadAndFeaturesWithoutMovingTheBody() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val profile = SpeciesMotionProfiles.forCharacter(style)
        val noHeadLag = profile.copy(headLag = 0)
        val withHeadLag = profile.copy(headLag = 1)
        val sample = (0L..1_500L step 25L).asSequence().map { elapsed ->
            Triple(
                elapsed,
                NpcMotionController.pose(NpcAnimation.WALK, elapsed, 0, noHeadLag),
                NpcMotionController.pose(NpcAnimation.WALK, elapsed, 0, withHeadLag),
            )
        }.first { (_, still, lagged) -> still.headTilt != lagged.headTilt || still.headDy != lagged.headDy }

        val still = CharacterPainter.paint(style, sample.second, noHeadLag.renderMotion()).image
        val lagged = CharacterPainter.paint(style, sample.third, withHeadLag.renderMotion()).image
        val changedRows = still.pixels.indices.filter { still.pixels[it] != lagged.pixels[it] }.map { it / still.width }.toSet()

        assertTrue("head lag should visibly change rendered pixels", changedRows.isNotEmpty())
        val headBottom = com.hoodie.app.pixel.character.BodyLayout.resolve(style, sample.third).headBottom
        assertTrue("head lag should stay in the head area, changed rows=$changedRows", changedRows.all { it <= headBottom + 1 })
    }
}
