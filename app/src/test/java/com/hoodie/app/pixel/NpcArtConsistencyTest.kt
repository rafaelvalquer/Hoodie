package com.hoodie.app.pixel

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterCanvas
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.character.CharacterRenderMotion
import com.hoodie.app.pixel.character.outfit.OutfitStyle
import com.hoodie.app.pixel.character.species.TailStyle
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcMotionController
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Legs
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NpcArtConsistencyTest {
    @Test fun catalogIdentityVariantsHaveUniqueIdsAndVisibleSpeciesPaletteOrOutfitDifferences() {
        val registered = NpcCharacterRegistry.all
        assertEquals("NPC registry IDs must be unique", registered.size, registered.map { it.id }.toSet().size)
        val idleFrames = registered.associate { style ->
            style.id to CharacterPainter.paint(
                style, NpcMotionController.pose(NpcAnimation.IDLE, 400, 0, SpeciesMotionProfiles.forCharacter(style)),
                SpeciesMotionProfiles.forCharacter(style).renderMotion(),
            ).image
        }

        registered.indices.forEach { firstIndex ->
            (firstIndex + 1 until registered.size).forEach { secondIndex ->
                val first = registered[firstIndex]
                val second = registered[secondIndex]
                if (first.species == second.species) {
                    val a = idleFrames.getValue(first.id)
                    val b = idleFrames.getValue(second.id)
                    val changedPixels = a.pixels.indices.count { a.pixels[it] != b.pixels[it] }
                    assertTrue(
                        "${first.id} and ${second.id} share ${first.species.id} and need visibly distinct palette/outfit treatment (changed=$changedPixels)",
                        changedPixels >= 24,
                    )
                }
            }
        }
    }

    @Test fun outfitsPreserveSpeciesHeadSilhouetteAndFacialPixelsAcrossViews() {
        val outfits = listOf(
            OutfitStyle.Suit, OutfitStyle.Casual, OutfitStyle.Student,
            OutfitStyle.Sport, OutfitStyle.Commuter,
        )

        NpcCharacterRegistry.all.forEach { registered ->
            Facing.entries.forEach { facing ->
                val pose = CharacterPose(facing = facing)
                val reference = CharacterPainter.paint(registered.copy(outfit = outfits.first()), pose).image
                // Rows 29+ include the neck/hood overlap zone, where outfits may layer by design.
                val headRows = 0..28
                outfits.drop(1).forEach { outfit ->
                    val actual = CharacterPainter.paint(registered.copy(outfit = outfit), pose).image
                    headRows.forEach { y -> (0 until reference.width).forEach { x ->
                        assertEquals(
                            "${registered.id}/$facing head/ears/muzzle changed with $outfit at ($x,$y)",
                            reference[x, y], actual[x, y],
                        )
                    } }
                }
            }
        }
    }

    @Test fun registeredNpcsUseOnlyTheirDeclaredTenColorPaletteAcrossOutfitsAndViews() {
        val outfits = listOf(
            OutfitStyle.Suit, OutfitStyle.Casual, OutfitStyle.Student,
            OutfitStyle.Sport, OutfitStyle.Commuter,
        )
        val facings = listOf(Facing.FRONT, Facing.SIDE, Facing.BACK)

        NpcCharacterRegistry.all.forEach { registered ->
            val palette = setOf(
                registered.palette.outline, registered.palette.furLight, registered.palette.fur,
                registered.palette.furDark, registered.palette.inner, registered.palette.outfitLight,
                registered.palette.outfit, registered.palette.outfitDark, registered.palette.shirt,
                registered.palette.accent,
            )
            assertTrue("${registered.id} declares more than ten palette colors", palette.size <= 10)

            outfits.forEach { outfit -> facings.forEach { facing ->
                val image = CharacterPainter.paint(
                    registered.copy(outfit = outfit), CharacterPose(facing = facing),
                ).image
                val used = image.pixels.filter { it ushr 24 != 0 }.toSet()
                assertTrue("${registered.id}/$outfit/$facing uses colors outside its palette: ${used - palette}", used.all { it in palette })
            } }
        }
    }

    @Test fun seatedAndWalkingNpcsKeepAtLeastOneFootOnTheSharedGroundLine() {
        val facings = listOf(Facing.FRONT, Facing.SIDE, Facing.BACK)
        val poses = listOf(CharacterPose(legs = Legs.SIT)) + (0..7).map { stride ->
            CharacterPose(legs = Legs.WALK, stride = stride)
        }

        NpcCharacterRegistry.all.forEach { registered ->
            val style = registered.copy(outfit = OutfitStyle.Suit)
            facings.forEach { facing -> poses.forEach { pose ->
                val frame = CharacterPainter.paint(style, pose.copy(facing = facing), SpeciesMotionProfiles.forCharacter(style).renderMotion()).image
                val groundRow = frame.pixels.indices.filter { it / frame.width == CharacterCanvas.GROUND_Y }
                assertTrue(
                    "${registered.id}/$facing/${pose.legs}/${pose.stride} floats above the shared ground anchor",
                    groundRow.any { frame.pixels[it] ushr 24 != 0 },
                )
            } }
        }
    }

    @Test fun bulldogKeepsHoodieScaleAcrossFrontWalkAndTalkPoses() {
        val style = NpcCharacterRegistry.BULLDOG_EXEC
        val profile = SpeciesMotionProfiles.forCharacter(style)
        val poses = listOf(
            NpcMotionController.pose(NpcAnimation.IDLE, 0, 0, profile).copy(facing = Facing.FRONT),
            NpcMotionController.pose(NpcAnimation.WALK, 460, 0, profile).copy(facing = Facing.SIDE),
            NpcMotionController.pose(NpcAnimation.TALK, 345, 0, profile).copy(facing = Facing.SIDE),
        )

        poses.forEachIndexed { index, pose ->
            val hoodieHeight = visibleHeight(CharacterPainter.paint(CharacterStyle.HOODIE, pose).image.pixels)
            val bulldogHeight = visibleHeight(CharacterPainter.paint(style, pose, profile.renderMotion()).image.pixels)
            assertTrue("Bulldog pose $index should stay close to Hoodie scale: hoodie=$hoodieHeight, bulldog=$bulldogHeight", abs(hoodieHeight - bulldogHeight) <= 8)
            assertTrue("Bulldog pose $index should fill the shared canvas ($bulldogHeight)", bulldogHeight >= 62)
        }
    }

    @Test fun bulldogHeadAndTorsoVolumesStayCloseToHoodieFrontIdle() {
        val pose = NpcMotionController.pose(
            NpcAnimation.IDLE, 0, 0,
            SpeciesMotionProfiles.forCharacter(NpcCharacterRegistry.BULLDOG_EXEC),
        ).copy(facing = Facing.FRONT)
        val hoodieStyle = CharacterStyle.HOODIE
        val bulldogStyle = NpcCharacterRegistry.BULLDOG_EXEC
        val hoodie = CharacterPainter.paint(hoodieStyle, pose).image
        val bulldog = CharacterPainter.paint(bulldogStyle, pose, SpeciesMotionProfiles.forCharacter(bulldogStyle).renderMotion()).image
        val hoodieHead = paletteBounds(
            hoodie.pixels, setOf(hoodieStyle.palette.furLight, hoodieStyle.palette.fur, hoodieStyle.palette.furDark, hoodieStyle.palette.inner), 0..33,
        )
        val bulldogHead = paletteBounds(
            bulldog.pixels, setOf(bulldogStyle.palette.furLight, bulldogStyle.palette.fur, bulldogStyle.palette.furDark, bulldogStyle.palette.inner), 0..33,
        )
        val hoodieTorso = paletteBounds(
            hoodie.pixels, setOf(hoodieStyle.palette.outfitLight, hoodieStyle.palette.outfit, hoodieStyle.palette.outfitDark, hoodieStyle.palette.shirt), 28..60,
        )
        val bulldogTorso = paletteBounds(
            bulldog.pixels, setOf(bulldogStyle.palette.outfitLight, bulldogStyle.palette.outfit, bulldogStyle.palette.outfitDark, bulldogStyle.palette.shirt), 28..60,
        )

        // V3: "cabeça muito larga" — o Bulldog é mais largo que o Hoodie, sem ultrapassar o canvas.
        assertTrue("Bulldog head should be wider than Hoodie's: hoodie=${hoodieHead.width}, bulldog=${bulldogHead.width}", bulldogHead.width in hoodieHead.width..44)
        assertTrue("Bulldog torso height should stay close to Hoodie: hoodie=${hoodieTorso.height}, bulldog=${bulldogTorso.height}", abs(hoodieTorso.height - bulldogTorso.height) <= 8)
    }

    @Test fun everySpeciesAnimationHasStable48By72ArtworkAndVisibleOutline() {
        NpcCharacterRegistry.all.forEach { style -> NpcAnimation.entries.forEach { animation -> Facing.entries.forEach { facing ->
            val pose = NpcMotionController.pose(animation, 920, style.id.hashCode(), SpeciesMotionProfiles.forCharacter(style)).copy(facing = facing)
            val renderMotion = SpeciesMotionProfiles.forCharacter(style).renderMotion()
            val first = CharacterPainter.paint(style, pose, renderMotion)
            val second = CharacterPainter.paint(style, pose, renderMotion)
            assertEquals("${style.id} canvas width", 48, first.image.width)
            assertEquals("${style.id} canvas height", 72, first.image.height)
            val occupied = first.image.pixels.indices.filter { first.image.pixels[it] ushr 24 != 0 }
            val outlinePixels = first.image.pixels.count { it == style.palette.outline }
            val outlineCoverage = outlinePixels.toFloat() / occupied.size
            assertTrue("${style.id}/$animation/$facing outline too sparse: $outlinePixels/$occupied.size", outlinePixels >= 64)
            assertTrue(
                "${style.id}/$animation/$facing outline coverage $outlineCoverage outside 8%..42%",
                outlineCoverage in 0.08f..0.42f,
            )
            val top = occupied.minOf { it / first.image.width }
            val bottom = occupied.maxOf { it / first.image.width }
            // O rato é a única espécie de escala pequena (pequeno, mas não minúsculo).
            val minHeight = if (style.species.id == "mouse") 44 else 48
            assertTrue("${style.id} visual height outside $minHeight..72: ${bottom - top + 1}", bottom - top + 1 in minHeight..72)
            assertTrue("${style.id} nondeterministic render", first.image.pixels.contentEquals(second.image.pixels))
        } } }
    }

    @Test fun noNpcSilhouetteOrTailIsClippedAtTheHorizontalCanvasEdges() {
        val sampleTimes = 0L..2_400L step 40L
        NpcCharacterRegistry.all.forEach { style ->
            val motion = SpeciesMotionProfiles.forCharacter(style)
            NpcAnimation.entries.forEach { animation -> sampleTimes.forEach { time ->
                val sampled = NpcMotionController.pose(animation, time, 0, motion)
                Facing.entries.forEach { facing ->
                    val frame = CharacterPainter.paint(style, sampled.copy(facing = facing), motion.renderMotion()).image
                    val clipped = (0 until frame.height).firstOrNull { y ->
                        frame[0, y] ushr 24 != 0 || frame[frame.width - 1, y] ushr 24 != 0
                    }
                    assertTrue(
                        "${style.id}/$animation/$facing at ${time}ms touches a canvas edge on row $clipped",
                        clipped == null,
                    )
                }
            } }
        }
    }

    @Test fun earsAndHeadKeepOnePixelOfClearanceFromTheTopAcrossEveryAnimationPhase() {
        val sampleTimes = 0L..2_400L step 40L
        NpcCharacterRegistry.all.forEach { style ->
            val motion = SpeciesMotionProfiles.forCharacter(style)
            NpcAnimation.entries.forEach { animation -> sampleTimes.forEach { time ->
                val sampled = NpcMotionController.pose(animation, time, 0, motion)
                Facing.entries.forEach { facing ->
                    val frame = CharacterPainter.paint(style, sampled.copy(facing = facing), motion.renderMotion()).image
                    val topPixels = (0 until frame.width).filter { x -> frame[x, 0] ushr 24 != 0 }
                    assertTrue(
                        "${style.id}/$animation/$facing at ${time}ms touches the top edge at $topPixels; pose=$sampled",
                        topPixels.isEmpty(),
                    )
                }
            } }
        }
    }

    @Test fun everyMammalTailStaysVisibleBeyondTheTorsoInSideAndBackViews() {
        NpcCharacterRegistry.all.filter { it.species.tailStyle !in setOf(TailStyle.NONE, TailStyle.DUCK) }.forEach { style ->
            listOf(Facing.SIDE, Facing.BACK).forEach { facing ->
                val pose = CharacterPose(facing = facing, stringSwing = 2)
                val still = CharacterPainter.paint(
                    style, pose, CharacterRenderMotion(tailAmplitude = 0),
                ).image
                val wagging = CharacterPainter.paint(
                    style, pose, CharacterRenderMotion(tailAmplitude = 4),
                ).image
                val changedOutsideTorso = still.pixels.indices.filter { index ->
                    if (still.pixels[index] == wagging.pixels[index]) return@filter false
                    val x = index % still.width
                    val y = index / still.width
                    // Caudas longas saem para fora do tronco; o pompom curto fica no meio das costas.
                    val short = style.species.tailStyle == TailStyle.SHORT
                    val behindTheBody = if (facing == Facing.SIDE) x in 30..46 else if (short) x in 14..34 else x in 36..46
                    behindTheBody && y in 32..62
                }
                assertTrue(
                    "${style.id}/$facing should keep the wagging tail visible and connected beyond the torso",
                    changedOutsideTorso.isNotEmpty(),
                )
            }
        }
    }

    @Test fun everySpeciesKeepsItsDesignedHeadAndTorsoVolume() {
        NpcCharacterRegistry.all.forEach { style ->
            val pose = NpcMotionController.pose(
                NpcAnimation.IDLE, 400, 0, SpeciesMotionProfiles.forCharacter(style),
            ).copy(facing = Facing.FRONT)
            val frame = CharacterPainter.paint(style, pose, SpeciesMotionProfiles.forCharacter(style).renderMotion()).image
            val l = com.hoodie.app.pixel.character.BodyLayout.resolve(style, pose)
            val head = paletteBounds(
                frame.pixels,
                setOf(style.palette.furLight, style.palette.fur, style.palette.furDark, style.palette.inner),
                0..l.headBottom,
            )
            val torso = paletteBounds(
                frame.pixels,
                setOf(style.palette.outfitLight, style.palette.outfit, style.palette.outfitDark, style.palette.shirt),
                l.headBottom + 1..l.torsoBottom,
                l.shoulderLeft..l.shoulderRight,
            )
            val pr = style.artProfile.proportions

            assertTrue(
                "${style.id} head width ${head.width} should reflect its ${style.species.id} muzzle/ear design",
                head.width in (style.species.headWidth - 3)..(style.species.headWidth + 13),
            )
            assertTrue(
                "${style.id} head should be tall enough for its species silhouette: ${head.height}",
                head.height >= style.species.headHeight - 3,
            )
            assertTrue(
                "${style.id} torso width ${torso.width} should stay close to ${pr.shoulderWidth}",
                torso.width in (pr.shoulderWidth - 4)..(pr.shoulderWidth + 1),
            )
            assertTrue(
                "${style.id} torso visible height ${torso.height} should reflect ${pr.torsoHeight}",
                torso.height in (pr.torsoHeight - 8)..(pr.torsoHeight + 3),
            )
        }
    }

    private fun visibleHeight(pixels: IntArray): Int {
        val occupiedRows = pixels.indices.filter { pixels[it] ushr 24 != 0 }.map { it / 48 }
        return occupiedRows.max() - occupiedRows.min() + 1
    }

    private data class Bounds(val width: Int, val height: Int)

    private fun paletteBounds(
        pixels: IntArray,
        palette: Set<Int>,
        rows: IntRange,
        columns: IntRange = 0..47,
    ): Bounds {
        val points = pixels.indices.filter { index ->
            index / 48 in rows && index % 48 in columns && pixels[index] in palette
        }
        val xs = points.map { it % 48 }
        val ys = points.map { it / 48 }
        return Bounds(xs.max() - xs.min() + 1, ys.max() - ys.min() + 1)
    }
}
