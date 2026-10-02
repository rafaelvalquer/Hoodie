package com.hoodie.app.pixel.diary

import com.hoodie.app.core.time.DayPeriod
import java.time.Instant
import java.time.ZoneId

/** Luz do mapa num instante: período, tinta (cor, intensidade) e se postes e janelas acendem. */
data class DiaryLighting(val period: DayPeriod, val tint: Pair<Int, Int>?, val lightsOn: Boolean)

/**
 * Única decisão de horário do mapa: timestamp → período → tinta/luzes. Sem
 * timestamp (mapa parado, sem replay) é dia. Renderer e UI só consomem o resultado.
 */
object DiaryLightingResolver {

    fun resolve(timestamp: Long?, zone: ZoneId): DiaryLighting =
        forPeriod(timestamp?.let { DayPeriod.of(Instant.ofEpochMilli(it).atZone(zone).hour) } ?: DayPeriod.DAY)

    fun forPeriod(period: DayPeriod) = DiaryLighting(
        period = period,
        tint = DiaryMapPalette.tint(period),
        lightsOn = period == DayPeriod.EVENING || period == DayPeriod.NIGHT,
    )
}
