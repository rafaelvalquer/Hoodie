package com.hoodie.app.pixel.npc

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.scene.SceneId

enum class NpcSpecies { DOG, BULLDOG, RABBIT, CAT, MOUSE, DUCK, RACCOON }
enum class NpcPose { WALK, SIT_PHONE, SIT_SLEEP, STAND, LOOK }

data class AmbientNpcDefinition(
    val id: String,
    val species: NpcSpecies,
    val fur: Int,
    val outfit: Int,
    val accent: Int,
    val pose: NpcPose,
    val lines: List<String> = emptyList(),
)

/** Coordenadas usam o centro e o chão do personagem na resolução lógica da cena. */
data class AmbientNpcSlot(
    val definition: AmbientNpcDefinition,
    val x: Int,
    val floorY: Int,
    val baseline: Int,
    val seed: Int,
)

object NpcDirector {
    private const val INK = 0xFF1A1C33.toInt()
    private const val CREAM = 0xFFF3EBD9.toInt()
    private const val BLUE = 0xFF86A9E8.toInt()
    private const val OLIVE = 0xFF839A68.toInt()
    private const val BROWN = 0xFF9B684B.toInt()
    private const val PINK = 0xFFEFA3C8.toInt()
    private const val GOLD = 0xFFE8B84A.toInt()

    private val exec = AmbientNpcDefinition("bulldog_exec", NpcSpecies.BULLDOG, 0xFFC9A58A.toInt(), 0xFF35415C.toInt(), 0xFFC9544F.toInt(), NpcPose.WALK,
        listOf("Reunião em 5 min.", "Café primeiro.", "Bom dia!"))
    private val rabbit = AmbientNpcDefinition("rabbit_analyst", NpcSpecies.RABBIT, CREAM, 0xFF5B83B3.toInt(), GOLD, NpcPose.LOOK)
    private val cat = AmbientNpcDefinition("cat_colleague", NpcSpecies.CAT, 0xFFB7A2C8.toInt(), 0xFF586987.toInt(), 0xFFB7A2C8.toInt(), NpcPose.WALK)
    private val mouse = AmbientNpcDefinition("mouse_commuter", NpcSpecies.MOUSE, 0xFFB78B76.toInt(), 0xFF547A91.toInt(), 0xFF62D3CF.toInt(), NpcPose.SIT_PHONE,
        listOf("Chego já.", "Trânsito de novo."))
    private val duck = AmbientNpcDefinition("duck_sleepy", NpcSpecies.DUCK, 0xFFE9C95A.toInt(), 0xFF7B739D.toInt(), 0xFFE9854B.toInt(), NpcPose.SIT_SLEEP)
    private val dog = AmbientNpcDefinition("dog_worker", NpcSpecies.DOG, 0xFFB78361.toInt(), 0xFF687C72.toInt(), 0xFFD7DCE3.toInt(), NpcPose.STAND)
    private val bunny = AmbientNpcDefinition("rabbit_reader", NpcSpecies.RABBIT, 0xFFE2D8C8.toInt(), 0xFFB65F65.toInt(), 0xFF7AB6B0.toInt(), NpcPose.SIT_PHONE)
    private val raccoon = AmbientNpcDefinition("raccoon_window", NpcSpecies.RACCOON, 0xFF9295A4.toInt(), 0xFF4D6686.toInt(), 0xFFE2B973.toInt(), NpcPose.LOOK)
    private val guest = AmbientNpcDefinition("cat_guest", NpcSpecies.CAT, 0xFFCEA6B8.toInt(), 0xFF597D73.toInt(), PINK, NpcPose.SIT_PHONE,
        listOf("Cheiro bom!", "Vou pedir o de sempre."))
    private val shopper = AmbientNpcDefinition("dog_shopper", NpcSpecies.DOG, 0xFFBE8E68.toInt(), 0xFF677C9B.toInt(), GOLD, NpcPose.WALK)
    private val walker = AmbientNpcDefinition("rabbit_walker", NpcSpecies.RABBIT, 0xFFD7C6B0.toInt(), 0xFF728B65.toInt(), PINK, NpcPose.WALK)

