package com.hoodie.app.pixel.renderer

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.scene.Light
import com.hoodie.app.pixel.scene.PixelScene
import com.hoodie.app.pixel.scene.SceneEnv
import com.hoodie.app.pixel.scene.SceneId

/**
 * Overlay de iluminação: a mesma arte base serve para manhã, dia, entardecer e
 * noite. Cada pixel recebe um fator RGB (0..256); janelas e telas são emissivas
 * e lâmpadas criam um halo. O mapa é calculado uma vez por cena/período.
 */
object Lighting {

    private data class Key(val scene: SceneId, val period: DayPeriod, val lights: List<Light>)

    private val cache = HashMap<Key, IntArray?>()

    private fun tint(period: DayPeriod): Triple<Int, Int, Int>? = when (period) {
        DayPeriod.DAY -> null
        DayPeriod.MORNING -> Triple(256, 238, 216)
        DayPeriod.EVENING -> Triple(238, 192, 182)
        DayPeriod.NIGHT -> Triple(122, 134, 204)
    }

    fun map(scene: PixelScene, env: SceneEnv): IntArray? {
        if (!scene.usesLighting) return null
        val lights = scene.lights(env)
        val key = Key(scene.id, env.period, lights)
        return synchronized(cache) { cache.getOrPut(key) { build(scene, env.period, lights) } }
    }

    private fun pack(r: Int, g: Int, b: Int) = (r.coerceIn(0, 256) shl 20) or (g.coerceIn(0, 256) shl 10) or b.coerceIn(0, 256)

    private fun build(scene: PixelScene, period: DayPeriod, lights: List<Light>): IntArray? {
        val (br, bg, bb) = tint(period) ?: return null
        val w = scene.width; val h = scene.height
        val r = IntArray(w * h) { br }; val g = IntArray(w * h) { bg }; val b = IntArray(w * h) { bb }
        // Luz quente das lâmpadas só faz diferença quando escurece.
        val glows = period == DayPeriod.NIGHT || period == DayPeriod.EVENING
        for (light in lights) when (light) {
            is Light.Glow -> if (glows) {
                val rad = light.radius
                for (y in (light.cy - rad).coerceAtLeast(0) until (light.cy + rad).coerceAtMost(h)) {
                    for (x in (light.cx - rad).coerceAtLeast(0) until (light.cx + rad).coerceAtMost(w)) {
                        val dx = x - light.cx; val dy = y - light.cy
                        val d2 = dx * dx + dy * dy
                        if (d2 >= rad * rad) continue
                        // Halo em degraus (3 anéis) para manter a estética pixel.
                        val ring = (Math.sqrt(d2.toDouble()) / rad * 3).toInt()
                        val k = light.strength * (3 - ring) / 3f
                        val i = y * w + x
                        r[i] = maxOf(r[i], lerp(br, 256, k)); g[i] = maxOf(g[i], lerp(bg, 236, k)); b[i] = maxOf(b[i], lerp(bb, 190, k))
                    }
                }
            }
            is Light.Emissive -> for (y in light.y0.coerceAtLeast(0)..light.y1.coerceAtMost(h - 1)) {
                for (x in light.x0.coerceAtLeast(0)..light.x1.coerceAtMost(w - 1)) {
                    val i = y * w + x; r[i] = 256; g[i] = 256; b[i] = 256
                }
            }
        }
        return IntArray(w * h) { pack(r[it], g[it], b[it]) }
    }

    private fun lerp(a: Int, b: Int, t: Float) = (a + (b - a) * t).toInt()

    fun apply(buf: PixelBuffer, map: IntArray?) {
        if (map == null) return
        val px = buf.pixels
        for (i in px.indices) {
            val f = map[i]
            if (f == FULL) continue
            val c = px[i]
            val r = (((c shr 16) and 0xFF) * (f ushr 20)) shr 8
            val g = (((c shr 8) and 0xFF) * ((f shr 10) and 0x3FF)) shr 8
            val b = ((c and 0xFF) * (f and 0x3FF)) shr 8
            px[i] = (0xFF shl 24) or (r.coerceAtMost(255) shl 16) or (g.coerceAtMost(255) shl 8) or b.coerceAtMost(255)
        }
    }

    /** Escurece tudo (transições de cena). [amount] 0 = preto, 1 = normal. */
    fun fade(buf: PixelBuffer, amount: Float) {
        if (amount >= 1f) return
        // Fade em degraus: 4 níveis, como nos consoles antigos.
        val k = ((amount.coerceIn(0f, 1f) * 4).toInt() * 64).coerceAtMost(256)
        val px = buf.pixels
        for (i in px.indices) {
            val c = px[i]
            px[i] = (0xFF shl 24) or (((((c shr 16) and 0xFF) * k) shr 8) shl 16) or (((((c shr 8) and 0xFF) * k) shr 8) shl 8) or (((c and 0xFF) * k) shr 8)
        }
    }

    private val FULL = pack(256, 256, 256)
}
