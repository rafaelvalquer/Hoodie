package com.hoodie.app.pixel.review

import com.hoodie.app.pixel.character.CharacterPainter
import com.hoodie.app.pixel.character.CharacterPose
import com.hoodie.app.pixel.character.CharacterStyle
import com.hoodie.app.pixel.character.outfit.OutfitStyle
import com.hoodie.app.pixel.npc.NpcCharacterRegistry
import com.hoodie.app.pixel.npc.NpcRenderer
import com.hoodie.app.pixel.review.VisualReviewPose
import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.Arm
import com.hoodie.app.pixel.sprite.Eyes
import com.hoodie.app.pixel.sprite.Facing
import com.hoodie.app.pixel.sprite.Item
import com.hoodie.app.pixel.sprite.Legs
import com.hoodie.app.pixel.sprite.Mouth

object NpcVisualReviewMatrix {
    private val rows = listOf(
        "IDLE" to VisualReviewPose.IDLE_NEUTRAL,
        "LOOK" to VisualReviewPose.LOOK_LEFT,
        "TALK" to VisualReviewPose.TALK_GESTURE,
        "STAND" to VisualReviewPose.STAND_NEUTRAL,
        "SIT" to VisualReviewPose.SIT_FINAL,
        "PHONE" to VisualReviewPose.PHONE_INTERACT,
        "EAT" to VisualReviewPose.EAT_CHEW,
        "SLEEP" to VisualReviewPose.SLEEP_RELAX,
        "REACT" to VisualReviewPose.REACTION_SURPRISED,
    )

    /** Uma matriz por espécie: Hoodie é sempre a comparação pareada em todas as poses e orientações. */
    fun species(style: CharacterStyle): PixelBuffer {
        val out = ReviewRaster.newSheet(columns = 6, rows = rows.size, left = 48, header = 19)
        ReviewRaster.text(out, style.id.replace('_', ' '), 2, 3)
        val headers = listOf("H FRONT", "H SIDE", "H BACK", "N FRONT", "N SIDE", "N BACK")
        headers.forEachIndexed { col, text -> ReviewRaster.text(out, text, 50 + col * 52, 11, scale = 1) }
        rows.forEachIndexed { row, (label, checkpoint) ->
            val y = 20 + row * 84
            ReviewRaster.text(out, label, 2, y + 32)
            ReviewRaster.divider(out, 0, y, out.width - 1, y)
            NpcVisualReviewRenderer.facings.forEachIndexed { direction, facing ->
                val hoodie = NpcVisualReviewRenderer.hoodieFrame(checkpoint, facing)
                val npc = NpcVisualReviewRenderer.frame(style, checkpoint, facing)
                NpcVisualReviewRenderer.paste(out, hoodie, 50 + direction * 52 + 2, y + 8)
                NpcVisualReviewRenderer.paste(out, npc, 50 + (direction + 3) * 52 + 2, y + 8)
            }
        }
        return out
    }

    /** Expressões: nove estados oculares e quatro estados de boca, em recorte de cabeça ampliável. */
    fun expressions(): PixelBuffer {
        val people = listOf(CharacterStyle.HOODIE) + NpcVisualReviewFrames.reviewSpecies
        val eyeRows = Eyes.entries.map { "EYE ${it.name}" to (it to Mouth.SMILE) }
        val mouthRows = Mouth.entries.map { "MOUTH ${it.name}" to (Eyes.OPEN to it) }
        val allRows = eyeRows + mouthRows
        val out = ReviewRaster.newSheet(people.size, allRows.size, cellWidth = 52, cellHeight = 46, left = 74, header = 18)
        people.forEachIndexed { col, person -> ReviewRaster.text(out, person.id.take(12), 76 + col * 52, 10) }
        allRows.forEachIndexed { row, (label, expr) ->
            val y = 18 + row * 46
            ReviewRaster.text(out, label.take(17), 2, y + 20)
            people.forEachIndexed { col, person ->
                val pose = CharacterPose(eyes = expr.first, mouth = expr.second, facing = Facing.FRONT)
                val frame = CharacterPainter.paint(person, pose)
                val crop = PixelBuffer(48, 38)
                for (yy in 0 until crop.height) for (xx in 0 until crop.width) crop.set(xx, yy, frame.image[xx, yy])
                out.blit(crop, 76 + col * 52, y + 6)
                out.hline(76 + col * 52, 123 + col * 52, y + 44, ReviewRaster.GRID)
            }
        }
        return out
    }

    /** Outfits aprovados para o mesmo personagem, incluindo a vista traseira com volume e acessórios. */
    fun outfits(): PixelBuffer {
        val base = NpcCharacterRegistry.BULLDOG_EXEC
        val styles = listOf(
            "HOODIE" to CharacterStyle.HOODIE,
            "SUIT" to base.copy(outfit = OutfitStyle.Suit),
            "CASUAL" to base.copy(outfit = OutfitStyle.Casual),
            "STUDENT" to base.copy(outfit = OutfitStyle.Student),
            "SPORT" to base.copy(outfit = OutfitStyle.Sport),
            "COMMUTE" to base.copy(outfit = OutfitStyle.Commuter, backAccessory = com.hoodie.app.pixel.character.outfit.BackAccessory.ShoulderBag),
        )
        val out = ReviewRaster.newSheet(styles.size, 3, cellWidth = 52, cellHeight = 84, left = 34, header = 20)
        styles.forEachIndexed { col, (label, _) -> ReviewRaster.text(out, label, 36 + col * 52, 10) }
        NpcVisualReviewRenderer.facings.forEachIndexed { row, facing ->
            val y = 20 + row * 84
            ReviewRaster.text(out, NpcVisualReviewRenderer.facingLabel(facing), 1, y + 34)
            styles.forEachIndexed { col, (_, style) ->
                val frame = CharacterPainter.paint(style, CharacterPose(facing = facing, eyes = Eyes.OPEN))
                NpcVisualReviewRenderer.paste(out, frame, 36 + col * 52, y + 7)
            }
        }
        return out
    }