    fun plan(scene: SceneId, variant: Int): List<AmbientNpcSlot> {
        val v = variant and 1
        return when (scene) {
            SceneId.OFFICE -> listOf(AmbientNpcSlot(if (v == 0) exec else cat, 211, 204, 204, 11 + v), AmbientNpcSlot(rabbit, 34, 204, 204, 31))
            SceneId.BUS -> listOf(AmbientNpcSlot(mouse, 38, 237, 240, 41), AmbientNpcSlot(duck, 202, 237, 240, 42), AmbientNpcSlot(dog, 119, 221, 222, 43))
            SceneId.TRAIN -> listOf(AmbientNpcSlot(bunny, 43, 240, 242, 51), AmbientNpcSlot(raccoon, 193, 240, 242, 52))
            SceneId.METRO -> listOf(AmbientNpcSlot(mouse, 44, 240, 242, 61), AmbientNpcSlot(dog, 194, 222, 224, 62), AmbientNpcSlot(duck, 119, 240, 242, 63))
            SceneId.RESTAURANT -> listOf(AmbientNpcSlot(guest, 207, 251, 252, 71))
            SceneId.SHOPPING -> listOf(AmbientNpcSlot(shopper, 207, 212, 214, 81))
            SceneId.LEISURE -> listOf(AmbientNpcSlot(walker, 204, 252, 254, 91), AmbientNpcSlot(cat, 38, 252, 254, 92))
            else -> emptyList()
        }
    }
}

/** Pixel art procedural em escala de fundo: contorno e paleta seguem o Hoodie. */
object NpcRenderer {
    private const val INK = 0xFF1A1C33.toInt()
    private const val EYE = 0xFF0F1124.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    fun draw(b: PixelBuffer, slot: AmbientNpcSlot, timeMs: Long) {
        val d = slot.definition
        val tick = (timeMs / 420 + slot.seed).toInt()
        val bob = if (d.pose == NpcPose.WALK && tick and 1 == 0) 1 else 0
        val x = slot.x
        val y = slot.floorY - 35 - bob
        val furShade = PixelBuffer.mix(d.fur, INK, .20f)
        // Orelhas/formatos variam a silhueta sem mudar proporções ou contorno.
        when (d.species) {
            NpcSpecies.RABBIT -> {
                b.outlined(x - 9, y - 9, x - 5, y + 2, d.fur, INK); b.box(x - 8, y - 7, x - 7, y, 0xFFDB91A8.toInt())
                b.outlined(x + 4, y - 12, x + 8, y + 1, d.fur, INK); b.box(x + 5, y - 9, x + 6, y - 1, 0xFFDB91A8.toInt())
            }
            NpcSpecies.CAT -> { b.disc(x - 6, y + 1, 4, INK); b.disc(x - 6, y + 1, 2, d.fur); b.disc(x + 6, y + 1, 4, INK); b.disc(x + 6, y + 1, 2, d.fur) }
            NpcSpecies.DOG, NpcSpecies.BULLDOG -> { b.ellipse(x - 10, y - 1, 5, 8, furShade); b.ellipse(x + 10, y - 1, 5, 8, furShade) }
            NpcSpecies.MOUSE -> { b.disc(x - 9, y - 4, 5, INK); b.disc(x - 9, y - 4, 3, 0xFFEFA3C8.toInt()); b.disc(x + 9, y - 4, 5, INK); b.disc(x + 9, y - 4, 3, 0xFFEFA3C8.toInt()) }
            NpcSpecies.DUCK -> { b.box(x - 9, y - 2, x - 5, y + 3, INK); b.box(x + 5, y - 2, x + 9, y + 3, INK); b.box(x + 9, y + 5, x + 14, y + 7, d.accent) }
            NpcSpecies.RACCOON -> { b.box(x - 12, y + 1, x + 12, y + 7, furShade) }
        }
        b.ellipse(x, y + 5, 12, 12, INK); b.ellipse(x, y + 5, 10, 10, d.fur)
        if (d.species == NpcSpecies.DOG || d.species == NpcSpecies.BULLDOG) {
            b.ellipse(x, y + 10, if (d.species == NpcSpecies.BULLDOG) 7 else 5, 4, furShade)
            b.box(x - 2, y + 8, x + 2, y + 9, INK)
        }
        b.set(x - 4, y + 4, EYE); b.set(x + 4, y + 4, EYE); b.set(x, y + 9, INK)
        // Corpo curto, roupa legível a duas cores e gravata/crachá como assinatura do executivo.
        b.outlined(x - 10, y + 15, x + 10, y + 31, d.outfit, INK)
        b.box(x - 7, y + 17, x + 7, y + 19, PixelBuffer.mix(d.outfit, WHITE, .22f))
        if (d.id == "bulldog_exec") {
            b.box(x - 1, y + 16, x + 1, y + 27, d.accent); b.box(x - 2, y + 16, x + 2, y + 18, d.accent)
        } else b.outlined(x + 4, y + 21, x + 7, y + 25, d.accent, INK)
        if (d.pose == NpcPose.SIT_PHONE || d.pose == NpcPose.SIT_SLEEP) {
            b.box(x - 10, y + 31, x - 2, y + 34, d.outfit); b.box(x + 2, y + 31, x + 10, y + 34, d.outfit)
            b.box(x - 12, y + 34, x - 2, y + 35, INK); b.box(x + 2, y + 34, x + 12, y + 35, INK)
            if (d.pose == NpcPose.SIT_PHONE) {
                b.line(x - 8, y + 19, x - 2, y + 26, INK); b.outlined(x - 4, y + 23, x, y + 29, 0xFF252A3A.toInt(), 0xFFD9E5F1.toInt())
            } else {
                b.hline(x - 6, x - 3, y + 4, INK); b.hline(x + 3, x + 6, y + 4, INK)
            }
        } else {
            val step = if (d.pose == NpcPose.WALK && tick and 1 == 0) 2 else 0
            b.box(x - 7, y + 31, x - 3, y + 36 - step, d.fur); b.box(x + 3, y + 31, x + 7, y + 36 - (2 - step), d.fur)
            b.hline(x - 9, x - 2, y + 36, INK); b.hline(x + 2, x + 9, y + 36, INK)
        }
        drawBubble(b, slot, timeMs)
    }

