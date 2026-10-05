package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.BodyLayout
import com.hoodie.app.pixel.character.CharacterCanvas
import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterRenderMotion
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.SpeciesMotionProfile
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Animações V3: poses calculadas (não clips do Hoodie) com props nas mãos e movimento por espécie. */
class NpcAnimationRegressionTest {
    private fun render(style: com.hoodie.app.pixel.character.CharacterStyle, animation: NpcAnimation, t: Long, seed: Int = 5) =
        NpcPoseLibrary.frame(animation, t, seed, SpeciesMotionProfiles.forCharacter(style)).let {
            CharacterPainter.paint(style, it.pose, it.motion)
        }

    @Test fun restaurantGuestEatsWithAForkAndCyclesFromPickToChew() {
        val guest = com.hoodie.app.pixel.npc.NpcDirector.plan(com.hoodie.app.pixel.scene.SceneId.RESTAURANT, 0).single()
        val profile = guest.definition.behaviorProfile.motion
        assertEquals(NpcAnimation.SIT_EAT, guest.definition.behaviorProfile.animation)
        val pose = NpcMotionController.pose(NpcAnimation.SIT_EAT, 360, guest.seed, profile)
        assertEquals(Legs.SIT, pose.legs)
        assertEquals(Item.FORK, pose.item)
        assertEquals(Facing.SIDE, pose.facing)
        val withFork = CharacterPainter.paint(guest.definition.characterStyle, pose, profile.renderMotion()).image
        val withoutFork = CharacterPainter.paint(guest.definition.characterStyle, pose.copy(item = Item.NONE), profile.renderMotion()).image
        assertTrue("restaurant NPC should visibly render the fork", withFork.pixels.indices.any { withFork.pixels[it] != withoutFork.pixels[it] })
        // Ciclo completo: levar à boca e mastigar.
        val mouths = (0L until 3_000L step 50L).map { NpcPoseLibrary.sitEat(it, 0).mouth }.toSet()
        assertTrue("eating cycle opens the mouth and chews: $mouths", Mouth.OPEN in mouths && Mouth.CHEW in mouths)
    }

    @Test fun universalNpcAnimationsProduceDistinctFramesForEverySpecies() {
        val universal = listOf(NpcAnimation.IDLE, NpcAnimation.WALK, NpcAnimation.LOOK, NpcAnimation.TALK, NpcAnimation.SIT_PHONE, NpcAnimation.SIT_SLEEP)
        NpcCharacterRegistry.all.forEach { style ->
            val renders = universal.map { render(style, it, 460).image.pixels.toList() }
            assertTrue("${style.id}: universal animations should not collapse", renders.distinct().size >= 5)
            assertFalse("${style.id}: talk should animate",
                render(style, NpcAnimation.TALK, 100).image.pixels.contentEquals(render(style, NpcAnimation.TALK, 805).image.pixels))
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
            val minX = phone.minOf { it % frame.image.width }; val maxX = phone.maxOf { it % frame.image.width }
            val minY = phone.minOf { it / frame.image.width }; val maxY = phone.maxOf { it / frame.image.width }
            assertTrue("${style.id} phone should stay a compact device (${maxX - minX + 1}×${maxY - minY + 1})", maxX - minX + 1 <= 7 && maxY - minY + 1 <= 11)
            val handDistance = listOf(frame.anchors.leftHand, frame.anchors.rightHand).minOf { hand ->
                kotlin.math.abs((minX + maxX) / 2 - hand.x) + kotlin.math.abs(maxY - hand.y)
            }
            assertTrue("${style.id} phone should sit against a held hand, distance=$handDistance", handDistance <= 5)
        }
    }

    @Test fun talkingMovesMouthHeadAndHand() {
        val frames = (0L until 2_400L step 40L).map { NpcPoseLibrary.talk(it, 0) }
        assertTrue("mouth opens", frames.any { it.mouth == Mouth.OPEN })
        assertTrue("head nods", frames.any { it.headDy != 0 })
        assertTrue("hand gestures", frames.any { it.rightArm != com.hoodie.app.pixel.sprite.Arm.DOWN })
        assertTrue("talk faces the Hoodie", frames.all { it.facing == Facing.FRONT })
        // Boca aberta mostra a língua (cor interna) dentro do focinho largo do Bulldog.
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val open = CharacterPainter.paint(style, CharacterPose(mouth = Mouth.OPEN)).image
        val closed = CharacterPainter.paint(style, CharacterPose(mouth = Mouth.SMILE)).image
        val tongue = open.pixels.indices.count { open.pixels[it] == style.palette.inner && closed.pixels[it] != style.palette.inner }
        assertTrue("Bulldog talk should show a small tongue ($tongue px)", tongue >= 2)
    }