    /** Props pousados na mão para conferir legibilidade, lado e distância em pixels. */
    fun props(): PixelBuffer {
        val cases = listOf(
            Triple("PHONE", NpcCharacterRegistry.MOUSE_COMMUTER, Item.PHONE),
            Triple("MUG", NpcCharacterRegistry.DOG_WORKER, Item.MUG),
            Triple("FORK", NpcCharacterRegistry.CAT_GUEST, Item.FORK),
            Triple("BOOK", NpcCharacterRegistry.RABBIT_READER, Item.BOOK),
            Triple("PRODUCT", NpcCharacterRegistry.DOG_SHOPPER, Item.PRODUCT),
        )
        val out = ReviewRaster.newSheet(2, cases.size, cellWidth = 52, cellHeight = 84, left = 42, header = 20)
        cases.forEachIndexed { row, (label, style, item) ->
            val y = 20 + row * 84
            ReviewRaster.text(out, label, 2, y + 34)
            listOf(Facing.FRONT, Facing.SIDE).forEachIndexed { direction, facing ->
                val pose = CharacterPose(
                    facing = facing,
                    legs = if (item == Item.BOOK || item == Item.FORK) Legs.SIT else Legs.STAND,
                    eyes = Eyes.LOOK_DOWN,
                    mouth = if (item == Item.FORK) Mouth.OPEN else Mouth.SMILE,
                    rightArm = if (item == Item.FORK) Arm.HOLD_MOUTH else Arm.HOLD_CHEST,
                    leftArm = if (item == Item.BOOK) Arm.HOLD_CHEST else Arm.DOWN,
                    item = item,
                    itemInBothHands = item == Item.BOOK,
                )
                val frame = CharacterPainter.paint(style, pose)
                val x = 44 + direction * 52 + 2
                NpcVisualReviewRenderer.paste(out, frame, x, y + 7, debug = true)
            }
        }
        return out
    }

    /** Matriz de legibilidade: somente escalas de produção e as sete espécies registradas. */
    fun scales(): PixelBuffer {
        val styles = NpcVisualReviewFrames.reviewSpecies
        // Inclui todas as escalas pedidas no plano (75–100%) e 95%, usada em cenas atuais.
        // Esta folha renderiza a escala solicitada sem aplicar o piso de produção, para
        // revelar exatamente quais detalhes se perdem antes de decidir o piso por espécie.
        val values = NpcScaleLegibility.reviewScales
        val out = ReviewRaster.newSheet(values.size * 2, styles.size, cellWidth = 52, cellHeight = 84, left = 52, header = 18)
        ReviewRaster.text(out, "FRONT", 54, 1)
        ReviewRaster.text(out, "SIDE", 54 + values.size * 52, 1)
        values.forEachIndexed { i, scale ->
            ReviewRaster.text(out, "${(scale * 100).toInt()}%", 54 + i * 52, 9)
            ReviewRaster.text(out, "${(scale * 100).toInt()}%", 54 + (i + values.size) * 52, 9)
        }
        styles.forEachIndexed { row, style ->
            val y = 18 + row * 84
            ReviewRaster.text(out, style.id.take(10).uppercase(), 1, y + 34)
            values.forEachIndexed { col, scale ->
                listOf(Facing.FRONT, Facing.SIDE).forEachIndexed { direction, facing ->
                    val rendered = CharacterPainter.paint(style, CharacterPose(facing = facing, eyes = Eyes.OPEN))
                    val scaled = NpcRenderer.scaleFrameForAmbient(rendered, scale)
                    NpcVisualReviewRenderer.paste(out, scaled, 54 + (col + direction * values.size) * 52 + 2, y + 7)
                }
            }
        }
        return out
    }

    /** Recorte individual no mesmo formato para inspeção detalhada por espécie. */
    fun scaleLegibility(style: CharacterStyle): PixelBuffer {
        val values = NpcScaleLegibility.reviewScales
        val out = ReviewRaster.newSheet(values.size * 2, 1, cellWidth = 52, cellHeight = 84, left = 2, header = 18)
        ReviewRaster.text(out, "FRONT", 4, 1)
        ReviewRaster.text(out, "SIDE", 4 + values.size * 52, 1)
        values.forEachIndexed { col, scale ->
            ReviewRaster.text(out, "${(scale * 100).toInt()}%", 4 + col * 52, 9)
            ReviewRaster.text(out, "${(scale * 100).toInt()}%", 4 + (col + values.size) * 52, 9)
            listOf(Facing.FRONT, Facing.SIDE).forEachIndexed { direction, facing ->
                val rendered = CharacterPainter.paint(style, CharacterPose(facing = facing, eyes = Eyes.OPEN))
                val scaled = NpcRenderer.scaleFrameForAmbient(rendered, scale)
                NpcVisualReviewRenderer.paste(out, scaled, 4 + (col + direction * values.size) * 52, 25)
            }
        }
        return out
    }
}
