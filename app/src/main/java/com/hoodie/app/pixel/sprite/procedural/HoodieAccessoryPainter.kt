package com.hoodie.app.pixel.sprite.procedural

import com.hoodie.app.pixel.renderer.PixelBuffer
import com.hoodie.app.pixel.sprite.*
import com.hoodie.app.pixel.sprite.procedural.HoodieArmsPainter.redrawPaws

/** Procedural HoodieAccessoryPainter; preserves drawing order and semantic part ownership. */
internal object HoodieAccessoryPainter {
    fun drawItemFront(b: PixelBuffer, p: HoodiePose, left: ArmShape, right: ArmShape, up: Int, a: SpriteAnchors) {
        val o = HoodiePalette.OUTLINE
        when (p.item) {
            Item.NONE -> Unit
            Item.BOOK, Item.MENU -> {
                val cover = if (p.item == Item.MENU) 0xFF2F4A3C.toInt() else 0xFFC8484A.toInt()
                b.outlined(15, 35 + up, 32, 46 + up, cover, o)
                b.box(16, 36 + up, 31, 37 + up, 0xFFF4EBD8.toInt())
                if (p.item == Item.MENU) for (y in 39..44 step 2) b.hline(17, 30, y + up, 0xFFDDE7E0.toInt())
                else { b.vline(23, 36 + up, 45 + up, 0xFF8E2E33.toInt()); b.vline(24, 36 + up, 45 + up, 0xFF8E2E33.toInt()) }
                redrawPaws(b, left, right, up)
            }
            Item.CONTROLLER -> {
                b.outlined(15, 38 + up, 32, 45 + up, 0xFF3B3F55.toInt(), o)
                b.set(18, 41 + up, 0xFFDDE2F0.toInt()); b.set(17, 41 + up, 0xFFDDE2F0.toInt()); b.set(19, 41 + up, 0xFFDDE2F0.toInt())
                b.set(18, 40 + up, 0xFFDDE2F0.toInt()); b.set(18, 42 + up, 0xFFDDE2F0.toInt())
                b.set(28, 40 + up, 0xFFE05A5A.toInt()); b.set(29, 42 + up, 0xFF5AC8E0.toInt())
                redrawPaws(b, left, right, up)
            }
            Item.DUMBBELL -> {
                val hands = if (p.itemInBothHands) listOf(a.leftHand, a.rightHand) else listOf(a.rightHand)
                hands.forEach { drawItemAt(b, Item.DUMBBELL, it) }
            }
            else -> drawItemAt(b, p.item, a.rightHand)
        }
    }

    fun drawItemAt(b: PixelBuffer, item: Item, hand: Point) {
        val o = HoodiePalette.OUTLINE
        val rx = hand.x; val ry = hand.y
        when (item) {
            Item.MUG -> {
                b.outlined(rx + 1, ry - 6, rx + 6, ry, 0xFFF4F1EA.toInt(), o)
                b.hline(rx + 2, rx + 5, ry - 5, 0xFF6B3E26.toInt())
                b.vline(rx + 7, ry - 4, ry - 2, o)
                b.set(rx + 3, ry - 2, 0xFFE07A93.toInt())
            }
            Item.PHONE -> {
                b.outlined(rx - 2, ry - 7, rx + 2, ry + 1, 0xFF2E3350.toInt(), o)
                b.box(rx - 1, ry - 6, rx + 1, ry - 1, 0xFF9FE3F0.toInt())
            }
            Item.BOOK -> {
                // Livro pequeno ancorado na mão para NPCs leitores; usa o mesmo contorno e papel do Hoodie.
                b.outlined(rx - 6, ry - 7, rx + 5, ry + 1, 0xFFC8484A.toInt(), o)
                b.box(rx - 4, ry - 6, rx + 4, ry - 5, 0xFFF4EBD8.toInt())
                b.vline(rx, ry - 6, ry, 0xFF8E2E33.toInt())
            }
            Item.BOTTLE -> {
                b.outlined(rx, ry - 8, rx + 4, ry + 1, 0xFF8AD6F2.toInt(), o)
                b.box(rx + 1, ry - 9, rx + 3, ry - 8, 0xFF2F6FD0.toInt())
            }
            Item.FORK -> {
                b.vline(rx + 2, ry - 7, ry + 1, 0xFFD0D4DE.toInt())
                b.set(rx + 1, ry - 7, 0xFFD0D4DE.toInt()); b.set(rx + 3, ry - 7, 0xFFD0D4DE.toInt())
            }
            Item.PAN -> {
                b.outlined(rx + 2, ry - 3, rx + 12, ry + 1, 0xFF3A3D4A.toInt(), o)
                b.hline(rx + 4, rx + 10, ry - 2, 0xFFF2D25C.toInt())
            }
            Item.BROOM -> {
                b.box(rx + 1, ry - 16, rx + 2, ry + 15, 0xFF8B5A2B.toInt())
                b.outlined(rx - 3, ry + 15, rx + 6, ry + 21, 0xFFE5C25A.toInt(), o)
                for (x in rx - 2..rx + 5 step 2) b.vline(x, ry + 17, ry + 20, 0xFFC49A35.toInt())
            }
            Item.DUMBBELL -> {
                b.hline(rx - 5, rx + 5, ry, 0xFF8A8F9E.toInt())
                b.outlined(rx - 7, ry - 3, rx - 4, ry + 3, 0xFF3A3D4A.toInt(), o)
                b.outlined(rx + 4, ry - 3, rx + 7, ry + 3, 0xFF3A3D4A.toInt(), o)
            }
            Item.PENCIL -> {
                // Lápis inclinado: corpo amarelo, ponta de grafite apoiada no caderno.
                b.line(rx + 4, ry - 7, rx + 1, ry - 1, 0xFFF2CF5B.toInt())
                b.line(rx + 5, ry - 7, rx + 2, ry - 1, 0xFFD9A83A.toInt())
                b.set(rx + 5, ry - 8, 0xFFE07A93.toInt())
                b.set(rx + 1, ry, o)
            }
            Item.PRODUCT -> {
                // Caixinha de produto com rótulo.
                b.outlined(rx - 1, ry - 9, rx + 5, ry + 1, 0xFF4F7FC9.toInt(), o)
                b.box(rx, ry - 6, rx + 4, ry - 4, 0xFFF6F3EA.toInt())
                b.set(rx + 2, ry - 8, 0xFFF2CF5B.toInt())
            }
            Item.SNACK -> {
                // Biscoito com gotas.
                b.disc(rx + 2, ry - 3, 3, o)
                b.disc(rx + 2, ry - 3, 2, 0xFFD9A15A.toInt())
                b.set(rx + 1, ry - 4, 0xFF6B3E26.toInt()); b.set(rx + 3, ry - 2, 0xFF6B3E26.toInt())
            }
            else -> Unit
        }
    }
}