    private fun drawBubble(b: PixelBuffer, slot: AmbientNpcSlot, timeMs: Long) {
        val lines = slot.definition.lines
        if (lines.isEmpty()) return
        val cycle = Math.floorMod(timeMs + slot.seed * 997L, 48_000L)
        if (cycle >= 1_500L) return
        val message = lines[((timeMs / 48_000L + slot.seed).toInt().let { Math.floorMod(it, lines.size) })]
        val chars = message.take(14).uppercase()
        val w = chars.length * 4 + 6
        val left = (slot.x - w / 2).coerceIn(2, 238 - w)
        val top = (slot.floorY - 54).coerceAtLeast(3)
        b.outlined(left, top, left + w, top + 12, WHITE, INK)
        b.box(slot.x.coerceIn(left + 2, left + w - 2), top + 12, slot.x.coerceIn(left + 2, left + w - 2) + 1, top + 14, INK)
        drawTinyText(b, chars, left + 3, top + 4)
    }

    private fun drawTinyText(b: PixelBuffer, text: String, x: Int, y: Int) {
        val glyphs = mapOf('A' to listOf(".#.", "#.#", "###", "#.#", "#.#"), 'B' to listOf("##.", "#.#", "##.", "#.#", "##."), 'C' to listOf(".##", "#..", "#..", "#..", ".##"), 'D' to listOf("##.", "#.#", "#.#", "#.#", "##."), 'E' to listOf("###", "#..", "##.", "#..", "###"), 'F' to listOf("###", "#..", "##.", "#..", "#.."), 'G' to listOf(".##", "#..", "#.#", "#.#", ".##"), 'H' to listOf("#.#", "#.#", "###", "#.#", "#.#"), 'I' to listOf("###", ".#.", ".#.", ".#.", "###"), 'J' to listOf("..#", "..#", "..#", "#.#", ".#."), 'M' to listOf("#.#", "###", "###", "#.#", "#.#"), 'N' to listOf("#.#", "###", "###", "###", "#.#"), 'O' to listOf(".#.", "#.#", "#.#", "#.#", ".#."), 'P' to listOf("##.", "#.#", "##.", "#..", "#.."), 'R' to listOf("##.", "#.#", "##.", "#.#", "#.#"), 'S' to listOf(".##", "#..", ".#.", "..#", "##."), 'T' to listOf("###", ".#.", ".#.", ".#.", ".#."), 'U' to listOf("#.#", "#.#", "#.#", "#.#", "###"), 'V' to listOf("#.#", "#.#", "#.#", "#.#", ".#."), ' ' to listOf("...", "...", "...", "...", "..."), '.' to listOf("...", "...", "...", "...", ".#"), '5' to listOf("###", "#..", "##.", "..#", "##."))
        text.forEachIndexed { i, c -> val rows = glyphs[c] ?: glyphs.getValue(' '); rows.forEachIndexed { row, pattern -> pattern.forEachIndexed { col, bit -> if (bit == '#') b.set(x + i * 4 + col, y + row, INK) } } }
    }
}

private fun PixelBuffer.ellipse(cx: Int, cy: Int, rx: Int, ry: Int, color: Int) {
    for (yy in -ry..ry) for (xx in -rx..rx) if (xx * xx * ry * ry + yy * yy * rx * rx <= rx * rx * ry * ry) set(cx + xx, cy + yy, color)
}
