package com.hoodie.app.core.time

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject
import javax.inject.Singleton

/** Fonte única de tempo. Injetável para que os testes controlem o relógio. */
interface ClockProvider {
    fun nowMillis(): Long
    fun zone(): ZoneId
    fun now(): ZonedDateTime = Instant.ofEpochMilli(nowMillis()).atZone(zone())
    fun today(): LocalDate = now().toLocalDate()
}

@Singleton
class SystemClockProvider @Inject constructor() : ClockProvider {
    override fun nowMillis(): Long = System.currentTimeMillis()

    // Lido a cada chamada: o usuário pode trocar de fuso (viagem) com o app vivo.
    override fun zone(): ZoneId = ZoneId.systemDefault()
}

class FixedClock(var millis: Long, private val zoneId: ZoneId = ZoneId.of("America/Sao_Paulo")) : ClockProvider {
    override fun nowMillis(): Long = millis
    override fun zone(): ZoneId = zoneId
}

const val MINUTE_MS = 60_000L
const val HOUR_MS = 60 * MINUTE_MS
const val DAY_MS = 24 * HOUR_MS

fun Long.atZone(zone: ZoneId): ZonedDateTime = Instant.ofEpochMilli(this).atZone(zone)

fun ZonedDateTime.minuteOfDay(): Int = hour * 60 + minute

fun formatHm(minuteOfDay: Int): String = "%02d:%02d".format(minuteOfDay / 60 % 24, minuteOfDay % 60)

fun formatClock(millis: Long, zone: ZoneId): String = formatHm(millis.atZone(zone).minuteOfDay())

fun formatDuration(millis: Long): String {
    val totalMin = (millis / MINUTE_MS).coerceAtLeast(0)
    val h = totalMin / 60
    val m = totalMin % 60
    return when {
        h == 0L -> "${m}min"
        m == 0L -> "${h}h"
        else -> "${h}h%02d".format(m)
    }
}

/** Minuto do dia está em [start, end), aceitando janelas que cruzam a meia-noite. */
fun inWindow(minute: Int, start: Int, end: Int): Boolean =
    if (start <= end) minute in start until end else minute >= start || minute < end

/** Próximo instante (estritamente depois de [from]) em que o relógio local marca [minuteOfDay]. Respeita horário de verão. */
fun nextOccurrence(from: Long, minuteOfDay: Int, zone: ZoneId): Long {
    val start = from.atZone(zone)
    var candidate = start.toLocalDate().atStartOfDay(zone)
        .withHour(minuteOfDay / 60).withMinute(minuteOfDay % 60)
    if (!candidate.isAfter(start)) candidate = candidate.plusDays(1)
    return candidate.toInstant().toEpochMilli()
}

fun startOfDay(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toInstant().toEpochMilli()
