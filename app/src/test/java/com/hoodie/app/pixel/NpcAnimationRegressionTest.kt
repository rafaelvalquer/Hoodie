package com.hoodie.app.pixel

import com.hoodie.app.pixel.animation.AnimationId
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterRenderMotion
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.SpeciesMotionProfile
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.character.CharacterGeometry
import com.hoodie.app.pixel.character.CharacterCanvas
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcAnimationRegressionTest {
    @Test fun restaurantGuestUsesTheEatingClipAndCarriesTheForkInsteadOfThePhone() {
        val guest = com.hoodie.app.pixel.npc.NpcDirector.plan(com.hoodie.app.pixel.scene.SceneId.RESTAURANT, 0).single()
        val profile = guest.definition.behaviorProfile.motion
        assertEquals(NpcAnimation.SIT_EAT, guest.definition.behaviorProfile.animation)
        val pose = NpcMotionController.pose(NpcAnimation.SIT_EAT, 360, guest.seed, profile)
        assertEquals(com.hoodie.app.pixel.sprite.Legs.SIT, pose.legs)
        assertEquals(Item.FORK, pose.item)
        assertEquals(Facing.SIDE, pose.facing)
        val withFork = CharacterPainter.paint(guest.definition.characterStyle, pose, profile.renderMotion()).image
        val withoutFork = CharacterPainter.paint(
            guest.definition.characterStyle, pose.copy(item = Item.NONE), profile.renderMotion(),
        ).image
        assertTrue("restaurant NPC should visibly render the fork", withFork.pixels.indices.any { withFork.pixels[it] != withoutFork.pixels[it] })
    }

    @Test fun universalNpcAnimationsProduceDistinctFramesForEverySpecies() {
        NpcCharacterRegistry.all.forEach { style ->
            val profile = SpeciesMotionProfiles.forCharacter(style)
            val renders = NpcAnimation.entries.take(4).map { animation ->
                val pose = NpcMotionController.pose(animation, 460, 5, profile)
                CharacterPainter.paint(style, pose, profile.renderMotion()).image.pixels.toList()
            }
            assertTrue("${style.id}: IDLE/WALK/LOOK/TALK should not collapse", renders.distinct().size >= 3)
            val talking = NpcMotionController.pose(NpcAnimation.TALK, 460, 5, profile)
            val secondTalkFrame = NpcMotionController.pose(NpcAnimation.TALK, 805, 5, profile)
            assertTrue("${style.id}: talk should animate", !CharacterPainter.paint(style, talking, profile.renderMotion()).image.pixels.contentEquals(CharacterPainter.paint(style, secondTalkFrame, profile.renderMotion()).image.pixels))
        }
    }

    @Test fun seatedPhoneAnimationRendersThePropAtItsCharacterAnchor() {
        NpcCharacterRegistry.all.forEach { style ->
            val profile = SpeciesMotionProfiles.forCharacter(style)
            val pose = NpcMotionController.pose(NpcAnimation.SIT_PHONE, 460, 5, profile)
            val motion = profile.renderMotion()
            val frame = CharacterPainter.paint(style, pose, motion)
            val withPhone = frame.image.pixels
            val withoutPhone = CharacterPainter.paint(style, pose.copy(item = Item.NONE), motion).image.pixels
            val phone = withPhone.indices.filter { withPhone[it] != withoutPhone[it] }
            assertTrue("${style.id}/SIT_PHONE must draw its phone prop", phone.isNotEmpty())
            val minX = phone.minOf { it % frame.image.width }
            val maxX = phone.maxOf { it % frame.image.width }
            val minY = phone.minOf { it / frame.image.width }
            val maxY = phone.maxOf { it / frame.image.width }
            assertEquals("${style.id} phone should remain a compact 5px device", 5, maxX - minX + 1)
            assertEquals("${style.id} phone should remain a readable 9px device", 9, maxY - minY + 1)
            val centerX = (minX + maxX) / 2
            val bottomY = maxY
            val handDistance = listOf(frame.anchors.leftHand, frame.anchors.rightHand).minOf { hand ->
                kotlin.math.abs(centerX - hand.x) + kotlin.math.abs(bottomY - hand.y)
            }
            assertTrue("${style.id} phone should sit against a held hand, distance=$handDistance", handDistance <= 4)
        }
    }

    @Test fun bulldogTalkUsesTongueAccentInsideItsBroadMuzzle() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val pose = NpcMotionController.pose(NpcAnimation.TALK, 650, 0, SpeciesMotionProfiles.forCharacter(style))
        val pixels = CharacterPainter.paint(style, pose, SpeciesMotionProfiles.forCharacter(style).renderMotion()).image
        val mouthAccent = (18 until 33).sumOf { y ->
            (0 until pixels.width).count { x -> pixels.pixels[y * pixels.width + x] == style.palette.accent }
        }

        assertTrue("Bulldog talk should show a small tongue in the muzzle", mouthAccent >= 2)
    }

    @Test fun bulldogShortWalkProducesVisibleFootMotionAcrossTheWholeCycle() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val profile = SpeciesMotionProfiles.forCharacter(style)
        val lowerBodyFrames = AnimationId.WALK.frames.mapIndexed { index, frame ->
            val elapsed = AnimationId.WALK.frames.take(index).sumOf { it.durationMs } + frame.durationMs / 2
            val pose = NpcMotionController.pose(NpcAnimation.WALK, elapsed, 0, profile)
            val pixels = CharacterPainter.paint(style, pose, profile.renderMotion()).image.pixels
            pixels.indices.filter { index -> index / CharacterPainter.WIDTH >= 52 && pixels[index] != 0 }
                .map { index -> index to pixels[index] }
        }

        assertTrue(
            "Bulldog's short walk must visibly animate feet and legs through its eight-frame cycle (distinct=${lowerBodyFrames.distinct().size})",
            lowerBodyFrames.distinct().size >= 5,
        )
    }

    @Test fun bulldogWalkKeepsAtLeastOnePawGroundedAtEveryTimedPhase() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val profile = SpeciesMotionProfiles.forCharacter(style)
        var frameStart = 0L

        AnimationId.WALK.frames.forEachIndexed { index, frame ->
            listOf(frameStart, frameStart + frame.durationMs / 2, frameStart + frame.durationMs - 1).forEach { elapsed ->
                val pose = NpcMotionController.pose(NpcAnimation.WALK, elapsed, 0, profile)
                val image = CharacterPainter.paint(style, pose, profile.renderMotion()).image
                val groundRow = image.pixels.indices.filter { it / image.width == 71 }
                assertTrue(
                    "Bulldog walk phase $index at ${elapsed}ms must keep a paw on the ground",
                    groundRow.any { image.pixels[it] ushr 24 != 0 },
                )
            }
            frameStart += frame.durationMs
        }
    }

    @Test fun speciesMotionProfileChangesTheRenderedStrideAndUpperBodyWeight() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val heavy = SpeciesMotionProfiles.forCharacter(style)
        val light = SpeciesMotionProfile(
            walkBob = 0,
            stepAmplitude = 0,
            headLag = 0,
            tailAmplitude = 0,
            armSwing = 0,
        )
        val elapsed = 460L
        val heavyPose = NpcMotionController.pose(NpcAnimation.WALK, elapsed, 2, heavy)
        val lightPose = NpcMotionController.pose(NpcAnimation.WALK, elapsed, 2, light)
        val heavyPixels = CharacterPainter.paint(style, heavyPose, heavy.renderMotion()).image.pixels
        val lightPixels = CharacterPainter.paint(style, lightPose, light.renderMotion()).image.pixels
        val lowerBodyChanges = (50 * CharacterPainter.WIDTH until 72 * CharacterPainter.WIDTH)
            .count { heavyPixels[it] != lightPixels[it] }
        val fullFrameChanges = heavyPixels.indices.count { heavyPixels[it] != lightPixels[it] }

        assertTrue("step amplitude must visibly change the legs and feet", lowerBodyChanges >= 8)
        assertTrue("body bob and arm swing must visibly change the character", fullFrameChanges > lowerBodyChanges)
    }

    @Test fun bulldogMotionWeightIsLocalizedToLimbsAndDoesNotChangeItsHead() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val profile = SpeciesMotionProfiles.forCharacter(style)
        val pose = NpcMotionController.pose(NpcAnimation.WALK, 460, 2, profile).copy(facing = com.hoodie.app.pixel.sprite.Facing.FRONT)
        val restrained = CharacterPainter.paint(style, pose, CharacterRenderMotion(stepAmplitude = 1, armSwing = 1, tailAmplitude = 0)).image
        val shared = CharacterPainter.paint(style, pose, CharacterRenderMotion(stepAmplitude = 2, armSwing = 2, tailAmplitude = 0)).image
        val armColumns = (0 until 12) + (36 until 48)
        val armChanges = armColumns.sumOf { x -> (28 until 52).count { y -> restrained[x, y] != shared[x, y] } }
        val legChanges = (50 until 72).sumOf { y -> (0 until 48).count { x -> restrained[x, y] != shared[x, y] } }
        val headUnchanged = (0 until 29).all { y -> (0 until 48).all { x -> restrained[x, y] == shared[x, y] } }

        assertTrue("Bulldog's contained arm swing must affect the arm silhouette", armChanges > 0)
        assertTrue("Bulldog's short stride must affect the legs and feet", legChanges > 0)
        assertTrue("limb motion modifiers must leave the Bulldog head unchanged", headUnchanged)
    }

    @Test fun rabbitReboundMovesTheBodyPixelsWithoutChangingThePoseContract() {
        val style = NpcCharacterRegistry.RABBIT_ANALYST
        val springy = SpeciesMotionProfiles.forCharacter(style)
        val baseline = springy.copy(walkBob = 1)
        val springyPose = NpcMotionController.pose(NpcAnimation.WALK, 460, 5, springy)
        val baselinePose = NpcMotionController.pose(NpcAnimation.WALK, 460, 5, baseline)
        val motion = springy.renderMotion()
        val springyFrame = CharacterPainter.paint(style, springyPose, motion).image
        val baselineFrame = CharacterPainter.paint(style, baselinePose, motion).image
        val bodyChanges = (28 until 56).sumOf { y -> (0 until 48).count { x -> springyFrame[x, y] != baselineFrame[x, y] } }

        assertTrue("rabbit's extra gait rebound should visibly move its body and limbs", bodyChanges > 0)
        assertEquals("body pose remains a shared value; only resolved bob differs", baselinePose.copy(bob = springyPose.bob), springyPose)
    }

    @Test fun walkBobWeightsTheGaitButPreservesIdleBreathingAmplitude() {
        val style = NpcCharacterRegistry.RABBIT_ANALYST
        val springy = SpeciesMotionProfiles.forCharacter(style)
        val baseline = springy.copy(walkBob = 1)
        val idleElapsed = 1_200L
        val expectedIdleBob = AnimationId.IDLE.frameAt(idleElapsed).bob
        val baselineIdle = NpcMotionController.pose(NpcAnimation.IDLE, idleElapsed, 0, baseline)
        val springyIdle = NpcMotionController.pose(NpcAnimation.IDLE, idleElapsed, 0, springy)

        assertEquals("walkBob must not amplify the idle breathing clip", expectedIdleBob, baselineIdle.bob)
        assertEquals("walkBob must not amplify the idle breathing clip", expectedIdleBob, springyIdle.bob)
        val walkDiffers = (0L..2_000L step 25L).any { elapsed ->
            NpcMotionController.pose(NpcAnimation.WALK, elapsed, 0, baseline).bob !=
                NpcMotionController.pose(NpcAnimation.WALK, elapsed, 0, springy).bob
        }
        assertTrue("Rabbit's walkBob should add rebound only during walking", walkDiffers)
    }

    @Test fun raccoonTailAmplitudeMovesOnlyTheTailPixels() {
        val style = NpcCharacterRegistry.RACCOON_COMMUTER
        val pose = CharacterPose(facing = Facing.SIDE, stringSwing = 2)
        val stillTail = CharacterPainter.paint(
            style, pose, CharacterRenderMotion(stepAmplitude = 0, armSwing = 0, tailAmplitude = 0),
        ).image
        val waggingTail = CharacterPainter.paint(
            style, pose, CharacterRenderMotion(stepAmplitude = 0, armSwing = 0, tailAmplitude = 4),
        ).image
        val changed = stillTail.pixels.indices.filter { stillTail.pixels[it] != waggingTail.pixels[it] }

        assertFalse("tail amplitude should change the rendered ringed tail", changed.isEmpty())
        assertTrue(
            "tail amplitude should not move the head, outfit or limbs",
            changed.all { index -> index % stillTail.width >= 32 && index / stillTail.width in 35..55 },
        )
    }

    @Test fun npcWalkUsesTheSharedClipsPhaseTimingAndSecondaryMotion() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val profile = SpeciesMotionProfiles.forCharacter(style)
        var frameStart = 0L

        AnimationId.WALK.frames.forEachIndexed { index, frame ->
            val elapsed = frameStart + frame.durationMs / 2
            val expected = AnimationId.WALK.frameAt(elapsed)
            val actual = NpcMotionController.pose(NpcAnimation.WALK, elapsed, 0, profile)
            assertEquals("stride phase $index", expected.stride, actual.stride)
            assertEquals("leg pose $index", expected.legs, actual.legs)
            assertEquals("arm swing $index", expected.leftArm, actual.leftArm)
            assertEquals("opposing arm swing $index", expected.rightArm, actual.rightArm)
            assertEquals("weighted body bob $index", expected.bob * profile.walkBob, actual.bob)
            assertEquals("weighted head lag $index", expected.headDy * profile.headLag, actual.headDy)
            frameStart += frame.durationMs
        }
    }

    @Test fun npcIdleLookTalkAndSeatedPosesReuseRefinedHoodieClipFrames() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val profile = SpeciesMotionProfiles.forCharacter(style)
        val sharedClips = listOf(
            NpcAnimation.IDLE to AnimationId.IDLE,
            NpcAnimation.WALK to AnimationId.WALK,
            NpcAnimation.LOOK to AnimationId.LOOK_AROUND,
            NpcAnimation.TALK to AnimationId.VISIT_CHAT,
            NpcAnimation.SIT_PHONE to AnimationId.PHONE_SIT,
            NpcAnimation.SIT_EAT to AnimationId.EAT,
            NpcAnimation.SIT_SLEEP to AnimationId.NAP_SIT,
            NpcAnimation.STAND to AnimationId.IDLE,
        )

        sharedClips.forEach { (npcAnimation, clipId) ->
            var frameStart = 0L
            clipId.frames.forEachIndexed { index, frame ->
                val elapsed = frameStart + frame.durationMs / 2
                val expected = clipId.frameAt(elapsed)
                val actual = NpcMotionController.pose(npcAnimation, elapsed, 0, profile)
                val expectedBob = if (npcAnimation == NpcAnimation.WALK) expected.bob * profile.walkBob else expected.bob
                assertEquals("$npcAnimation bob in $clipId frame $index", expectedBob, actual.bob)
                assertEquals("$npcAnimation head lag in $clipId frame $index", expected.headDy * profile.headLag, actual.headDy)
                assertEquals("$npcAnimation left arm in $clipId frame $index", expected.leftArm, actual.leftArm)
                if (npcAnimation != NpcAnimation.STAND) {
                    assertEquals("$npcAnimation right arm in $clipId frame $index", expected.rightArm, actual.rightArm)
                }
                if (npcAnimation != NpcAnimation.IDLE && npcAnimation != NpcAnimation.STAND && npcAnimation != NpcAnimation.SIT_SLEEP) {
                    assertEquals("$npcAnimation mouth in $clipId frame $index", expected.mouth, actual.mouth)
                }
                frameStart += frame.durationMs
            }
        }
    }

    @Test fun seatedSleepKeepsClosedEyesAndFivePixelBodyShiftAcrossEverySpecies() {
        NpcCharacterRegistry.all.forEach { style ->
            val profile = SpeciesMotionProfiles.forCharacter(style)
            listOf(400L, 1_200L).forEach { elapsed ->
                val pose = NpcMotionController.pose(NpcAnimation.SIT_SLEEP, elapsed, 0, profile)
                val frame = CharacterPainter.paint(style, pose, profile.renderMotion())
                val openEyes = CharacterPainter.paint(
                    style, pose.copy(eyes = Eyes.OPEN), profile.renderMotion(),
                ).image
                val changedFacePixels = (0 until 32).sumOf { y ->
                    (0 until frame.image.width).count { x -> frame.image[x, y] != openEyes[x, y] }
                }
                val sleepingGeometry = CharacterGeometry.resolve(style, pose)
                val standingGeometry = CharacterGeometry.resolve(style, pose.copy(legs = Legs.STAND))

                assertEquals("${style.id} sleep frame at ${elapsed}ms remains seated", Legs.SIT, pose.legs)
                assertEquals("${style.id} sleep frame at ${elapsed}ms closes its eyes", Eyes.CLOSED, pose.eyes)
                assertEquals("${style.id} sleep frame at ${elapsed}ms relaxes the mouth", com.hoodie.app.pixel.sprite.Mouth.FLAT, pose.mouth)
                assertTrue("${style.id} closed eyes must change visible face pixels", changedFacePixels > 0)
                assertEquals("${style.id} sleep lowers the head by five pixels", 5, sleepingGeometry.headTop - standingGeometry.headTop)
                assertEquals("${style.id} sleep lowers the torso by five pixels", 5, sleepingGeometry.bodyTop - standingGeometry.bodyTop)
                assertEquals("${style.id} sleep keeps the feet baseline", CharacterCanvas.FEET.y, frame.anchors.feet.y)
            }
        }
    }
}
