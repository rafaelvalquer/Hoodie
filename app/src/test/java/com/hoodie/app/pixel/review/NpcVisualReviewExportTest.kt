package com.hoodie.app.pixel.review

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.PreviewExport
import com.hoodie.app.pixel.npc.NpcAnimation
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcGait
import com.hoodie.app.pixel.npc.NpcPoseLibrary
import com.hoodie.app.pixel.npc.SpeciesMotionProfiles
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.review.VisualReviewPose
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import java.io.File
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class NpcVisualReviewExportTest {
    private val root = File(System.getProperty("npcVisualReviewOutput") ?: "build/pixel-preview/npc-v3-review")

    @Test fun semanticReviewPosesArePixelDeterministic() {
        NpcVisualReviewFrames.reviewSpecies.forEach { style ->
            VisualReviewPose.entries.forEach { checkpoint ->
                val a = NpcVisualReviewFrames.resolve(style, checkpoint)
                val b = NpcVisualReviewFrames.resolve(style, checkpoint)
                assertEquals("frame semântico instável: ${style.id}/${checkpoint.name}", a, b)
                assertEquals(
                    digest(CharacterPainter.paint(style, a.pose, a.motion).image),
                    digest(CharacterPainter.paint(style, b.pose, b.motion).image),
                )
            }
        }
    }

    @Test fun hardVisualRulesPassForEverySpeciesCheckpointAndProp() {
        val reports = buildList {
            NpcVisualReviewFrames.reviewSpecies.forEach { style ->
                VisualReviewPose.entries.forEach { checkpoint -> add(NpcVisualQualityRules.inspect(style, checkpoint)) }
                addAll(walkReports(style))
            }
            propCases().forEach { (style, pose, item) ->
                add(NpcVisualQualityRules.inspect(style, "PROP_${item.name}", CharacterPainter.paint(style, pose), item))
            }
        }
        val failed = reports.filter { it.hardIssues.isNotEmpty() }
        assertTrue("regras visuais HARD quebradas:\n${failed.joinToString("\n") { it.json() }}", failed.isEmpty())
        assertTrue("há checkpoints de revisão a menos que as 7 espécies", reports.size >= 7 * VisualReviewPose.entries.size)
    }

    @Test fun exportCandidateMatrixWhenExplicitlyRequested() {
        assumeTrue("execute com -PnpcVisualReview=true para exportar os PNGs candidatos", System.getProperty("npcVisualReview") == "true")
        root.deleteRecursively()
        root.resolve("matrices").mkdirs()
        root.resolve("animation-sheets").mkdirs()
        root.resolve("comparisons").mkdirs()
        root.resolve("npc-scale-v3").mkdirs()
        root.resolve("scenes").mkdirs()

        val styles = NpcVisualReviewFrames.reviewSpecies
        styles.forEach { style ->
            val slug = style.id.substringBefore('_')
            write("matrices/$slug-animation-matrix.png", NpcVisualReviewMatrix.species(style), 2)
            write("animation-sheets/${style.id}-walk-clean.png", NpcAnimationContactSheet.walk(style, debug = false), 4)
            write("animation-sheets/${style.id}-walk-debug.png", NpcAnimationContactSheet.walk(style, debug = true), 4)
            write("animation-sheets/${style.id}-turn.png", NpcAnimationContactSheet.turn(style), 4)
            write("animation-sheets/${style.id}-sit-transition.png", NpcAnimationContactSheet.sit(style), 4)
            write("animation-sheets/${style.id}-talk.png", NpcAnimationContactSheet.talk(style), 4)
        }
        write("comparisons/expression-matrix.png", NpcVisualReviewMatrix.expressions(), 2)
        write("comparisons/head-crops-8x.png", headCrops(), 1)
        write("comparisons/outfit-matrix.png", NpcVisualReviewMatrix.outfits(), 3)
        write("comparisons/props-matrix.png", NpcVisualReviewMatrix.props(), 3)
        write("comparisons/scale-matrix.png", NpcVisualReviewMatrix.scales(), 2)
        styles.forEach { style ->
            val slug = style.id.substringBefore('_')
            write("npc-scale-v3/$slug.png", NpcVisualReviewMatrix.scaleLegibility(style), 3)
        }
        val scaleReports = styles.flatMap(NpcScaleLegibility::reviewReports)
        root.resolve("npc-scale-v3/report.json").writeText(scaleReports.joinToString(",\n", "[\n", "\n]\n") { "  ${it.json()}" })
        val performance = NpcScalePerformance.measure()
        root.resolve("performance-report.json").writeText(performance.joinToString(",\n", "[\n", "\n]\n") { "  ${it.json()}" })
        NpcSceneReviewRenderer.all().forEach { (name, image) -> write("scenes/$name.png", image, 2) }
        NpcSceneReviewRenderer.propScenes().forEach { (name, image) -> write("scenes/$name.png", image, 2) }

        val report = styles.flatMap { style ->
            VisualReviewPose.entries.map { NpcVisualQualityRules.inspect(style, it) } + walkReports(style)
        }
        root.resolve("quality-report.json").writeText(report.joinToString(",\n", "[\n", "\n]\n") { "  ${it.json()}" })
        root.resolve("review-manifest.json").writeText(File("src/test/resources/npc-art-v3-review-manifest.json").readText())
        root.resolve("index.html").writeText(htmlIndex())
    }

    private fun propCases() = listOf(
        Triple(NpcCharacterRegistry.MOUSE_COMMUTER, CharacterPose(rightArm = com.hoodie.app.pixel.sprite.Arm.HOLD_CHEST, item = Item.PHONE), Item.PHONE),
        Triple(NpcCharacterRegistry.DOG_WORKER, CharacterPose(rightArm = com.hoodie.app.pixel.sprite.Arm.HOLD_CHEST, item = Item.MUG), Item.MUG),
        Triple(NpcCharacterRegistry.CAT_GUEST, CharacterPose(rightArm = com.hoodie.app.pixel.sprite.Arm.HOLD_MOUTH, item = Item.FORK), Item.FORK),
        Triple(NpcCharacterRegistry.RABBIT_READER, CharacterPose(leftArm = com.hoodie.app.pixel.sprite.Arm.HOLD_CHEST, rightArm = com.hoodie.app.pixel.sprite.Arm.HOLD_CHEST, item = Item.BOOK, itemInBothHands = true), Item.BOOK),
        Triple(NpcCharacterRegistry.DOG_SHOPPER, CharacterPose(rightArm = com.hoodie.app.pixel.sprite.Arm.HOLD_CHEST, item = Item.PRODUCT), Item.PRODUCT),
    )

    private fun walkReports(style: CharacterStyle): List<NpcVisualQualityReport> {
        val motion = SpeciesMotionProfiles.forCharacter(style)
        return NpcAnimationContactSheet.phaseLabels.mapIndexed { phase, label ->
            val walked = motion.stepLength * (phase / 4f)
            val frame = NpcPoseLibrary.frame(NpcAnimation.WALK, 0, 0, motion, walkedPx = walked, scale = 1f)
            NpcVisualQualityRules.inspect(style, "WALK_${label}_$phase", CharacterPainter.paint(style, frame.pose, frame.motion))
        }
    }

    private fun headCrops(): PixelBuffer {
        val people = listOf(CharacterStyle.HOODIE) + NpcVisualReviewFrames.reviewSpecies
        val cropX = 6
        val cropY = 0
        val cropWidth = 36
        val cropHeight = 36
        val enlargement = 8
        val scaledWidth = cropWidth * enlargement
        val scaledHeight = cropHeight * enlargement
        val cellWidth = scaledWidth + 8
        val out = PixelBuffer(people.size * cellWidth, scaledHeight + 28).also { it.fill(ReviewRaster.BG) }
        people.forEachIndexed { i, style ->
            val left = i * cellWidth + 4
            ReviewRaster.text(out, style.id.take(12), left, 8)
            val frame = CharacterPainter.paint(style, CharacterPose(facing = Facing.FRONT))
            for (y in 0 until cropHeight) for (x in 0 until cropWidth) {
                val color = frame.image[cropX + x, cropY + y]
                if (color ushr 24 != 0) {
                    for (dy in 0 until enlargement) for (dx in 0 until enlargement) {
                        out.set(left + x * enlargement + dx, 24 + y * enlargement + dy, color)
                    }
                }
            }
        }
        return out
    }

    private fun write(relative: String, buffer: PixelBuffer, scale: Int) {
        PreviewExport.write(root.resolve(relative), buffer, scale, ReviewRaster.BG)
    }

    private fun digest(buffer: PixelBuffer): String {
        val md = MessageDigest.getInstance("SHA-256")
        buffer.pixels.forEach { p -> md.update(byteArrayOf((p ushr 24).toByte(), (p ushr 16).toByte(), (p ushr 8).toByte(), p.toByte())) }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun htmlIndex() = buildString {
        appendLine("<!doctype html><meta charset=\"utf-8\"><title>NPC V3 Visual Review</title>")
        appendLine("<style>body{font:16px system-ui;background:#242742;color:#eee;padding:2rem}img{image-rendering:pixelated;max-width:100%;background:#2b2e4a}section{margin:2rem 0}a{color:#f1c66d}</style>")
        appendLine("<h1>NPC Art V3 — candidate review</h1><p>Status: PENDING · veja o manifest e o relatório de qualidade antes de aprovar.</p>")
        appendLine("<p><a href=\"review-manifest.json\">Manifest</a> · <a href=\"quality-report.json\">Qualidade</a> · <a href=\"npc-scale-v3/report.json\">Métricas de legibilidade</a> · <a href=\"performance-report.json\">Desempenho</a></p>")
        appendLine("<section><h2>Matrizes por espécie</h2>")
        NpcVisualReviewFrames.reviewSpecies.forEach { style ->
            val slug = style.id.substringBefore('_')
            appendLine("<h3>${style.id}</h3><img src=\"matrices/${slug}-animation-matrix.png\">")
            appendLine("<p>${listOf("walk-clean", "walk-debug", "turn", "sit-transition", "talk").joinToString(" · ") { "<a href=\"animation-sheets/${style.id}-$it.png\">$it</a>" }}</p>")
        }
        appendLine("</section><section><h2>Comparações</h2>")
        listOf("expression-matrix", "head-crops-8x", "outfit-matrix", "props-matrix", "scale-matrix").forEach { appendLine("<h3>$it</h3><img src=\"comparisons/$it.png\">") }
        appendLine("<h3>SCALE LEGIBILITY · 75% / 80% / 85% / 90% / 95% / 100%</h3><p>As imagens mostram a escala solicitada; o relatório também registra o piso aplicado pela política de produção.</p>")
        NpcVisualReviewFrames.reviewSpecies.forEach { style ->
            val slug = style.id.substringBefore('_')
            appendLine("<h4>${style.id}</h4><img src=\"npc-scale-v3/$slug.png\">")
        }
        appendLine("</section><section><h2>Cenas</h2>")
        NpcSceneReviewRenderer.all().keys.forEach { appendLine("<h3>$it</h3><img src=\"scenes/$it.png\">") }
        NpcSceneReviewRenderer.propScenes().keys.forEach { appendLine("<h3>$it</h3><img src=\"scenes/$it.png\">") }
        appendLine("</section>")
    }
}
