package com.hoodie.app.pixel.diary.journey

import com.hoodie.app.core.time.DayPeriod
import com.hoodie.app.pixel.diary.DiaryMapPalette
import com.hoodie.app.pixel.renderer.PixelBuffer
import java.time.Instant
import java.time.ZoneId

/** Luz da jornada num instante: tinta sobre o chão, céu do horizonte, postes/janelas e estrelas. */
data class JourneyLight(
    val period: DayPeriod,
    val tintColor: Int,
    /** 0 = sem tinta (meio-dia). */
    val tintAlpha: Int,
    val lightsOn: Boolean,
    val skyTop: Int,
    val skyBottom: Int,
    /** 0..255: estrelas no céu à noite. */
    val starAlpha: Int,
)

/**
 * Luz contínua: em vez de pular entre manhã/dia/entardecer/noite, a tinta é
 * interpolada entre quadros-chave por minuto do dia. No replay o céu clareia e
 * escurece aos poucos; sem replay (mapa parado) é sempre dia claro.
 */
object JourneyLightingRenderer {

    private data class Key(val minute: Int, val tint: Int, val alpha: Int, val sky: Pair<Int, Int>, val stars: Int)

    private fun key(minute: Int, period: DayPeriod, stars: Int = 0): Key {
        val (tint, alpha) = DiaryMapPalette.tint(period) ?: (0xFFFFFFFF.toInt() to 0)
        return Key(minute, tint, alpha, JourneyPalette.sky(period), stars)
    }

    private val KEYS = listOf(
        key(0, DayPeriod.NIGHT, 255),
        key(5 * 60, DayPeriod.NIGHT, 255),
        key(6 * 60 + 30, DayPeriod.MORNING),
        key(8 * 60 + 30, DayPeriod.MORNING),
        key(9 * 60 + 30, DayPeriod.DAY),
        key(17 * 60, DayPeriod.DAY),
        key(18 * 60 + 30, DayPeriod.EVENING),
        key(20 * 60, DayPeriod.EVENING),
        key(21 * 60, DayPeriod.NIGHT, 255),
        key(24 * 60, DayPeriod.NIGHT, 255),
    )

    /** Postes e janelas acendem do entardecer até o amanhecer. */
    fun lightsOn(minuteOfDay: Int) = minuteOfDay >= 18 * 60 || minuteOfDay < 6 * 60 + 30

    val DAYLIGHT: JourneyLight = at(12 * 60)

    fun resolve(timestamp: Long?, zone: ZoneId): JourneyLight {
        if (timestamp == null) return DAYLIGHT
        val t = Instant.ofEpochMilli(timestamp).atZone(zone)
        return at(t.hour * 60 + t.minute)
    }

    fun at(minuteOfDay: Int): JourneyLight {
        val m = minuteOfDay.coerceIn(0, 24 * 60 - 1)
        val i = KEYS.indexOfLast { it.minute <= m }.coerceIn(0, KEYS.size - 2)
        val a = KEYS[i]; val b = KEYS[i + 1]
        val f = if (b.minute == a.minute) 0f else (m - a.minute).toFloat() / (b.minute - a.minute)
        // Cor da tinta: quando um lado não tem tinta, mantém a cor do outro e só varia a intensidade.
        val tint = when {
            a.alpha == 0 -> b.tint
            b.alpha == 0 -> a.tint
            else -> PixelBuffer.mix(a.tint, b.tint, f)
        }
        return JourneyLight(
            period = DayPeriod.of(m / 60),
            tintColor = tint,
            tintAlpha = lerp(a.alpha, b.alpha, f),
            lightsOn = lightsOn(m),
            skyTop = PixelBuffer.mix(a.sky.first, b.sky.first, f),
            skyBottom = PixelBuffer.mix(a.sky.second, b.sky.second, f),
            starAlpha = lerp(a.stars, b.stars, f),
        )
    }

    /** Tinta só sobre o chão (o céu já vem pintado na cor do horário). */
    fun applyTint(b: PixelBuffer, light: JourneyLight, fromY: Int = JourneyLayoutEngine.HEADER) {
        if (light.tintAlpha <= 0) return
        val start = (fromY.coerceAtLeast(0) * b.width).coerceAtMost(b.pixels.size)
        for (i in start until b.pixels.size) b.pixels[i] = PixelBuffer.blend(b.pixels[i], light.tintColor, light.tintAlpha)
    }

    /** Halo em degraus de um poste aceso (desenhado depois da tinta, para "furar" a noite). */
    fun lampHalo(b: PixelBuffer, x: Int, y: Int) {
        b.disc(x, y + 2, 6, JourneyPalette.LAMP_LIGHT and 0x22FFFFFF)
        b.disc(x, y + 1, 3, JourneyPalette.LAMP_LIGHT and 0x44FFFFFF)
        b.set(x, y, JourneyPalette.LAMP_LIGHT)
    }

    private fun lerp(a: Int, b: Int, f: Float) = (a + (b - a) * f).toInt()
}
