package com.hoodie.app.pixel.diary.overworld

import com.hoodie.app.engine.diary.journey.OverworldLayout
import com.hoodie.app.pixel.diary.journey.JourneyRect
import com.hoodie.app.pixel.renderer.PixelBuffer
import java.util.Random
import kotlin.math.hypot

/** Peça de decoração parada (árvore, arbusto, pedra, flores, lago). */
data class OverworldDecor(val kind: Kind, val x: Int, val y: Int, val variant: Int) {
    enum class Kind { TREE, BUSH, ROCK, FLOWERS, POND }

    val bounds: JourneyRect get() = when (kind) {
        Kind.TREE -> JourneyRect(x - 7, y - 16, 15, 18)
        Kind.BUSH -> JourneyRect(x - 4, y - 4, 9, 6)
        Kind.ROCK -> JourneyRect(x - 3, y - 3, 7, 5)
        Kind.FLOWERS -> JourneyRect(x - 3, y - 2, 7, 4)
        Kind.POND -> JourneyRect(x - 13, y - 6, 27, 12)
    }
}

/**
 * Chão e decoração por seed (a data): mesmo dia → mesmos pixels. Nada encosta em
 * trilhas, construções, placas ou portais.
 */
object OverworldDecorPlacer {
    private const val TRAIL_CLEARANCE = 9f

    fun place(layout: OverworldLayout, seed: Long): List<OverworldDecor> {
        val rnd = Random(seed * 31 + layout.height)
        val trail = layout.links.flatMap { l ->
            val n = (l.path.length / 3f).toInt().coerceAtLeast(1)
            (0..n).map { l.path.pointAt(it / n.toFloat()) }
        } + layout.stops.map { it.point }
        val out = mutableListOf<OverworldDecor>()
        fun free(d: OverworldDecor): Boolean {
            val r = d.bounds
            if (r.x < 1 || r.y < 1 || r.right > layout.width - 1 || r.bottom > layout.height - 1) return false
            if (layout.reserved.any { it.grow(3).intersects(r) }) return false
            if (out.any { it.bounds.grow(1).intersects(r) }) return false
            val cx = r.x + r.w / 2f; val cy = r.y + r.h / 2f
            val reach = maxOf(r.w, r.h) / 2f + TRAIL_CLEARANCE
            return trail.none { hypot(it.x - cx, it.y - cy) < reach }
        }
        // Um lago se couber, depois árvores, arbustos, pedras e flores numa grade com jitter.
        run {
            repeat(12) {
                val d = OverworldDecor(OverworldDecor.Kind.POND, 20 + rnd.nextInt(layout.width - 40), 14 + rnd.nextInt((layout.height - 28).coerceAtLeast(1)), 0)
                if (free(d)) { out += d; return@run }
            }
        }
        val step = 14
        for (gy in 6 until layout.height step step) for (gx in 4 until layout.width step step) {
            val x = gx + rnd.nextInt(step - 2); val y = gy + rnd.nextInt(step - 2)
            val roll = rnd.nextInt(100)
            val kind = when {
                roll < 30 -> OverworldDecor.Kind.TREE
                roll < 48 -> OverworldDecor.Kind.BUSH
                roll < 60 -> OverworldDecor.Kind.ROCK
                roll < 78 -> OverworldDecor.Kind.FLOWERS
                else -> null
            } ?: continue
            val d = OverworldDecor(kind, x, y + if (kind == OverworldDecor.Kind.TREE) 12 else 0, rnd.nextInt(4))
            if (free(d)) out += d
        }
        return out.sortedBy { it.y }
    }

    /** Grama com pontilhado e tufos por seed. */
    fun paintGround(b: PixelBuffer, seed: Long) {
        val rnd = Random(seed)
        b.fill(OverworldPalette.GRASS)
        for (y in 0 until b.height) for (x in 0 until b.width) {
            val h = ((x * 73856093) xor (y * 19349663) xor seed.toInt()) and 0xFF
            when {
                h < 14 -> b.set(x, y, OverworldPalette.GRASS_DARK)
                h > 246 -> b.set(x, y, OverworldPalette.GRASS_LIGHT)
            }
        }
        repeat(b.width * b.height / 220) {
            val x = rnd.nextInt(b.width); val y = rnd.nextInt(b.height)
            b.set(x, y, OverworldPalette.GRASS_TUFT); b.set(x + 1, y - 1, OverworldPalette.GRASS_TUFT); b.set(x + 2, y, OverworldPalette.GRASS_TUFT)
        }
    }

    fun paintStatic(b: PixelBuffer, d: OverworldDecor) {
        val x = d.x; val y = d.y
        when (d.kind) {
            OverworldDecor.Kind.TREE -> {
                b.box(x - 5, y, x + 5, y + 1, PixelBuffer.mix(OverworldPalette.GRASS, OverworldPalette.OUTLINE, 0.25f))
                b.box(x - 1, y - 5, x + 1, y, OverworldPalette.TRUNK)
                b.disc(x, y - 10, 7, OverworldPalette.OUTLINE)
                b.disc(x, y - 10, 6, OverworldPalette.TREE)
                b.disc(x - 2, y - 12, 3, OverworldPalette.TREE_LIGHT)
                if (d.variant == 1) b.set(x + 2, y - 8, OverworldPalette.FLOWER_A)
            }
            OverworldDecor.Kind.BUSH -> {
                b.disc(x, y - 1, 4, OverworldPalette.OUTLINE); b.disc(x, y - 1, 3, OverworldPalette.TREE_LIGHT)
                b.set(x - 1, y - 2, OverworldPalette.GRASS_LIGHT)
            }
            OverworldDecor.Kind.ROCK -> {
                b.outlined(x - 3, y - 2, x + 3, y + 1, OverworldPalette.STONE, OverworldPalette.OUTLINE)
                b.hline(x - 2, x, y - 1, OverworldPalette.STONE_LIGHT)
            }
            OverworldDecor.Kind.FLOWERS -> {
                val c = if (d.variant % 2 == 0) OverworldPalette.FLOWER_A else OverworldPalette.FLOWER_B
                b.set(x - 2, y, c); b.set(x, y - 1, c); b.set(x + 2, y, c); b.set(x + 1, y + 1, OverworldPalette.GRASS_TUFT)
            }
            OverworldDecor.Kind.POND -> {
                for (dy in -5..5) for (dx in -12..12) {
                    val v = dx * dx / 144f + dy * dy / 25f
                    if (v <= 1f) b.set(x + dx, y + dy, if (v > 0.8f) OverworldPalette.OUTLINE else OverworldPalette.WATER)
                }
                b.hline(x - 6, x - 2, y - 2, OverworldPalette.WATER_LIGHT)
            }
        }
    }

    /** Vida: brilho na água e copas balançando (camada 6). */
    fun paintAnimated(b: PixelBuffer, d: OverworldDecor, timeMs: Long) {
        val f = ((timeMs / 400 + d.x) % 4).toInt()
        when (d.kind) {
            OverworldDecor.Kind.POND -> b.hline(d.x - 2 + f, d.x + f, d.y + 2, OverworldPalette.WATER_LIGHT)
            OverworldDecor.Kind.TREE -> if (f == 0) b.set(d.x + 4, d.y - 14, OverworldPalette.TREE_LIGHT)
            else -> Unit
        }
    }
}