    @Test fun bulldogShortWalkVisiblyAnimatesFeetAndKeepsAPawGrounded() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val profile = SpeciesMotionProfiles.forCharacter(style)
        val cycle = profile.stepLength * 2 * profile.msPerPixel
        val frames = (0 until 16).map { i ->
            val f = NpcPoseLibrary.frame(NpcAnimation.WALK, i * cycle / 16, 0, profile)
            CharacterPainter.paint(style, f.pose, f.motion).image
        }
        val lower = frames.map { img -> img.pixels.indices.filter { it / img.width >= 60 && img.pixels[it] != 0 }.map { it to img.pixels[it] } }
        assertTrue("Bulldog's short walk must animate feet (distinct=${lower.distinct().size})", lower.distinct().size >= 5)
        frames.forEachIndexed { i, img ->
            assertTrue("phase $i must keep a paw on the ground", (0 until img.width).any { img[it, 71] ushr 24 != 0 })
        }
    }

    @Test fun speciesMotionProfileChangesStrideAndBodyWeight() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val heavy = SpeciesMotionProfiles.forCharacter(style)
        val light = SpeciesMotionProfile(walkBob = 2, stepAmplitude = 2, headLag = 0, tailAmplitude = 2, armSwing = 3, stepLength = 11, footLift = 3)
        val t = heavy.stepLength * heavy.msPerPixel / 2
        val a = NpcPoseLibrary.frame(NpcAnimation.WALK, t, 0, heavy)
        val b = NpcPoseLibrary.frame(NpcAnimation.WALK, t, 0, light)
        val pa = CharacterPainter.paint(style, a.pose, a.motion).image.pixels
        val pb = CharacterPainter.paint(style, b.pose, b.motion).image.pixels
        assertFalse("species weight must change the rendered walk", pa.contentEquals(pb))
        assertTrue("heavier species take shorter steps", heavy.stepLength < light.stepLength)
    }

    @Test fun rabbitEarsAndMouseTailTrailTheBody() {
        val rabbit = SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.RABBIT_ANALYST)
        val mouse = SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.MOUSE_COMMUTER)
        assertTrue(rabbit.earLag > 0)
        assertTrue(mouse.tailLag > 0)
        val earLags = (0 until 32).map { NpcPoseLibrary.walk(it * 1.5f, 0, rabbit).motion.earLag }.toSet()
        assertTrue("rabbit ears should lag behind the bob: $earLags", earLags.size >= 2)
        val duck = SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.DUCK_SLEEPY)
        val sways = (0 until 32).map { NpcPoseLibrary.walk(it * 1.5f, 0, duck).motion.sway }.toSet()
        assertEquals("duck walks with a lateral sway", setOf(-1, 1), sways)
    }

    @Test fun raccoonTailAmplitudeMovesOnlyTheTailPixels() {
        val style = NpcCharacterRegistry.RACCOON_COMMUTER
        val pose = CharacterPose(facing = Facing.SIDE, stringSwing = 2)
        val still = CharacterPainter.paint(style, pose, CharacterRenderMotion(stepAmplitude = 0, armSwing = 0, tailAmplitude = 0)).image
        val wag = CharacterPainter.paint(style, pose, CharacterRenderMotion(stepAmplitude = 0, armSwing = 0, tailAmplitude = 4)).image
        val changed = still.pixels.indices.filter { still.pixels[it] != wag.pixels[it] }
        assertFalse("tail amplitude should change the rendered ringed tail", changed.isEmpty())
        val l = BodyLayout.resolve(style, pose)
        // Perfil canônico é espelhado: a cabeça fica em 47-headRight..47-headLeft.
        val headXs = (47 - l.headRight)..(47 - l.headLeft)
        assertTrue("tail motion must not touch the head", changed.none { (it % still.width) in headXs && (it / still.width) in l.headTop..l.headBottom })
    }

    @Test fun seatedSleepKeepsClosedEyesBreathesAndDroopsTheHead() {
        NpcCharacterRegistry.all.forEach { style ->
            val profile = SpeciesMotionProfiles.forCharacter(style)
            val frames = (0L until 4_000L step 100L).map { NpcPoseLibrary.frame(NpcAnimation.SIT_SLEEP, it, 0, profile) }
            assertTrue(frames.all { it.pose.legs == Legs.SIT && it.pose.eyes == Eyes.CLOSED && it.pose.mouth == Mouth.FLAT })
            assertTrue("${style.id} breathes", frames.map { it.motion.breath }.toSet().size == 2)
            assertTrue("${style.id} head droops and corrects", frames.map { it.pose.headDy }.toSet().size >= 3)
            val sleeping = CharacterPainter.paint(style, frames.first().pose, frames.first().motion)
            assertEquals(CharacterCanvas.FEET.y, sleeping.anchors.feet.y)
            val standing = BodyLayout.resolve(style, CharacterPose())
            val seated = BodyLayout.resolve(style, frames.first().pose)
            assertEquals("${style.id} sits lower", BodyLayout.SIT_DROP, seated.shoulderY - standing.shoulderY)
        }
    }

    @Test fun sitDownIsATransitionNotATeleport() {
        val drops = (0L..640L step 20L).map { t ->
            val p = NpcPoseLibrary.sitDown(t, 0)
            BodyLayout.resolve(NpcCharacterRegistry.CAT_COLLEAGUE, p).drop
        }
        val distinct = drops.distinct()
        assertTrue("STAND→BEND→LOWER→CONTACT→SIT passes through intermediate heights: $distinct", distinct.size >= 4)
        drops.zipWithNext().forEach { (a, b) -> assertTrue("no jump larger than 4px ($a→$b)", kotlin.math.abs(b - a) <= 4) }
        assertEquals(0, drops.first()); assertEquals(BodyLayout.SIT_DROP, drops.last())
    }
}
